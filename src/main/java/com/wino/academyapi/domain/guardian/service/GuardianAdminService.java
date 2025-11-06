// src/main/java/com/wino/academyapi/domain/guardian/service/GuardianAdminService.java
package com.wino.academyapi.domain.guardian.service;

import com.wino.academyapi.domain.guardian.dto.GuardianDtos.*;
import com.wino.academyapi.domain.guardian.entity.Guardian;
import com.wino.academyapi.domain.guardian.repository.GuardianRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuardianAdminService {

    private final GuardianRepository repo;
    private final DbSessionVars dbVars;

    @Transactional(readOnly = true)
    public Page<GuardianSummary> list(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String kw = (keyword==null||keyword.isBlank())?null:keyword.trim();
        return repo.search(kw, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public GuardianSummary get(Long id) {
        return repo.findById(id).map(this::toSummary).orElseThrow();
    }

    @Transactional
    public GuardianSummary create(GuardianCreateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Guardian g = Guardian.builder()
                .name(p.getName())
                .phone(p.getPhone())
                .email(p.getEmail())
                .preferSms(p.isPreferSms())
                .preferEmail(p.isPreferEmail())
                .preferPush(p.isPreferPush())
                .pushUserKey(p.getPushUserKey())
                .postalCode(p.getPostalCode())
                .address(p.getAddress())
                .detailAddress(p.getDetailAddress())
                .memo(p.getMemo())
                .build();
        return toSummary(repo.save(g));
    }

    @Transactional
    public void update(Long id, GuardianUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Guardian g = repo.findById(id).orElseThrow();

        if (p.getName()!=null) g.setName(p.getName());
        if (p.getPhone()!=null) g.setPhone(p.getPhone());
        if (p.getEmail()!=null) g.setEmail(p.getEmail());
        if (p.getPreferSms()!=null) g.setPreferSms(p.getPreferSms());
        if (p.getPreferEmail()!=null) g.setPreferEmail(p.getPreferEmail());
        if (p.getPreferPush()!=null) g.setPreferPush(p.getPreferPush());
        if (p.getPushUserKey()!=null) g.setPushUserKey(p.getPushUserKey());
        if (p.getPostalCode()!=null) g.setPostalCode(p.getPostalCode());
        if (p.getAddress()!=null) g.setAddress(p.getAddress());
        if (p.getDetailAddress()!=null) g.setDetailAddress(p.getDetailAddress());
        if (p.getMemo()!=null) g.setMemo(p.getMemo());
    }

    @Transactional
    public void delete(Long id) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        repo.deleteById(id);
    }

    private GuardianSummary toSummary(Guardian g) {
        return GuardianSummary.builder()
                .id(g.getId())
                .name(g.getName())
                .phone(g.getPhone())
                .email(g.getEmail())
                .preferSms(g.isPreferSms())
                .preferEmail(g.isPreferEmail())
                .preferPush(g.isPreferPush())
                .pushUserKey(g.getPushUserKey())
                .postalCode(g.getPostalCode())
                .address(g.getAddress())
                .detailAddress(g.getDetailAddress())
                .memo(g.getMemo())
                .build();
    }
}