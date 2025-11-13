// src/main/java/com/wino/academyapi/domain/student/dto/StudentSiblingDtos.java
package com.wino.academyapi.domain.student.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class StudentSiblingDtos {

    /**
     * 형제 목록 조회 응답 DTO
     * (연결된 '상대방' 학생의 요약 정보)
     */
    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiblingLinkDto {
        private Long id; // student_sibling.id (연결 ID)
        private Long studentId; //
        private String studentName;
        private String schoolStage;
        private String workLocationCode;
        private String status;
        private String relationNote;
    }

    /**
     * 형제 연결 생성 요청 DTO
     */
    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiblingCreateRequest {
        @NotNull
        private Long studentId2; //

        private String relationNote;
    }
}