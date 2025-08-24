// src/main/java/com/wino/academyapi/global/config/StartupChecks.java
package com.wino.academyapi.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 필수 시크릿 누락 시 기동 실패시키는 가드.
 * - 개발 초반엔 주석 처리해도 됨.
 */
@Configuration
public class StartupChecks {

    @Value("${SPRING_MAIL_USERNAME:}") String mailUser;
    @Value("${SPRING_MAIL_PASSWORD:}") String mailPass;
    @Value("${JWT_SECRET_KEY:}") String jwtKey;
    @Value("${AES_SECRET_KEY:}") String aesKey;

    @Bean
    ApplicationRunner verifySecrets() {
        return args -> {
            if (mailUser.isBlank() || mailPass.isBlank() || jwtKey.isBlank() || aesKey.isBlank()) {
                throw new IllegalStateException("필수 환경변수(SMTP/JWT/AES)가 누락되었습니다.");
            }
        };
    }
}
