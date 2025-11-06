// src/main/java/com/wino/academyapi/domain/student/service/StudentAdminService.java
package com.wino.academyapi.domain.student.service;

import com.wino.academyapi.domain.file.entity.AttachFile;
import com.wino.academyapi.domain.student.dto.StudentDtos.*;
import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.global.file.PublicUrlHelper;
import com.wino.academyapi.global.storage.LocalFileStorageService;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/* ✅ enduser 패키지 의존 */
import com.wino.academyapi.domain.enduser.entity.EndUser;
import com.wino.academyapi.domain.enduser.entity.EndUserStudentMap;
import com.wino.academyapi.domain.enduser.repository.EndUserRepository;
import com.wino.academyapi.domain.enduser.repository.EndUserStudentMapRepository;

/* ✅ 학교명 resolve */
import com.wino.academyapi.domain.school.repository.SchoolRepository;

/* ✅ 학생 메모 */
import com.wino.academyapi.domain.student.memo.entity.StudentMemo;
import com.wino.academyapi.domain.student.memo.repository.StudentMemoRepository;

/* ✅ 메타 정보(히스토리 + 작성자명) */
import com.wino.academyapi.domain.student.entity.StudentHist;
import com.wino.academyapi.domain.student.repository.StudentHistRepository;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;

@Service
@RequiredArgsConstructor
public class StudentAdminService {

    private final StudentRepository repo;
    private final LocalFileStorageService storage;
    private final PublicUrlHelper publicUrlHelper;
    private final DbSessionVars dbVars;

    /* enduser */
    private final EndUserRepository endUserRepo;
    private final EndUserStudentMapRepository mapRepo;
    private final PasswordEncoder passwordEncoder;

    /* 학교 */
    private final SchoolRepository schoolRepo;

    /* 학생 메모 */
    private final StudentMemoRepository memoRepo;

    /* 메타(히스토리 + 작성자명) */
    private final StudentHistRepository histRepo;
    private final AdminUserRepository adminUserRepo;

    /** 날짜 포맷 (프런트 표준 포맷) */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /* ================= 조회 ================= */

