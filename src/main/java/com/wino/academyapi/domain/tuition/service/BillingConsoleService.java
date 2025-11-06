package com.wino.academyapi.domain.tuition.service;

import com.wino.academyapi.domain.tuition.dto.BillingConsoleDtos.*;
import com.wino.academyapi.domain.tuition.entity.StudentInvoice;
import com.wino.academyapi.domain.tuition.repository.*;
import com.wino.academyapi.domain.tuition.repository.TuitionPriceInfoRepository.PriceInfo;
import com.wino.academyapi.domain.tuition.repository.projection.BillingCandidateRow;
import com.wino.academyapi.domain.tuition.repository.projection.CategoryPathRow;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Billing Console 핵심 서비스
 *
 * 정책
 * - 자동 청구는 MONTH 단위만 지원
 * - 중복 청구 방지: (student_id, bill_month, tuition_price_id) 존재 시 SKIP
 * - 쓰기 트랜잭션 진입 시 @app_user_id/@event_note 세션 변수 주입
 */
@Service
@RequiredArgsConstructor
public class BillingConsoleService {

    private final BillingCandidateRepository candidateRepo;
    private final CategoryPathRepository categoryPathRepo;
    private final TuitionPriceInfoRepository priceInfoRepo;
    private final StudentInvoiceRepository invoiceRepo;

    private final DbSessionVars dbSessionVars;

    /* =========================[ Preview ]========================= */

