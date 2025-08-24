// src/main/java/com/wino/academyapi/global/storage/FileStorageProperties.java
package com.wino.academyapi.global.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application-*.yml 의 storage.attach.* 바인딩 */
@Getter @Setter
@ConfigurationProperties(prefix = "storage.attach")
public class FileStorageProperties {
    /** 베이스 디렉터리 (dev: D:\projects\attach_file / prod: /data/attach_file 등) */
    private String basePath;
}
