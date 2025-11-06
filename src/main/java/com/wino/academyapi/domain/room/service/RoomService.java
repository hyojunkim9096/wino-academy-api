// src/main/java/com/wino/academyapi/domain/room/service/RoomService.java
package com.wino.academyapi.domain.room.service;

import com.wino.academyapi.domain.room.dto.RoomDtos;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 강의실(Room) 관리 & 시간표 배정 서비스 (JDBC)
 *
 * ✅ 주의: 테이블 구조는 room_id(FK) 기준으로 구현됨.
 *  - class_timeslot.room_id (nullable)
 *  - 조회 표시는 room_master.code/name 로 보여주지만, 배정/검증은 room_id 로 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final NamedParameterJdbcTemplate jdbc;
    private final DbSessionVars dbVars;

    /* =========================================================================
       목록
       ========================================================================= */
    public RoomDtos.PageRes<RoomDtos.RoomRow> list(RoomDtos.ListReq req) {
        var p = new MapSqlParameterSource()
                .addValue("work", req.getWorkLocationCode())
                .addValue("kw", like(req.getKeyword()))
                .addValue("status", nullOr(req.getStatus()))
                .addValue("useYn", nullOr(req.getUseYn()))
                .addValue("limit", req.getSize())
                .addValue("offset", (req.getPage() - 1) * req.getSize());

        String where = """
            WHERE work_location_code = :work
              AND (:status IS NULL OR status = :status)
              AND (:useYn  IS NULL OR use_yn = :useYn)
              AND (:kw IS NULL OR (code LIKE :kw OR name LIKE :kw))
        """;

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM room_master " + where, p, Long.class);

        List<RoomDtos.RoomRow> items = jdbc.query("""
            SELECT id, work_location_code, code, name, capacity, max_parallel, status, memo, use_yn,
                   DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') AS created_at,
                   DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') AS updated_at
              FROM room_master
            """ + where + """
             ORDER BY sort_order ASC, code ASC
             LIMIT :limit OFFSET :offset
        """, p, roomRowMapper());

        return new RoomDtos.PageRes<>(items, total, req.getPage(), req.getSize());
    }

    private RowMapper<RoomDtos.RoomRow> roomRowMapper() {
        return (rs, rn) -> new RoomDtos.RoomRow(
                rs.getLong("id"),
                rs.getString("work_location_code"),
                rs.getString("code"),
                rs.getString("name"),
                (Integer) rs.getObject("capacity"),
                rs.getInt("max_parallel"),
                rs.getString("status"),
                rs.getString("memo"),
                rs.getString("use_yn"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private String like(String s) { return (s == null || s.isBlank()) ? null : "%" + s.trim() + "%"; }
    private String nullOr(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    /* =========================================================================
       생성
       ========================================================================= */
    @Transactional
    public Long create(Long appUserId, RoomDtos.SaveReq req) {
        dbVars.setAppUserId(appUserId);

        var p = new MapSqlParameterSource()
                .addValue("work", req.getWorkLocationCode())
                .addValue("code", req.getCode())
                .addValue("name", req.getName())
                .addValue("capacity", req.getCapacity())
                .addValue("maxp", req.getMaxParallel())
                .addValue("status", req.getStatus())
                .addValue("memo", req.getMemo())
                .addValue("useYn", req.getUseYn());

        jdbc.update("""
            INSERT INTO room_master
              (work_location_code, code, name, capacity, max_parallel, status, memo, use_yn, created_at, updated_at)
            VALUES
              (:work, :code, :name, :capacity, :maxp, :status, :memo, :useYn, NOW(), NOW())
        """, p);

        return jdbc.queryForObject("""
            SELECT id FROM room_master
             WHERE work_location_code = :work AND code = :code
        """, p, Long.class);
    }

    /* =========================================================================
       수정
       ========================================================================= */
    @Transactional
    public int update(Long appUserId, RoomDtos.UpdateReq req) {
        dbVars.setAppUserId(appUserId);

        var p = new MapSqlParameterSource()
                .addValue("id", req.getId())
                .addValue("work", req.getWorkLocationCode())
                .addValue("name", req.getName())
                .addValue("capacity", req.getCapacity())
                .addValue("maxp", req.getMaxParallel())
                .addValue("status", req.getStatus())
                .addValue("memo", req.getMemo())
                .addValue("useYn", req.getUseYn());

        return jdbc.update("""
            UPDATE room_master
               SET name = :name,
                   capacity = :capacity,
                   max_parallel = :maxp,
                   status = :status,
                   memo = :memo,
                   use_yn = :useYn,
                   updated_at = NOW()
             WHERE id = :id
               AND work_location_code = :work
        """, p);
    }

    /* =========================================================================
       사용 여부 토글
       ========================================================================= */
    @Transactional
    public int toggleUse(Long appUserId, RoomDtos.ToggleUseReq req) {
        dbVars.setAppUserId(appUserId);
        var p = new MapSqlParameterSource()
                .addValue("id", req.getId())
                .addValue("work", req.getWorkLocationCode())
                .addValue("useYn", req.getUseYn());
        return jdbc.update("""
            UPDATE room_master
               SET use_yn = :useYn,
                   updated_at = NOW()
             WHERE id = :id
               AND work_location_code = :work
        """, p);
    }

    /* =========================================================================
       정렬 저장 (지점 내 sort_order = 0..n 재부여)
       ========================================================================= */
    @Transactional
    public void reorder(RoomDtos.ReorderReq req) {
        var ids = req.getOrderedIds();
        if (ids == null || ids.isEmpty()) return;

        // 1) 요청한 id들이 해당 지점(work)에 모두 속하는지 검증
        var pCheck = new MapSqlParameterSource()
                .addValue("work", req.getWorkLocationCode())
                .addValue("ids", ids);
        int cnt = jdbc.queryForObject("""
            SELECT COUNT(*) FROM room_master
             WHERE work_location_code = :work
               AND id IN (:ids)
        """, pCheck, Integer.class);
        if (cnt != ids.size()) {
            throw new IllegalArgumentException("다른 지점의 강의실이 포함되어 있습니다.");
        }

        // 2) UNION ALL 파생 테이블 조합으로 일괄 업데이트
        StringBuilder sql = new StringBuilder();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("work", req.getWorkLocationCode());

        sql.append("UPDATE room_master rm ");
        sql.append("JOIN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(" UNION ALL ");
            sql.append("SELECT :id").append(i).append(" AS id, :ord").append(i).append(" AS ord");
            p.addValue("id" + i, ids.get(i));
            p.addValue("ord" + i, i);
        }
        sql.append(") x ON x.id = rm.id ");
        sql.append("SET rm.sort_order = x.ord, rm.updated_at = NOW() ");
        sql.append("WHERE rm.work_location_code = :work");

        jdbc.update(sql.toString(), p);
    }

    /* =========================================================================
       배정 PREVIEW
       ========================================================================= */
    public RoomDtos.AssignPreviewRes preview(RoomDtos.AssignPreviewReq req) {
        var p = new MapSqlParameterSource()
                .addValue("work", req.getWorkLocationCode())
                .addValue("dow", req.getDayOfWeek())
                .addValue("tcode", req.getClassTimeCode());

        // 현재 시간대 room_id별 배정수
        Map<Long, Integer> current = new HashMap<>();
        jdbc.query("""
            SELECT ct.room_id AS room_id, COUNT(*) AS cnt
              FROM class_timeslot ct
              JOIN class_subject cs ON cs.id = ct.class_subject_id
              JOIN class_master  cm ON cm.id = cs.class_id
             WHERE cm.work_location_code = :work
               AND ct.day_of_week = :dow
               AND ct.class_time_code = :tcode
               AND ct.room_id IS NOT NULL
             GROUP BY ct.room_id
        """, p, (ResultSet rs) -> {
            while (rs.next()) current.put(rs.getLong("room_id"), rs.getInt("cnt"));
            return null;
        });

        // 사용 가능한 방 목록(OPEN & use_yn='1') + sort_order 순
        List<RoomDtos.RoomUsage> rooms = jdbc.query("""
            SELECT id, code, name, capacity, max_parallel, status, use_yn
              FROM room_master
             WHERE work_location_code = :work
               AND status = 'OPEN'
               AND use_yn = '1'
             ORDER BY sort_order ASC, code ASC
        """, p, (rs, rn) -> {
            long id = rs.getLong("id");
            int maxp = rs.getInt("max_parallel");
            int assigned = current.getOrDefault(id, 0);
            return new RoomDtos.RoomUsage(
                    id,
                    rs.getString("code"),
                    rs.getString("name"),
                    (Integer) rs.getObject("capacity"),
                    maxp,
                    rs.getString("status"),
                    rs.getString("use_yn"),
                    assigned,
                    Math.max(0, maxp - assigned)
            );
        });

        // 시간표 행 (방 이름 표시)
        List<RoomDtos.TimeslotRow> timeslots = jdbc.query("""
            SELECT ct.id AS timeslot_id,
                   cs.class_id,
                   cm.code AS class_code,
                   cm.name AS class_name,
                   (SELECT s.name FROM subject s WHERE s.id = cs.subject_id) AS subject_name,
                   ct.day_of_week,
                   ct.class_time_code,
                   DATE_FORMAT(ct.start_time, '%H:%i:%s') AS start_time,
                   DATE_FORMAT(ct.end_time,   '%H:%i:%s') AS end_time,
                   rm.name AS room_name,
                   rm.id   AS room_id
              FROM class_timeslot ct
              JOIN class_subject cs ON cs.id = ct.class_subject_id
              JOIN class_master  cm ON cm.id = cs.class_id
              LEFT JOIN room_master rm ON rm.id = ct.room_id
             WHERE cm.work_location_code = :work
               AND ct.day_of_week = :dow
               AND ct.class_time_code = :tcode
             ORDER BY class_code, subject_name
        """, p, (rs, rn) -> new RoomDtos.TimeslotRow(
                rs.getLong("timeslot_id"),
                rs.getLong("class_id"),
                rs.getString("class_code"),
                rs.getString("class_name"),
                rs.getString("subject_name"),
                rs.getInt("day_of_week"),
                rs.getString("class_time_code"),
                rs.getString("start_time"),
                rs.getString("end_time"),
                rs.getString("room_name"),
                (Long) rs.getObject("room_id")
        ));

        return new RoomDtos.AssignPreviewRes(rooms, timeslots);
    }

    /* =========================================================================
       배정 EXEC
       ========================================================================= */
    @Transactional
    public RoomDtos.AssignAck assign(Long appUserId, RoomDtos.AssignReq req) {
        dbVars.setAppUserId(appUserId);

        String work = req.getWorkLocationCode();
        int dow = req.getDayOfWeek();
        String tcode = req.getClassTimeCode();

        Map<Long, List<Long>> wantAssign = req.getItems().stream()
                .filter(it -> it.getRoomId() != null)
                .collect(Collectors.groupingBy(RoomDtos.AssignReq.Item::getRoomId,
                        Collectors.mapping(RoomDtos.AssignReq.Item::getTimeslotId, Collectors.toList())));
        List<Long> wantClear = req.getItems().stream()
                .filter(it -> it.getRoomId() == null)
                .map(RoomDtos.AssignReq.Item::getTimeslotId)
                .collect(Collectors.toList());

        // 경합 가능성이 있는 방들을 FOR UPDATE 로 락
        Map<Long, RoomRow> roomMap = new HashMap<>();
        if (!wantAssign.isEmpty()) {
            var p = new MapSqlParameterSource()
                    .addValue("work", work)
                    .addValue("ids", wantAssign.keySet());
            jdbc.query("""
                SELECT id, code, max_parallel
                  FROM room_master
                 WHERE work_location_code = :work
                   AND id IN (:ids)
                 FOR UPDATE
            """, p, (ResultSet rs) -> {
                while (rs.next()) {
                    roomMap.put(rs.getLong("id"), new RoomRow(
                            rs.getLong("id"),
                            rs.getString("code"),
                            rs.getInt("max_parallel")
                    ));
                }
                return null;
            });
            if (roomMap.size() != wantAssign.keySet().size()) {
                throw new IllegalArgumentException("존재하지 않거나 사용할 수 없는 강의실이 포함되어 있습니다.");
            }
        }

        // 현재 시간대 room_id별 점유 수
        var base = new MapSqlParameterSource()
                .addValue("work", work).addValue("dow", dow).addValue("tcode", tcode);
        Map<Long, Integer> currentByRoom = new HashMap<>();
        jdbc.query("""
            SELECT ct.room_id, COUNT(*) AS cnt
              FROM class_timeslot ct
              JOIN class_subject cs ON cs.id = ct.class_subject_id
              JOIN class_master  cm ON cm.id = cs.class_id
             WHERE cm.work_location_code = :work
               AND ct.day_of_week = :dow
               AND ct.class_time_code = :tcode
               AND ct.room_id IS NOT NULL
             GROUP BY ct.room_id
        """, base, (ResultSet rs) -> {
            while (rs.next()) {
                Long rid = (Long) rs.getObject("room_id");
                if (rid != null) currentByRoom.put(rid, rs.getInt("cnt"));
            }
            return null;
        });

        // 요청에 포함된 타임슬롯들의 현재 room_id
        Map<Long, Long> currentRoomOfTs = new HashMap<>();
        if (!req.getItems().isEmpty()) {
            var pTs = new MapSqlParameterSource()
                    .addValue("ids", req.getItems().stream().map(RoomDtos.AssignReq.Item::getTimeslotId).toList());
            jdbc.query("""
                SELECT id AS ts_id, room_id
                  FROM class_timeslot
                 WHERE id IN (:ids)
            """, pTs, (ResultSet rs) -> {
                while (rs.next()) {
                    Long rid = (Long) rs.getObject("room_id");
                    currentRoomOfTs.put(rs.getLong("ts_id"), rid);
                }
                return null;
            });
        }

        // 시뮬레이션 및 검증
        for (var entry : wantAssign.entrySet()) {
            Long roomId = entry.getKey();
            RoomRow room = roomMap.get(roomId);
            int cur = currentByRoom.getOrDefault(roomId, 0);

            long leaveCnt = req.getItems().stream()
                    .filter(it -> Objects.equals(currentRoomOfTs.get(it.getTimeslotId()), roomId)
                            && !Objects.equals(it.getRoomId(), roomId))
                    .count();

            int incoming = (int) entry.getValue().stream()
                    .map(tsId -> Objects.equals(currentRoomOfTs.get(tsId), roomId) ? 0 : 1)
                    .reduce(0, Integer::sum);

            int projected = cur - (int) leaveCnt + incoming;
            if (projected > room.maxParallel()) {
                throw new IllegalStateException(
                        String.format("강의실[%s] 동시 허용(%d) 초과: 요청 결과 %d개 배정",
                                room.code(), room.maxParallel(), projected)
                );
            }
        }

        if (req.isDryRun()) {
            return new RoomDtos.AssignAck("dry-run ok", 0, 0);
        }

        // 실제 업데이트 수행
        int updated = 0;
        int cleared = 0;

        // 배정: room_id 세팅
        for (var entry : wantAssign.entrySet()) {
            Long roomId = entry.getKey();
            List<Long> tsIds = entry.getValue();
            var p = new MapSqlParameterSource()
                    .addValue("work", work).addValue("dow", dow).addValue("tcode", tcode)
                    .addValue("roomId", roomId).addValue("ids", tsIds);
            int n = jdbc.update("""
                UPDATE class_timeslot ct
                JOIN class_subject cs ON cs.id = ct.class_subject_id
                JOIN class_master  cm ON cm.id = cs.class_id
                   AND cm.work_location_code = :work
                   SET ct.room_id = :roomId,
                       ct.updated_at = NOW()
                 WHERE ct.id IN (:ids)
                   AND ct.day_of_week = :dow
                   AND ct.class_time_code = :tcode
            """, p);
            updated += n;
        }

        // 해제: room_id = NULL
        if (!wantClear.isEmpty()) {
            var p = new MapSqlParameterSource()
                    .addValue("work", work).addValue("dow", dow).addValue("tcode", tcode)
                    .addValue("ids", wantClear);
            cleared = jdbc.update("""
                UPDATE class_timeslot ct
                JOIN class_subject cs ON cs.id = ct.class_subject_id
                JOIN class_master  cm ON cm.id = cs.class_id
                   AND cm.work_location_code = :work
                   SET ct.room_id = NULL,
                       ct.updated_at = NOW()
                 WHERE ct.id IN (:ids)
                   AND ct.day_of_week = :dow
                   AND ct.class_time_code = :tcode
            """, p);
        }

        log.info("[Room Assign] work={}, dow={}, tcode={}, updated={}, cleared={}",
                work, dow, tcode, updated, cleared);

        return new RoomDtos.AssignAck("assign done", updated, cleared);
    }

    /** 하루 스케줄(그리드) */
    public RoomDtos.DayScheduleRes daySchedule(RoomDtos.DayScheduleReq req) {
        var p = new MapSqlParameterSource()
                .addValue("work", req.getWorkLocationCode())
                .addValue("dow", req.getDay())
                .addValue("codes", req.getClassTimeCodes());

        List<RoomDtos.RoomHead> rooms = jdbc.query("""
            SELECT id, code, name
              FROM room_master
             WHERE work_location_code = :work
               AND use_yn = '1'
             ORDER BY sort_order ASC, code ASC
        """, p, (rs, rn) -> new RoomDtos.RoomHead(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name")
        ));

        List<RoomDtos.DaySlot> slots = jdbc.query("""
            SELECT ct.class_time_code,
                   ct.room_id,
                   cm.name AS class_name
              FROM class_timeslot ct
              JOIN class_subject cs ON cs.id = ct.class_subject_id
              JOIN class_master  cm ON cm.id = cs.class_id
             WHERE cm.work_location_code = :work
               AND ct.day_of_week = :dow
               AND ct.class_time_code IN (:codes)
               AND ct.room_id IS NOT NULL
             ORDER BY ct.class_time_code, ct.room_id, cm.code
        """, p, (rs, rn) -> {
            Long rid = (Long) rs.getObject("room_id");
            return new RoomDtos.DaySlot(
                    rid,
                    rs.getString("class_time_code"),
                    rs.getString("class_name"),
                    null,
                    null
            );
        });

        return new RoomDtos.DayScheduleRes(rooms, slots);
    }

    /** 내부 record: 검증/로그 메시지용(코드+허용치) */
    private record RoomRow(Long id, String code, int maxParallel) {}
}