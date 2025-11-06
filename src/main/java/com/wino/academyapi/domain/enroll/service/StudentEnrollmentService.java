package com.wino.academyapi.domain.enroll.service;

import com.wino.academyapi.domain.enroll.dto.EnrollmentDtos.*;
import com.wino.academyapi.domain.enroll.entity.StudentClassEnrollment;
import com.wino.academyapi.domain.enroll.entity.StudentEnrollTimeslot;
import com.wino.academyapi.domain.enroll.repository.EnrollmentExtViewRepository;
import com.wino.academyapi.domain.enroll.repository.StudentClassEnrollmentRepository;
import com.wino.academyapi.domain.enroll.repository.StudentEnrollTimeslotRepository;
import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// ✅ MAIN 기본 타임슬롯 자동 연결을 위해 추가
import com.wino.academyapi.domain.classs.repository.ClassTimeslotRepository;

/**
 * 학생 반 배정 서비스
 *
 * ✅ 2025-10 DDL 반영 사항
 *  - roleCode → classStatusCode(MAIN/CROSS)로 용어/컬럼 정리
 *  - attendDays(csv) → attendDaysMask(INT, 비트마스크)
 *    · 마스크 값은 직접 세팅하지 않음. student_enroll_timeslot 매핑을 저장/갱신하면
 *      DB 트리거가 자동으로 student_class_enrollment.attend_days_mask 를 갱신함.
 *  - (옵션) timeslotIds 를 전달받으면 매핑(student_enroll_timeslot)을 생성/교체함
 *
 * 주의:
 *  - 동일 학생·반 조합의 ACTIVE 배정 중복 금지(메인/교차 무관).
 *  - timeslot 유효성/기간/소속 반 검증은 DB 트리거에서 수행.
 *
 * 구현 포인트:
 *  - 목록 조회는 확장 뷰(student_enrollment_ext_v)를 사용해 classStatusName/attendDaysLabel 을 포함합니다.
 *  - 컨트롤러 시그니처(Page 유지)를 위해 수동 페이징 수행(뷰 SQL 정렬 포함).
 *  - ✅ MAIN 배정 생성 시 timeslotIds 미전달이면 반의 "활성 기본 타임슬롯"을 자동 연결(운영 편의/집계 자동화).
 */
@Service
@RequiredArgsConstructor
public class StudentEnrollmentService {

    private final StudentClassEnrollmentRepository repo;
    private final StudentEnrollTimeslotRepository setlRepo; // 🆕 배정-타임슬롯 매핑
    private final StudentRepository studentRepo;
    private final DbSessionVars dbVars;

    // ✅ 확장 뷰(라벨 포함) 리포지토리 — classStatusName / attendDaysLabel 제공
    private final EnrollmentExtViewRepository extRepo;

    // ✅ 반의 활성 타임슬롯 조회용(기본 슬롯 자동 연결)
    private final ClassTimeslotRepository classTimeslotRepo;

    /* ================= 조회 ================= */

