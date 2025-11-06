// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/controller/AdminSubjectController.java
// ----------------------------------------------------------------------------
// 관리자 과목 관리 컨트롤러
// - 트리 노드(카테고리/리프) 조회/업서트
// - 리프 과목 평가항목(SDL/DT) 조회/전체저장
// - 점수 코멘트 세트(버전+밴드) 최신 조회(헤더/풀) 및 업서트
//
// [변경/보강 요약]
// - 전체 메소드에 "무엇을/왜" 하는지 주석 보강
// - upsertSet: path 변수와 payload 검증 유지(불일치 시 400 유도)
// ============================================================================

package com.wino.academyapi.domain.subject.controller;

import com.wino.academyapi.domain.subject.dto.SubjectDtos;
import com.wino.academyapi.domain.subject.entity.ScoreCommentSet;
import com.wino.academyapi.domain.subject.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {
    private final SubjectService svc;

    /**
     * 트리 노드 조회
     * - parentId = null → 루트(Depth1) 목록
     * - parentId = {id} → 해당 부모의 즉시 하위
     * - schoolStage로 학부(E/M/H...) 범위를 제한
     */
    @GetMapping("/nodes")
    public List<SubjectDtos.SubjectRes> list(@RequestParam String schoolStage,
                                             @RequestParam(required = false) Long parentId) {
        return svc.listByParent(schoolStage, parentId).stream().map(e ->
                new SubjectDtos.SubjectRes(
                        e.getId(), e.getSchoolStage(), e.getName(), e.getCode(), e.isLeaf(),
                        e.getDepth(), e.getParentId(), e.getSortOrder(), e.isUseYn(), e.getDescription()
                )
        ).toList();
    }

    /**
     * 트리 노드 업서트(카테고리/리프)
     * - id 파라미터 없으면 삽입, 있으면 수정
     * - 유효성(학부/깊이/정렬/부모 등)은 서비스에서 추가 검증하는 것이 안전
     */
    @PostMapping("/nodes")
    public SubjectDtos.SubjectRes upsert(@RequestParam(required = false) Long id,
                                         @Validated @RequestBody SubjectDtos.SubjectUpsertReq r) {
        var e = svc.upsert(id, r);
        return new SubjectDtos.SubjectRes(
                e.getId(), e.getSchoolStage(), e.getName(), e.getCode(), e.isLeaf(),
                e.getDepth(), e.getParentId(), e.getSortOrder(), e.isUseYn(), e.getDescription()
        );
    }

    // ------------------------------------------------------------------------
    // 리프 과목 평가 항목
    // ------------------------------------------------------------------------

    /** 리프 과목 평가 항목 조회 */
    @GetMapping("/{subjectId}/items")
    public List<SubjectDtos.SubjectItemRes> listItems(@PathVariable Long subjectId) {
        return svc.listItems(subjectId).stream().map(e ->
                new SubjectDtos.SubjectItemRes(
                        e.getId(), e.getSubjectId(), e.getKind(),
                        e.getName(), e.getMaxScore(), e.getSortOrder(), e.isUseYn()
                )
        ).toList();
    }

    /**
     * 리프 과목 평가 항목 저장(전체 재작성)
     * - 프런트에서 정렬된 배열을 넘겨주면 서버가 기존 것을 삭제/대체하는 정책
     */
    @PostMapping("/{subjectId}/items")
    public List<SubjectDtos.SubjectItemRes> saveItems(@PathVariable Long subjectId,
                                                      @RequestBody List<SubjectDtos.SubjectItemReq> items) {
        return svc.saveItems(subjectId, items).stream().map(e ->
                new SubjectDtos.SubjectItemRes(
                        e.getId(), e.getSubjectId(), e.getKind(),
                        e.getName(), e.getMaxScore(), e.getSortOrder(), e.isUseYn()
                )
        ).toList();
    }

    // ------------------------------------------------------------------------
    // 점수 코멘트 세트 (버전/밴드)
    // ------------------------------------------------------------------------

    /**
     * 점수 코멘트 최신 버전 헤더 조회(밴드 제외)
     * - scope(OVERALL/SDL_ITEM/DT_ITEM), period(WEEK/TERM) 조합으로 최신 버전 1건
     */
    @GetMapping("/{subjectId}/comments/latest")
    public SubjectDtos.BandSetRes latest(@PathVariable Long subjectId, @RequestParam String schoolStage,
                                         @RequestParam ScoreCommentSet.ScopeType scopeType,
                                         @RequestParam(required = false) Long scopeRefId,
                                         @RequestParam ScoreCommentSet.PeriodType periodType) {
        return svc.latest(subjectId, schoolStage, scopeType, scopeRefId, periodType)
                .map(set -> new SubjectDtos.BandSetRes(
                        set.getId(), set.getSubjectId(), set.getSchoolStage(),
                        set.getScopeType(), set.getScopeRefId(), set.getPeriodType(), set.getVersion(), set.getMemo(), List.of()
                ))
                .orElse(null);
    }

    /**
     * 점수 코멘트 최신 버전(밴드 포함) 조회(full)
     * - 관리 UI에서 바로 편집할 때 편리
     */
    @GetMapping("/{subjectId}/comments/latest/full")
    public SubjectDtos.BandSetRes latestFull(@PathVariable Long subjectId, @RequestParam String schoolStage,
                                             @RequestParam ScoreCommentSet.ScopeType scopeType,
                                             @RequestParam(required = false) Long scopeRefId,
                                             @RequestParam ScoreCommentSet.PeriodType periodType) {
        return svc.latestFull(subjectId, schoolStage, scopeType, scopeRefId, periodType);
    }

    /**
     * 점수 코멘트 세트 업서트(밴드 포함)
     * - subjectId 경로 변수와 payload.subjectId 일치 검증
     * - 서비스에서 버전 생성/갱신 정책 적용
     */
    @PostMapping("/{subjectId}/comments")
    public SubjectDtos.BandSetRes upsertSet(@PathVariable Long subjectId,
                                            @Validated @RequestBody SubjectDtos.BandSetUpsertReq r) {
        if (!subjectId.equals(r.subjectId())) throw new IllegalArgumentException("subjectId mismatch");
        return svc.upsertBandSet(r);
    }

    /** ✅ 배치 정렬 저장(Depth1/Children 공용) */
    @PostMapping("/nodes/reorder")
    public List<SubjectDtos.SubjectRes> reorder(@Validated @RequestBody SubjectDtos.SubjectOrderReq r){
        return svc.reorderNodes(r).stream().map(e ->
                new SubjectDtos.SubjectRes(
                        e.getId(), e.getSchoolStage(), e.getName(), e.getCode(), e.isLeaf(),
                        e.getDepth(), e.getParentId(), e.getSortOrder(), e.isUseYn(), e.getDescription()
                )
        ).toList();
    }
}