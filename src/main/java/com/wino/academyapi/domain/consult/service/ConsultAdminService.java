// src/main/java/com/wino/academyapi/domain/consult/service/ConsultAdminService.java
package com.wino.academyapi.domain.consult.service;

import com.wino.academyapi.domain.consult.dto.ConsultDtos.*;
import com.wino.academyapi.domain.consult.entity.ConsultNote;
import com.wino.academyapi.domain.consult.entity.ConsultNoteGuardian;
import com.wino.academyapi.domain.consult.repository.ConsultNoteGuardianRepository;
import com.wino.academyapi.domain.consult.repository.ConsultNoteRepository;
import com.wino.academyapi.domain.guardian.entity.Guardian;
import com.wino.academyapi.domain.guardian.repository.GuardianRepository;
import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// ✅ [리팩토링] 권한 확인을 위해 import
import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import com.wino.academyapi.domain.admin.team.entity.TeamMember;
import com.wino.academyapi.domain.admin.team.repository.TeamMemberRepository;

import java.time.LocalDateTime;
// ✅ [오류 수정] 누락된 import 3개 추가
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
// (기존)
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ConsultAdminService {

    private final ConsultNoteRepository repo;
    private final ConsultNoteGuardianRepository cgRepo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final DbSessionVars dbVars;

    // ✅ [리팩토링] 권한 확인용 리포지토리 주입
    private final AdminUserRepository adminUserRepo;
    private final TeamMemberRepository teamMemberRepo;


    /** DB CHECK(chk_cn_type)에 맞춘 허용셋 — 필요 시 DB와 같이 늘려야 함 */
    private static final Set<String> ALLOWED_TYPES = Set.of("REGULAR", "EMERGENCY");

    /** ✅ [리팩토링] 시스템 관리자(전체 조회) 역할 정의 (DB 공통코드 기준) */
    private static final Set<String> SYSTEM_ADMIN_ROLES = Set.of(
            "ROLE_SYSTEM_ADMIN",
            "ROLE_PRESIDENT",
            "ROLE_PRINCIPAL",
            // ✅ [요청 반영] STAFF 역할군 전체 관리자 권한 부여
            "ROLE_STAFF",
            "ROLE_STAFF_INFO",
            "ROLE_STAFF_ADMIN"
    );

    /* ================================ 조회 ================================ */

    /**
     * ✅ [리팩토링] 학생별 목록 조회 (권한 검사 제거)
     */
    @Transactional(readOnly = true)
    public Page<ConsultSummary> listByStudent(Long studentId, int page, int size) {
        // 1.
        // PermissionScope scope = calculateScope(AppUserContext.getUserId());

        // 2.
        Pageable pageable = PageRequest.of(page, size); //

        return repo.searchWithPermissions(
                studentId, //
                null,      //
                null,      //
                null,      //
                null,      //
                null,      //
                pageable
        );
    }

    /**
     * ✅ [리팩토링] 상단 컬렉션 검색 (권한 검사 제거)
     * - Long studentId, Long writerId (ID
     * - String studentName, String writerName (
     */
    @Transactional(readOnly = true)
    public Page<ConsultSummary> search(
            Long studentId, String studentName,
            Long writerId, String writerName,
            LocalDateTime from, LocalDateTime to,
            int page, int size)
    {
        // 1.
        // PermissionScope scope = calculateScope(AppUserContext.getUserId());

        // 2.
        Pageable pageable = PageRequest.of(page, size); //

        return repo.searchWithPermissions(
                studentId,   //
                studentName, //
                writerId,    //
                writerName,  //
                from,        //
                to,          //
                pageable
        );
    }

    /**
     * ✅ [수정] 이 메서드는 "승인(approve)" 시에만 사용됩니다.
     * - 시스템 관리자: isSystemAdmin = true
     * - 팀장: isSystemAdmin = false, viewableWriterIds = [팀원 ID 목록]
     * - 팀원: isSystemAdmin = false, viewableWriterIds = [0L]
     */
    private PermissionScope calculateScope(Long currentAdminId) {
        if (currentAdminId == null || currentAdminId <= 0) {
            throw new ResponseStatusException(FORBIDDEN, "인증된 사용자만 조회할 수 있습니다.");
        }

        AdminUser currentUser = adminUserRepo.findById(currentAdminId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "사용자 정보를 찾을 수 없습니다."));
        String currentRole = currentUser.getRole();

        // 1.
        if (SYSTEM_ADMIN_ROLES.contains(currentRole)) {
            return new PermissionScope(currentAdminId, true, List.of(0L));
        }

        // 2.
        List<TeamMember> myMemberships = teamMemberRepo.findAllByAdminId(currentAdminId);
        Set<Long> viewableIds = new HashSet<>();
        for (TeamMember membership : myMemberships) {
            if ("LEADER".equalsIgnoreCase(membership.getRoleInTeam())) {
                List<Long> myTeamMemberIds = teamMemberRepo.findActiveAdminIdsByTeamId(membership.getTeam().getId());
                if (myTeamMemberIds != null) {
                    viewableIds.addAll(myTeamMemberIds);
                }
            }
        }
        return new PermissionScope(currentAdminId, false, viewableIds.isEmpty() ? List.of(0L) : new ArrayList<>(viewableIds));
    }

    /** 권한 범위 전달용 내부 record */
    private record PermissionScope(
            Long currentAdminId,
            boolean isSystemAdmin,
            List<Long> viewableWriterIds
    ) {}

    /** * 단건 조회
     * ✅ [수정] 단건 조회 시에는 참석자(attendees) 목록을 채우기 위해 toSummaryWithAttendees를 사용합니다.
     */
    @Transactional(readOnly = true)
    public ConsultSummary get(Long id) {
        return repo.findById(id).map(this::toSummaryWithAttendees)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "상담 기록을 찾을 수 없습니다."));
    }

    /* ================================ CUD ================================ */

    /** 생성 (이 로직은 StudentAdminPage에서 호출될 수 있음) */
    @Transactional
    public Long create(ConsultCreateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Long writerId = AppUserContext.getUserId();
        if (writerId == null || writerId <= 0) {
            throw new ResponseStatusException(FORBIDDEN, "인증된 사용자만 상담을 등록할 수 있습니다.");
        }
        String method = upper(trim(p.getConsultMethod()));
        String type   = clampType(p.getConsultType());
        String title  = trim(p.getTitle());
        String content= trim(p.getContent());
        String action = nullIfBlank(p.getActionPlan());
        String vis    = upperOrNull(p.getVisibilityRole());
        LocalDateTime consultAt = (p.getConsultAt() != null) ? p.getConsultAt() : LocalDateTime.now();
        LocalDateTime nextAt    = p.getNextFollowupAt();
        if (isBlank(title) || isBlank(content)) {
            throw new ResponseStatusException(BAD_REQUEST, "title/content must not be blank");
        }
        Student s = studentRepo.findById(p.getStudentId()).orElseThrow();
        ConsultNote c = ConsultNote.builder()
                .student(s)
                .writerId(writerId)
                .homeroomOk(false)
                .consultMethod(method)
                .consultType(type)
                .title(title)
                .content(content)
                .actionPlan(action)
                .consultAt(consultAt)
                .nextFollowupAt(nextAt)
                .visibilityRole(vis)
                .useYn(p.isUseYn())
                .build();
        Long id = repo.save(c).getId();
        upsertAttendees(id, p.getAttendees());
        return id;
    }

    /** 수정 */
    @Transactional
    public void update(Long id, ConsultUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        ConsultNote c = repo.findById(id).orElseThrow();

        // (권한 검증: 본인 또는 관리자만 수정 가능하도록 로직 추가 권장)

        // homeroomOk(승인) 필드는 이 API로 수정할 수 없음

        if (p.getConsultMethod()!=null) c.setConsultMethod(upper(trim(p.getConsultMethod())));
        if (p.getConsultType()!=null)   c.setConsultType(clampType(p.getConsultType()));
        if (p.getTitle()!=null)         c.setTitle(trim(p.getTitle()));
        if (p.getContent()!=null)       c.setContent(trim(p.getContent()));
        if (p.getActionPlan()!=null)    c.setActionPlan(nullIfBlank(p.getActionPlan()));
        if (p.getConsultAt()!=null)     c.setConsultAt(p.getConsultAt());
        if (p.getNextFollowupAt()!=null)c.setNextFollowupAt(p.getNextFollowupAt());
        if (p.getVisibilityRole()!=null)c.setVisibilityRole(upperOrNull(p.getVisibilityRole()));
        if (p.getUseYn()!=null)         c.setUseYn(p.getUseYn());
        if (p.getAttendees()!=null) {
            upsertAttendees(id, p.getAttendees());
        }
        c.setUpdatedBy(AppUserContext.getUserId());
    }

    /** 승인 */
    @Transactional
    public void approve(Long consultId) {
        Long currentAdminId = AppUserContext.getUserId();
        if (currentAdminId == null || currentAdminId <= 0) {
            throw new ResponseStatusException(FORBIDDEN, "승인 권한이 없습니다.");
        }
        dbVars.setAppVars(currentAdminId, "Approve Consultation");
        ConsultNote c = repo.findById(consultId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "상담 기록을 찾을 수 없습니다."));
        if (Objects.equals(c.getWriterId(), currentAdminId)) {
            throw new ResponseStatusException(FORBIDDEN, "본인이 작성한 상담은 승인할 수 없습니다.");
        }
        PermissionScope scope = calculateScope(currentAdminId);
        if (scope.isSystemAdmin()) {
            c.setHomeroomOk(true);
            c.setUpdatedBy(currentAdminId);
            repo.save(c);
            return;
        }
        if (scope.viewableWriterIds().contains(c.getWriterId())) {
            c.setHomeroomOk(true);
            c.setUpdatedBy(currentAdminId);
            repo.save(c);
            return;
        }
        throw new ResponseStatusException(FORBIDDEN, "이 상담 기록을 승인할 권한이 없습니다.");
    }

    /** 삭제 */
    @Transactional
    public void delete(Long id) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        cgRepo.deleteByConsult_Id(id);
        repo.deleteById(id);
    }

    /* ============================== 내부 유틸 ============================== */

    private void upsertAttendees(Long consultId, List<ConsultGuardianAttendee> list) {
        cgRepo.deleteByConsult_Id(consultId);
        if (list == null || list.isEmpty()) return;
        ConsultNote c = repo.getReferenceById(consultId);
        for (ConsultGuardianAttendee a : list) {
            Guardian g = (a.getGuardianId()!=null ? guardianRepo.findById(a.getGuardianId()).orElse(null) : null);
            ConsultNoteGuardian row = ConsultNoteGuardian.builder()
                    .consult(c)
                    .guardian(g)
                    .relationCode(upperOrNull(a.getRelationCode()))
                    .nameSnapshot(nullIfBlank(a.getName()))
                    .phoneSnapshot(nullIfBlank(a.getPhone()))
                    .presentYn(a.isPresentYn())
                    .memo(nullIfBlank(a.getMemo()))
                    .build();
            cgRepo.save(row);
        }
    }

    private ConsultSummary toSummaryWithAttendees(ConsultNote c) {
        var attendees = cgRepo.findByConsult_Id(c.getId()).stream().map(gg ->
                ConsultGuardianAttendee.builder()
                        .guardianId(gg.getGuardian()!=null ? gg.getGuardian().getId() : null)
                        .relationCode(gg.getRelationCode())
                        .name(gg.getNameSnapshot())
                        .phone(gg.getPhoneSnapshot())
                        .presentYn(gg.isPresentYn())
                        .memo(gg.getMemo())
                        .build()
        ).toList();

        String studentName = c.getStudent() != null ? c.getStudent().getName() : null;
        String writerName = c.getWriterId() != null ? adminUserRepo.findById(c.getWriterId()).map(AdminUser::getUserName).orElse(null) : null;

        return ConsultSummary.builder()
                .id(c.getId())
                .studentId(c.getStudent().getId())
                .studentName(studentName)
                .classId(null)
                .className(null)
                .homeroomTeacherId(null)
                .homeroomTeacherName(null)
                .writerId(c.getWriterId())
                .writerName(writerName)
                .homeroomOk(c.isHomeroomOk())
                .consultMethod(c.getConsultMethod())
                .consultType(c.getConsultType())
                .title(c.getTitle())
                .content(c.getContent())
                .actionPlan(c.getActionPlan())
                .consultAt(c.getConsultAt())
                .nextFollowupAt(c.getNextFollowupAt())
                .visibilityRole(c.getVisibilityRole())
                .useYn(c.isUseYn())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .attendees(attendees)
                .build();
    }

    private static String clampType(String value) {
        String up = upper(trim(value));
        if (up == null) {
            throw new ResponseStatusException(BAD_REQUEST, "consultType is required");
        }
        return up;
    }

    private static String upper(String s) {
        return (s == null) ? null : s.toUpperCase();
    }
    private static String upperOrNull(String s) {
        String t = trim(s);
        return (t == null) ? null : t.toUpperCase();
    }
    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? "" : t;
    }
    private static String nullIfBlank(String s) {
        String t = (s == null) ? null : s.trim();
        return (t == null || t.isEmpty()) ? null : t;
    }
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}