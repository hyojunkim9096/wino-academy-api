// src/main/java/com/wino/academyapi/domain/menu/controller/admin/AdminMenuQueryController.java
package com.wino.academyapi.domain.menu.controller.admin;

import com.wino.academyapi.domain.menu.dto.MenuDtos.TreeNode;
import com.wino.academyapi.domain.menu.entity.MenuItem.Audience;
import com.wino.academyapi.domain.menu.service.AdminMenuQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
public class AdminMenuQueryController {

    private final AdminMenuQueryService queryService;

    @GetMapping(value="/my", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TreeNode>> myMenus(
            @RequestParam(defaultValue = "ADMIN") Audience audience
    ) {
        return ResponseEntity.ok(queryService.mySidebar(audience));
    }
}
