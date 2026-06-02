package com.firstteam.taskbountyplatform.point.service;

import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.common.enums.PointFlowType;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PointService {

    private final PointAccountRepository pointAccountRepository;
    private final PointFlowRepository pointFlowRepository;
    private final PlatformConfig platformConfig;

    public PointService(PointAccountRepository pointAccountRepository,
                        PointFlowRepository pointFlowRepository,
                        PlatformConfig platformConfig) {
        this.pointAccountRepository = pointAccountRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.platformConfig = platformConfig;
    }

    /**
     * Get the point account for a user. Creates one with initial points if it does not exist.
     */
    public PointAccount getAccount(Long userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseGet(() -> {
                    PointAccount account = new PointAccount();
                    account.setUserId(userId);
                    account.setAvailablePoints(platformConfig.getInitialPoints());
                    account.setFrozenPoints(0);
                    account.setTotalIncome(0);
                    account.setTotalExpense(0);
                    return pointAccountRepository.save(account);
                });
    }

    /**
     * Freeze a specified amount of points from available balance.
     * Uses pessimistic lock to prevent concurrency issues.
     */
    @Transactional
    public PointAccount freezePoints(Long userId, int amount) {
        if (amount <= 0) {
            throw new RuntimeException("冻结金额必须大于0");
        }

        PointAccount account = pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("点券账户不存在"));

        int availableBefore = account.getAvailablePoints();
        if (availableBefore < amount) {
            throw new RuntimeException("可用点券不足，当前可用：" + availableBefore + "，需要冻结：" + amount);
        }

        account.setAvailablePoints(availableBefore - amount);
        account.setFrozenPoints(account.getFrozenPoints() + amount);
        pointAccountRepository.save(account);

        // Create PointFlow (FREEZE type)
        PointFlow flow = new PointFlow();
        flow.setUserId(userId);
        flow.setTaskId(null);
        flow.setChangeAmount(-amount);
        flow.setBalanceBefore(availableBefore);
        flow.setBalanceAfter(account.getAvailablePoints());
        flow.setFlowType(PointFlowType.FREEZE);
        flow.setDescription("冻结点券 " + amount + "，用于任务发布");
        pointFlowRepository.save(flow);

        return account;
    }

    /**
     * Unfreeze a specified amount of points back to available balance.
     * Uses pessimistic lock to prevent concurrency issues.
     */
    @Transactional
    public PointAccount unfreezePoints(Long userId, int amount) {
        if (amount <= 0) {
            throw new RuntimeException("解冻金额必须大于0");
        }

        PointAccount account = pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("点券账户不存在"));

        int frozenBefore = account.getFrozenPoints();
        if (frozenBefore < amount) {
            throw new RuntimeException("冻结点券不足，当前冻结：" + frozenBefore + "，需要解冻：" + amount);
        }

        account.setFrozenPoints(frozenBefore - amount);
        account.setAvailablePoints(account.getAvailablePoints() + amount);
        pointAccountRepository.save(account);

        // Create PointFlow (UNFREEZE type)
        PointFlow flow = new PointFlow();
        flow.setUserId(userId);
        flow.setTaskId(null);
        flow.setChangeAmount(amount);
        flow.setBalanceBefore(account.getAvailablePoints() - amount);
        flow.setBalanceAfter(account.getAvailablePoints());
        flow.setFlowType(PointFlowType.UNFREEZE);
        flow.setDescription("解冻点券 " + amount);
        pointFlowRepository.save(flow);

        return account;
    }

    /**
     * Transfer points from one user to another (used when task is completed).
     * Deducts from fromUser's frozenPoints and adds to toUser's availablePoints.
     * Uses pessimistic locks on both accounts.
     */
    @Transactional
    public void transferPoints(Long fromUserId, Long toUserId, int amount, Long taskId) {
        if (amount <= 0) {
            throw new RuntimeException("转账金额必须大于0");
        }

        // Lock and deduct from sender's frozen points
        PointAccount fromAccount = pointAccountRepository.findByUserIdForUpdate(fromUserId)
                .orElseThrow(() -> new RuntimeException("转出方点券账户不存在"));

        int fromFrozenBefore = fromAccount.getFrozenPoints();
        if (fromFrozenBefore < amount) {
            throw new RuntimeException("转出方冻结点券不足，当前冻结：" + fromFrozenBefore + "，需要转账：" + amount);
        }

        fromAccount.setFrozenPoints(fromFrozenBefore - amount);
        fromAccount.setTotalExpense(fromAccount.getTotalExpense() + amount);
        pointAccountRepository.save(fromAccount);

        // Create PointFlow EXPENSE for sender
        PointFlow expenseFlow = new PointFlow();
        expenseFlow.setUserId(fromUserId);
        expenseFlow.setTaskId(taskId);
        expenseFlow.setChangeAmount(-amount);
        expenseFlow.setBalanceBefore(fromFrozenBefore);
        expenseFlow.setBalanceAfter(fromAccount.getFrozenPoints());
        expenseFlow.setFlowType(PointFlowType.EXPENSE);
        expenseFlow.setDescription("点券支出，转账给用户 " + toUserId + "，任务ID：" + taskId);
        pointFlowRepository.save(expenseFlow);

        // Lock and add to receiver's available points
        PointAccount toAccount = pointAccountRepository.findByUserIdForUpdate(toUserId)
                .orElseThrow(() -> new RuntimeException("接收方点券账户不存在"));

        int toAvailableBefore = toAccount.getAvailablePoints();
        toAccount.setAvailablePoints(toAvailableBefore + amount);
        toAccount.setTotalIncome(toAccount.getTotalIncome() + amount);
        pointAccountRepository.save(toAccount);

        // Create PointFlow INCOME for receiver
        PointFlow incomeFlow = new PointFlow();
        incomeFlow.setUserId(toUserId);
        incomeFlow.setTaskId(taskId);
        incomeFlow.setChangeAmount(amount);
        incomeFlow.setBalanceBefore(toAvailableBefore);
        incomeFlow.setBalanceAfter(toAccount.getAvailablePoints());
        incomeFlow.setFlowType(PointFlowType.INCOME);
        incomeFlow.setDescription("点券收入，来自用户 " + fromUserId + "，任务ID：" + taskId);
        pointFlowRepository.save(incomeFlow);
    }

    /**
     * Penalize a user's points (deduction goes to platform).
     * Deducts from availablePoints first, then frozenPoints if needed.
     */
    @Transactional
    public PointAccount penalizePoints(Long userId, int amount, Long taskId, String reason) {
        if (amount <= 0) {
            throw new RuntimeException("扣除金额必须大于0");
        }

        PointAccount account = pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("点券账户不存在"));

        int availableBefore = account.getAvailablePoints();
        int frozenBefore = account.getFrozenPoints();
        int totalBefore = availableBefore + frozenBefore;

        if (totalBefore < amount) {
            throw new RuntimeException("点券不足，当前可用：" + availableBefore
                    + "，冻结：" + frozenBefore + "，需要扣除：" + amount);
        }

        // Deduct from available first, then frozen
        int remainingDeduction = amount;
        int deductedFromAvailable = 0;
        int deductedFromFrozen = 0;

        if (availableBefore > 0) {
            deductedFromAvailable = Math.min(availableBefore, remainingDeduction);
            account.setAvailablePoints(availableBefore - deductedFromAvailable);
            remainingDeduction -= deductedFromAvailable;
        }

        if (remainingDeduction > 0 && frozenBefore > 0) {
            deductedFromFrozen = Math.min(frozenBefore, remainingDeduction);
            account.setFrozenPoints(frozenBefore - deductedFromFrozen);
            remainingDeduction -= deductedFromFrozen;
        }

        pointAccountRepository.save(account);

        // Create PointFlow EXPENSE for the penalty
        PointFlow flow = new PointFlow();
        flow.setUserId(userId);
        flow.setTaskId(taskId);
        flow.setChangeAmount(-amount);
        flow.setBalanceBefore(availableBefore + frozenBefore);
        flow.setBalanceAfter(account.getAvailablePoints() + account.getFrozenPoints());
        flow.setFlowType(PointFlowType.EXPENSE);
        flow.setDescription("点券处罚扣除 " + amount + "（可用扣除：" + deductedFromAvailable
                + "，冻结扣除：" + deductedFromFrozen + "），原因：" + reason);
        pointFlowRepository.save(flow);

        return account;
    }

    /**
     * Get paginated point flow history for a user with optional date range and type filtering.
     */
    public Page<PointFlow> getPointFlows(Long userId, LocalDateTime start, LocalDateTime end,
                                          String flowType, Pageable pageable) {
        if (flowType != null && !flowType.isEmpty()) {
            if (start != null && end != null) {
                // Filter by type and date range
                return filterByTypeAndDateRange(userId, flowType, start, end, pageable);
            }
            // Filter by type only
            return pointFlowRepository.findByUserIdAndFlowTypeOrderByCreatedAtDesc(userId, flowType, pageable);
        }

        if (start != null && end != null) {
            // Filter by date range only
            return pointFlowRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    userId, start, end, pageable);
        }

        // No filters, return all
        return pointFlowRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Custom filter for both type and date range since the repository doesn't have a combined method.
     * Uses a reasonable default page size and does in-memory filtering on a larger dataset.
     */
    private Page<PointFlow> filterByTypeAndDateRange(Long userId, String flowType,
                                                      LocalDateTime start, LocalDateTime end,
                                                      Pageable pageable) {
        // Retrieve all flows for the user in the date range, then filter by type in memory.
        // For large datasets this would need a proper repository query.
        Page<PointFlow> allInRange = pointFlowRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, start, end,
                        PageRequest.of(0, Integer.MAX_VALUE));
        java.util.List<PointFlow> filtered = allInRange.getContent().stream()
                .filter(f -> flowType.equalsIgnoreCase(
                        f.getFlowType() != null ? f.getFlowType().name() : ""))
                .collect(java.util.stream.Collectors.toList());

        int startIdx = (int) pageable.getOffset();
        int endIdx = Math.min(startIdx + pageable.getPageSize(), filtered.size());

        if (startIdx >= filtered.size()) {
            return new org.springframework.data.domain.PageImpl<>(
                    java.util.Collections.emptyList(), pageable, filtered.size());
        }

        return new org.springframework.data.domain.PageImpl<>(
                filtered.subList(startIdx, endIdx), pageable, filtered.size());
    }

    /**
     * Get the platform's total point balance (sum of all availablePoints).
     */
    public long getPlatformBalance() {
        return pointAccountRepository.findAll().stream()
                .mapToLong(account -> account.getAvailablePoints() != null
                        ? account.getAvailablePoints().longValue() : 0L)
                .sum();
    }
}
