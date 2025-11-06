// src/main/java/com/wino/academyapi/domain/room/controller/AdminRoomController.java
package com.wino.academyapi.domain.room.controller;

import com.wino.academyapi.domain.room.dto.RoomDtos;
import com.wino.academyapi.domain.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 관리자용 강의실(Room) 관리 REST 컨트롤러
 * - 목록/검색/페이징
 * - 생성/수정
 * - 사용여부 토글
 * - 순서 변경(지점 내 정렬 저장)
 * - 시간표 배정 PREVIEW/EXEC(dryRun 지원)
 *
 * URL prefix: /api/admin/rooms
 *
 * 요청 헤더:
 *   X-App-User-Id (선택) → 없으면 0L로 처리
 */
@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
@Validated
public class AdminRoomController {

    private final RoomService svc;

    /** 목록/검색/페이징 */
    @GetMapping
    public RoomDtos.PageRes<RoomDtos.RoomRow> list(@Valid RoomDtos.ListReq req){
        return svc.list(req);
    }

    /** 생성 */
    @PostMapping
    public Long create(@RequestHeader(value="X-App-User-Id", required=false) Long appUserId,
                       @Valid @RequestBody RoomDtos.SaveReq req){
        Long uid = Objects.requireNonNullElse(appUserId, 0L);
        return svc.create(uid, req);
    }

    /** 수정 */
    @PutMapping("/{id}")
    public int update(@RequestHeader(value="X-App-User-Id", required=false) Long appUserId,
                      @PathVariable Long id,
                      @Valid @RequestBody RoomDtos.UpdateReq req){
        Long uid = Objects.requireNonNullElse(appUserId, 0L);
        req.setId(id); // PathVariable → DTO 주입
        return svc.update(uid, req);
    }

    /** 사용여부 토글 */
    @PostMapping("/toggle-use")
    public int toggleUse(@RequestHeader(value="X-App-User-Id", required=false) Long appUserId,
                         @Valid @RequestBody RoomDtos.ToggleUseReq req){
        Long uid = Objects.requireNonNullElse(appUserId, 0L);
        return svc.toggleUse(uid, req);
    }

    /** 지점 내 정렬 저장 (orderedIds 순서대로 0..n 부여) */
    @PostMapping("/reorder")
    public void reorder(@RequestHeader(value="X-App-User-Id", required=false) Long appUserId,
                        @Valid @RequestBody RoomDtos.ReorderReq req){
        svc.reorder(req);
    }

    /** 배정 PREVIEW (방 가용 & 해당 시간의 시간표 목록) */
    @GetMapping("/assign/preview")
    public RoomDtos.AssignPreviewRes preview(@Valid RoomDtos.AssignPreviewReq req){
        return svc.preview(req);
    }

    /**
     * 배정 EXEC (dryRun 지원)
     * - dryRun = true → 검증만 수행, 업데이트 없음
     * - dryRun = false → 실제 업데이트
     */
    @PostMapping("/assign")
    public RoomDtos.AssignAck assign(@RequestHeader(value="X-App-User-Id", required=false) Long appUserId,
                                     @Valid @RequestBody RoomDtos.AssignReq req){
        Long uid = Objects.requireNonNullElse(appUserId, 0L);
        return svc.assign(uid, req);
    }

    /** 하루 스케줄(그리드용) - 요일 + 여러 교시 코드 */
    @GetMapping("/schedule/day")
    public RoomDtos.DayScheduleRes daySchedule(@Valid RoomDtos.DayScheduleReq req) {
        return svc.daySchedule(req);
    }
}