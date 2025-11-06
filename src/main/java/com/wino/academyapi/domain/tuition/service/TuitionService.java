package com.wino.academyapi.domain.tuition.service;

import com.wino.academyapi.domain.tuition.dto.TuitionDtos.*;
import com.wino.academyapi.domain.tuition.entity.TuitionCategory;
import com.wino.academyapi.domain.tuition.entity.TuitionPrice;
import com.wino.academyapi.domain.tuition.repository.TuitionCategoryRepository;
import com.wino.academyapi.domain.tuition.repository.TuitionPriceRepository;
import com.wino.academyapi.domain.code.entity.CommonCode;
import com.wino.academyapi.domain.code.repository.CommonCodeRepository;
import com.wino.academyapi.domain.tuition.support.TuitionCategoryUsageChecker;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;
import static org.springframework.http.HttpStatus.*;

/**
 * 수강료 카테고리/가격 도메인 서비스
 * - 학부/학년코드 정합성 검사
 * - 카테고리 생성/수정시 부모-자식 정합성/깊이(depth) 자동산정
 * - 삭제시 외부 참조 체크(확장포인트 TuitionCategoryUsageChecker)
 * - 활성 가격 조회(stage + gradeCode)
 */
@Service
@RequiredArgsConstructor
public class TuitionService {

    private final TuitionCategoryRepository catRepo;
    private final TuitionPriceRepository priceRepo;
    private final CommonCodeRepository codeRepo;
    private final DbSessionVars dbVars;
    private final TuitionCategoryUsageChecker usageChecker; // 🔌 외부 참조 확인(기본 Noop)

    private void setActor(Long appUserId) {
        dbVars.setAppUserId(appUserId == null ? 0L : appUserId);
    }

    private static String stageToGroup(String stage){
        return switch (String.valueOf(stage).toUpperCase()) {
            case "E" -> "GRADE_E";
            case "M" -> "GRADE_M";
            case "H" -> "GRADE_H";
            default -> null;
        };
    }
    private static String blankToNull(String s){ return (s==null || s.isBlank()) ? null : s; }

    /** 리프 카테고리의 (학부 ↔ 학년그룹/코드) 정합성 검증 */
    private void validateGradePair(String stage, String gradeGroup, String gradeCode, boolean isLeaf){
        if (!isLeaf) return; // 리프 아닐 때는 스킵
        if (blankToNull(gradeGroup) == null || blankToNull(gradeCode) == null) {
            throw new ResponseStatusException(BAD_REQUEST, "리프 카테고리는 학년코드를 선택하세요.");
        }
        String expected = stageToGroup(stage);
        if (!Objects.equals(expected, gradeGroup)) {
            throw new ResponseStatusException(BAD_REQUEST, "학부와 학년 그룹이 일치하지 않습니다.");
        }
        // 공통코드 존재 + enabled
        Optional<CommonCode> oc = codeRepo.findByGroupCodeAndCode(gradeGroup, gradeCode);
        if (oc.isEmpty() || !oc.get().isEnabled()) {
            throw new ResponseStatusException(BAD_REQUEST, "유효하지 않은 학년 코드입니다.");
        }
    }

    // ========================== Category ==========================

    @Transactional(readOnly = true)
    public List<CategoryRes> listCategories(String stage, Long parentId) {
        return catRepo.findByStageAndParent(stage, parentId).stream().map(this::toRes).toList();
    }

