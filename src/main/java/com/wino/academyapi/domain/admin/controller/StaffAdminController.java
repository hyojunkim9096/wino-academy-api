// src/main/java/com/wino/academyapi/domain/admin/controller/StaffAdminController.java
package com.wino.academyapi.domain.admin.controller;

import com.wino.academyapi.domain.admin.dto.AdminUserDtos.*;
import com.wino.academyapi.domain.admin.service.StaffAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;                        // ✅ Spring Data Page
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/staffs")
@RequiredArgsConstructor
public class StaffAdminController {

    private final StaffAdminService staffService;

    /**
     * ✅ 직원/강사 목록
     * - 정렬은 Repository @Query(ORDER BY lower(u.userName) asc, u.id desc)에서 DB 수행
     * - 여기서는 page/size만 전달
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<StaffSummary>> list(
            @RequestParam(defaultValue = "ALL") String employeeType,
            @RequestParam(required = false) String workLocation,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        // 안전 가드: page >= 0, 1 <= size <= 100
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<StaffSummary> result = staffService.list(employeeType, workLocation, keyword, safePage, safeSize);
        return ResponseEntity.ok(result);
    }

    /** ✅ 상세 조회 */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StaffSummary> get(@PathVariable Long id) {
        return ResponseEntity.ok(staffService.get(id));
    }

    /**
     * ✅ 정보 수정 (부분 업데이트)
     * - StaffUpdateRequest 에는 employeeType(STAFF/TEACHER) 포함
     * - 성공 시 204(No Content)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody StaffUpdateRequest req) {
        staffService.update(id, req);
        return ResponseEntity.noContent().build();
    }

    /**
     * ✅ 비밀번호 변경
     * - DTO: AdminUserDtos.PasswordChangeRequest 사용 (중복 타입 제거)
     * - 성공 시 204(No Content)
     */
    @PostMapping(value = "/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(@PathVariable Long id, @RequestBody PasswordChangeRequest body) {
        staffService.changePassword(id, body.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * ✅ 프로필 사진 업로드 (멀티파트)
     * - 성공 시 { "fileId": <Long> } 반환
     */
    @PostMapping(
            value = "/{id}/photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UploadResponse> uploadPhoto(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        Long fileId = staffService.uploadProfile(id, file);
        return ResponseEntity.ok(new UploadResponse(fileId));
    }

    // === 로컬 응답 DTO ===
    public record UploadResponse(Long fileId) {}
}
