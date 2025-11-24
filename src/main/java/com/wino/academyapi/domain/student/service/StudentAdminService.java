// src/main/java/com/wino/academyapi/domain/student/service/StudentAdminService.java
package com.wino.academyapi.domain.student.service;

import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import com.wino.academyapi.domain.enduser.entity.EndUser;
import com.wino.academyapi.domain.enduser.entity.EndUserStudentMap;
import com.wino.academyapi.domain.enduser.repository.EndUserStudentMapRepository;
import com.wino.academyapi.domain.enduser.repository.EndUserRepository;
import com.wino.academyapi.domain.file.entity.AttachFile;
import com.wino.academyapi.domain.file.repository.AttachFileRepository;
import com.wino.academyapi.domain.school.repository.SchoolRepository;
import com.wino.academyapi.domain.student.dto.StudentDtos.*;
import com.wino.academyapi.domain.student.dto.StudentSiblingDtos.SiblingCreateRequest;
import com.wino.academyapi.domain.student.dto.StudentSiblingDtos.SiblingLinkDto;
import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.entity.StudentHist;
import com.wino.academyapi.domain.student.entity.StudentSibling;
import com.wino.academyapi.domain.student.memo.entity.StudentMemo;
import com.wino.academyapi.domain.student.memo.repository.StudentMemoRepository;
import com.wino.academyapi.domain.student.repository.StudentHistRepository;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.domain.student.repository.StudentSiblingRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.global.file.PublicUrlHelper;
import com.wino.academyapi.global.storage.LocalFileStorageService;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class StudentAdminService {

    private final StudentRepository repo;
    private final LocalFileStorageService storage;
    private final PublicUrlHelper publicUrlHelper;
    private final DbSessionVars dbVars;

    // ✅ FileRepository 직접 주입 (storage 서비스와 역할 분리)
    private final AttachFileRepository fileRepo;

    /* enduser */
    private final EndUserRepository endUserRepo;
    private final EndUserStudentMapRepository mapRepo;
    private final PasswordEncoder passwordEncoder;

    /* 학교 */
    private final SchoolRepository schoolRepo;

    /* 학생 메모 */
    private final StudentMemoRepository memoRepo;

    // ✅ 형제 리포지토리 주입
    private final StudentSiblingRepository siblingRepo;

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

        // ✅ N+1이 해결된 DTO 프로젝션 쿼리 사용
        Page<StudentSummary> summaryPage = repo.searchWithSummary(stg, wl, kw, pageable);

        // 메타정보(createdAt/By)와 사진URL(photoUrl) 후처리
        summaryPage.getContent().forEach(dto -> {
            // (1) 메타 정보 주입
            enrichMeta(dto, dto.getId());
            // (2) 사진 URL 주입
            if (dto.getProfileImageId() != null) {
                AttachFile f = fileRepo.findById(dto.getProfileImageId()).orElse(null);
                if (f != null) {
                    String relPath = null;
                    if (hasText(f.getRelativePath())) relPath = normalizeSlash(f.getRelativePath());
                    else if (hasText(f.getDirectory()) && hasText(f.getSavedName()))
                        relPath = normalizeSlash(f.getDirectory() + "/" + f.getSavedName());

                    String storedForUrl = null;
                    if (hasText(f.getAbsolutePath())) storedForUrl = f.getAbsolutePath();
                    else if (hasText(relPath))        storedForUrl = relPath;

                    dto.setPhotoPath(relPath);
                    dto.setPhotoUrl(storedForUrl != null ? publicUrlHelper.toPublicUrl(storedForUrl) : null);
                }
            }
        });

        return summaryPage;
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
                .gender(p.getGender())
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
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Student s = repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        if (p.getWorkLocationCode()!=null) s.setWorkLocationCode(p.getWorkLocationCode());
        if (p.getSchoolStage()!=null)      s.setSchoolStage(p.getSchoolStage());
        if (p.getStatus()!=null)           s.setStatus(p.getStatus());
        if (p.getName()!=null)             s.setName(p.getName());
        if (p.getBirthdate()!=null)        s.setBirthdate(p.getBirthdate());
        if (p.getGender()!=null)           s.setGender(p.getGender());
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

        if (s.isPreferSms() && !hasText(s.getPhone()))   s.setPreferSms(false);
        if (s.isPreferEmail() && !hasText(s.getEmail())) s.setPreferEmail(false);
        if (s.getStatus() == null || s.getStatus().isBlank()) s.setStatus("ACTIVE");
    }

    @Transactional
    public void delete(Long id) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        mapRepo.deleteByStudentId(id);
        repo.deleteById(id);
    }

    @Transactional
    public Long uploadProfile(Long id, MultipartFile file) throws IOException {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Student s = repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
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

        if (req != null && req.loginId() != null) {
            String login = req.loginId().trim();
            if (login.isEmpty()) {
                eu.setLoginId(null);
            } else {
                Optional<EndUser> dup = endUserRepo.findByLoginIdIgnoreCase(login);
                if (dup.isPresent() && !dup.get().getId().equals(eu.getId()))
                    throw new ResponseStatusException(CONFLICT, "LOGIN_ID_DUP");
                eu.setLoginId(login);
            }
        }
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

    // =====================================================================
    // ✅ 형제/자매 관리 로직
    // =====================================================================

    /**
     * 특정 학생에 연결된 모든 형제/자매의 DTO 목록을 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<SiblingLinkDto> listSiblings(Long studentId) {
        // 1. 내가 Low일 때 (상대방 High 조회)
        List<SiblingLinkDto> list1 = siblingRepo.findSiblingsAsLow(studentId);
        // 2. 내가 High일 때 (상대방 Low 조회)
        List<SiblingLinkDto> list2 = siblingRepo.findSiblingsAsHigh(studentId);

        // 3. 병합
        return Stream.concat(list1.stream(), list2.stream())
                .collect(Collectors.toList());
    }

    /**
     * 두 학생을 형제/자매로 연결합니다.
     */
    @Transactional
    public Long linkSibling(Long studentId1, SiblingCreateRequest req) {
        dbVars.setAppVars(AppUserContext.getUserId(), "Link Sibling");

        Long studentId2 = req.getStudentId2();
        if (studentId1 == null || studentId2 == null) {
            throw new ResponseStatusException(BAD_REQUEST, "학생 ID는 필수입니다.");
        }
        if (studentId1.equals(studentId2)) {
            throw new ResponseStatusException(BAD_REQUEST, "동일한 학생을 형제로 연결할 수 없습니다.");
        }

        // DB 트리거가 (low_id, high_id) Unique 제약을 처리해주므로,
        // 서비스 레이어에서는 student_id_1, student_id_2만 세팅합니다.
        StudentSibling sibling = StudentSibling.builder()
                .studentId1(studentId1)
                .studentId2(studentId2)
                .relationNote(req.getRelationNote())
                .build();

        try {
            StudentSibling saved = siblingRepo.save(sibling);
            return saved.getId();
        } catch (Exception e) {
            throw new ResponseStatusException(CONFLICT, "이미 형제로 연결된 관계입니다.", e);
        }
    }

    /**
     * 형제/자매 연결을 해제합니다.
     */
    @Transactional
    public void unlinkSibling(Long linkId) {
        dbVars.setAppVars(AppUserContext.getUserId(), "Unlink Sibling");
        if (!siblingRepo.existsById(linkId)) {
            return;
        }
        siblingRepo.deleteById(linkId);
    }


    /* ================= 매핑/유틸 ================= */

    /**
     * ✅ [수정] 학생 계정(EndUser) 확보 로직 개선 (ObjectOptimisticLockingFailureException 방지)
     * - 이미 매핑이 있으면 그 유저 반환
     * - 없으면 새 EndUser 생성 후 매핑 테이블에 'INSERT'
     * - saveAndFlush() 사용하여 즉시 DB 반영
     */
    private EndUser ensureEndUserForStudent(Long studentId) {
        // 1. 매핑 존재 여부 확인
        Optional<EndUserStudentMap> mapOpt = mapRepo.findByStudentId(studentId);

        if (mapOpt.isPresent()) {
            // 이미 계정이 존재하면 해당 계정 반환
            Long userId = mapOpt.get().getUserId();
            return endUserRepo.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("EndUserStudentMap mismatch: user " + userId + " not found"));
        }

        // 2. 계정이 없으면 새로 생성
        LocalDateTime now = LocalDateTime.now();

        // 2-1. EndUser 생성 (먼저 저장해서 ID 확보)
        EndUser eu = EndUser.builder()
                .userType("STUDENT")
                .status("ACTIVE")
                .marketingOptIn(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        EndUser savedUser = endUserRepo.saveAndFlush(eu); // ✅ 즉시 반영

        // 2-2. Student 프록시 조회
        Student studentRef = repo.getReferenceById(studentId);

        // 2-3. 매핑 테이블 저장 (새로운 ID로 INSERT 보장)
        EndUserStudentMap newMap = EndUserStudentMap.builder()
                .user(savedUser)      // @MapsId에 의해 userId가 PK로 사용됨
                .student(studentRef)
                .build();

        mapRepo.saveAndFlush(newMap); // ✅ 즉시 반영

        return savedUser;
    }

    private StudentSummary toSummary(Student s) {
        AttachFile f = s.getProfileImage();
        String relPath = null;
        String publicUrl = null;
        Long fileId = null;

        if (f != null) {
            fileId = f.getId();
            if (hasText(f.getRelativePath())) relPath = normalizeSlash(f.getRelativePath());
            else if (hasText(f.getDirectory()) && hasText(f.getSavedName())) {
                relPath = normalizeSlash(f.getDirectory() + "/" + f.getSavedName());
            }
            String stored = hasText(f.getAbsolutePath()) ? f.getAbsolutePath() : relPath;
            publicUrl = publicUrlHelper.toPublicUrl(stored);
        }

        Long userId = null;
        String loginId = null;
        if (s.getEndUserMap() != null && s.getEndUserMap().getUser() != null) {
            userId = s.getEndUserMap().getUserId();
            loginId = s.getEndUserMap().getUser().getLoginId();
        }
        String schoolName = null;
        if (s.getSchoolId()!=null) {
            schoolName = schoolRepo.findNameById(s.getSchoolId()).orElse(null);
        }
        String latestMemo = memoRepo.findTopByStudent_IdOrderByPinnedDescCreatedAtDesc(s.getId())
                .map(StudentMemo::getContent)
                .orElse(null);

        StudentSummary dto = StudentSummary.builder()
                .id(s.getId())
                .userId(userId)
                .loginId(loginId)
                .workLocationCode(s.getWorkLocationCode())
                .schoolStage(s.getSchoolStage())
                .status(s.getStatus())
                .name(s.getName())
                .birthdate(s.getBirthdate())
                .gender(s.getGender())
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
                .profileImageId(fileId)
                .photoPath(relPath)
                .photoUrl(publicUrl)
                .memo(latestMemo)
                .build();

        enrichMeta(dto, s.getId());
        return dto;
    }

    private void enrichMeta(StudentSummary dto, Long studentId) {
        StudentHist first = histRepo.findFirstByRefIdOrderByVersionAsc(studentId).orElse(null);
        StudentHist last  = histRepo.findFirstByRefIdOrderByVersionDesc(studentId).orElse(null);

        if (first != null) {
            dto.setCreatedAt(first.getEventAt() != null ? TS.format(first.getEventAt()) : null);
            String name = (first.getEventBy() != null)
                    ? adminUserRepo.findUserNameById(first.getEventBy()).orElse(null)
                    : null;
            dto.setCreatedByName(name);
        }
        if (last != null) {
            dto.setUpdatedAt(last.getEventAt() != null ? TS.format(last.getEventAt()) : null);
            String name = (last.getEventBy() != null)
                    ? adminUserRepo.findUserNameById(last.getEventBy()).orElse(null)
                    : null;
            dto.setUpdatedByName(name);
        }
    }

    private static String emptyToNull(String s){ return (s==null||s.isBlank())?null:s; }
    private static boolean hasText(String s){ return s!=null && !s.trim().isEmpty(); }
    private static String normalizeSlash(String p){ return p.replace('\\','/'); }
}