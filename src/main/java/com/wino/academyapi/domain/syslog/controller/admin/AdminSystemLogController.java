// src/main/java/com/wino/academyapi/domain/syslog/controller/admin/AdminSystemLogController.java
package com.wino.academyapi.domain.syslog.controller.admin;

import com.wino.academyapi.domain.syslog.entity.AdminSystemLog;
import com.wino.academyapi.domain.syslog.repository.AdminSystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system-logs")
@RequiredArgsConstructor
public class AdminSystemLogController {
    private final AdminSystemLogRepository repo;

    /** 간단 목록(최신순). page/size 파라미터 지원 */
    @GetMapping
    public List<AdminSystemLog> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repo.findAll(pr).getContent();
    }
}
