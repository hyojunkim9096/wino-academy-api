// src/main/java/com/wino/academyapi/domain/dbconn/controller/admin/AdminDbConnectionProfileController.java
package com.wino.academyapi.domain.dbconn.controller.admin;

import com.wino.academyapi.domain.dbconn.dto.DbConnProfileDto;
import com.wino.academyapi.domain.dbconn.entity.DbConnectionProfile;
import com.wino.academyapi.domain.dbconn.service.DbConnectionProfileService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.net.URI;
import java.util.List;
import org.springframework.dao.PessimisticLockingFailureException;

@RestController
@RequestMapping("/api/admin/db-connections")
@RequiredArgsConstructor
public class AdminDbConnectionProfileController {

    private final DbConnectionProfileService service;

    /* ========================= 목록 ========================= */

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DbConnectionProfile> listAll() {
        return service.listAll();
    }

    @GetMapping(params = "env", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DbConnectionProfile> listByEnv(@RequestParam("env") String env) {
        return service.listByEnv(env);
    }

    /* ========================= 삭제 이력 ========================= */

    @GetMapping(value = "/deleted", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DbConnProfileDto.Deleted>> listDeleted(@RequestParam("env") String env) {
        return ResponseEntity.ok(service.listDeleted(env));
    }

    /* ========================= 생성/수정/삭제 ========================= */

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DbConnectionProfile> create(@RequestBody DbConnProfileDto dto) {
        DbConnectionProfile saved = service.create(dto);
        return ResponseEntity
                .created(URI.create("/api/admin/db-connections/" + saved.getId()))
                .body(saved);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DbConnectionProfile> update(@PathVariable Long id, @RequestBody DbConnProfileDto dto) {
        DbConnectionProfile saved = service.update(id, dto);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id, currentUserIdOr("system"));
        return ResponseEntity.noContent().build();
    }

    /* ========================= 활성/비활성 (교차-정책+리로드) ========================= */

    @PostMapping(value = "/{id}/activate", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> activate(@PathVariable Long id) {
        // 존재 확인
        try {
            service.get(id);
        } catch (RuntimeException notFound) {
            return ResponseEntity.status(404).body("profile not found: id=" + id);
        }

        try {
            service.activate(id);
            return ResponseEntity.noContent().build(); // 204
        } catch (PessimisticLockingFailureException | LockTimeoutException | PessimisticLockException lockEx) {
            return ResponseEntity.status(409).body("activation busy: " + safeMsg(lockEx));
        } catch (IllegalStateException | IllegalArgumentException domainErr) {
            return ResponseEntity.status(409).body(domainErr.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("activation error: " + safeMsg(ex));
        }
    }

    @PostMapping(value = "/{id}/deactivate", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        // 존재 확인
        try {
            service.get(id);
        } catch (RuntimeException notFound) {
            return ResponseEntity.status(404).body("profile not found: id=" + id);
        }

        try {
            service.deactivate(id);      // 멱등
            return ResponseEntity.noContent().build(); // 204
        } catch (PessimisticLockingFailureException | LockTimeoutException | PessimisticLockException lockEx) {
            return ResponseEntity.status(409).body("deactivation busy: " + safeMsg(lockEx));
        } catch (IllegalStateException | IllegalArgumentException domainErr) {
            return ResponseEntity.status(409).body(domainErr.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("deactivation error: " + safeMsg(ex));
        }
    }

    /* ========================= YML 스니펫 ========================= */

    @GetMapping(value = "/{id}/yml", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> yml(@PathVariable Long id) {
        String yml = service.generateYmlSnippet(id);
        return ResponseEntity.ok(yml);
    }

    /* ========================= 내부 유틸 ========================= */

    private String currentUserIdOr(String fallback) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isBlank()) return auth.getName();
        } catch (Exception ignore) {}
        return fallback;
    }

    // 예외 메시지 노출 최소화(원인 정도만)
    private String safeMsg(Throwable t) {
        String m = t.getMessage();
        String cls = t.getClass().getSimpleName();
        if (m == null || m.isBlank()) return cls;
        String body = m.length() > 300 ? m.substring(0, 300) + "..." : m;
        return cls + ": " + body;
    }

    /** (선택) 단순 업데이트 폼 — 클라이언트에서 이 구조로 보내고 싶을 때 사용 */
    @Data
    public static class UpdateForm {
        private String profileName;
        private String envCode;
        private String jdbcUrl;
        private String username;
        private String passwordPlain;    // 미입력 시 기존 유지
        private String driverClass;
        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private Long idleTimeoutMs;
        private Long maxLifetimeMs;
        private Boolean isActive;
        private String remark;
    }
}
