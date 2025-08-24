// src/main/java/com/wino/academyapi/domain/cors/controller/AdminCorsController.java
package com.wino.academyapi.domain.cors.controller;

import com.wino.academyapi.domain.cors.entity.CorsSetting;
import com.wino.academyapi.domain.cors.repository.CorsSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/cors-setting")
@RequiredArgsConstructor
public class AdminCorsController {
    private final CorsSettingRepository repo;

    @GetMapping public CorsSetting get() { return repo.findTopByOrderByIdAsc().orElse(null); }

    @PostMapping @Transactional
    public CorsSetting save(@RequestBody CorsSetting req){
        return repo.findTopByOrderByIdAsc().map(prev -> {
            prev.setAllowedOrigins(req.getAllowedOrigins());
            prev.setAllowedMethods(req.getAllowedMethods());
            prev.setAllowedHeaders(req.getAllowedHeaders());
            prev.setExposedHeaders(req.getExposedHeaders());
            prev.setMaxAge(req.getMaxAge());
            prev.setAllowCredentials(req.getAllowCredentials());
            return repo.save(prev);
        }).orElseGet(() -> repo.save(req));
    }
}