    /**
     * 목록 조회를 확장 뷰(student_enrollment_ext_v)로 수행
     * - classStatusName, attendDaysLabel 포함
     * - 컨트롤러 시그니처(Page)를 유지하기 위해 수동 페이징
     */
    @Transactional(readOnly = true)
    public Page<EnrollmentSummary> listByStudent(Long studentId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        // 뷰에서 전체 로우 가져와서(정렬 포함) 슬라이싱
        List<EnrollmentExtViewRepository.EnrollmentExtRow> all = extRepo.findByStudentId(studentId);
        int total = all.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<EnrollmentSummary> content = all.subList(from, to).stream()
                .map(this::toSummaryFromExt)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    /* ================= 현재원 집계 ================= */

    @Transactional(readOnly = true)
    public Map<Long, Long> countActiveByClassIds(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Long> out = new HashMap<>();
        repo.countActiveByClassIds(classIds).forEach(p -> out.put(p.getClassId(), p.getCnt()));
        return out;
    }

    /* ================= 쓰기 ================= */

    /**
     * 배정 생성
     * - 중복 ACTIVE 가드(메인/교차 무관)
     * - classStatusCode 기본 MAIN
     * - timeslotIds 가 전달되면 매핑을 생성(트리거로 요일 마스크 자동 갱신)
     * - ✅ MAIN이고 timeslotIds 미지정이면, 반의 활성 기본 타임슬롯 자동 연결
     * - updated_by: AppUserContext 기준으로 기록
     */
    @Transactional
    public Long create(Long studentId, EnrollmentCreateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Student s = studentRepo.findById(studentId).orElseThrow();

        // 🛑 동일 학생·반에 ACTIVE 가 이미 있으면 409
        if (repo.existsByStudent_IdAndClassIdAndStatus(studentId, p.getClassId(), "ACTIVE")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 해당 반에 ACTIVE 배정이 존재합니다. 기존 배정을 종료(상태 변경)한 뒤 다시 시도하세요."
            );
        }

        // 배정 저장
        StudentClassEnrollment e = StudentClassEnrollment.builder()
                .student(s)
                .classId(Objects.requireNonNull(p.getClassId(), "classId는 필수입니다."))
                .enrolledAt(Objects.requireNonNull(p.getEnrolledAt(), "enrolledAt은 필수입니다."))
                .leftAt(null)
                .status(safeStatus(p.getStatus()))
                .memo(p.getMemo())
                .classStatusCode(safeClassStatus(p.getClassStatusCode())) // MAIN | CROSS
                .attendDaysMask(0) // 초기값. setl 저장 시 트리거가 재계산
                .updatedBy(AppUserContext.getUserId()) // ✅ updated_by 기록
                .build();

        e = repo.save(e);

        // (옵션 1) 타임슬롯 매핑 명시 전달 → 치환 저장 (트리거로 attend_days_mask 갱신)
        if (p.getTimeslotIds() != null) {
            replaceTimeslots(e.getId(), p.getTimeslotIds());

            // (옵션 2) ✅ MAIN && 미전달 → 반의 "활성 기본 타임슬롯" 자동 연결
        } else if ("MAIN".equalsIgnoreCase(e.getClassStatusCode())) {
            // 운영 정책:
            //  - class_subject.use_yn은 데이터 품질 문제가 있어 신뢰하지 않음
            //  - class_master.use_yn 및 class_timeslot.use_yn 만 신뢰(기존 충돌검사 쿼리와 동일 기준)
            List<Long> defaultTsIds = classTimeslotRepo.findActiveTimeslotIdsByClassId(e.getClassId());
            if (defaultTsIds != null && !defaultTsIds.isEmpty()) {
                replaceTimeslots(e.getId(), defaultTsIds); // 트리거가 요일 집계 수행
            }
        }

        return e.getId();
    }

