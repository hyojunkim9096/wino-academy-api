// src/main/java/com/wino/academyapi/domain/menu/dto/MenuDtos.java
package com.wino.academyapi.domain.menu.dto;

import com.wino.academyapi.domain.menu.entity.MenuItem;
import com.wino.academyapi.domain.menu.entity.MenuItem.Audience;
import com.wino.academyapi.domain.menu.entity.MenuItem.MenuType;

import java.util.*;
import java.util.stream.Collectors;

public class MenuDtos {

    /* ====== 생성/수정/재정렬 DTO(기존 유지) ====== */

    public record CreateReq(
            Audience audience,
            Long parentId,
            String name,
            MenuType type,
            String path,
            String componentKey,
            String boardType,
            String icon,
            Boolean visible,
            Boolean enabled,
            String requiredRole
    ) {}

    public record UpdateReq(
            String name,
            MenuType type,
            Long parentId,
            String path,
            String componentKey,
            String boardType,
            String icon,
            Boolean visible,
            Boolean enabled,
            String requiredRole
    ) {}

    public record ReorderReq(
            Audience audience,
            Long parentId,
            List<Long> orderedIds
    ) {}

    public record ItemRes(
            Long id,
            Audience audience,
            int depth,
            String name,
            MenuType type,
            String path,
            String componentKey,
            String boardType,
            String icon,
            Long parentId,
            int sortOrder,
            boolean visible,
            boolean enabled,
            String requiredRole
    ) {}

    /* ====== 트리 노드 ====== */
    public static class TreeNode {
        public Long id;
        public Audience audience;
        public int depth;
        public String name;
        public MenuType type;
        public String path;
        public String componentKey;
        public String boardType;
        public String icon;
        public Long parentId;
        public int sortOrder;
        public boolean visible;
        public boolean enabled;
        public String requiredRole;
        public List<TreeNode> children = new ArrayList<>();
    }

    /* ====== 유틸: 평면 → 트리 ====== */
    public static List<TreeNode> buildTreeFrom(List<MenuItem> flat) {
        if (flat == null || flat.isEmpty()) return List.of();

        Map<Long, TreeNode> map = new LinkedHashMap<>();
        for (MenuItem m : flat) {
            TreeNode t = new TreeNode();
            t.id = m.getId();
            t.audience = m.getAudience();
            t.depth = m.getDepth();
            t.name = m.getName();
            t.type = m.getType();
            t.path = m.getPath();
            t.componentKey = m.getComponentKey();
            t.boardType = m.getBoardType();
            t.icon = m.getIcon();
            t.parentId = m.getParentId();
            t.sortOrder = m.getSortOrder();
            t.visible = m.isVisible();
            t.enabled = m.isEnabled();
            t.requiredRole = m.getRequiredRole();
            map.put(t.id, t);
        }
        List<TreeNode> roots = new ArrayList<>();
        for (TreeNode t : map.values()) {
            if (t.parentId == null) {
                roots.add(t);
            } else {
                TreeNode p = map.get(t.parentId);
                if (p != null) p.children.add(t);
                else roots.add(t); // 고아 데이터 방어
            }
        }
        // 자식 정렬(옵션)
        map.values().forEach(n ->
                n.children.sort(Comparator.comparingInt((TreeNode a) -> a.sortOrder).thenComparingLong(a -> a.id))
        );
        roots.sort(Comparator.comparingInt((TreeNode a) -> a.sortOrder).thenComparingLong(a -> a.id));
        return roots;
    }

    /* ====== 유틸: allowed id 기준 필터 ====== */
    public static List<TreeNode> filterByAllowedIds(List<TreeNode> roots, Set<Long> allowed) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<TreeNode> out = new ArrayList<>();
        for (TreeNode n : roots) {
            TreeNode kept = keepIfAllowed(copyNode(n), allowed);
            if (kept != null) out.add(kept);
        }
        return out;
    }

    private static TreeNode keepIfAllowed(TreeNode n, Set<Long> allowed) {
        if (n.children == null || n.children.isEmpty()) {
            return allowed.contains(n.id) ? n : null;
        }
        List<TreeNode> kept = new ArrayList<>();
        for (TreeNode ch : n.children) {
            TreeNode k = keepIfAllowed(ch, allowed);
            if (k != null) kept.add(k);
        }
        n.children = kept;
        if (!kept.isEmpty()) return n;          // 자식 중 하나라도 허용 → 폴더 유지
        return allowed.contains(n.id) ? n : null;
    }

    private static TreeNode copyNode(TreeNode m) {
        TreeNode t = new TreeNode();
        t.id = m.id; t.audience = m.audience; t.depth = m.depth; t.name = m.name;
        t.type = m.type; t.path = m.path; t.componentKey = m.componentKey; t.boardType = m.boardType;
        t.icon = m.icon; t.parentId = m.parentId; t.sortOrder = m.sortOrder; t.visible = m.visible;
        t.enabled = m.enabled; t.requiredRole = m.requiredRole;
        t.children = new ArrayList<>();
        m.children.forEach(c -> t.children.add(copyNode(c)));
        return t;
    }
}
