// src/main/java/com/wino/academyapi/domain/menu/controller/admin/MenuController.java
package com.wino.academyapi.domain.menu.controller.admin;

import com.wino.academyapi.domain.menu.dto.MenuDtos;
import com.wino.academyapi.domain.menu.entity.MenuItem.Audience;
import com.wino.academyapi.domain.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/menus")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/flat")
    public ResponseEntity<List<MenuDtos.ItemRes>> flat(@RequestParam("audience") Audience audience) {
        return ResponseEntity.ok(menuService.listFlat(audience));
    }

    @GetMapping("/tree")
    public ResponseEntity<List<MenuDtos.TreeNode>> tree(@RequestParam("audience") Audience audience) {
        return ResponseEntity.ok(menuService.tree(audience));
    }

    @PostMapping
    public ResponseEntity<MenuDtos.ItemRes> create(@RequestBody MenuDtos.CreateReq req) {
        var created = menuService.create(req);
        return ResponseEntity.created(URI.create("/api/admin/menus/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuDtos.ItemRes> update(@PathVariable Long id, @RequestBody MenuDtos.UpdateReq req) {
        return ResponseEntity.ok(menuService.update(id, req));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody MenuDtos.ReorderReq req) {
        menuService.reorder(req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
