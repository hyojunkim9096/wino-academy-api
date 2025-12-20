package com.wino.academyapi.domain.enroll.service;

import com.wino.academyapi.domain.enroll.dto.EnrollmentDtos.*;
import com.wino.academyapi.domain.enroll.entity.StudentClassEnrollment;
import com.wino.academyapi.domain.enroll.entity.StudentEnrollTimeslot;
import com.wino.academyapi.domain.enroll.repository.EnrollmentExtViewRepository;
import com.wino.academyapi.domain.enroll.repository.StudentClassEnrollmentRepository;
import com.wino.academyapi.domain.enroll.repository.StudentEnrollTimeslotRepository;
import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.domain.course.repository.CourseRepository;
import com.wino.academyapi.domain.course.entity.Course;
import com.wino.academyapi.domain.course.repository.CourseTimeslotRepository;
import com.wino.academyapi.domain.semester.repository.SemesterRepository;
import com.wino.academyapi.domain.semester.entity.Semester;
import com.wino.academyapi.domain.semester.entity.SemesterType;

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

@Service
@RequiredArgsConstructor
public class StudentEnrollmentService {

    private final StudentClassEnrollmentRepository repo;
    private final StudentEnrollTimeslotRepository setlRepo;
    private final StudentRepository studentRepo;
    private final DbSessionVars dbVars;
    private final EnrollmentExtViewRepository extRepo;
    private final CourseTimeslotRepository courseTimeslotRepo;
    private final CourseRepository courseRepo;
    private final SemesterRepository semesterRepo;

    /* ================= 조회 ================= */

