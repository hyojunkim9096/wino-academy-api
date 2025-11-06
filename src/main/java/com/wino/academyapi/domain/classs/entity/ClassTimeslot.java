package com.wino.academyapi.domain.classs.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.*;

/**
 * 시간표 슬롯 - class_timeslot
 * - DB 컬럼: class_time_code / class_time_label 를 보유 (공통코드 CLASS_TIME의 code/name 저장)
 * - JSON 직렬화 필드명은 프론트 호환을 위해 startTimeCode / startTimeName 유지
 * - 여전히 start_time / end_time TIME 컬럼을 사용 (계산/검증용)
 * - DDL 정합성:
 *   * class_time_code  : VARCHAR(64)
 *   * class_time_label : VARCHAR(64)
 *   * room_id          : BIGINT NULL  // ✅ FK(선택)
 *   * room             : VARCHAR(80)
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "class_timeslot",
        indexes = @Index(name="idx_ct_week", columnList="class_subject_id, day_of_week, start_time")
)
public class ClassTimeslot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="class_subject_id", nullable=false)
    private Long classSubjectId;

    @Column(name="day_of_week", nullable=false)
    private int dayOfWeek; // 1..7

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Column(name="start_time", nullable=false)
    private LocalTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Column(name="end_time", nullable=false)
    private LocalTime endTime;

    @Column(name="room", length=80) // DDL: VARCHAR(80)
    private String room;

    // ✅ NEW: 강의실 FK — 스냅샷/복원 로직(room_id)과 일치
    @Column(name="room_id")
    private Long roomId;

    // ✅ 공통코드 CLASS_TIME (code / name) 보관용 (DB는 class_time_code/label)
    @JsonProperty("startTimeCode")                  // 프론트와의 JSON 이름 유지
    @Column(name="class_time_code", length=64)
    private String classTimeCode;

    @JsonProperty("startTimeName")                  // 프론트와의 JSON 이름 유지
    @Column(name="class_time_label", length=64)
    private String classTimeLabel;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(name="use_yn", columnDefinition="TINYINT(1)")
    private boolean useYn = true;

    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}