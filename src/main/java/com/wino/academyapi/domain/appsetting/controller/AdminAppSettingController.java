// src/main/java/com/wino/academyapi/domain/appsetting/controller/AdminAppSettingController.java
package com.wino.academyapi.domain.appsetting.controller;

import com.wino.academyapi.domain.appsetting.entity.AppSetting;
import com.wino.academyapi.domain.appsetting.entity.AppSettingDeleted;
import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * AppSetting 관리 REST
 * - 생성/수정/삭제: 현재 로그인 사용자 ID를 서비스로 전달(추후 감사로그 붙일 때 활용)
 * - 목록/삭제이력 조회
 * - 베이스 경로: /api/admin/app-settings
 */
@RestController
@RequestMapping("/api/admin/app-settings")
@RequiredArgsConstructor
public class AdminAppSettingController {

    private final AppSettingService service;

    /** 전체 목록 */
    @GetMapping
    public List<AppSetting> list() {
        return service.list();
    }

    /** 삭제 이력 목록 */
    @GetMapping("/deleted")
    public List<AppSettingDeleted> deleted() {
        return service.listDeleted();
    }

    /** 생성 */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody AppSetting req) {
        String who = currentUserIdOr("system");
        AppSetting saved = service.create(req, who);
        return ResponseEntity.created(URI.create("/api/admin/app-settings/" + saved.getId())).body(saved);
    }

    /** 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody AppSetting req) {
        String who = currentUserIdOr("system");
        return ResponseEntity.ok(service.update(id, req, who));
    }

    /** 삭제(+아카이브) */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        String who = currentUserIdOr("system");
        service.deleteAndArchive(id, who);
        return ResponseEntity.noContent().build();
    }

    /* ---- 내부 유틸: 현재 사용자 ---- */
    private String currentUserIdOr(String fallback) {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getName() != null && !a.getName().isBlank()) return a.getName();
        } catch (Exception ignored) {}
        return fallback;
    }
}
