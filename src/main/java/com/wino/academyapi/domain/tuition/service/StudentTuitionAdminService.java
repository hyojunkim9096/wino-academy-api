package com.wino.academyapi.domain.tuition.service;

import com.wino.academyapi.domain.code.repository.CommonCodeRepository;
import com.wino.academyapi.domain.tuition.dto.StudentTuitionDtos.*;
import com.wino.academyapi.domain.tuition.entity.*;
import com.wino.academyapi.domain.tuition.repository.*;
import com.wino.academyapi.domain.enroll.repository.StudentClassEnrollmentRepository;
import com.wino.academyapi.domain.student.repository.StudentSimpleRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class StudentTuitionAdminService {

    // ---- Repositories (학생/수강료/청구/결제) ----
    private final StudentTuitionRepository tuitionRepo;
    private final StudentInvoiceRepository invoiceRepo;
    private final StudentPaymentRepository paymentRepo;
    private final TuitionPriceRepository priceRepo;
    private final TuitionCategoryRepository catRepo;

    // ---- Guard(학생 활성/배정 체크) ----
    private final StudentSimpleRepository studentRepo;
    private final StudentClassEnrollmentRepository enrollRepo;

    // ---- 공통코드 저장소(결제수단 검증용) ----
    private final CommonCodeRepository codeRepo;

    // ---- DB 세션 변수 주입기 ----
    private final DbSessionVars dbVars;

    /* ====================== 수강료 등록 ====================== */

    @Transactional(readOnly = true)
    public List<TuitionAssignRes> listAssignments(Long studentId) {
        var rows = tuitionRepo.findByStudentIdOrderByIdAsc(studentId);

        Map<Long, TuitionPrice> priceMap = priceRepo.findAllById(
                rows.stream().map(StudentTuition::getTuitionPriceId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(TuitionPrice::getId, p -> p));

        Map<Long, TuitionCategory> catAll = catRepo.findAll().stream()
                .collect(Collectors.toMap(TuitionCategory::getId, c -> c));

        return rows.stream().map(st -> {
            TuitionPrice p = priceMap.get(st.getTuitionPriceId());
            String label = unitLabel(p == null ? null : p.getUnit());
            String cpath = (p == null ? null : buildPath(catAll, p.getCategoryId()));
            return new TuitionAssignRes(
                    st.getId(),
                    st.getTuitionPriceId(),
                    p == null ? null : p.getUnit(),
                    p == null ? BigDecimal.ZERO : p.getPrice(),
                    st.getStartMonth(),
                    st.isActive(),
                    label,
                    cpath
            );
        }).toList();
    }

    @Transactional
    public TuitionAssignRes addAssignment(Long studentId, TuitionAssignReq req) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        assertStudentActiveAndEnrolled(studentId);

        if (req == null || req.tuitionPriceId() == null || req.startMonth() == null || req.startMonth().length() != 7) {
            throw new ResponseStatusException(BAD_REQUEST, "유효하지 않은 요청입니다.(priceId/startMonth 확인)");
        }
        if (tuitionRepo.existsByStudentIdAndTuitionPriceIdAndActiveTrue(studentId, req.tuitionPriceId())) {
            throw new ResponseStatusException(CONFLICT, "이미 동일 수강료가 활성 상태입니다.");
        }

        StudentTuition st = StudentTuition.builder()
                .studentId(studentId)
                .tuitionPriceId(req.tuitionPriceId())
                .startMonth(req.startMonth())
                .active(true)
                .build();
        st = tuitionRepo.save(st);

        return toRes(st);
    }

    @Transactional
    public TuitionAssignRes updateAssignment(Long studentId, Long assignId, TuitionAssignUpdateReq req) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        StudentTuition st = tuitionRepo.findByIdAndStudentId(assignId, studentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "등록을 찾을 수 없습니다."));

        if (req.startMonth() != null) {
            String ym = req.startMonth();
            if (ym.length() != 7) throw new ResponseStatusException(BAD_REQUEST, "시작월 형식(YYYY-MM)이 올바르지 않습니다.");
            st.setStartMonth(ym);
        }

        if (req.active() != null) {
            boolean wantActive = req.active();
            if (wantActive) {
                if (tuitionRepo.existsByStudentIdAndTuitionPriceIdAndActiveTrueAndIdNot(
                        st.getStudentId(), st.getTuitionPriceId(), st.getId())) {
                    throw new ResponseStatusException(CONFLICT, "동일 수강료의 활성 등록이 이미 존재합니다.");
                }
            }
            st.setActive(wantActive);
        }

        return toRes(tuitionRepo.save(st));
    }

    @Transactional
    public void deleteAssignment(Long studentId, Long assignId, boolean force) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        StudentTuition st = tuitionRepo.findByIdAndStudentId(assignId, studentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "등록을 찾을 수 없습니다."));

        if (invoiceRepo.existsByTuitionId(st.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "연결된 청구서가 있어 삭제할 수 없습니다. 비활성화를 사용하세요.");
        }
        tuitionRepo.delete(st);
    }

    /* ====================== 청구 생성/조회 ====================== */

    @Transactional
    public GenerateResult generateMonthlyInvoices(Long studentId, GenerateInvoicesReq req) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        assertStudentActiveAndEnrolled(studentId);

        YearMonth from = safeYm(req.fromMonth(), "fromMonth");
        YearMonth to   = safeYm(req.toMonth(), "toMonth");
        if (to.isBefore(from)) throw new ResponseStatusException(BAD_REQUEST, "종료월이 시작월보다 이전입니다.");

        var assigns = tuitionRepo.findByStudentIdOrderByIdAsc(studentId)
                .stream().filter(StudentTuition::isActive).toList();

        Map<Long, TuitionPrice> priceMap = priceRepo.findAllById(
                assigns.stream().map(StudentTuition::getTuitionPriceId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(TuitionPrice::getId, p -> p));

        Map<Long, TuitionCategory> cats = catRepo.findAll().stream()
                .collect(Collectors.toMap(TuitionCategory::getId, c -> c));

        int created = 0;
        for (StudentTuition a : assigns) {
            TuitionPrice p = priceMap.get(a.getTuitionPriceId());
            if (p == null) continue;
            if (!"MONTH".equalsIgnoreCase(p.getUnit())) continue; // 현재 월 단위만

            YearMonth start = safeYm(a.getStartMonth(), "startMonth");
            for (YearMonth cur = from; !cur.isAfter(to); cur = cur.plusMonths(1)) {
                if (!cur.isBefore(start)) {
                    String ym = cur.toString(); // "YYYY-MM"
                    if (!invoiceRepo.existsByStudentIdAndTuitionPriceIdAndBillMonth(studentId, p.getId(), ym)) {
                        String itemName = (buildPath(cats, p.getCategoryId()) + " / " + unitLabel(p.getUnit()));
                        StudentInvoice inv = StudentInvoice.builder()
                                .studentId(studentId)
                                .tuitionId(a.getId())
                                .tuitionPriceId(p.getId())
                                .billMonth(ym)
                                .itemName(itemName)
                                .amount(p.getPrice())
                                .status("PENDING")
                                .build();
                        invoiceRepo.save(inv);
                        created++;
                    }
                }
            }
        }
        return new GenerateResult(created);
    }

    @Transactional(readOnly = true)
    public List<InvoiceRes> listRecentInvoices(Long studentId, int size) {
        int n = Math.max(1, Math.min(size, 100));
        return invoiceRepo.findRecent(studentId, PageRequest.of(0, n)).stream()
                .map(i -> new InvoiceRes(i.getId(), i.getBillMonth(), i.getItemName(), i.getAmount(), i.getStatus()))
                .toList();
    }

    /* ====================== 납부 처리 ====================== */

    @Transactional
    public void payInvoice(Long invoiceId, PayReq req) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        StudentInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "청구서를 찾을 수 없습니다."));

        if ("PAID".equalsIgnoreCase(inv.getStatus())) return; // 멱등

        LocalDateTime paidAt = parseDateTime(req.paidAt());
        BigDecimal amount = (req.amount() == null ? inv.getAmount() : req.amount());
        String methodRaw = (req.method() == null ? "CASH" : req.method());
        String method = mapAndValidatePaymentMethod(methodRaw); // ★ 정규화 + 검증

        StudentPayment pay = StudentPayment.builder()
                .invoiceId(inv.getId())
                .paidAt(paidAt)
                .amount(amount)
                .method(method)
                .memo(req.memo())
                .build();
        paymentRepo.save(pay);
        // 상태/누적금액/paid_at은 DB 트리거(sp_recalc_student_invoice)에서 자동 반영
    }

    /* ====================== Helper ====================== */

    /** 결제수단 정규화 & 유효성 검증(PAYMENT_METHOD 그룹 기반) */
    private String mapAndValidatePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) raw = "CASH";
        String s = raw.trim().toUpperCase(Locale.ROOT);

        // 흔한 별칭 → 공식 코드로 맵핑
        switch (s) {
            case "BANK", "TRANSFER", "BANK_TRANSFER":
                s = "BANK_TRANSFER"; break;
            case "VBANK", "VIRTUAL", "VIRTUAL_ACC", "VIRTUAL_ACCOUNT":
                s = "VIRTUAL_ACCOUNT"; break;
            case "POS", "POS_CARD":
                s = "POS_CARD"; break;
            case "PG", "PG_CARD":
                s = "PG_CARD"; break;
            case "CARD", "CREDIT", "CC":
                s = "CARD"; break;
            case "CASH":
                s = "CASH"; break;
            default:
                // 그대로 두고 존재 검증
                break;
        }

        // 공통코드 존재/활성 확인
        boolean ok = codeRepo.existsByGroupCodeAndCode("PAYMENT_METHOD", s);
        if (!ok) {
            throw new ResponseStatusException(BAD_REQUEST, "허용되지 않은 결제수단입니다: " + raw);
        }
        return s;
    }

    private TuitionAssignRes toRes(StudentTuition st) {
        TuitionPrice p = priceRepo.findById(st.getTuitionPriceId()).orElse(null);
        Map<Long, TuitionCategory> map = catRepo.findAll().stream()
                .collect(Collectors.toMap(TuitionCategory::getId, c -> c));
        String label = unitLabel(p == null ? null : p.getUnit());
        String cpath = (p == null ? null : buildPath(map, p.getCategoryId()));
        return new TuitionAssignRes(
                st.getId(),
                st.getTuitionPriceId(),
                p == null ? null : p.getUnit(),
                p == null ? BigDecimal.ZERO : p.getPrice(),
                st.getStartMonth(),
                st.isActive(),
                label,
                cpath
        );
    }

    private void assertStudentActiveAndEnrolled(Long studentId) {
        var basic = studentRepo.findBasicById(studentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "학생을 찾을 수 없습니다."));
        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(basic.getStatus()))) {
            throw new ResponseStatusException(BAD_REQUEST, "비활성 상태의 학생입니다. 반배정/수강료 처리를 할 수 없습니다.");
        }
        boolean enrolled = enrollRepo.existsByStudent_IdAndStatus(studentId, "ACTIVE");
        if (!enrolled) {
            throw new ResponseStatusException(BAD_REQUEST, "활성 반배정이 있어야 수강료 등록/청구가 가능합니다.");
        }
    }

    private static String unitLabel(String unit){
        if (unit == null) return "수강료";
        return switch (unit.toUpperCase()) {
            case "MONTH" -> "월 수강료";
            case "TERM" -> "학기 수강료";
            case "SESSION" -> "회차 수강료";
            default -> unit;
        };
    }

    private static String buildPath(Map<Long, TuitionCategory> map, Long leafId){
        if (leafId == null) return "-";
        List<String> parts = new ArrayList<>();
        TuitionCategory cur = map.get(leafId);
        int guard = 0;
        while (cur != null && guard++ < 50) {
            parts.add(Objects.toString(cur.getName(), "-"));
            Long pid = cur.getParentId();
            cur = (pid == null ? null : map.get(pid));
        }
        Collections.reverse(parts);
        return String.join("/", parts);
    }

    private static YearMonth safeYm(String ym, String field){
        if (ym == null || ym.length() != 7) {
            throw new ResponseStatusException(BAD_REQUEST, field + " 형식(YYYY-MM)이 올바르지 않습니다.");
        }
        try { return YearMonth.parse(ym); }
        catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, field + " 형식(YYYY-MM)이 올바르지 않습니다.", e);
        }
    }

    private static LocalDateTime parseDateTime(String s){
        if (s == null || s.isBlank()) return LocalDateTime.now();
        return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}