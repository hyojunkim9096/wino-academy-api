// src/main/java/com/wino/academyapi/domain/file/repository/AttachFileRepository.java
package com.wino.academyapi.domain.file.repository;

import com.wino.academyapi.domain.file.entity.AttachFile;
import org.springframework.data.jpa.repository.JpaRepository;

/** 첨부파일 메타 JPA 리포지토리 */
public interface AttachFileRepository extends JpaRepository<AttachFile, Long> {}
