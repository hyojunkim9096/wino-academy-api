// src/main/java/com/wino/academyapi/domain/student/memo/service/StudentMemoService.java
package com.wino.academyapi.domain.student.memo.service;

import com.wino.academyapi.domain.student.dto.StudentDtos.StudentMemoCreateRequest;
import com.wino.academyapi.domain.student.dto.StudentDtos.StudentMemoSummary;
import com.wino.academyapi.domain.student.memo.entity.StudentMemo;
import com.wino.academyapi.domain.student.memo.repository.StudentMemoRepository;
import com.wino.academyapi.domain.student.repository.StudentRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class StudentMemoService {

    private final StudentMemoRepository repo;
    private final StudentRepository studentRepo;
    private final DbSessionVars dbVars;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional(readOnly = true)
    public Page<StudentMemoSummary> list(Long studentId, int page, int size){
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        return repo.findByStudent_IdOrderByPinnedDescCreatedAtDesc(studentId, pageable)
                .map(m -> StudentMemoSummary.builder()
                        .id(m.getId())
                        .studentId(m.getStudent()!=null ? m.getStudent().getId() : null)
                        .content(m.getContent())
                        .pinned(m.isPinned()) // ✅ [추가] 핀 상태 노출
                        .visibilityRole(null) // 스키마에 없음
                        .createdAt(m.getCreatedAt()!=null ? ISO.format(m.getCreatedAt()) : null)
                        .createdBy(m.getCreatedBy())
                        .build());
    }

    @Transactional
    public StudentMemoSummary create(Long studentId, StudentMemoCreateRequest req){
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        var now = LocalDateTime.now();
        var saved = repo.save(
                StudentMemo.builder()
                        .student(studentRepo.getReferenceById(studentId))
                        .content(req != null && req.content()!=null ? req.content().trim() : "")
                        .pinned(false) // 기본 false
                        .createdAt(now)
                        .createdBy(AppUserContext.getUserId())
                        .updatedAt(now)
                        .updatedBy(AppUserContext.getUserId())
                        .build()
        );

        return StudentMemoSummary.builder()
                .id(saved.getId())
                .studentId(saved.getStudent()!=null ? saved.getStudent().getId() : null)
                .content(saved.getContent())
                .pinned(saved.isPinned()) // ✅ [추가]
                .visibilityRole(null) // 스키마에 없음
                .createdAt(ISO.format(saved.getCreatedAt()))
                .createdBy(saved.getCreatedBy())
                .build();
    }

    // ✅ [추가] 컨트롤러에서 update/delete 전에 학생 소유 여부를 확인하기 위한 유틸
    @Transactional(readOnly = true)
    public boolean belongsTo(Long memoId, Long studentId){
        return repo.findById(memoId)
                .map(m -> m.getStudent()!=null && m.getStudent().getId()!=null && m.getStudent().getId().equals(studentId))
                .orElse(false);
    }
}