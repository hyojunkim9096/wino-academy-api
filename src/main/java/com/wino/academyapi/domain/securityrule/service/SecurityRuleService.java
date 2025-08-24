// src/main/java/com/wino/academyapi/domain/securityrule/service/SecurityRuleService.java
package com.wino.academyapi.domain.securityrule.service;

import com.wino.academyapi.domain.securityrule.entity.SecurityRule;
import com.wino.academyapi.domain.securityrule.entity.SecurityRuleDeleted;
import com.wino.academyapi.domain.securityrule.repository.SecurityRuleDeletedRepository;
import com.wino.academyapi.domain.securityrule.repository.SecurityRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityRuleService {

    private final SecurityRuleRepository repo;
    private final SecurityRuleDeletedRepository deletedRepo;

    private volatile List<SecurityRule> cached = Collections.emptyList();

    @Transactional(readOnly = true)
    public List<SecurityRule> loadActiveRules() {
        try {
            List<SecurityRule> rules = repo.findByEnabledTrueOrderByOrderIndexAscIdAsc();
            cached = rules;
            return rules;
        } catch (DataAccessException e) {
            log.warn("[SecurityRule] DB 접근 실패 → 폴백 사용", e);
            cached = Collections.emptyList();
            return cached;
        }
    }

    public List<SecurityRule> current() { return cached; }
    public void refresh() { loadActiveRules(); }

    @Transactional(readOnly = true)
    public List<SecurityRule> list() { return repo.findAll(); }

    @Transactional
    public SecurityRule create(SecurityRule r) {
        SecurityRule saved = repo.save(r);
        refresh();
        return saved;
    }

    @Transactional
    public SecurityRule update(Long id, SecurityRule patch) {
        SecurityRule cur = repo.findById(id).orElseThrow();
        cur.setHttpMethod(patch.getHttpMethod());
        cur.setPattern(patch.getPattern());
        cur.setAccessType(patch.getAccessType());
        cur.setAuthoritiesCsv(patch.getAuthoritiesCsv());
        cur.setOrderIndex(patch.getOrderIndex());
        cur.setEnabled(patch.getEnabled());
        cur.setRemark(patch.getRemark());
        SecurityRule saved = repo.save(cur);
        refresh();
        return saved;
    }

    @Transactional
    public void deleteAndArchive(Long id, String deletedBy) {
        SecurityRule r = repo.findById(id).orElseThrow();

        deletedRepo.save(SecurityRuleDeleted.builder()
                .ruleId(r.getId())
                .httpMethod(r.getHttpMethod())
                .pattern(r.getPattern())
                .accessType(r.getAccessType().name())
                .authoritiesCsv(r.getAuthoritiesCsv())
                .orderIndex(r.getOrderIndex())
                .enabled(r.getEnabled())
                .remark(r.getRemark())
                .deletedAt(LocalDateTime.now())
                .deletedBy(deletedBy)
                .build());

        repo.delete(r);
        refresh();
    }

    @Transactional(readOnly = true)
    public List<SecurityRuleDeleted> listDeleted() {
        return deletedRepo.findAllByOrderByDeletedAtDesc();
    }
}
