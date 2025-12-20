package com.wino.academyapi.domain.student.family.service;

import com.wino.academyapi.domain.guardian.entity.Guardian;
import com.wino.academyapi.domain.guardian.repository.GuardianRepository;
import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.entity.StudentSibling;
import com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary;
import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.*;
import com.wino.academyapi.domain.student.family.dto.StudentLinkSummary;
import com.wino.academyapi.domain.student.family.entity.StudentGuardianLink;
import com.wino.academyapi.domain.student.family.repository.StudentGuardianLinkRepository;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.domain.student.repository.StudentSiblingRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 학생 도메인의 가족(보호자) 연결 및 동기화 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentFamilyService {

    private final StudentGuardianLinkRepository repo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final StudentSiblingRepository siblingRepo;
    private final DbSessionVars dbVars;

    /* ================= 조회 ================= */

    @Transactional(readOnly = true)
    public List<GuardianLinkSummary> listGuardiansByStudent(Long studentId) {
        return repo.findGuardianSummariesByStudent(studentId);
    }

    @Transactional(readOnly = true)
    public List<StudentLinkSummary> listStudentsByGuardian(Long guardianId) {
        return repo.findStudentSummariesByGuardian(guardianId);
    }

    /* ================= 생성 ================= */

    @Transactional
    public Long createLink(LinkCreateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        if (p.getStudentId() == null || p.getGuardianId() == null) {
            throw new IllegalArgumentException("studentId와 guardianId는 필수입니다.");
        }

        repo.findByStudent_IdAndGuardian_Id(p.getStudentId(), p.getGuardianId())
                .ifPresent(l -> { throw new IllegalStateException("이미 연결된 학생-보호자입니다."); });

        Student s = studentRepo.findById(p.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("학생 없음"));
        Guardian g = guardianRepo.findById(p.getGuardianId())
                .orElseThrow(() -> new IllegalArgumentException("보호자 없음"));

        StudentGuardianLink link = StudentGuardianLink.builder()
                .student(s)
                .guardian(g)
                .relationCode(p.getRelationCode())
                .isPrimary(p.isPrimary())
                .legalGuardian(p.isLegalGuardian())
                .receiveNotice(p.isReceiveNotice())
                .receiveBilling(p.isReceiveBilling())
                .build();

        return repo.save(link).getId();
    }

    /* ================= 수정 ================= */

    @Transactional
    public void updateLink(Long linkId, LinkUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        StudentGuardianLink l = repo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("연결 정보 없음"));

        if (p.getRelationCode() != null)   l.setRelationCode(p.getRelationCode());
        if (p.getPrimary() != null)        l.setPrimary(p.getPrimary()); // Setter
        if (p.getLegalGuardian() != null)  l.setLegalGuardian(p.getLegalGuardian());
        if (p.getReceiveNotice() != null)  l.setReceiveNotice(p.getReceiveNotice());
        if (p.getReceiveBilling() != null) l.setReceiveBilling(p.getReceiveBilling());
    }

    /* ================= 삭제 ================= */

    @Transactional
    public void deleteLink(Long linkId) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        if (!repo.existsById(linkId)) {
            return; // 이미 없으면 무시
        }

        try {
            repo.deleteById(linkId);
            repo.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해당 보호자와 관련된 데이터(청구 등)가 있어 연결을 해제할 수 없습니다.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "연결 해제 중 오류가 발생했습니다.");
        }
    }

    /* ================= [핵심] 가족 동기화 (Family Sync) ================= */

    /**
     * 1. 연결된 '모든' 형제 그룹을 찾습니다 (BFS 탐색).
     * 2. 형제 그룹 내에서 서로 연결되지 않은 학생들을 모두 형제로 맺어줍니다 (상호 연결 생성).
     * 3. 모든 형제에게 보호자 정보를 복사합니다.
     */
    @Transactional
    public void syncFamily(Long targetStudentId) {
        dbVars.setAppVars(AppUserContext.getUserId(), "Family Sync");

        // 1. BFS로 모든 연결된 형제 찾기
        Set<Long> familyGroupIds = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();

        familyGroupIds.add(targetStudentId);
        queue.add(targetStudentId);

        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            List<StudentSibling> relations = siblingRepo.findAllByStudentId(currentId);

            for (StudentSibling rel : relations) {
                // Low/High 객체를 통해 ID 추출 (엔티티 구조 반영)
                Long siblingId;
                if (rel.getLow().getId().equals(currentId)) {
                    siblingId = rel.getHigh().getId();
                } else {
                    siblingId = rel.getLow().getId();
                }

                if (!familyGroupIds.contains(siblingId)) {
                    familyGroupIds.add(siblingId);
                    queue.add(siblingId);
                }
            }
        }

        if (familyGroupIds.size() <= 1) return; // 나 혼자면 종료

        // 2. 형제 상호 연결 (누락된 형제 관계 자동 생성)
        List<Long> sortedIds = new ArrayList<>(familyGroupIds);
        Collections.sort(sortedIds);

        for (int i = 0; i < sortedIds.size(); i++) {
            for (int j = i + 1; j < sortedIds.size(); j++) {
                Long id1 = sortedIds.get(i);
                Long id2 = sortedIds.get(j);

                // low=id1, high=id2 기준으로 연결 여부 확인
                if (!siblingRepo.existsConnection(id1, id2)) {
                    // 없으면 연결 생성 (트리거용 필드 사용)
                    StudentSibling newSibling = StudentSibling.builder()
                            .studentId1(id1)
                            .studentId2(id2)
                            .relationNote("가족 동기화 자동 연결")
                            .build();
                    siblingRepo.save(newSibling);
                }
            }
        }

        // 3. 보호자 정보 수집
        Map<Long, Guardian> guardianMap = new HashMap<>();
        Map<Long, String> relationMap = new HashMap<>();

        for (Long sId : familyGroupIds) {
            List<StudentGuardianLink> links = repo.findByStudentId(sId);
            for (StudentGuardianLink l : links) {
                Long gId = l.getGuardian().getId();

                if (!guardianMap.containsKey(gId)) {
                    guardianMap.put(gId, l.getGuardian());
                    relationMap.put(gId, l.getRelationCode());
                } else {
                    // 구체적인 관계 코드 우선 (PARENT -> MOTHER)
                    String curRel = relationMap.get(gId);
                    String newRel = l.getRelationCode();
                    if ("PARENT".equals(curRel) && !"PARENT".equals(newRel)) {
                        relationMap.put(gId, newRel);
                    }
                }
            }
        }

        if (guardianMap.isEmpty()) return;

        // 4. 수집된 보호자 정보를 모든 형제들에게 적용
        List<Student> students = studentRepo.findAllById(familyGroupIds);

        for (Student s : students) {
            List<StudentGuardianLink> currentLinks = repo.findByStudentId(s.getId());
            Set<Long> linkedGuardianIds = currentLinks.stream()
                    .map(l -> l.getGuardian().getId())
                    .collect(Collectors.toSet());

            for (Long gId : guardianMap.keySet()) {
                if (!linkedGuardianIds.contains(gId)) {
                    Guardian g = guardianMap.get(gId);
                    String relCode = relationMap.getOrDefault(gId, "PARENT");

                    StudentGuardianLink newLink = StudentGuardianLink.builder()
                            .student(s)
                            .guardian(g)
                            .relationCode(relCode)
                            .isPrimary(false)
                            .legalGuardian(true)
                            .receiveNotice(true)
                            .receiveBilling(true)
                            .build();
                    repo.save(newLink);
                }
            }
        }
    }
}