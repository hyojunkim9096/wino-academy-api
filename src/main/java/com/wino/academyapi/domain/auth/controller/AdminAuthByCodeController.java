// src/main/java/com/wino/academyapi/domain/auth/controller/AdminAuthByCodeController.java
package com.wino.academyapi.domain.auth.controller;

import com.wino.academyapi.domain.auth.service.AdminAuthByCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping({"/api/admin/auth", // 권장
})
@RequiredArgsConstructor
public class AdminAuthByCodeController {

    private final AdminAuthByCodeService service;

    @GetMapping(value = "/roles/{roleCode}/menus", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Long> getRoleMenus(@PathVariable String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleCode is required");
        }
        return service.getRoleMenus(roleCode.trim());
    }

    @PutMapping(value = "/roles/{roleCode}/menus", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRoleMenus(@PathVariable String roleCode,
                                @RequestBody RoleMenusUpdateRequest body) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleCode is required");
        }
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        // null 허용(=전체 삭제). 숫자 외 값은 무시.
        List<Long> ids = body.menuIds();
        service.updateRoleMenus(roleCode.trim(), ids);
    }

    public record RoleMenusUpdateRequest(List<Long> menuIds) {}
}
