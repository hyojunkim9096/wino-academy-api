package com.wino.academyapi.domain.file.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 첨부파일 메타정보
 * DB 테이블 정의(네가 준 스키마)에 정확히 맞춘 매핑:
 *
 *  CREATE TABLE attach_file (
 *    id BIGINT PK,
 *    original_name  VARCHAR(255) NOT NULL,
 *    saved_name     VARCHAR(255) NOT NULL,
 *    ext            VARCHAR(20)  NULL,
 *    content_type   VARCHAR(100) NULL,
 *    file_size      BIGINT       NULL,
 *    directory      VARCHAR(255) NULL,
 *    absolute_path  VARCHAR(500) NULL,
 *    relative_path  VARCHAR(500) NULL,            -- ★ 본문 DDL로 추가
 *    created_at     DATETIME     NULL
 *  )
 *
 * 주의:
 *  - absolute_path 가 있으면 파일 응답 시 최우선 사용
 *  - relative_path 가 있으면 base-path + relative_path 사용
 *  - 둘 다 없으면 directory + saved_name 로 상대경로 구성
 */
@Entity
@Table(name = "attach_file")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AttachFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원본 파일명 */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** 저장 파일명(UUID.확장자 등) */
    @Column(name = "saved_name", nullable = false, length = 255)
    private String savedName;

    /** 확장자 (예: jpg, png, webp) */
    @Column(name = "ext", length = 20)
    private String ext;

    /** MIME 타입 (예: image/jpeg) */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 파일 크기(byte) */
    @Column(name = "file_size")
    private Long fileSize;

    /** 논리 디렉토리 (예: profiles/2025/08) */
    @Column(name = "directory", length = 255)
    private String directory;

    /** 절대경로(옵션) */
    @Column(name = "absolute_path", length = 500)
    private String absolutePath;

    /** base-path 기준 상대경로 (예: profiles/2025/08/abc.png) */
    @Column(name = "relative_path", length = 500)
    private String relativePath;

    /** 생성 시각(애플리케이션에서 세팅 or DB 트리거) */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /* ================= 편의 메서드 ================= */

    /**
     * 안전한 상대경로 반환
     * - relative_path 있으면 그대로 사용
     * - 없으면 directory + '/' + saved_name
     * - 둘 다 없으면 null
     */
    public String getSafeRelativePath() {
        if (relativePath != null && !relativePath.isBlank()) return relativePath;
        if (directory != null && !directory.isBlank() && savedName != null && !savedName.isBlank()) {
            String dir = directory.endsWith("/") ? directory.substring(0, directory.length() - 1) : directory;
            return dir + "/" + savedName;
        }
        return null;
    }
}