    @Transactional(readOnly = true)
    public Page<StudentSummary> list(String stage, String workLocation, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String stg = emptyToNull(stage);
        String wl  = emptyToNull(workLocation);
        String kw  = emptyToNull(keyword);
        return repo.search(stg, wl, kw, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public StudentSummary get(Long id) {
        return repo.findById(id).map(this::toSummary).orElseThrow();
    }

    /* ================= 쓰기 ================= */

    @Transactional
    public StudentSummary create(StudentCreateRequest p) {
        // 🔐 앱 세션 변수 주입(@app_user_id, @event_note) — DB 트리거에서 참조
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        Student s = Student.builder()
                .workLocationCode(p.getWorkLocationCode())
                .schoolStage(p.getSchoolStage())
                .status(p.getStatus() == null || p.getStatus().isBlank() ? "PENDING" : p.getStatus())
                .name(p.getName())
                .birthdate(p.getBirthdate())
                .schoolId(p.getSchoolId())
                .gradeLabel(p.getGradeLabel())
                .phone(p.getPhone())
                .email(p.getEmail())
                .preferSms(p.isPreferSms())
                .preferEmail(p.isPreferEmail())
                .preferPush(p.isPreferPush())
                .pushUserKey(p.getPushUserKey())
                .postalCode(p.getPostalCode())
                .address(p.getAddress())
                .detailAddress(p.getDetailAddress())
                .build();
        s = repo.save(s);

        // 요청에 메모가 왔다면 최신 메모로 1건 append
        if (hasText(p.getMemo())) {
            StudentMemo sm = StudentMemo.builder()
                    .student(s)
                    .content(p.getMemo().trim())
                    .pinned(false)
                    .createdAt(LocalDateTime.now())
                    .createdBy(AppUserContext.getUserId())
                    .updatedAt(LocalDateTime.now())
                    .updatedBy(AppUserContext.getUserId())
                    .build();
            memoRepo.save(sm);
        }
        return toSummary(s);
    }

    @Transactional
    public void update(Long id, StudentUpdateRequest p) {
        // 🔐 세션 변수 주입 (히스토리 트리거가 @app_user_id 사용)
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Student s = repo.findById(id).orElseThrow();

        // 부분 업데이트 — null 아닌 필드만 반영
        if (p.getWorkLocationCode()!=null) s.setWorkLocationCode(p.getWorkLocationCode());
        if (p.getSchoolStage()!=null)      s.setSchoolStage(p.getSchoolStage());
        if (p.getStatus()!=null)           s.setStatus(p.getStatus());
        if (p.getName()!=null)             s.setName(p.getName());
        if (p.getBirthdate()!=null)        s.setBirthdate(p.getBirthdate());
        if (p.getSchoolId()!=null)         s.setSchoolId(p.getSchoolId());
        if (p.getGradeLabel()!=null)       s.setGradeLabel(p.getGradeLabel());
        if (p.getPhone()!=null)            s.setPhone(p.getPhone());
        if (p.getEmail()!=null)            s.setEmail(p.getEmail());
        if (p.getPreferSms()!=null)        s.setPreferSms(p.getPreferSms());
        if (p.getPreferEmail()!=null)      s.setPreferEmail(p.getPreferEmail());
        if (p.getPreferPush()!=null)       s.setPreferPush(p.getPreferPush());
        if (p.getPushUserKey()!=null)      s.setPushUserKey(p.getPushUserKey());
        if (p.getPostalCode()!=null)       s.setPostalCode(p.getPostalCode());
        if (p.getAddress()!=null)          s.setAddress(p.getAddress());
        if (p.getDetailAddress()!=null)    s.setDetailAddress(p.getDetailAddress());

        // 🔁 메모는 student_memo로 append
        if (p.getMemo()!=null && hasText(p.getMemo())) {
            StudentMemo sm = StudentMemo.builder()
                    .student(s)
                    .content(p.getMemo().trim())
                    .pinned(false)
                    .createdAt(LocalDateTime.now())
                    .createdBy(AppUserContext.getUserId())
                    .updatedAt(LocalDateTime.now())
                    .updatedBy(AppUserContext.getUserId())
                    .build();
            memoRepo.save(sm);
        }

        // 정합성 보강 — 연락수단 플래그/상태 기본값
        if (s.isPreferSms() && !hasText(s.getPhone()))   s.setPreferSms(false);
        if (s.isPreferEmail() && !hasText(s.getEmail())) s.setPreferEmail(false);
        if (s.getStatus() == null || s.getStatus().isBlank()) s.setStatus("ACTIVE");
    }

    @Transactional
    public void delete(Long id) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        // 매핑 선행 삭제 (고아 방지)
        mapRepo.deleteByStudentId(id);
        // student_memo 등은 FK CASCADE 가정
        repo.deleteById(id);
    }

    @Transactional
    public Long uploadProfile(Long id, MultipartFile file) throws IOException {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Student s = repo.findById(id).orElseThrow();
        AttachFile saved = storage.saveProfileImage(file);
        s.setProfileImage(saved);
        return saved.getId();
    }

    /** 🔐 비밀번호 변경 — 없는 계정이면 생성 후 암호 설정 */
    @Transactional
    public void changePassword(Long studentId, String rawPw) {
        String trimmed = (rawPw == null ? "" : rawPw.trim());
        if (trimmed.length() < 6) throw new IllegalArgumentException("PASSWORD_TOO_SHORT");

        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        EndUser eu = ensureEndUserForStudent(studentId);

        eu.setPasswordHash(passwordEncoder.encode(trimmed));
        eu.setPasswordAlgo("bcrypt");
        eu.setUpdatedAt(LocalDateTime.now());
        endUserRepo.save(eu);
    }

    /** ✅ 로그인ID/비번 upsert (둘 중 전달된 것만 변경) */
    @Transactional
    public void upsertAccount(Long studentId, AccountUpsertRequest req){
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        EndUser eu = ensureEndUserForStudent(studentId);

        // loginId 변경(중복 체크)
        if (req != null && req.loginId() != null) {
            String login = req.loginId().trim();
            if (login.isEmpty()) {
                eu.setLoginId(null); // 비우면 제거
            } else {
                Optional<EndUser> dup = endUserRepo.findByLoginIdIgnoreCase(login);
                if (dup.isPresent() && !dup.get().getId().equals(eu.getId()))
                    throw new IllegalStateException("LOGIN_ID_DUP");
                eu.setLoginId(login);
            }
        }

        // password 변경(조건부)
        if (req != null && req.password() != null) {
            String pw = req.password().trim();
            if (pw.length() < 6) throw new IllegalArgumentException("PASSWORD_TOO_SHORT");
            eu.setPasswordHash(passwordEncoder.encode(pw));
            eu.setPasswordAlgo("bcrypt");
        }

        eu.setUpdatedAt(LocalDateTime.now());
        endUserRepo.save(eu);
    }

    /* ================= 🆕 학생 메모 수정/삭제 (컨트롤러에서 호출) ================= */

    @Transactional
    public void updateMemo(Long memoId, String content, Boolean pinned) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        StudentMemo memo = memoRepo.findById(memoId).orElseThrow();
        boolean changed = false;

        if (content != null) {
            memo.setContent(content.trim());
            changed = true;
        }
        if (pinned != null) {
            memo.setPinned(pinned);
            changed = true;
        }

        if (changed) {
            memo.setUpdatedAt(LocalDateTime.now());
            memo.setUpdatedBy(AppUserContext.getUserId());
            memoRepo.save(memo);
        }
    }

