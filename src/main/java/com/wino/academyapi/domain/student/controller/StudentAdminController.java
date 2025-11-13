// src/main/java/com/wino/academyapi/domain/student/controller/StudentAdminController.java
package com.wino.academyapi.domain.student.controller;

import com.wino.academyapi.domain.student.dto.StudentDtos.*;
// ✅ [신규] Sibling DTO import
import com.wino.academyapi.domain.student.dto.StudentSiblingDtos.*;
import com.wino.academyapi.domain.student.service.StudentAdminService;
import com.wino.academyapi.domain.student.memo.service.StudentMemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
// ✅ [신규] List import
import java.util.List;

/**
 * /api/admin/students — 학생 관리 컨트롤러
 * - CRUD
 * - 사진 업로드
 * - 계정/비밀번호
 * - 학생 메모
 * - ✅ [신규] 형제/자매 연결
 */
@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
public class StudentAdminController {

    private final StudentAdminService service;
    private final StudentMemoService memoService;

    /** 목록 */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<StudentSummary>> list(
            @RequestParam(required = false, name = "stage") String stage,
            @RequestParam(required = false, name = "schoolStage") String schoolStage,
            @RequestParam(required = false, name = "workLocation") String workLocation,
            @RequestParam(required = false, name = "workLocationCode") String workLocationCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        final String stageParam = coalesce(schoolStage, stage);
        final String workLocParam = coalesce(workLocationCode, workLocation);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ResponseEntity.ok(service.list(stageParam, workLocParam, keyword, safePage, safeSize));
    }

    /** 상세 */
    @GetMapping(value="/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentSummary> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** 생성 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentSummary> create(@RequestBody StudentCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    /** 수정(부분) */
    @PutMapping(value="/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody StudentUpdateRequest req) {
        service.update(id, req);
        return ResponseEntity.noContent().build();
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 프로필 사진 업로드 */
    @PostMapping(value="/{id}/photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        Long fileId = service.uploadProfile(id, file);
        return ResponseEntity.ok(new UploadResponse(fileId));
    }

    /** 🔐 비밀번호 변경 (없는 계정이면 생성 후 설정) */
    @PostMapping(value="/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(@PathVariable Long id, @RequestBody PasswordChangeRequest req) {
        if (req == null || req.password() == null || req.password().trim().length() < 6) {
            return ResponseEntity.badRequest().build();
        }
        service.changePassword(id, req.password().trim());
        return ResponseEntity.ok().build();
    }

    /** ✅ 계정 upsert (loginId/password 중 전달된 항목만 변경) */
    @PostMapping(value="/{id}/account", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> upsertAccount(@PathVariable Long id, @RequestBody AccountUpsertRequest req) {
        service.upsertAccount(id, req);
        return ResponseEntity.ok().build();
    }

    /* ========================= 학생 메모 API ========================== */

    /** ✅ 학생 메모 목록(최근순) */
    @GetMapping(value="/{id}/memos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<StudentMemoSummary>> listMemos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        return ResponseEntity.ok(memoService.list(id, page, size));
    }

    /** ✅ 학생 메모 등록 */
    @PostMapping(value="/{id}/memos", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentMemoSummary> addMemo(
            @PathVariable Long id,
            @RequestBody StudentMemoCreateRequest req
    ){
        return ResponseEntity.status(HttpStatus.CREATED).body(memoService.create(id, req));
    }

    /** ✅ 학생 메모 수정 (본문/고정여부) — 사전 소유 검증 */
    @PutMapping(value="/{id}/memos/{memoId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateMemo(
            @PathVariable Long id,
            @PathVariable Long memoId,
            @RequestBody StudentMemoUpdateRequest req
    ){
        if (!memoService.belongsTo(memoId, id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        service.updateMemo(memoId, req != null ? req.content() : null, req != null ? req.pinned() : null);
        return ResponseEntity.noContent().build();
    }

    /** ✅ 학생 메모 삭제 — 사전 소유 검증 */
    @DeleteMapping("/{id}/memos/{memoId}")
    public ResponseEntity<Void> deleteMemo(
            @PathVariable Long id,
            @PathVariable Long memoId
    ){
        if (!memoService.belongsTo(memoId, id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        service.deleteMemo(memoId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // ✅ [신규] 형제/자매 연결 API
    // =====================================================================

    /**
     * 특정 학생에 연결된 형제/자매 목록 조회
     * @param id 기준 학생 ID
     * @return List<SiblingLinkDto> (상대방 학생 정보)
     */
    @GetMapping(value = "/{id}/siblings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SiblingLinkDto>> listSiblings(@PathVariable Long id) {
        return ResponseEntity.ok(service.listSiblings(id));
    }

    /**
     * 형제/자매 연결
     * @param id 기준 학생 ID (Path)
     * @param req 연결할 학생 ID (Body: { studentId2: ... })
     */
    @PostMapping(value = "/{id}/siblings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> linkSibling(@PathVariable Long id, @RequestBody SiblingCreateRequest req) {
        Long linkId = service.linkSibling(id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkId);
    }

    /**
     * 형제/자매 연결 해제 (student_sibling.id 기준)
     * @param linkId 연결 ID (student_sibling.id)
     */
    @DeleteMapping("/siblings/{linkId}")
    public ResponseEntity<Void> unlinkSibling(@PathVariable Long linkId) {
        service.unlinkSibling(linkId);
        return ResponseEntity.noContent().build();
    }

    /* ---------------- 내부 유틸 ---------------- */
    private static String coalesce(String a, String b) {
        return hasText(a) ? a : (hasText(b) ? b : null);
    }
    private static boolean hasText(String s){ return s!=null && !s.trim().isEmpty(); }
}