    /**
     * 배정 수정
     * - ACTIVE 로 변경하려면 동일 반에 다른 ACTIVE 가 없어야 함
     * - classStatusCode, memo, status, leftAt 반영
     * - timeslotIds 를 전달하면 "전체 교체" 정책으로 매핑 재구성(없으면 유지, 빈 리스트면 모두 제거)
     * - updated_by 업데이트
     */
    @Transactional
    public void update(Long enrollId, EnrollmentUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        StudentClassEnrollment e = repo.findById(enrollId).orElseThrow();

        // 🛑 ACTIVE 로 변경 시 자기 자신 외에 ACTIVE 존재하면 409
        if (p.getStatus() != null && "ACTIVE".equalsIgnoreCase(p.getStatus())) {
            boolean another = repo.existsAnotherActive(e.getStudent().getId(), e.getClassId(), e.getId());
            if (another) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "이미 해당 반에 ACTIVE 배정이 존재합니다. 기존 배정을 종료(상태 변경)한 뒤 다시 시도하세요."
                );
            }
        }

        if (p.getLeftAt() != null)        e.setLeftAt(p.getLeftAt());
        if (p.getStatus() != null)        e.setStatus(safeStatus(p.getStatus()));
        if (p.getMemo() != null)          e.setMemo(p.getMemo());
        if (p.getClassStatusCode()!=null) e.setClassStatusCode(safeClassStatus(p.getClassStatusCode()));

        // (옵션) 타임슬롯 매핑 교체
        if (p.getTimeslotIds() != null) {
            replaceTimeslots(enrollId, p.getTimeslotIds());
        }

        // ✅ updated_by 갱신
        e.setUpdatedBy(AppUserContext.getUserId());
        // JPA dirty checking으로 enrollment 변경사항 반영
    }

    @Transactional
    public void delete(Long enrollId) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        // 매핑은 FK ON DELETE CASCADE 로 자동 삭제됨
        repo.deleteById(enrollId);
    }

    /* ================= 내부 유틸 ================= */

    // (엔티티 기반 목록에서 쓰던 보조 — 다른 곳에서 재사용 가능)
    private EnrollmentSummary toSummary(StudentClassEnrollment e) {
        String daysLabel = maskToLabel(e.getAttendDaysMask());
        return EnrollmentSummary.builder()
                .id(e.getId())
                .studentId(e.getStudent().getId())
                .classId(e.getClassId())
                .enrolledAt(e.getEnrolledAt())
                .leftAt(e.getLeftAt())
                .status(e.getStatus())
                .memo(e.getMemo())
                .classStatusCode(e.getClassStatusCode())   // MAIN | CROSS
                .classStatusName(null)                     // 엔티티 경로에선 라벨이 없음 (뷰 사용 시에만 존재)
                .attendDaysMask(e.getAttendDaysMask())     // INT bitmask
                .attendDaysLabel(daysLabel)                // '월,수,금' (편의)
                .attendDaysUpdatedAt(e.getAttendDaysUpdatedAt())
                .build();
    }

    // ✅ 확장 뷰 결과 → Summary 매핑 (라벨 포함)
    private EnrollmentSummary toSummaryFromExt(EnrollmentExtViewRepository.EnrollmentExtRow v) {
        return EnrollmentSummary.builder()
                .id(v.getId())
                .studentId(v.getStudentId())
                .classId(v.getClassId())
                .enrolledAt(v.getEnrolledAt())
                .leftAt(v.getLeftAt())
                .status(v.getStatus())
                .memo(v.getMemo())
                .classStatusCode(v.getClassStatusCode())
                .classStatusName(v.getClassStatusName())      // ✅ 공통코드 라벨 세팅
                .attendDaysMask(v.getAttendDaysMask())
                .attendDaysLabel(v.getAttendDaysLabel())
                .attendDaysUpdatedAt(v.getAttendDaysUpdatedAt())
                .build();
    }

    private String safeStatus(String s) {
        String v = (s == null ? "ACTIVE" : s.trim().toUpperCase(Locale.ROOT));
        return v.isBlank() ? "ACTIVE" : v;
    }

    private String safeClassStatus(String s) {
        String v = (s == null ? "MAIN" : s.trim().toUpperCase(Locale.ROOT));
        if (!"MAIN".equals(v) && !"CROSS".equals(v)) v = "MAIN";
        return v;
    }

    /**
     * 타임슬롯 교체(트랜잭션 내부)
     * - 전달 리스트가 null 이 아닌 경우에만 수행
     * - 비어있는 리스트면 전체 삭제
     * - insert/삭제 시 트리거가 attend_days_mask 를 재계산
     *
     * ✅ 안전 보강:
     *   - 중복/NULL/비양수(≤0) ID 제거 후 삽입 → UNIQUE (enroll_id,timeslot_id) 충돌 예방
     */
    private void replaceTimeslots(Long enrollId, List<Long> timeslotIds) {
        // 전체 삭제 (치환 정책)
        setlRepo.deleteByEnrollId(enrollId);

        // 전달이 null 또는 빈 배열 → 모두 제거 후 종료
        if (timeslotIds == null || timeslotIds.isEmpty()) {
            return;
        }

        // ✅ 입력 정규화: null 제거, 양수만 허용, 중복 제거(원순서 유지)
        final List<Long> target = timeslotIds.stream()
                .filter(Objects::nonNull)
                .map(Long::longValue)
                .filter(id -> id > 0)
                .distinct()
                .toList();

        if (target.isEmpty()) {
            return; // 정규화 결과가 비었으면 끝
        }

        // 일괄 삽입 — 유효성/기간/반 일치 검사는 DB 트리거에서 수행
        Long updater = AppUserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        List<StudentEnrollTimeslot> rows = new ArrayList<>(target.size());
        for (Long tsId : target) {
            rows.add(StudentEnrollTimeslot.builder()
                    .enrollId(enrollId)
                    .timeslotId(tsId)
                    .updatedBy(updater)
                    .createdAt(now) // DB DEFAULT 이지만 일관성 유지 목적
                    .build());
        }
        setlRepo.saveAll(rows);

        // (참고) 트리거가 student_class_enrollment.attend_days_mask 및 *_updated_at 를 업데이트함
    }

    /** 비트마스크 → '월,화,...' 라벨(csv) */
    private String maskToLabel(Integer mask) {
        if (mask == null || mask == 0) return "";
        String[] names = {"월","화","수","목","금","토","일"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(',');
                sb.append(names[i]);
            }
        }
        return sb.toString();
    }
}