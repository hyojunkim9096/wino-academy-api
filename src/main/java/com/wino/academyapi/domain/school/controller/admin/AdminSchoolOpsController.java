// src/main/java/com/wino/academyapi/domain/school/controller/admin/AdminSchoolOpsController.java
package com.wino.academyapi.domain.school.controller.admin;

import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.service.SchoolImportService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/schools/ops")
@RequiredArgsConstructor
public class AdminSchoolOpsController {

    private final SchoolImportService importService;

    @PostMapping(
            value = "/sync-schoolinfo",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> syncSchoolInfo(@RequestBody SchoolInfoSyncRequest req) {
        if (!StringUtils.hasText(req.getSidoCode()))  return bad("sidoCode가 필요합니다.");
        if (!StringUtils.hasText(req.getSggCode()))   return bad("sggCode가 필요합니다.");
        if (!StringUtils.hasText(req.getStage()))     return bad("stage(E/M/H 또는 02/03/04)가 필요합니다.");

        SchoolStage stage = parseStage(req.getStage());
        if (stage == null) return bad("stage 값이 올바르지 않습니다. (허용: E/M/H 또는 02/03/04)");

        long t0 = System.nanoTime();
        try {
            var result = importService.importSchoolInfo(req.getSidoCode(), req.getSggCode(), stage);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
            return ResponseEntity.ok(Map.of(
                    "sidoCode", req.getSidoCode(),
                    "sggCode", req.getSggCode(),
                    "stage", stage.name(),
                    "result", result,
                    "elapsedMs", elapsedMs
            ));
        } catch (IllegalStateException e) {
            if (isAlimiDisabled(e)) {
                return ResponseEntity.status(501).body(Map.of("message", e.getMessage()));
            }
            log.warn("[OPS] sync-schoolinfo failed: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.warn("[OPS] sync-schoolinfo failed: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(
            value = "/sync-schoolinfo-all",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> syncSchoolInfoAll(@RequestBody(required = false) SchoolInfoSyncAllRequest req) {
        EnumSet<SchoolStage> stages = EnumSet.noneOf(SchoolStage.class);
        if (req != null && req.getStages() != null) {
            for (String s : req.getStages()) {
                SchoolStage st = parseStage(s);
                if (st != null) stages.add(st);
            }
        }
        if (stages.isEmpty()) stages = EnumSet.of(SchoolStage.E, SchoolStage.M, SchoolStage.H);

        long t0 = System.nanoTime();
        try {
            var result = importService.importSchoolInfoAll(stages);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
            return ResponseEntity.ok(Map.of(
                    "stages", stages,
                    "result", result,
                    "elapsedMs", elapsedMs
            ));
        } catch (IllegalStateException e) {
            if (isAlimiDisabled(e)) {
                return ResponseEntity.status(501).body(Map.of("message", e.getMessage()));
            }
            log.warn("[OPS] sync-schoolinfo-all failed: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.warn("[OPS] sync-schoolinfo-all failed: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        }
    }

    /* ========== 유틸/DTO ========== */

    private boolean isAlimiDisabled(Throwable e) {
        String msg = e.getMessage();
        return msg != null && msg.startsWith("ALIMI_DISABLED_OR_MISCONFIGURED");
    }

    private ResponseEntity<Map<String,String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }

    /** stage 파싱: 허용(E/M/H, e/m/h, "02/03/04") */
    private SchoolStage parseStage(String v) {
        if (!StringUtils.hasText(v)) return null;
        String s = v.trim().toUpperCase(Locale.ROOT);
        if ("E".equals(s) || "M".equals(s) || "H".equals(s)) return SchoolStage.valueOf(s);
        return switch (s) {
            case "02" -> SchoolStage.E;
            case "03" -> SchoolStage.M;
            case "04" -> SchoolStage.H;
            default -> null;
        };
    }

    @Data
    public static class SchoolInfoSyncRequest {
        private String sidoCode; // 예: 11
        private String sggCode;  // 예: 11110
        private String stage;    // E/M/H 또는 02/03/04
    }

    @Data
    public static class SchoolInfoSyncAllRequest {
        private java.util.List<String> stages; // 생략 시 E/M/H
    }
}
