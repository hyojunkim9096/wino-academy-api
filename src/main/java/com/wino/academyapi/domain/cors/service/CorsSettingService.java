// src/main/java/com/wino/academyapi/domain/cors/service/CorsSettingService.java
package com.wino.academyapi.domain.cors.service;

import com.wino.academyapi.domain.cors.entity.CorsSetting;
import com.wino.academyapi.domain.cors.repository.CorsSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CorsSettingService {
    private final CorsSettingRepository repo;

    public CorsConfiguration loadAsSpringConfig() {
        try {
            CorsSetting s = repo.findTopByOrderByIdAsc().orElse(null);
            if (s == null) return defaultConfig();

            CorsConfiguration c = new CorsConfiguration();
            c.setAllowedOriginPatterns(splitCsv(s.getAllowedOrigins()));
            c.setAllowedMethods(splitCsv(s.getAllowedMethods()));
            c.setAllowedHeaders(splitCsv(s.getAllowedHeaders()));
            c.setExposedHeaders(splitCsv(s.getExposedHeaders()));
            if (s.getAllowCredentials() != null) c.setAllowCredentials(s.getAllowCredentials());
            if (s.getMaxAge() != null) c.setMaxAge(s.getMaxAge().longValue());
            return c;
        } catch (DataAccessException e) {
            return defaultConfig();
        }
    }

    private CorsConfiguration defaultConfig() {
        CorsConfiguration c = new CorsConfiguration();
        // Java 8 호환: List.of → Collections.singletonList / Arrays.asList
        c.setAllowedOriginPatterns(Collections.singletonList("*"));
        c.setAllowedMethods(
                Arrays.stream(HttpMethod.values()).map(h -> h.name()).collect(Collectors.toList())
        );
        c.setAllowedHeaders(Collections.singletonList("*"));
        c.setExposedHeaders(Arrays.asList("Authorization", "X-Requested-With", "Content-Disposition"));
        c.setAllowCredentials(false);
        c.setMaxAge(3600L);
        return c;
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(csv.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
