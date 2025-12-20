package com.wino.academyapi.domain.enroll.service;

import com.wino.academyapi.domain.enroll.entity.StudentClassEnrollment;
import com.wino.academyapi.domain.enroll.entity.StudentEnrollTimeslot;
import com.wino.academyapi.domain.enroll.repository.StudentClassEnrollmentRepository;
import com.wino.academyapi.domain.enroll.repository.StudentEnrollTimeslotRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 타임슬롯 매핑 관리 서비스
 *
 * - 주요 기능:
 * 1. 배정별 타임슬롯 목록 조회
 * 2. 학생별 점유(충돌) 타임슬롯 조회 (ACTIVE 상태 기준)
 * 3. 타임슬롯 매핑 전체 치환 (Delete -> Insert) + [NEW] 서버 측 충돌 검증
 */
@Service
@RequiredArgsConstructor
public class StudentEnrollTimeslotService {

    private final StudentEnrollTimeslotRepository repo;
    private final StudentClassEnrollmentRepository enrollRepo;
    private final DbSessionVars dbVars;

    /**
     * 조회: 특정 배정(enrollId)에 연결된 타임슬롯 ID 목록 반환
     */
    @Transactional(readOnly = true)
    public List<Long> listTimeslotIds(Long enrollId) {
        if (!enrollRepo.existsById(enrollId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "배정 정보를 찾을 수 없습니다.");
        }
        return repo.findByEnrollmentId(enrollId).stream()
                .map(StudentEnrollTimeslot::getTimeslotId)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 충돌 방지용: 학생이 현재 점유 중인 타임슬롯 목록 조회
     * - 이미 'ACTIVE' 상태로 수강 중인 반의 시간표를 가져옴
     * - excludeEnrollId: 수정/재가입 시 자기 자신의 ID는 제외하고 검사하기 위함
     */
    @Transactional(readOnly = true)
    public List<Long> getOccupiedTimeslotIds(Long studentId, Long excludeEnrollId) {
        return repo.findActiveTimeslotIdsByStudent(studentId, excludeEnrollId);
    }

    /**
     * 치환: 배정의 타임슬롯을 전달된 목록으로 완전히 교체 (Clean & Insert)
     * - 기존 매핑을 모두 삭제하고, 새로 전달된 ID들로 다시 생성함.
     * - ✅ [추가] 서버 사이드 충돌 검증 로직 포함
     */
    @Transactional
    public List<Long> replaceTimeslots(Long enrollId, List<Long> timeslotIds) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        // 1. 배정 엔티티 조회 (학생 ID 필요)
        StudentClassEnrollment enroll = enrollRepo.findById(enrollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배정 정보를 찾을 수 없습니다."));

        // 2. 입력값 정제 (null, 중복, 0 이하 제거)
        final List<Long> target = (timeslotIds == null) ? List.of() :
                timeslotIds.stream()
                        .filter(Objects::nonNull)
                        .map(Long::longValue)
                        .filter(id -> id > 0)
                        .distinct()
                        .sorted()
                        .toList();

        // 3. ✅ [검증] 타임슬롯 충돌 체크 (빈 리스트면 체크 불필요)
        if (!target.isEmpty()) {
            validateConflict(enroll.getStudent().getId(), enrollId, target);
        }

        // 4. 기존 매핑 전체 삭제 (초기화)
        repo.deleteByEnrollmentId(enrollId);

        if (target.isEmpty()) {
            return List.of();
        }

        // 5. 신규 매핑 일괄 저장
        Long updater = AppUserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        List<StudentEnrollTimeslot> rows = new ArrayList<>(target.size());

        // 프록시 대신 조회된 엔티티 사용 (이미 1번에서 조회함)
        for (Long tsId : target) {
            rows.add(StudentEnrollTimeslot.builder()
                    .enrollment(enroll)
                    .timeslotId(tsId)
                    .updatedBy(updater)
                    .createdAt(now)
                    .build());
        }
        repo.saveAll(rows);

        return target;
    }

    /**
     * 내부 검증 로직: 요청된 타임슬롯들이 이미 점유된 시간표와 겹치는지 확인
     */
    private void validateConflict(Long studentId, Long currentEnrollId, List<Long> requestIds) {
        // 이미 수강 중인 타임슬롯 조회 (내꺼 제외)
        List<Long> occupied = repo.findActiveTimeslotIdsByStudent(studentId, currentEnrollId);

        if (occupied.isEmpty()) return;

        // 교집합 확인 (요청된 ID 중 점유된 ID가 하나라도 있으면 충돌)
        Set<Long> occupiedSet = new HashSet<>(occupied);
        for (Long reqId : requestIds) {
            if (occupiedSet.contains(reqId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "선택한 시간표 중 이미 다른 수업과 겹치는 시간이 포함되어 있습니다. (Timeslot ID: " + reqId + ")");
            }
        }
    }
}