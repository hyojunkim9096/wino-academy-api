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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class ConsultAdminService {

    private final ConsultNoteRepository repo;
    private final ConsultNoteGuardianRepository cgRepo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final DbSessionVars dbVars;

    /** DB CHECK(chk_cn_type)에 맞춘 허용셋 — 필요 시 DB와 같이 늘려야 함 */
    private static final Set<String> ALLOWED_TYPES = Set.of("REGULAR", "EMERGENCY");

    /* ================================ 조회 ================================ */

    /** 학생별 페이징 목록 */
    @Transactional(readOnly = true)
    public Page<ConsultSummary> listByStudent(Long studentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repo.findByStudent(studentId, pageable).map(this::toSummaryWithAttendees);
    }

    /** 상단 컬렉션 검색(학생/작성자/기간) */
    @Transactional(readOnly = true)
    public Page<ConsultSummary> search(Long studentId, Long writerId,
                                       LocalDateTime from, LocalDateTime to,
                                       int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repo.search(studentId, writerId, from, to, pageable)
                .map(this::toSummaryWithAttendees);
    }

    /** 단건 조회 */
    @Transactional(readOnly = true)
    public ConsultSummary get(Long id) {
        return repo.findById(id).map(this::toSummaryWithAttendees).orElseThrow();
    }

    /* ================================ CUD ================================ */

    /** 생성 */
    @Transactional
    public Long create(ConsultCreateRequest p) {
        // 트리거/히스토리용 세션 변수 (hist.event_by 등에서 사용)
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        // -------- 표준화/검증/기본값 --------
        // ✅ 최초 작성자는 무조건 현재 로그인 사용자로 고정(클라이언트 writerId 무시)
        Long writerId = AppUserContext.getUserId();

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
                .writerId(writerId)           // ✅ 메인 테이블에 최초 작성자 저장
                .homeroomOk(p.isHomeroomOk())
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

        // 참석자 스냅샷 저장(전체 교체 방식)
        upsertAttendees(id, p.getAttendees());

        return id;
    }

    /** 수정(부분 업데이트 + 참석자 전체 교체) */
    @Transactional
    public void update(Long id, ConsultUpdateRequest p) {
        // 트리거/히스토리용 세션 변수 (hist.event_by 등)
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        ConsultNote c = repo.findById(id).orElseThrow();

        // writerId는 최초 작성자 고정 — 절대 변경하지 않음

        if (p.getHomeroomOk()!=null)    c.setHomeroomOk(p.getHomeroomOk());
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

        // 선택: 메인 테이블에도 수정자 남기고 싶다면(히스토리와 별개)
        c.setUpdatedBy(AppUserContext.getUserId()); // 컬럼 존재(Nullable)하므로 안전
        // @UpdateTimestamp가 updatedAt 자동 반영
    }

    /** 삭제(자식 먼저 제거 후 본문 삭제) */
    @Transactional
    public void delete(Long id) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        cgRepo.deleteByConsult_Id(id);
        repo.deleteById(id);
    }

    /* ============================== 내부 유틸 ============================== */

    /** 참석자 스냅샷 전체 교체 */
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

    /** 엔티티 → 요약 DTO(+참석자) */
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

        return ConsultSummary.builder()
                .id(c.getId())
                .studentId(c.getStudent().getId())
                .writerId(c.getWriterId())          // ✅ 최초 작성자 확인용
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
                .createdAt(c.getCreatedAt())        // ✅ 프런트 표기용
                .updatedAt(c.getUpdatedAt())        // ✅ 프런트 표기용
                .attendees(attendees)
                .build();
    }

    /* -------- 문자열/코드 정규화 & 검증 -------- */

    private static String clampType(String value) {
        String up = upper(trim(value));
        if (up == null) {
            throw new ResponseStatusException(BAD_REQUEST, "consultType is required");
        }
        if (!ALLOWED_TYPES.contains(up)) {
            // DB 체크 제약(chk_cn_type)을 사전 차단
            throw new ResponseStatusException(BAD_REQUEST, "consultType must be one of " + ALLOWED_TYPES);
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