    @Transactional(readOnly = true)
    public Page<EnrollmentSummary> listByStudent(Long studentId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        List<EnrollmentExtViewRepository.EnrollmentExtRow> all = extRepo.findByStudentId(studentId);
        int total = all.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<EnrollmentSummary> content = all.subList(from, to).stream()
                .map(this::toSummaryFromExt)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countActiveByClassIds(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Long> out = new HashMap<>();
        repo.countActiveByClassIds(classIds).forEach(p -> out.put(p.getClassId(), p.getCnt()));
        return out;
    }

    /* ================= 쓰기 (로직 보강) ================= */

    @Transactional
    public Long create(Long studentId, EnrollmentCreateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Student s = studentRepo.findById(studentId).orElseThrow();

        Long classId = Objects.requireNonNull(p.getClassId(), "classId는 필수입니다.");

        // 1. 반 정보 및 학기 정보 조회
        Course course = courseRepo.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("반 정보를 찾을 수 없습니다."));

        Semester semester = null;
        if (course.getSemesterId() != null) {
            semester = semesterRepo.findById(course.getSemesterId()).orElse(null);
        }

        // 2. 이미 'ACTIVE'인 배정이 있는지 체크 (현재 수강중인 경우 중복 불가)
        if (repo.existsByStudent_IdAndClassIdAndStatus(studentId, classId, "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 해당 반에 ACTIVE 배정이 존재합니다.");
        }

        // 3. '정규학기(REGULAR)'이고 'MAIN'인 경우 유일성 체크
        String targetStatus = safeClassStatus(p.getClassStatusCode());

        if ("MAIN".equalsIgnoreCase(targetStatus)
                && semester != null
                && semester.getSemesterType() == SemesterType.REGULAR) {

            boolean hasMain = repo.existsActiveMainInSemester(studentId, course.getSemesterId(), null);
            if (hasMain) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "정규 학기에는 오직 하나의 '메인(MAIN)' 반만 배정할 수 있습니다. 기존 반을 종료하거나 '교차(CROSS)'로 등록하세요.");
            }
        }

        // 4. 재가입 처리 (Duplicate Key Error 방지)
        Optional<StudentClassEnrollment> existingOpt = repo.findByStudent_IdAndClassIdAndEnrolledAt(
                studentId, classId, p.getEnrolledAt()
        );

        Long enrollId;

        if (existingOpt.isPresent()) {
            // A. 기존 데이터 부활
            StudentClassEnrollment existing = existingOpt.get();

            if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 해당 날짜로 등록된 활성 배정이 존재합니다.");
            }

            existing.setStatus("ACTIVE");
            existing.setLeftAt(null);
            existing.setMemo(p.getMemo());
            existing.setClassStatusCode(targetStatus);
            existing.setUpdatedBy(AppUserContext.getUserId());

            // ✅ [수정] 메서드명 변경 반영 (deleteByEnrollId -> deleteByEnrollmentId)
            setlRepo.deleteByEnrollmentId(existing.getId());

            enrollId = existing.getId();

        } else {
            // B. 신규 생성
            StudentClassEnrollment e = StudentClassEnrollment.builder()
                    .student(s)
                    .classId(classId)
                    .enrolledAt(Objects.requireNonNull(p.getEnrolledAt(), "enrolledAt은 필수입니다."))
                    .leftAt(null)
                    .status(safeStatus(p.getStatus()))
                    .memo(p.getMemo())
                    .classStatusCode(targetStatus)
                    .attendDaysMask(0)
                    .updatedBy(AppUserContext.getUserId())
                    .build();

            e = repo.save(e);
            enrollId = e.getId();
        }

        // 5. 타임슬롯 처리
        if (p.getTimeslotIds() != null) {
            replaceTimeslots(enrollId, p.getTimeslotIds());
        } else if ("MAIN".equalsIgnoreCase(targetStatus)) {
            List<Long> defaultTsIds = courseTimeslotRepo.findActiveTimeslotIdsByCourseId(classId);
            if (defaultTsIds != null && !defaultTsIds.isEmpty()) {
                replaceTimeslots(enrollId, defaultTsIds);
            }
        }

        return enrollId;
    }

    @Transactional
    public void update(Long enrollId, EnrollmentUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        StudentClassEnrollment e = repo.findById(enrollId).orElseThrow();

        String nextStatus = p.getStatus() != null ? safeStatus(p.getStatus()) : e.getStatus();
        String nextClassStatus = p.getClassStatusCode() != null ? safeClassStatus(p.getClassStatusCode()) : e.getClassStatusCode();

        if ("ACTIVE".equalsIgnoreCase(nextStatus)) {
            if (repo.existsAnotherActive(e.getStudent().getId(), e.getClassId(), enrollId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 해당 반에 ACTIVE 배정이 존재합니다.");
            }

            if ("MAIN".equalsIgnoreCase(nextClassStatus)) {
                Course course = courseRepo.findById(e.getClassId()).orElseThrow();
                Semester semester = (course.getSemesterId() != null)
                        ? semesterRepo.findById(course.getSemesterId()).orElse(null)
                        : null;

                if (semester != null && semester.getSemesterType() == SemesterType.REGULAR) {
                    boolean hasMain = repo.existsActiveMainInSemester(e.getStudent().getId(), course.getSemesterId(), enrollId);
                    if (hasMain) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "정규 학기에는 오직 하나의 '메인(MAIN)' 반만 배정할 수 있습니다.");
                    }
                }
            }
        }

        if (p.getLeftAt() != null)        e.setLeftAt(p.getLeftAt());
        if (p.getStatus() != null)        e.setStatus(nextStatus);
        if (p.getMemo() != null)          e.setMemo(p.getMemo());
        if (p.getClassStatusCode()!=null) e.setClassStatusCode(nextClassStatus);

        if (p.getTimeslotIds() != null) {
            replaceTimeslots(enrollId, p.getTimeslotIds());
        }

        e.setUpdatedBy(AppUserContext.getUserId());
    }

    @Transactional
    public void delete(Long enrollId) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        repo.deleteById(enrollId);
    }

    /* ================= 내부 유틸 ================= */

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
                .classStatusName(v.getClassStatusName())
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

    private void replaceTimeslots(Long enrollId, List<Long> timeslotIds) {
        // ✅ [수정] 메서드명 변경 반영 (deleteByEnrollId -> deleteByEnrollmentId)
        setlRepo.deleteByEnrollmentId(enrollId);

        if (timeslotIds == null || timeslotIds.isEmpty()) return;

        final List<Long> target = timeslotIds.stream()
                .filter(Objects::nonNull).map(Long::longValue).filter(id -> id > 0).distinct().toList();

        if (target.isEmpty()) return;

        Long updater = AppUserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        List<StudentEnrollTimeslot> rows = new ArrayList<>(target.size());

        // ✅ [수정] 엔티티 참조(Proxy) 생성하여 주입
        StudentClassEnrollment enrollRef = repo.getReferenceById(enrollId);

        for (Long tsId : target) {
            rows.add(StudentEnrollTimeslot.builder()
                    .enrollment(enrollRef) // ✅ 객체 주입
                    .timeslotId(tsId)
                    .updatedBy(updater)
                    .createdAt(now)
                    .build());
        }
        setlRepo.saveAll(rows);
    }
}