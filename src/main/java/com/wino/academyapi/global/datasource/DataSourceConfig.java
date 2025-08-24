// src/main/java/com/wino/academyapi/global/datasource/DataSourceConfig.java
package com.wino.academyapi.global.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * - 부트가 제공하는 DataSourceProperties 빈을 파라미터로 주입받아 원본 Hikari 풀 생성(bootDataSource)
 * - Hikari 전용 설정은 spring.datasource.hikari.* 로 바인딩
 * - 최종적으로 'dataSource' 이름의 LazySwapDataSource 를 @Primary 로 노출(JPA가 참조)
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    /** 원본 Hikari 풀 (코어 속성: url/username/password/driver-class) + hikari.* 바인딩 */
    @Bean(name = "bootDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource bootDataSource(DataSourceProperties props) {
        // DataSourceProperties 는 스프링 부트가 이미 빈으로 제공 → 여기서 주입만 받음(중복 등록 X)
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /** JPA가 찾는 'dataSource' 이름의 @Primary 빈 = LazySwap 래퍼 */
    @Bean(name = "dataSource")
    @Primary
    public LazySwapDataSource dataSource(DataSource bootDataSource) {
        log.info("[DS] Wrapping bootDataSource with LazySwapDataSource");
        return new LazySwapDataSource(bootDataSource);
    }
}
