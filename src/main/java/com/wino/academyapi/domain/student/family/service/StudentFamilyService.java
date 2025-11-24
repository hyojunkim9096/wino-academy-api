// src/main/java/com/wino/academyapi/domain/student/family/service/StudentFamilyService.java
package com.wino.academyapi.domain.student.family.service;

import com.wino.academyapi.domain.guardian.entity.Guardian;
import com.wino.academyapi.domain.guardian.repository.GuardianRepository;
import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary;
import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.*;
import com.wino.academyapi.domain.student.family.dto.StudentLinkSummary;
import com.wino.academyapi.domain.student.family.entity.StudentGuardianLink;
import com.wino.academyapi.domain.student.family.repository.StudentGuardianLinkRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 학생 도메인의 가족(보호자) 연결 서비스
 * - URI: /api/admin/students/{studentId}/guardians  (학생 하위 서브리소스)
 * - (보조) /api/admin/links/*  엔드포인트도 이 서비스 사용
 */
@Service
@RequiredArgsConstructor
public class StudentFamilyService {

    private final StudentGuardianLinkRepository repo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final DbSessionVars dbVars;

    /* ===== 조회 ===== */

    /** 학생 기준 보호자 목록 */
    @Transactional(readOnly = true)
    public List<GuardianLinkSummary> listGuardiansByStudent(Long studentId) {
        return repo.findGuardianSummariesByStudent(studentId);
    }

    /** 보호자 기준 학생 목록 */
    @Transactional(readOnly = true)
    public List<StudentLinkSummary> listStudentsByGuardian(Long guardianId) {
        return repo.findStudentSummariesByGuardian(guardianId);
    }

    /* ===== 생성 ===== */

    @Transactional
    public Long createLink(LinkCreateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        if (p.getStudentId() == null || p.getGuardianId() == null) {
            throw new IllegalArgumentException("studentId/guardianId는 필수입니다.");
        }

        // (student, guardian) 유니크 보호
        repo.findByStudent_IdAndGuardian_Id(p.getStudentId(), p.getGuardianId())
                .ifPresent(l -> {
                    throw new IllegalStateException("이미 연결된 학생-보호자입니다.");
                });

        Student s = studentRepo.findById(p.getStudentId()).orElseThrow();
        Guardian g = guardianRepo.findById(p.getGuardianId()).orElseThrow();

        StudentGuardianLink link = StudentGuardianLink.builder()
                .student(s)
                .guardian(g)
                .relationCode(p.getRelationCode())
                .primary(p.isPrimary())
                .legalGuardian(p.isLegalGuardian())
                .receiveNotice(p.isReceiveNotice())
                .receiveBilling(p.isReceiveBilling())
                .build();

        return repo.save(link).getId();
    }

    /* ===== 수정 ===== */

    @Transactional
    public void updateLink(Long linkId, LinkUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        StudentGuardianLink l = repo.findById(linkId).orElseThrow();

        if (p.getRelationCode() != null)   l.setRelationCode(p.getRelationCode());
        if (p.getPrimary() != null)        l.setPrimary(p.getPrimary());
        if (p.getLegalGuardian() != null)  l.setLegalGuardian(p.getLegalGuardian());
        if (p.getReceiveNotice() != null)  l.setReceiveNotice(p.getReceiveNotice());
        if (p.getReceiveBilling() != null) l.setReceiveBilling(p.getReceiveBilling());
        // JPA dirty checking으로 자동 반영
    }

    /* ===== 삭제 ===== */

    @Transactional
    public void deleteLink(Long linkId) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        repo.deleteById(linkId);
    }
}
