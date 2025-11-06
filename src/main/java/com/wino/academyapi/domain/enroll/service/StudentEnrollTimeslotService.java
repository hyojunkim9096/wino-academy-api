// src/main/java/com/wino/academyapi/domain/enroll/service/StudentEnrollTimeslotService.java
package com.wino.academyapi.domain.enroll.service;

import com.wino.academyapi.domain.enroll.entity.StudentEnrollTimeslot;
import com.wino.academyapi.domain.enroll.repository.StudentClassEnrollmentRepository;
import com.wino.academyapi.domain.enroll.repository.StudentEnrollTimeslotRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;                 // ✅ 범용 예외
import org.springframework.dao.DataIntegrityViolationException; // (하위 포함)
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 타임슬롯 매핑 서비스
 *
 * - GET: enrollId 기준 매핑된 timeslotId 목록 반환
 * - PUT(치환): 전달된 timeslotId 배열로 전체 교체
 *   · 추가/삭제만 수행, 검증/집계는 DB 트리거가 처리
 *   · 잘못된 timeslotId나 반 불일치 등은 DB에서 45000 에러를 발생 → 400으로 래핑
 */
@Service
@RequiredArgsConstructor
public class StudentEnrollTimeslotService {

    private final StudentEnrollTimeslotRepository repo;
    private final StudentClassEnrollmentRepository enrollRepo;
    private final DbSessionVars dbVars;

    /** 조회: enrollId의 매핑된 timeslotId 목록(오름차순 정렬) */
    @Transactional(readOnly = true)
    public List<Long> listTimeslotIds(Long enrollId) {
        // 존재 여부 가드(404) — 잘못된 enrollId를 조기에 차단
        enrollRepo.findById(enrollId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 배정을 찾을 수 없습니다."));
        return repo.findByEnrollId(enrollId).stream()
                .map(StudentEnrollTimeslot::getTimeslotId)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 치환: enrollId에 대해 전달된 timeslotIds로 전체 교체
     * - null → 빈 목록 취급(모두 제거)
     * - 중복/공백 제거 + 음수/0 제거 (입력 가드)
     * - 추가/삭제 차집합만 수행
     */
    @Transactional
    public List<Long> replaceTimeslots(Long enrollId, List<Long> timeslotIds) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        // enroll 존재 가드(404)
        enrollRepo.findById(enrollId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 배정을 찾을 수 없습니다."));

        // 입력 정규화: null → 빈, 중복 제거, null/음수/0 제거
        final List<Long> target = (timeslotIds == null ? List.of() :
                timeslotIds.stream()
                        .filter(Objects::nonNull)
                        .map(Long::longValue)
                        .filter(id -> id > 0)                  // ✅ 음수/0 제거
                        .distinct()
                        .toList());

        // 현재 매핑 조회
        final List<StudentEnrollTimeslot> current = repo.findByEnrollId(enrollId);
        final Set<Long> curIds = current.stream().map(StudentEnrollTimeslot::getTimeslotId).collect(Collectors.toSet());
        final Set<Long> tarIds = new LinkedHashSet<>(target); // 순서 보존

        // 차집합 계산
        final Set<Long> toAdd = tarIds.stream().filter(id -> !curIds.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));
        final Set<Long> toRemove = curIds.stream().filter(id -> !tarIds.contains(id)).collect(Collectors.toSet());

        // 삭제 먼저(중복/제약 에러 가능성 최소화)
        if (!toRemove.isEmpty()) {
            repo.deleteByEnrollIdAndTimeslotIdIn(enrollId, toRemove);
        }

        // 추가
        try {
            for (Long tsId : toAdd) {
                StudentEnrollTimeslot row = StudentEnrollTimeslot.builder()
                        .enrollId(enrollId)
                        .timeslotId(tsId)
                        // updatedBy는 NULL 허용, 세션변수(@app_user_id)는 트리거 event_by로 기록됨
                        .build();
                repo.save(row);
            }
        } catch (DataAccessException ex) { // ✅ 범용 DataAccessException으로 확대
            // FK/UNIQUE/트리거 SIGNAL(45000) 등 → 400으로 래핑
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "타임슬롯 매핑 중 유효성 오류가 발생했습니다. 반과 타임슬롯의 일치/기간/활성 상태를 확인하세요.",
                    ex
            );
        }

        // (DB 트리거가 attend_days_mask 재계산)
        return listTimeslotIds(enrollId);
    }
}