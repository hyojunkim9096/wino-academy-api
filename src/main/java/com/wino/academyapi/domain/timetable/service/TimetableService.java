package com.wino.academyapi.domain.timetable.service;

import com.wino.academyapi.domain.timetable.dto.TimetableDtos.EventRes;
import com.wino.academyapi.domain.timetable.repository.TimetableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 시간표 조회 서비스
 * - 학기(semesterId)는 선택: null → 전체 학기
 * - 관(workLocation)은 선택: null/빈값 → 관 조건 제외
 * - 단일 교사/전체 교사 공통 정책
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository repo;

    public List<EventRes> getTeacherEvents(Long teacherId, Long semesterId, String workLocation){
        if (teacherId == null) throw new IllegalArgumentException("teacherId는 필수입니다.");
        return repo.findTeacherEvents(teacherId, semesterId, emptyToNull(workLocation)); // ★ semesterId 그대로 전달(null 가능)
    }

    public List<EventRes> getClassEvents(String workLocation, Long classId){
        if (workLocation == null || workLocation.isBlank())
            throw new IllegalArgumentException("workLocation은 필수입니다.");
        if (classId == null)
            throw new IllegalArgumentException("classId는 필수입니다.");
        if (!repo.existsClassInWork(classId, workLocation))
            throw new IllegalArgumentException("해당 관에 속하지 않는 반입니다.");
        return repo.findClassEvents(workLocation, classId);
    }

    public List<EventRes> getAllTeacherEvents(Long semesterId, String workLocation){
        return repo.findAllTeacherEvents(semesterId, emptyToNull(workLocation)); // ★ semesterId null 허용 → 전체 학기
    }

    private String emptyToNull(String s){ return (s == null || s.isBlank()) ? null : s; }
}