// src/main/java/com/wino/academyapi/domain/school/service/SchoolBatchWriter.java
package com.wino.academyapi.domain.school.service;

import com.wino.academyapi.domain.school.entity.School;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.repository.SchoolRepository;
import com.wino.academyapi.external.alimi.SchoolAlimiClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 한 배치(페이지) 단위로 업서트하고 즉시 커밋한다.
 * - 트랜잭션: REQUIRES_NEW (페이지마다 독립 커밋)
 * - 1차 캐시 폭주 방지: 적당히 flush/clear
 */
@Service
@RequiredArgsConstructor
public class SchoolBatchWriter {

    private final SchoolRepository repo;

    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int upsertBatch(List<SchoolAlimiClient.SchoolDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return 0;

        // 1) 존재여부를 한 번에 프리패치
        List<String> codes = dtos.stream()
                .map(SchoolAlimiClient.SchoolDto::getExternalCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, School> existing = repo.findAllByExternalCodeIn(codes).stream()
                .collect(Collectors.toMap(School::getExternalCode, Function.identity()));

        int changed = 0;
        int row = 0;

        for (SchoolAlimiClient.SchoolDto d : dtos) {
            if (d.getExternalCode() == null || d.getExternalCode().isBlank()) continue;

            School s = existing.get(d.getExternalCode());
            boolean created = false;
            if (s == null) {
                s = new School();
                s.setExternalCode(d.getExternalCode());
                s.setActive(true);
                s.setStage(Optional.ofNullable(d.getStage()).orElse(SchoolStage.E));
                s.setName(Optional.ofNullable(d.getName()).filter(v -> !v.isBlank()).orElse("학교명미상"));
                repo.save(s);
                existing.put(d.getExternalCode(), s);
                changed++;
                created = true;
            }

            boolean dirty = false;

            // stage/active/name
            if (d.getStage() != null && d.getStage() != s.getStage()) { s.setStage(d.getStage()); dirty = true; }
            if (d.getName() != null && !d.getName().isBlank() && !Objects.equals(d.getName().trim(), s.getName())) { s.setName(d.getName().trim()); dirty = true; }

            // eduOffice, postal
            if (!Objects.equals(trim(d.getEduOfficeCode()), s.getEduOfficeCode())) { s.setEduOfficeCode(trim(d.getEduOfficeCode())); dirty = true; }
            if (!Objects.equals(trim(d.getPostalCode()), s.getPostalCode())) { s.setPostalCode(trim(d.getPostalCode())); dirty = true; }

            // address(도로명=address), detailAddress(지번=별도 컬럼인 경우 매핑 조정)
            // 여기서는 roadAddress→address, jibunAddress→detailAddress 로 가정
            if (!Objects.equals(trim(d.getRoadAddress()), s.getAddress())) { s.setAddress(trim(d.getRoadAddress())); dirty = true; }
            if (!Objects.equals(trim(d.getJibunAddress()), s.getDetailAddress())) { s.setDetailAddress(trim(d.getJibunAddress())); dirty = true; }

            // admCode
            if (!Objects.equals(trim(d.getAdmCode()), s.getAdmCode())) { s.setAdmCode(trim(d.getAdmCode())); dirty = true; }

            // lat/lng (스케일 고정은 엔티티/컬럼 정의에 맞게)
            if (!Objects.equals(scale(d.getLat()), s.getLat())) { s.setLat(scale(d.getLat())); dirty = true; }
            if (!Objects.equals(scale(d.getLng()), s.getLng())) { s.setLng(scale(d.getLng())); dirty = true; }

            // homepage / phone
            if (!Objects.equals(trim(d.getHomepageUrl()), s.getHomepageUrl())) { s.setHomepageUrl(trim(d.getHomepageUrl())); dirty = true; }
            if (!Objects.equals(trim(d.getPhone()), s.getPhone())) { s.setPhone(trim(d.getPhone())); dirty = true; }

            if (!created && dirty) changed++;

            if ((++row % 1000) == 0) { em.flush(); em.clear(); }
        }

        em.flush();
        em.clear();
        return changed;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static java.math.BigDecimal scale(BigDecimal v) {
        if (v == null) return null;
        return v.setScale(7, java.math.RoundingMode.HALF_UP);
    }
}