    @Transactional(readOnly = true)
    public PreviewRes preview(PreviewReq req) {
        final String month = normalizeMonth(req.month());
        final String stage = blankToNull(req.stage());
        final String loc   = blankToNull(req.workLocationCode());
        final boolean onlyActive = req.onlyActiveStudents() == null || req.onlyActiveStudents();

        // 후보 조회(네이티브)
        List<BillingCandidateRow> rows = candidateRepo.findCandidatesNative(month, stage, loc, onlyActive);

        // 필요한 categoryId → path 맵을 한 번에 조회
        Set<Long> categoryIds = rows.stream()
                .map(BillingCandidateRow::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> categoryPathMap = Collections.emptyMap();
        if (!categoryIds.isEmpty()) {
            categoryPathMap = categoryPathRepo.findPaths(categoryIds).stream()
                    .collect(Collectors.toMap(CategoryPathRow::getLeafId, CategoryPathRow::getPath));
        }

        // 응답 변환 + limit 적용
        int limit = req.limit() == null ? 500 : Math.max(1, req.limit());
        List<ItemPreview> previews = new ArrayList<>(Math.min(limit, rows.size()));

        long already = 0;
        long creatable = 0;

        for (BillingCandidateRow r : rows) {
            if (previews.size() >= limit) break;

            String categoryPath = categoryPathMap.getOrDefault(r.getCategoryId(), r.getCategoryName());
            // itemLabel은 run 시 PriceInfo로 재확정하지만,
            // preview에서는 unit 기반 라벨을 간단히 노출 (월/학기/회차 수강료)
            String itemLabel = unitLabel(r.getUnit());

            boolean alreadyBilled = Boolean.TRUE.equals(r.getAlreadyBilled());
            if (alreadyBilled) already++;

            String skip = null;
            if (!"MONTH".equalsIgnoreCase(r.getUnit())) {
                skip = "자동 청구는 MONTH 단위만 지원";
            } else if (alreadyBilled) {
                skip = "이미 해당 월 청구가 존재";
            } else {
                creatable++;
            }

            previews.add(new ItemPreview(
                    r.getStudentId(),
                    r.getStudentName(),
                    r.getSchoolStage(),
                    r.getWorkLocationCode(),
                    r.getTuitionId(),
                    r.getTuitionPriceId(),
                    r.getCategoryId(),
                    r.getCategoryName(),
                    categoryPath,
                    itemLabel,
                    r.getUnit(),
                    r.getPrice(),
                    r.getBillMonth(),
                    alreadyBilled,
                    skip
            ));
        }

        return new PreviewRes(month, rows.size(), already, creatable, previews);
    }

    /* =========================[ Run ]========================= */

    @Transactional
    public RunRes run(RunReq req) {
        final String month = normalizeMonth(req.month());
        final String stage = blankToNull(req.stage());
        final String loc   = blankToNull(req.workLocationCode());
        final boolean onlyActive = req.onlyActiveStudents() == null || req.onlyActiveStudents();

        // 같은 트랜잭션/같은 커넥션에서 세션 변수 설정 (MANDATORY)
        dbSessionVars.setAppVars(AppUserContext.getUserId(), "BillingConsole.run " + month);

        // 후보 조회
        List<BillingCandidateRow> all = candidateRepo.findCandidatesNative(month, stage, loc, onlyActive);

        // 선택 실행이면 교집합만 처리
        Set<Long> selected = (req.tuitionIds() == null || req.tuitionIds().isEmpty())
                ? null
                : new HashSet<>(req.tuitionIds());

        List<RunItemResult> results = new ArrayList<>();
        long created = 0, skipped = 0, errors = 0;

        // 필요한 category path 미리 로딩
        Set<Long> categoryIds = all.stream().map(BillingCandidateRow::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> categoryPathMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryPathRepo.findPaths(categoryIds).stream()
                .collect(Collectors.toMap(CategoryPathRow::getLeafId, CategoryPathRow::getPath));

        for (BillingCandidateRow r : all) {
            if (selected != null && !selected.contains(r.getTuitionId())) {
                // 선택 외 항목은 스킵(표에 넣지 않음)
                continue;
            }
            try {
                // MONTH 단위만 처리
                if (!"MONTH".equalsIgnoreCase(r.getUnit())) {
                    skipped++;
                    results.add(new RunItemResult(r.getTuitionId(), r.getStudentId(), r.getStudentName(),
                            "SKIPPED", "자동 청구는 MONTH 단위만 지원"));
                    continue;
                }

                // 중복 방지: (student_id, bill_month, tuition_price_id)
                boolean exists = invoiceRepo.existsByStudentIdAndTuitionPriceIdAndBillMonth(
                        r.getStudentId(), r.getTuitionPriceId(), month);
                if (exists) {
                    skipped++;
                    results.add(new RunItemResult(r.getTuitionId(), r.getStudentId(), r.getStudentName(),
                            "SKIPPED", "이미 해당 월 청구가 존재"));
                    continue;
                }

                // 금액/라벨 스냅샷 재확정
                PriceInfo pinfo = priceInfoRepo.findPriceInfo(r.getTuitionPriceId());
                if (pinfo == null) {
                    throw new IllegalStateException("가격표를 찾을 수 없습니다: priceId=" + r.getTuitionPriceId());
                }
                if (!"MONTH".equalsIgnoreCase(pinfo.getUnit())) {
                    skipped++;
                    results.add(new RunItemResult(r.getTuitionId(), r.getStudentId(), r.getStudentName(),
                            "SKIPPED", "가격표 단위가 MONTH가 아닙니다"));
                    continue;
                }

                BigDecimal amount = pinfo.getPrice() == null ? BigDecimal.ZERO : pinfo.getPrice();
                String categoryPath = categoryPathMap.getOrDefault(pinfo.getCategoryId(), r.getCategoryName());
                String itemName = buildItemName(categoryPath, pinfo.getItemLabel());

                // 청구 생성
                StudentInvoice inv = StudentInvoice.builder()
                        .studentId(r.getStudentId())
                        .tuitionId(r.getTuitionId())
                        .tuitionPriceId(r.getTuitionPriceId())
                        .billMonth(month)
                        .itemName(itemName)
                        .amount(amount)
                        .status("PENDING")
                        .build();

                invoiceRepo.save(inv);

                created++;
                results.add(new RunItemResult(r.getTuitionId(), r.getStudentId(), r.getStudentName(),
                        "CREATED", "생성 완료"));
            } catch (Exception e) {
                errors++;
                results.add(new RunItemResult(r.getTuitionId(), r.getStudentId(), r.getStudentName(),
                        "ERROR", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }

        long attempted = (selected == null) ? all.size()
                : all.stream().filter(r -> selected.contains(r.getTuitionId())).count();

        return new RunRes(month, attempted, created, skipped, errors, results);
    }

    /* =========================[ 내부 유틸 ]========================= */

    private String unitLabel(String unit){
        if (unit == null) return "수강료";
        return switch (unit.toUpperCase()) {
            case "MONTH" -> "월 수강료";
            case "TERM" -> "학기 수강료";
            case "SESSION" -> "회차 수강료";
            default -> unit;
        };
    }

    private String normalizeMonth(String m) {
        if (m == null || !m.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("month 형식은 yyyy-MM 이어야 합니다.");
        }
        return m;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String buildItemName(String categoryPath, String itemLabel) {
        String left = (categoryPath == null || categoryPath.isBlank()) ? "" : categoryPath.trim();
        String right = (itemLabel == null || itemLabel.isBlank()) ? "" : itemLabel.trim();
        if (left.isEmpty()) return right.isEmpty() ? "수강료" : right;
        if (right.isEmpty()) return left;
        return left + " / " + right;
    }
}