    @Transactional
    public CategoryRes createCategory(CategoryUpsertReq req, Long appUserId) {
        setActor(appUserId);

        // (stage, code) 유니크(선택 컬럼이므로 null은 허용)
        if (req.code() != null && !req.code().isBlank()) {
            catRepo.findBySchoolStageAndCode(req.schoolStage(), req.code())
                    .ifPresent(x -> { throw new ResponseStatusException(CONFLICT, "동일 학부 내 코드가 이미 존재합니다."); });
        }

        // 부모-자식 정합성: parentId가 있으면 같은 학부여야 하며 depth = parent.depth + 1
        Integer depth = requireNonNullElse(req.depth(), 1);
        if (req.parentId() != null) {
            TuitionCategory parent = catRepo.findById(req.parentId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "상위 카테고리를 찾을 수 없습니다."));
            if (!Objects.equals(parent.getSchoolStage(), req.schoolStage())) {
                throw new ResponseStatusException(BAD_REQUEST, "상위 카테고리의 학부와 일치하지 않습니다.");
            }
            depth = requireNonNullElse(parent.getDepth(), 1) + 1;
        }

        boolean leaf = Boolean.TRUE.equals(req.isLeaf());
        String gradeGroup = blankToNull(req.gradeGroup());
        String gradeCode  = blankToNull(req.gradeCode());
        if (leaf) validateGradePair(req.schoolStage(), gradeGroup, gradeCode, true);

        TuitionCategory c = TuitionCategory.builder()
                .schoolStage(req.schoolStage())
                .name(req.name())
                .code(blankToNull(req.code()))
                .description(req.description())
                .depth(depth)
                .parentId(req.parentId())
                .sortOrder(requireNonNullElse(req.sortOrder(), 0))
                .leaf(leaf)
                .useYn(!Boolean.FALSE.equals(req.useYn()))
                .gradeGroup(leaf ? gradeGroup : null)
                .gradeCode(leaf ? gradeCode  : null)
                .build();

