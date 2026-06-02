package com.firstteam.taskbountyplatform.credit.repository;

import com.firstteam.taskbountyplatform.credit.entity.CreditRuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditRuleConfigRepository extends JpaRepository<CreditRuleConfig, Long> {
    Optional<CreditRuleConfig> findByRuleKey(String ruleKey);
}
