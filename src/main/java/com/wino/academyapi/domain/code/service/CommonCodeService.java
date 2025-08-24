// src/main/java/com/wino/academyapi/domain/code/service/CommonCodeService.java
package com.wino.academyapi.domain.code.service;

import com.wino.academyapi.domain.code.dto.CommonCodeDtos.*;
import com.wino.academyapi.domain.code.entity.CommonCode;
import com.wino.academyapi.domain.code.entity.CommonCodeGroup;
import com.wino.academyapi.domain.code.repository.CommonCodeGroupRepository;
import com.wino.academyapi.domain.code.repository.CommonCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonCodeService {

    private final CommonCodeGroupRepository groupRepo;
    private final CommonCodeRepository codeRepo;

    /* ===== 그룹 ===== */

    @Transactional(readOnly = true)
    public List<CodeGroupResponse> listGroups() {
        return groupRepo.findAll().stream()
                .sorted(Comparator.comparingInt(CommonCodeGroup::getSortOrder).thenComparing(CommonCodeGroup::getGroupCode))
                .map(this::toGroupRes)
                .toList();
    }

    @Transactional
    public void createGroup(CodeGroupRequest req) {
        final String groupCode = nn(req.getGroupCode(), "groupCode는 필수입니다.").toUpperCase();
        if (groupRepo.existsByGroupCode(groupCode)) {
            throw new IllegalArgumentException("이미 존재하는 groupCode 입니다.");
        }
        CommonCodeGroup g = CommonCodeGroup.builder()
                .groupCode(groupCode)
                .name(nn(req.getName(), "name은 필수입니다."))
                .description(nz(req.getDescription()))
                .sortOrder(nni(req.getSortOrder(), 0))
                .enabled(nnb(req.getEnabled(), true))
                .build();
        groupRepo.save(g);
    }

    @Transactional
    public void updateGroup(String groupCode, CodeGroupRequest req) {
        CommonCodeGroup g = groupRepo.findByGroupCode(groupCode).orElseThrow();
        g.setName(nn(req.getName(), "name은 필수입니다."));
        g.setDescription(nz(req.getDescription()));
        g.setSortOrder(nni(req.getSortOrder(), 0));
        g.setEnabled(nnb(req.getEnabled(), true));
    }

    @Transactional
    public void deleteGroup(String groupCode) {
        // 소속 코드부터 삭제
        codeRepo.findByGroupCodeOrderBySortOrderAscCodeAsc(groupCode)
                .forEach(c -> codeRepo.deleteById(c.getId()));
        groupRepo.deleteByGroupCode(groupCode);
    }

    /* ===== 코드 ===== */

    @Transactional(readOnly = true)
    public List<CodeItemResponse> listCodes(String groupCode) {
        return codeRepo.findByGroupCodeOrderBySortOrderAscCodeAsc(groupCode).stream()
                .map(this::toCodeRes)
                .toList();
    }

    @Transactional
    public void createCode(String groupCode, CodeItemRequest req) {
        groupRepo.findByGroupCode(groupCode).orElseThrow(); // 그룹 존재 검증
        final String code = nn(req.getCode(), "code는 필수입니다.").toUpperCase();
        if (codeRepo.existsByGroupCodeAndCode(groupCode, code)) {
            throw new IllegalArgumentException("이미 존재하는 code 입니다.");
        }
        CommonCode c = CommonCode.builder()
                .groupCode(groupCode)
                .code(code)
                .name(nn(req.getName(), "name은 필수입니다."))
                .sortOrder(nni(req.getSortOrder(), 0))
                .enabled(nnb(req.getEnabled(), true))
                .metaJson(nz(req.getMetaJson()))
                .build();
        codeRepo.save(c);
    }

    @Transactional
    public void updateCode(String groupCode, String origCode, CodeItemRequest req) {
        CommonCode c = codeRepo.findByGroupCodeAndCode(groupCode, origCode).orElseThrow();
        final String newCode = nn(req.getCode(), "code는 필수입니다.").toUpperCase();
        if (!newCode.equals(origCode) && codeRepo.existsByGroupCodeAndCode(groupCode, newCode)) {
            throw new IllegalArgumentException("이미 존재하는 code 입니다.");
        }
        c.setCode(newCode);
        c.setName(nn(req.getName(), "name은 필수입니다."));
        c.setSortOrder(nni(req.getSortOrder(), 0));
        c.setEnabled(nnb(req.getEnabled(), true));
        c.setMetaJson(nz(req.getMetaJson()));
    }

    @Transactional
    public void deleteCode(String groupCode, String code) {
        codeRepo.deleteByGroupCodeAndCode(groupCode, code);
    }

    /* ===== 변환/유틸 ===== */

    private CodeGroupResponse toGroupRes(CommonCodeGroup g) {
        return CodeGroupResponse.builder()
                .groupCode(g.getGroupCode())
                .name(g.getName())
                .description(g.getDescription())
                .sortOrder(g.getSortOrder())
                .enabled(g.isEnabled())
                .build();
    }

    private CodeItemResponse toCodeRes(CommonCode c) {
        return CodeItemResponse.builder()
                .code(c.getCode())
                .name(c.getName())
                .sortOrder(c.getSortOrder())
                .enabled(c.isEnabled())
                .metaJson(c.getMetaJson())
                .build();
    }

    private static String nn(String v, String err) {
        String t = nz(v); if (t == null) throw new IllegalArgumentException(err); return t;
    }
    private static String nz(String v) {
        if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : t;
    }
    private static int nni(Integer v, int def) { return v != null ? v : def; }
    private static boolean nnb(Boolean v, boolean def) { return v != null ? v : def; }
}
