package com.firstteam.taskbountyplatform.auth.service;

import com.firstteam.taskbountyplatform.auth.dto.LoginRequest;
import com.firstteam.taskbountyplatform.auth.dto.LoginResponse;
import com.firstteam.taskbountyplatform.auth.dto.UserInfoDTO;
import com.firstteam.taskbountyplatform.auth.security.JwtUtils;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.AccountRole;
import com.firstteam.taskbountyplatform.common.enums.UserStatus;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PointAccountRepository pointAccountRepository;
    private final JwtUtils jwtUtils;
    private final UserContext userContext;
    private final PlatformConfig platformConfig;

    public AuthService(UserRepository userRepository, PointAccountRepository pointAccountRepository,
                       JwtUtils jwtUtils, UserContext userContext, PlatformConfig platformConfig) {
        this.userRepository = userRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.jwtUtils = jwtUtils;
        this.userContext = userContext;
        this.platformConfig = platformConfig;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String studentNo = request.getStudentNo();
        // Simulated OAuth2: auto-create user on first login
        Optional<User> existingUser = userRepository.findByStudentNo(studentNo);
        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Check if account is frozen
            if (user.getAccountStatus() == UserStatus.FROZEN) {
                throw new RuntimeException("账户已被冻结，无法登录");
            }
            user.setLastLoginTime(LocalDateTime.now());
            userRepository.save(user);
        } else {
            // Create new user with defaults
            user = new User();
            user.setStudentNo(studentNo);
            user.setRealName("用户" + studentNo); // placeholder
            user.setNickname(generateUniqueNickname());
            user.setAvatarUrl("/avatars/default.png");
            user.setCreditScore(platformConfig.getCredit().getInitialScore());
            user.setAccountStatus(UserStatus.NORMAL);
            user.setRole(AccountRole.USER);
            user.setCreatedAt(LocalDateTime.now());
            user.setLastLoginTime(LocalDateTime.now());
            user = userRepository.save(user);

            // Create point account with initial points
            PointAccount account = new PointAccount();
            account.setUserId(user.getId());
            account.setAvailablePoints(platformConfig.getInitialPoints());
            account.setFrozenPoints(0);
            account.setTotalIncome(0);
            account.setTotalExpense(0);
            pointAccountRepository.save(account);
        }

        String token = jwtUtils.generateToken(user.getId(), user.getStudentNo(), user.getRole().name());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(toUserInfoDTO(user));
        return response;
    }

    public UserInfoDTO getCurrentUser() {
        Long userId = userContext.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElse(new PointAccount());
        UserInfoDTO dto = toUserInfoDTO(user);
        dto.setAvailablePoints(account.getAvailablePoints());
        dto.setFrozenPoints(account.getFrozenPoints());
        dto.setTotalIncome(account.getTotalIncome());
        dto.setTotalExpense(account.getTotalExpense());
        return dto;
    }

    private String generateUniqueNickname() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        String nickname;
        do {
            StringBuilder sb = new StringBuilder("用户");
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            nickname = sb.toString();
        } while (userRepository.existsByNickname(nickname));
        return nickname;
    }

    private UserInfoDTO toUserInfoDTO(User user) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(user.getId());
        dto.setStudentNo(user.getStudentNo());
        dto.setRealName(user.getRealName());
        dto.setNickname(user.getNickname());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setAnnouncement(user.getAnnouncement());
        dto.setGrade(user.getGrade());
        dto.setCollege(user.getCollege());
        dto.setAcademy(user.getAcademy());
        dto.setCreditScore(user.getCreditScore());
        dto.setAccountStatus(user.getAccountStatus().name());
        dto.setRole(user.getRole().name());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
