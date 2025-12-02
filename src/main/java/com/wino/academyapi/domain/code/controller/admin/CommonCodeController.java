// src/main/java/com/wino/academyapi/domain/code/controller/admin/CommonCodeAdminController.java
package com.wino.academyapi.domain.code.controller.admin;

import com.wino.academyapi.domain.code.dto.CommonCodeDtos.*;
import com.wino.academyapi.domain.code.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 공통코드(관리) API
 * base: /api/common-codes
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/common-codes")
public class CommonCodeController {

    private final CommonCodeService service;

    // ===== 그룹 =====
    @GetMapping("/groups")
    public List<CodeGroupResponse> groups() {
        return service.listGroups();
    }

    @PostMapping("/groups")
    public ResponseEntity<Void> createGroup(@RequestBody CodeGroupRequest req) {
        service.createGroup(req);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/groups/{groupCode}")
    public ResponseEntity<Void> updateGroup(@PathVariable String groupCode, @RequestBody CodeGroupRequest req) {
        service.updateGroup(groupCode, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/groups/{groupCode}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupCode) {
        service.deleteGroup(groupCode);
        return ResponseEntity.ok().build();
    }

    // ===== 코드(아이템) =====
    @GetMapping("/{groupCode}/items")
    public List<CodeItemResponse> items(@PathVariable String groupCode) {
        return service.listCodes(groupCode);
    }

    @PostMapping("/{groupCode}/items")
    public ResponseEntity<Void> createItem(@PathVariable String groupCode, @RequestBody CodeItemRequest req) {
        service.createCode(groupCode, req);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{groupCode}/items/{code}")
    public ResponseEntity<Void> updateItem(@PathVariable String groupCode, @PathVariable String code, @RequestBody CodeItemRequest req) {
        service.updateCode(groupCode, code, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{groupCode}/items/{code}")
    public ResponseEntity<Void> deleteItem(@PathVariable String groupCode, @PathVariable String code) {
        service.deleteCode(groupCode, code);
        return ResponseEntity.ok().build();
    }
}
