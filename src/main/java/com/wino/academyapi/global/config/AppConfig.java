// src/main/java/com/wino/academyapi/global/config/AppConfig.java
package com.wino.academyapi.global.config;

import com.wino.academyapi.global.storage.FileStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** storage.attach.* 바인딩 활성화 */
@Configuration
@EnableConfigurationProperties({ FileStorageProperties.class })
public class AppConfig { }
