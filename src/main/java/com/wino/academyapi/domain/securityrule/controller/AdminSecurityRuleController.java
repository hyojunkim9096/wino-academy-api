package com.wino.academyapi.domain.securityrule.controller;

import com.wino.academyapi.domain.securityrule.dto.SecurityRuleDto;
import com.wino.academyapi.domain.securityrule.entity.SecurityRule;
import com.wino.academyapi.domain.securityrule.entity.SecurityRuleAccessType;
import com.wino.academyapi.domain.securityrule.entity.SecurityRuleDeleted;
import com.wino.academyapi.domain.securityrule.repository.SecurityRuleRepository;
import com.wino.academyapi.domain.securityrule.service.SecurityRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/security-rules")
@RequiredArgsConstructor
public class AdminSecurityRuleController {
    private final SecurityRuleRepository repo;
    private final SecurityRuleService service;

    @GetMapping public List<SecurityRule> list() { return repo.findAll(); }

    /** 삭제 이력 목록 (deleted 테이블) */
    @GetMapping("/deleted")
    public List<SecurityRuleDeleted> listDeleted() { return service.listDeleted(); }

    @PostMapping @Transactional
    public ResponseEntity<?> create(@RequestBody SecurityRuleDto dto) {
        validate(dto);
        SecurityRule saved = repo.save(toEntity(dto, null));
        service.refresh();
        return ResponseEntity.created(URI.create("/api/admin/security-rules/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}") @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SecurityRuleDto dto) {
        validate(dto);
        SecurityRule prev = repo.findById(id).orElseThrow();
        SecurityRule saved = repo.save(toEntity(dto, prev));
        service.refresh();
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}") @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        String who = currentUserIdOr("system");
        service.deleteAndArchive(id, who);
        return ResponseEntity.noContent().build();
    }

    /* 내부 유틸 */
    private void validate(SecurityRuleDto dto) {
        if (dto.getPattern() == null || dto.getPattern().isBlank())
            throw new IllegalArgumentException("pattern 필수");
        if (dto.getOrderIndex() == null)
            throw new IllegalArgumentException("orderIndex 필수");
        if (dto.getEnabled() == null)
            throw new IllegalArgumentException("enabled 필수");
        if (dto.getAccessType() == SecurityRuleAccessType.HAS_ANY_AUTHORITY
                && (dto.getAuthoritiesCsv() == null || dto.getAuthoritiesCsv().isBlank()))
            throw new IllegalArgumentException("HAS_ANY_AUTHORITY 는 authoritiesCsv 필요");
    }

    private SecurityRule toEntity(SecurityRuleDto dto, SecurityRule base) {
        SecurityRule r = (base != null) ? base : new SecurityRule();
        r.setHttpMethod(emptyToNull(dto.getHttpMethod()));
        r.setPattern(dto.getPattern().trim());
        r.setAccessType(dto.getAccessType());
        r.setAuthoritiesCsv(emptyToNull(dto.getAuthoritiesCsv()));
        r.setOrderIndex(dto.getOrderIndex());
        r.setEnabled(dto.getEnabled());
        r.setRemark(dto.getRemark());
        return r;
    }
    private String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
    private String currentUserIdOr(String fallback) {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getName() != null && !a.getName().isBlank()) return a.getName();
        } catch (Exception ignored) {}
        return fallback;
    }
}
