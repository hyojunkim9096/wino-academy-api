package com.wino.academyapi.domain.course.controller.admin; // ✅ 패키지 변경: controller.admin

import com.wino.academyapi.domain.course.dto.CourseOpsDtos; // ✅ DTO 이름 변경 (구 ClassOpsDtos)
import com.wino.academyapi.domain.course.service.CourseOpsService; // ✅ 서비스 이름 변경 (구 ClassOpsService)
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * 반 운영 작업(학기 마감/되돌리기/미리보기) 컨트롤러
 * - 프런트 헤더 X-App-User-Id → event_by(@app_user_id)
 * - 한글 메모 헤더 이슈: 바디 note 우선, 없으면 X-Event-Note-B64(UTF-8), 없으면 X-Event-Note(ASCII)
 */
@RestController
@RequestMapping("/api/admin/courses/ops") // ✅ URL 변경: /classes/ops -> /courses/ops
@RequiredArgsConstructor
public class CourseOpsController { // ✅ 클래스명 변경: AdminClassOpsController -> CourseOpsController

    private final CourseOpsService svc; // ✅ 서비스 타입 변경

    /** 미리보기(영향 범위 집계) */
    @GetMapping("/preview")
    public CourseOpsDtos.PreviewRes preview(
            @RequestParam String workLocationCode,
            @RequestParam String schoolStage,
            @RequestParam Long semesterId
    ){
        return svc.preview(workLocationCode, schoolStage, semesterId);
    }

    /** 학기 마감: 스냅샷 저장 후 반/과목/시간표 초기화 */
    @PostMapping("/close")
    public CourseOpsDtos.SimpleAck close(
            @RequestHeader(value = "X-App-User-Id",     required = false) Long   appUserId,
            @RequestHeader(value = "X-Event-Note",      required = false) String noteHeaderAscii,
            @RequestHeader(value = "X-Event-Note-B64",  required = false) String noteHeaderB64,
            @Validated @RequestBody CourseOpsDtos.CloseReq req
    ){
        Long uid = Objects.requireNonNullElse(appUserId, 0L);
        String note = coalesceNote(req.getNote(), noteHeaderAscii, noteHeaderB64);
        req.setNote(note);
        return svc.snapshotAndClose(uid, req);
    }

    /** 되돌리기: 선택한 학기의 "마지막 SNAP" 기준으로 선택항목 복원(+옵션: 복원 직전 SNAP) */
    @PostMapping("/restore")
    public CourseOpsDtos.SimpleAck restore(
            @RequestHeader(value = "X-App-User-Id",     required = false) Long   appUserId,
            @RequestHeader(value = "X-Event-Note",      required = false) String noteHeaderAscii,
            @RequestHeader(value = "X-Event-Note-B64",  required = false) String noteHeaderB64,
            @Validated @RequestBody CourseOpsDtos.RestoreReq req
    ){
        Long uid = Objects.requireNonNullElse(appUserId, 0L);
        String note = coalesceNote(req.getNote(), noteHeaderAscii, noteHeaderB64);
        req.setNote(note);
        return svc.restoreFromSnapshot(uid, req);
    }

    // ───────────────────────────────────────────────────────────────
    // 내부 유틸: 메모 정규화 (body → B64 header → ASCII header)
    // ───────────────────────────────────────────────────────────────
    private static String coalesceNote(String body, String asciiHeader, String b64Header){
        String candidate = (body == null || body.isBlank()) ? null : body.trim();

        if (candidate == null && b64Header != null && !b64Header.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(b64Header.trim());
                candidate = new String(decoded, StandardCharsets.UTF_8).trim();
            } catch (IllegalArgumentException ignore) { /* 잘못된 Base64는 무시 */ }
        }
        if (candidate == null && asciiHeader != null && !asciiHeader.isBlank()) {
            candidate = asciiHeader.trim();
        }
        return (candidate == null || candidate.isBlank()) ? null : candidate;
    }
}