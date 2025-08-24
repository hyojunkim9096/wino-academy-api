// src/main/java/com/wino/academyapi/domain/menu/service/MenuService.java
package com.wino.academyapi.domain.menu.service;

import com.wino.academyapi.domain.menu.dto.MenuDtos;
import com.wino.academyapi.domain.menu.entity.MenuItem;
import com.wino.academyapi.domain.menu.entity.MenuItem.Audience;
import com.wino.academyapi.domain.menu.entity.MenuItem.MenuType;
import com.wino.academyapi.domain.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 비즈니스 규칙
 * 1) 깊이: 최대 3
 *    - parent == null -> depth=1
 *    - depth = parent.depth + 1
 * 2) 타입:
 *    - depth 3 은 반드시 SCREEN
 *    - SCREEN 은 자식 생성 불가
 *    - FOLDER -> SCREEN 전환 시 자식이 있으면 불가
 * 3) 이름 유니크: (audience, parentId, name)
 * 4) 🆕 boardType:
 *    - USER + SCREEN일 때만 의미. 그 외에는 항상 null로 저장/유지
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepository repo;

    /* ============ 조회 ============ */

    @Transactional(readOnly = true)
    public List<MenuDtos.ItemRes> listFlat(Audience audience) {
        return repo.findByAudienceOrderByParentIdAscSortOrderAsc(audience).stream().map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.TreeNode> tree(Audience audience) {
        List<MenuItem> all = repo.findByAudienceOrderByParentIdAscSortOrderAsc(audience);
        Map<Long, MenuDtos.TreeNode> map = new HashMap<>();
        List<MenuDtos.TreeNode> roots = new ArrayList<>();
        for (MenuItem m : all) {
            MenuDtos.TreeNode n = toNode(m);
            map.put(m.getId(), n);
        }
        for (MenuItem m : all) {
            MenuDtos.TreeNode n = map.get(m.getId());
            if (m.getParentId() == null) roots.add(n);
            else {
                MenuDtos.TreeNode p = map.get(m.getParentId());
                if (p != null) p.children.add(n);
                else roots.add(n);
            }
        }
        sortTree(roots);
        return roots;
    }

    /* ============ 생성 ============ */

    @Transactional
    public MenuDtos.ItemRes create(MenuDtos.CreateReq req) {
        Audience audience = Objects.requireNonNull(req.audience(), "audience는 필수입니다.");
        String name = nn(req.name(), "이름은 필수입니다.");
        Long parentId = req.parentId();

        int depth = 1;
        MenuItem parent = null;
        if (parentId != null) {
            parent = repo.findById(parentId).orElseThrow();
            if (parent.getAudience() != audience) {
                throw new IllegalArgumentException("Audience가 다른 상위 메뉴에는 추가할 수 없습니다.");
            }
            if (parent.getType() == MenuType.SCREEN) {
                throw new IllegalArgumentException("화면(SCREEN) 아래에는 하위 메뉴를 추가할 수 없습니다.");
            }
            depth = parent.getDepth() + 1;
            if (depth > 3) throw new IllegalArgumentException("메뉴는 최대 3뎁스까지 허용됩니다.");
        }

        MenuType type = Objects.requireNonNull(req.type(), "type은 필수입니다.");
        if (depth == 3 && type != MenuType.SCREEN) {
            throw new IllegalArgumentException("3뎁스에는 화면(SCREEN)만 등록할 수 있습니다.");
        }

        // 이름 유니크
        if (repo.existsByAudienceAndParentIdAndName(audience, parentId, name)) {
            throw new IllegalArgumentException("같은 위치에 동일한 메뉴명이 이미 존재합니다.");
        }

        // 정렬 마지막 뒤로
        int nextOrder = repo.findByAudienceAndParentIdOrderBySortOrderAsc(audience, parentId).size();

        // SCREEN 필수 필드 검증
        String path = nz(req.path());
        String key = nz(req.componentKey());
        if (type == MenuType.SCREEN) {
            if (path == null || key == null) {
                throw new IllegalArgumentException("화면(SCREEN)에는 path와 componentKey가 필요합니다.");
            }
        } else {
            // FOLDER는 path/componentKey 제거
            path = null;
            key = null;
        }

        // 🆕 boardType 결정 — USER+SCREEN일 때만 값 반영(없으면 null로 저장)
        String boardType = null;
        if (type == MenuType.SCREEN && audience == Audience.USER) {
            boardType = nz(req.boardType());
            // 필수로 강제하려면 위를: boardType = nn(req.boardType(), "게시판 타입을 선택하세요.");
        }

        MenuItem m = MenuItem.builder()
                .audience(audience)
                .type(type)
                .depth(depth)
                .name(name)
                .path(path)
                .componentKey(key)
                .boardType(boardType)                  // ✅ 저장
                .icon(nz(req.icon()))
                .parentId(parentId)
                .sortOrder(nextOrder)
                .visible(Boolean.TRUE.equals(req.visible()))
                .enabled(Boolean.TRUE.equals(req.enabled()))
                .requiredRole(nz(req.requiredRole()))
                .build();

        return toRes(repo.save(m));
    }

    /* ============ 수정 ============ */

    @Transactional
    public MenuDtos.ItemRes update(Long id, MenuDtos.UpdateReq req) {
        MenuItem m = repo.findById(id).orElseThrow();

        // 이름 유니크 체크(변경 시)
        String newName = nn(req.name(), "이름은 필수입니다.");
        if (!newName.equals(m.getName())) {
            if (repo.existsByAudienceAndParentIdAndName(m.getAudience(), m.getParentId(), newName)) {
                throw new IllegalArgumentException("같은 위치에 동일한 메뉴명이 이미 존재합니다.");
            }
            m.setName(newName);
        }

        // 타입 변경
        MenuType newType = Objects.requireNonNull(req.type(), "type은 필수입니다.");
        if (m.getType() != newType) {
            if (newType == MenuType.SCREEN) {
                // FOLDER -> SCREEN 전환: 자식 없어야 함
                List<MenuItem> children = repo.findByAudienceAndParentIdOrderBySortOrderAsc(m.getAudience(), m.getId());
                if (!children.isEmpty()) {
                    throw new IllegalArgumentException("하위 메뉴가 있어 화면(SCREEN)으로 변경할 수 없습니다.");
                }
            }
            if (m.getDepth() == 3 && newType != MenuType.SCREEN) {
                throw new IllegalArgumentException("3뎁스에는 화면(SCREEN)만 허용됩니다.");
            }
            m.setType(newType);
        }

        // 부모 변경
        Long oldParent = m.getParentId();
        Long newParent = req.parentId();
        if (!Objects.equals(oldParent, newParent)) {
            MenuItem parent = null;
            int newDepth = 1;
            if (newParent != null) {
                parent = repo.findById(newParent).orElseThrow();
                if (parent.getAudience() != m.getAudience()) {
                    throw new IllegalArgumentException("Audience가 다른 상위 메뉴로 이동할 수 없습니다.");
                }
                if (parent.getType() == MenuType.SCREEN) {
                    throw new IllegalArgumentException("화면(SCREEN) 아래에는 하위 메뉴를 둘 수 없습니다.");
                }
                newDepth = parent.getDepth() + 1;
                if (newDepth > 3) throw new IllegalArgumentException("메뉴는 최대 3뎁스까지 허용됩니다.");
            }
            if (newDepth == 3 && m.getType() != MenuType.SCREEN) {
                throw new IllegalArgumentException("3뎁스에는 화면(SCREEN)만 허용됩니다.");
            }
            // 기존 부모 정렬 압축
            compressOrder(m.getAudience(), oldParent);
            // 새 부모의 맨 뒤로
            int nextOrder = repo.findByAudienceAndParentIdOrderBySortOrderAsc(m.getAudience(), newParent).size();
            m.setParentId(newParent);
            m.setSortOrder(nextOrder);
            m.setDepth(newDepth);
        }

        // SCREEN 필드 검증
        String path = nz(req.path());
        String key = nz(req.componentKey());
        if (m.getType() == MenuType.SCREEN) {
            if (path == null || key == null) {
                throw new IllegalArgumentException("화면(SCREEN)에는 path와 componentKey가 필요합니다.");
            }
            m.setPath(path);
            m.setComponentKey(key);

            // 🆕 boardType: USER일 때만 반영, 그 외 null
            if (m.getAudience() == Audience.USER) {
                String bt = (req.boardType() != null) ? nz(req.boardType()) : m.getBoardType();
                m.setBoardType(bt);     // (필수 강제하려면 nn(..) 사용)
            } else {
                m.setBoardType(null);
            }
        } else {
            // FOLDER면 관련 필드 제거
            m.setPath(null);
            m.setComponentKey(null);
            m.setBoardType(null);        // 🆕
        }

        m.setIcon(nz(req.icon()));
        m.setVisible(Boolean.TRUE.equals(req.visible()));
        m.setEnabled(Boolean.TRUE.equals(req.enabled()));
        m.setRequiredRole(nz(req.requiredRole()));

        return toRes(m);
    }

    /* ============ 삭제 ============ */

    @Transactional
    public void delete(Long id) {
        MenuItem m = repo.findById(id).orElseThrow();
        // 재귀 삭제
        deleteRecursively(m.getAudience(), id);
        // 기존 부모 정렬 압축
        compressOrder(m.getAudience(), m.getParentId());
    }

    /* ============ 재정렬 ============ */

    @Transactional
    public void reorder(MenuDtos.ReorderReq req) {
        Audience audience = Objects.requireNonNull(req.audience(), "audience는 필수입니다.");
        Long parent = req.parentId();
        List<Long> ids = Optional.ofNullable(req.orderedIds()).orElse(List.of());

        List<MenuItem> children = repo.findByAudienceAndParentIdOrderBySortOrderAsc(audience, parent);
        Map<Long, MenuItem> map = children.stream().collect(Collectors.toMap(MenuItem::getId, x -> x));

        // 요청된 id 검증
        for (Long id : ids) {
            if (!map.containsKey(id)) throw new IllegalArgumentException("잘못된 재정렬 요청입니다.");
        }
        // 0.. 순서로 부여
        int i = 0;
        for (Long id : ids) map.get(id).moveTo(i++);
        for (MenuItem c : children) if (!ids.contains(c.getId())) c.moveTo(i++);
    }

    /* ============ 내부 유틸 ============ */

    private void deleteRecursively(Audience audience, Long id) {
        List<MenuItem> children = repo.findByAudienceAndParentIdOrderBySortOrderAsc(audience, id);
        for (MenuItem c : children) deleteRecursively(audience, c.getId());
        repo.deleteById(id);
    }

    private void compressOrder(Audience audience, Long parentId) {
        List<MenuItem> children = repo.findByAudienceAndParentIdOrderBySortOrderAsc(audience, parentId);
        int i = 0; for (MenuItem c : children) c.setSortOrder(i++);
    }

    private void sortTree(List<MenuDtos.TreeNode> nodes) {
        nodes.sort(Comparator.comparingInt(n -> n.sortOrder));
        for (MenuDtos.TreeNode n : nodes) {
            if (n.children != null && !n.children.isEmpty()) sortTree(n.children);
        }
    }

    private MenuDtos.ItemRes toRes(MenuItem m) {
        return new MenuDtos.ItemRes(
                m.getId(), m.getAudience(), m.getDepth(), m.getName(), m.getType(),
                m.getPath(), m.getComponentKey(), m.getBoardType(),    // 🆕 포함
                m.getIcon(), m.getParentId(), m.getSortOrder(),
                m.isVisible(), m.isEnabled(), m.getRequiredRole()
        );
    }

    private MenuDtos.TreeNode toNode(MenuItem m) {
        var n = new MenuDtos.TreeNode();
        n.id = m.getId(); n.audience = m.getAudience(); n.depth = m.getDepth(); n.name = m.getName();
        n.type = m.getType(); n.path = m.getPath(); n.componentKey = m.getComponentKey();
        n.boardType = m.getBoardType();        // 🆕 포함
        n.icon = m.getIcon(); n.parentId = m.getParentId(); n.sortOrder = m.getSortOrder();
        n.visible = m.isVisible(); n.enabled = m.isEnabled(); n.requiredRole = m.getRequiredRole();
        return n;
    }

    private static String nn(String v, String err) {
        String t = nz(v); if (t == null) throw new IllegalArgumentException(err); return t;
    }
    private static String nz(String v) {
        if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : t;
    }
}
