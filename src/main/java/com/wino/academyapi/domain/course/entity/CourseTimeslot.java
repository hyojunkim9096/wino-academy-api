package com.wino.academyapi.domain.course.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.*;

/**
 * 시간표 슬롯 엔티티 - (구 ClassTimeslot)
 * ✅ 리네이밍: ClassTimeslot -> CourseTimeslot
 * ✅ 테이블명 유지: class_timeslot
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "class_timeslot",
        indexes = @Index(name="idx_ct_week", columnList="class_subject_id, day_of_week, start_time")
)
public class CourseTimeslot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DB 컬럼명 유지, 자바 필드명 변경
    @Column(name="class_subject_id", nullable=false)
    private Long courseSubjectId;

    @Column(name="day_of_week", nullable=false)
    private int dayOfWeek;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Column(name="start_time", nullable=false)
    private LocalTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Column(name="end_time", nullable=false)
    private LocalTime endTime;

    @Column(name="room", length=80)
    private String room;

    @Column(name="room_id")
    private Long roomId;

    // 프론트 JSON 호환 유지
    @JsonProperty("startTimeCode")
    @Column(name="class_time_code", length=64)
    private String classTimeCode;

    @JsonProperty("startTimeName")
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