    @Transactional
    public void deleteMemo(Long memoId) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        memoRepo.deleteById(memoId);
    }

    /* ================= 매핑/유틸 ================= */

    /** 학생에 대응하는 EndUser가 없으면 새로 만든 뒤 매핑까지 보장 */
    private EndUser ensureEndUserForStudent(Long studentId){
        EndUserStudentMap map = mapRepo.findByStudentId(studentId).orElse(null);
        EndUser eu;
        if (map == null) {
            LocalDateTime now = LocalDateTime.now();
            eu = EndUser.builder()
                    .userType("STUDENT")
                    .status("ACTIVE")
                    .marketingOptIn(false)
                    .createdAt(now).updatedAt(now)
                    .build();
            eu = endUserRepo.save(eu);

            map = EndUserStudentMap.builder()
                    .userId(eu.getId())
                    .studentId(studentId)
                    .build();
            mapRepo.save(map);
        } else {
            eu = endUserRepo.findById(map.getUserId()).orElseThrow();
        }
        return eu;
    }

    /** DTO 매핑 — schoolName, photoUrl, 최신 메모 1건 반영 + ✅ 메타 주입 */
    private StudentSummary toSummary(Student s) {
        AttachFile f = s.getProfileImage();

        // 파일 경로 정규화
        String relPath = null;
        if (f != null) {
            if (hasText(f.getRelativePath())) relPath = normalizeSlash(f.getRelativePath());
            else if (hasText(f.getDirectory()) && hasText(f.getSavedName()))
                relPath = normalizeSlash(f.getDirectory() + "/" + f.getSavedName());
        }

        // 퍼블릭 URL 구성
        String storedForUrl = null;
        if (f != null) {
            if (hasText(f.getAbsolutePath())) storedForUrl = f.getAbsolutePath();
            else if (hasText(relPath))        storedForUrl = relPath;
        }
        String publicUrl = (storedForUrl != null ? publicUrlHelper.toPublicUrl(storedForUrl) : null);

        // 로그인아이디 로딩
        Long userId = null;
        String loginId = null;
        EndUserStudentMap map = mapRepo.findByStudentId(s.getId()).orElse(null);
        if (map != null) {
            userId = map.getUserId();
            loginId = endUserRepo.findById(userId).map(EndUser::getLoginId).orElse(null);
        }

        // 학교명 resolve
        String schoolName = null;
        if (s.getSchoolId()!=null) {
            schoolName = schoolRepo.findNameById(s.getSchoolId()).orElse(null);
        }

        // 최신 메모 1건 조회(고정>최신순)
        String latestMemo = memoRepo.findTopByStudent_IdOrderByPinnedDescCreatedAtDesc(s.getId())
                .map(StudentMemo::getContent)
                .orElse(null);

        // 1차 DTO
        StudentSummary dto = StudentSummary.builder()
                .id(s.getId())
                .userId(userId)
                .loginId(loginId)
                .workLocationCode(s.getWorkLocationCode())
                .schoolStage(s.getSchoolStage())
                .status(s.getStatus())
                .name(s.getName())
                .birthdate(s.getBirthdate())
                .schoolId(s.getSchoolId())
                .schoolName(schoolName)
                .gradeLabel(s.getGradeLabel())
                .phone(s.getPhone())
                .email(s.getEmail())
                .preferSms(s.isPreferSms())
                .preferEmail(s.isPreferEmail())
                .preferPush(s.isPreferPush())
                .pushUserKey(s.getPushUserKey())
                .postalCode(s.getPostalCode())
                .address(s.getAddress())
                .detailAddress(s.getDetailAddress())
                .profileImageId(f != null ? f.getId() : null)
                .photoPath(relPath)
                .photoUrl(publicUrl)
                .memo(latestMemo)
                .build();

        // ✅ 메타 보강 (student_hist → admin_user_info.user_name)
        enrichMeta(dto, s);

        return dto;
    }

    /** 최초/최신 히스토리 기반으로 메타 필드 채우기 */
    private void enrichMeta(StudentSummary dto, Student s) {
        StudentHist first = histRepo.findFirstByRefIdOrderByVersionAsc(s.getId()).orElse(null);
        StudentHist last  = histRepo.findFirstByRefIdOrderByVersionDesc(s.getId()).orElse(null);

        // 최초 메타
        if (first != null) {
            dto.setCreatedAt(first.getEventAt() != null ? TS.format(first.getEventAt()) : null);
            String name = (first.getEventBy() != null)
                    ? adminUserRepo.findUserNameById(first.getEventBy()).orElse(null)
                    : null;
            dto.setCreatedByName(name);
        } else {
            // 히스토리가 없다면 student.created_at로 폴백
            dto.setCreatedAt(s.getCreatedAt() != null ? TS.format(s.getCreatedAt()) : null);
            // createdByName은 알 수 없으면 null
        }

        // 최신 메타
        if (last != null) {
            dto.setUpdatedAt(last.getEventAt() != null ? TS.format(last.getEventAt()) : null);
            String name = (last.getEventBy() != null)
                    ? adminUserRepo.findUserNameById(last.getEventBy()).orElse(null)
                    : null;
            dto.setUpdatedByName(name);
        } else {
            dto.setUpdatedAt(s.getUpdatedAt() != null ? TS.format(s.getUpdatedAt()) : null);
            String name = (s.getUpdatedBy() != null)
                    ? adminUserRepo.findUserNameById(s.getUpdatedBy()).orElse(null)
                    : null;
            dto.setUpdatedByName(name);
        }
    }

    /* ======= 공통 ======= */
    private static String emptyToNull(String s){ return (s==null||s.isBlank())?null:s; }
    private static boolean hasText(String s){ return s!=null && !s.trim().isEmpty(); }
    private static String normalizeSlash(String p){ return p.replace('\\','/'); }
}