        return toRes(catRepo.save(c));
    }

    @Transactional
    public CategoryRes updateCategory(Long id, CategoryUpsertReq req, Long appUserId) {
        setActor(appUserId);

        TuitionCategory c = catRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카테고리를 찾을 수 없습니다."));

        // (stage, code) 변경 시 유니크 보장
        String newCode = blankToNull(req.code());
        if (!Objects.equals(newCode, c.getCode()) && newCode != null) {
            catRepo.findBySchoolStageAndCode(req.schoolStage(), newCode)
                    .filter(x -> !Objects.equals(x.getId(), id))
                    .ifPresent(x -> { throw new ResponseStatusException(CONFLICT, "동일 학부 내 코드가 이미 존재합니다."); });
        }

        // 부모-자식 정합성: parentId가 있으면 같은 학부여야 하며 depth 재산정
        Integer depth = requireNonNullElse(req.depth(), c.getDepth());
        if (req.parentId() != null) {
            TuitionCategory parent = catRepo.findById(req.parentId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "상위 카테고리를 찾을 수 없습니다."));
            if (!Objects.equals(parent.getSchoolStage(), req.schoolStage())) {
                throw new ResponseStatusException(BAD_REQUEST, "상위 카테고리의 학부와 일치하지 않습니다.");
            }
            depth = requireNonNullElse(parent.getDepth(), 1) + 1;
        } else {
            depth = 1; // 루트면 depth=1
        }

        boolean leaf = Boolean.TRUE.equals(req.isLeaf());
        String gradeGroup = blankToNull(req.gradeGroup());
        String gradeCode  = blankToNull(req.gradeCode());
        if (leaf) validateGradePair(req.schoolStage(), gradeGroup, gradeCode, true);

        c.setSchoolStage(req.schoolStage());
        c.setName(req.name());
        c.setCode(newCode);
        c.setDescription(req.description());
        c.setDepth(depth);
        c.setParentId(req.parentId());
        c.setSortOrder(requireNonNullElse(req.sortOrder(), c.getSortOrder()));
        c.setLeaf(leaf);
        c.setUseYn(!Boolean.FALSE.equals(req.useYn()));
        c.setGradeGroup(leaf ? gradeGroup : null);
        c.setGradeCode(leaf ? gradeCode  : null);

        return toRes(catRepo.save(c));
    }

    @Transactional
    public void deleteCategory(Long id, boolean force, Long appUserId) {
        setActor(appUserId);

        TuitionCategory c = catRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카테고리를 찾을 수 없습니다."));

        // 확장포인트: 외부 참조 체크
        usageChecker.findFirstUsageReason(id).ifPresent(reason -> {
            if (!force) {
                throw new ResponseStatusException(CONFLICT, "사용 중이라 삭제할 수 없습니다. (" + reason + ") — 강제 삭제는 force=true");
            }
        });

        boolean hasChildren = catRepo.existsByParentId(id);
        boolean hasPrices   = priceRepo.existsByCategoryId(id);

        if (!force && (hasChildren || hasPrices)) {
            StringBuilder sb = new StringBuilder("사용 중이라 삭제할 수 없습니다.");
            if (hasChildren) sb.append(" (하위 카테고리 존재)");
            if (hasPrices)   sb.append(" (요금표 존재)");
            sb.append(" — 강제 삭제는 force=true");
            throw new ResponseStatusException(CONFLICT, sb.toString());
        }

        try {
            catRepo.deleteById(id);
        } catch (DataIntegrityViolationException dive) {
            throw new ResponseStatusException(CONFLICT, "외부에서 참조 중이라 삭제할 수 없습니다.", dive);
        }
    }

    @Transactional
    public void reorder(String stage, Long parentId, List<Long> orderedIds, Long appUserId) {
        setActor(appUserId);
        if (orderedIds == null || orderedIds.isEmpty()) return;

        // 🔒 검증: 동일 stage+parent 그룹의 모든 항목을 같은 집합으로 전달해야 한다.
        List<TuitionCategory> siblings = catRepo.findByStageAndParent(stage, parentId);
        Set<Long> expected = siblings.stream().map(TuitionCategory::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> provided = new LinkedHashSet<>(orderedIds);

        if (!expected.equals(provided)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "reorder 대상 집합이 일치하지 않습니다. (stage/parent 그룹의 전체 항목을 동일 집합으로 전달해야 함)");
        }

        Map<Long, Integer> orderMap = new LinkedHashMap<>();
        for (int i = 0; i < orderedIds.size(); i++) orderMap.put(orderedIds.get(i), i);

        List<TuitionCategory> cats = catRepo.findAllById(orderedIds);
        for (TuitionCategory c : cats) {
            Integer order = orderMap.get(c.getId());
            if (order != null) c.setSortOrder(order);
        }
        catRepo.saveAll(cats);
    }

    // =================== Active Prices (flat) ======================

    @Transactional(readOnly = true)
    public List<PriceRowRes> listPrices(Long categoryId) {
        return priceRepo.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId)
                .stream().map(this::toRes).toList();
    }

    @Transactional
    public void replacePrices(Long categoryId, List<PriceRowReq> rows, Long appUserId) {
        setActor(appUserId);
        priceRepo.deleteByCategoryId(categoryId);
        if (rows == null || rows.isEmpty()) return;
        int i = 0;
        for (PriceRowReq r : rows) {
            TuitionPrice p = TuitionPrice.builder()
                    .categoryId(categoryId)
                    .unit(requireNonNullElse(r.unit(), "MONTH"))
                    .price(requireNonNullElse(r.price(), BigDecimal.ZERO))
                    .enabled(!Boolean.FALSE.equals(r.enabled()))
                    .memo(blankToNull(r.memo()))
                    .sortOrder(requireNonNullElse(r.sortOrder(), i))
                    .build();
            priceRepo.save(p); i++;
        }
    }

    @Transactional(readOnly = true)
    public List<ActivePriceRes> listActivePricesByStage(String stage) {
        return listActivePricesByStage(stage, null);
    }

    @Transactional(readOnly = true)
    public List<ActivePriceRes> listActivePricesByStage(String stage, String gradeCode) {
        List<TuitionCategory> cats = catRepo.findBySchoolStageOrderBySortOrderAscIdAsc(stage);
        if (cats.isEmpty()) return List.of();

        Map<Long, TuitionCategory> catMap = cats.stream()
                .collect(Collectors.toMap(TuitionCategory::getId, x -> x));

        final String targetGroup = stageToGroup(stage);
        final String targetGrade = blankToNull(gradeCode);

        // leaf + 학년필터 일치 항목만 선별
        Set<Long> eligibleCategoryIds = cats.stream()
                .filter(TuitionCategory::isLeaf)
                .filter(c -> {
                    String cg = blankToNull(c.getGradeCode());
                    String gg = blankToNull(c.getGradeGroup());
                    if (targetGrade != null) {
                        return targetGrade.equalsIgnoreCase(cg);
                    } else {
                        return gg != null && gg.equalsIgnoreCase(targetGroup);
                    }
                })
                .map(TuitionCategory::getId)
                .collect(Collectors.toSet());
        if (eligibleCategoryIds.isEmpty()) return List.of();

        List<TuitionPrice> prices = priceRepo.findEnabledByCategoryIds(eligibleCategoryIds);

        return prices.stream()
                .map(p -> {
                    Long cid = p.getCategoryId();
                    String path = buildCategoryPath(catMap, cid);
                    String leafName = Optional.ofNullable(catMap.get(cid)).map(TuitionCategory::getName).orElse("-");
                    String label = unitLabel(p.getUnit());
                    return new ActivePriceRes(
                            p.getId(), cid, path, leafName, p.getUnit(), label,
                            p.getPrice(), p.getSortOrder(), p.isEnabled()
                    );
                })
                .sorted(Comparator
                        .comparing(ActivePriceRes::categoryPath, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ActivePriceRes::sortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ActivePriceRes::id))
                .toList();
    }

    // 학부별 학년 코드(공통코드 래핑)
    @Transactional(readOnly = true)
    public List<GradeCodeRes> listGradeCodesByStage(String stage){
        String group = stageToGroup(stage);
        if (group == null) return List.of();
        return codeRepo.findByGroupCodeOrderBySortOrderAscCodeAsc(group).stream()
                .filter(CommonCode::isEnabled)
                .map(r -> new GradeCodeRes(r.getCode(), r.getName()))
                .toList();
    }

    // --------------------------- helper --------------------------

    /** leaf로부터 부모로 거슬러 올라가며 "부모/자식/leaf" 경로 생성 */
    private String buildCategoryPath(Map<Long, TuitionCategory> map, Long leafId) {
        if (leafId == null) return null;
        List<String> parts = new ArrayList<>();
        TuitionCategory cur = map.get(leafId);
        int guard = 0;
        while (cur != null && guard++ < 50) {
            parts.add(cur.getName() == null ? "-" : cur.getName());
            Long pid = cur.getParentId();
            cur = (pid == null ? null : map.get(pid));
        }
        Collections.reverse(parts);
        return String.join("/", parts);
    }

    private String unitLabel(String unit) {
        if (unit == null) return "수강료";
        return switch (String.valueOf(unit).toUpperCase()) {
            case "MONTH" -> "월 수강료";
            case "TERM" -> "학기 수강료";
            case "SESSION" -> "회차 수강료";
            default -> unit;
        };
    }

    private CategoryRes toRes(TuitionCategory c) {
        return new CategoryRes(
                c.getId(), c.getSchoolStage(), c.getName(), c.getCode(),
                c.getDescription(), c.getDepth(), c.getParentId(),
                c.getSortOrder(), c.isLeaf(), c.isUseYn(),
                c.getGradeGroup(), c.getGradeCode()
        );
    }
    private PriceRowRes toRes(TuitionPrice p) {
        return new PriceRowRes(
                p.getId(), p.getUnit(), p.getPrice(), p.isEnabled(), p.getMemo(), p.getSortOrder()
        );
    }
}