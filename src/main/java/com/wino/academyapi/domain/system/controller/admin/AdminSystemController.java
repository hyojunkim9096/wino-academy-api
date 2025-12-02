// src/main/java/com/wino/academyapi/domain/system/controller/admin/AdminSystemController.java
package com.wino.academyapi.domain.system.controller.admin;

import com.wino.academyapi.global.datasource.DataSourceReloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final DataSourceReloadService reloadService;

    /** DataSource 교체(활성 프로필 사용). env 로 dev/prod 강제 가능 */
    @PostMapping("/reload-datasource")
    public ResponseEntity<?> reload(@RequestParam(value = "env", required = false) String env) {
        try {
            var result = reloadService.reload(env);
            return ResponseEntity.ok(result); // { env, before, after, appliedAt ... }
        } catch (Exception e) {
            log.warn("[SYSTEM] Reload datasource failed. env={}, err={}", env, e.toString());
            // 503으로 내려주면 프런트에서 "재시도" 버튼/토스트 처리 용이
            return ResponseEntity.status(503).body("Reload failed: " + e.getMessage());
        }
    }

    /** 현재 사용 중인 DS 요약 (사람이 읽기 쉬운 문자열) */
    @GetMapping(value = "/datasource", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> current() {
        return ResponseEntity.ok(reloadService.current());
    }
}
