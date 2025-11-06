// src/main/java/com/wino/academyapi/domain/subject/service/SubjectService.java
package com.wino.academyapi.domain.subject.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wino.academyapi.domain.subject.dto.SubjectDtos;
import com.wino.academyapi.domain.subject.entity.*;
import com.wino.academyapi.domain.subject.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SubjectService {
    private final SubjectRepository subjectRepo;
    private final SubjectItemRepository itemRepo;
    private final ScoreCommentSetRepository setRepo;
    private final ScoreCommentBandRepository bandRepo;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Subject> listByParent(String schoolStage, Long parentId){
        return subjectRepo.findBySchoolStageAndParentIdOrderBySortOrderAsc(schoolStage, parentId);
    }

    @Transactional
    public Subject upsert(Long id, SubjectDtos.SubjectUpsertReq r){
        Subject e = (id==null) ? new Subject() : subjectRepo.findById(id).orElseThrow();
        e.setSchoolStage(r.schoolStage());
        e.setName(r.name());
        e.setDepth(r.depth());
        e.setParentId(r.parentId());
        e.setSortOrder(r.sortOrder());
        if (r.isLeaf()!=null) e.setLeaf(r.isLeaf());
        e.setCode(r.code());
        e.setDescription(r.description());
        if (r.useYn()!=null) e.setUseYn(r.useYn());
        return subjectRepo.save(e);
    }

    /* ---------------------- ✅ 추가: 리프 과목 평가 항목 조회 ---------------------- */
    @Transactional(readOnly = true)
    public List<SubjectItem> listItems(Long subjectId){
        return itemRepo.findBySubjectIdOrderBySortOrderAsc(subjectId);
    }
    /* ---------------------------------------------------------------------- */

    private Integer extractTotalLimit(String descJson){
        if (descJson == null || descJson.isBlank()) return null;
        try {
            JsonNode n = objectMapper.readTree(descJson);
            return n.hasNonNull("totalLimit") ? n.get("totalLimit").asInt() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    @Transactional
    public List<SubjectItem> saveItems(Long subjectId, List<SubjectDtos.SubjectItemReq> reqs){
        Subject subj = subjectRepo.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
        if (!subj.isLeaf()) {
            throw new IllegalStateException("카테고리에는 평가 항목을 저장할 수 없습니다. (리프 과목만 가능)");
        }
        Integer totalLimit = (subj.getParentId()==null) ? null
                : subjectRepo.findById(subj.getParentId())
                .map(p -> extractTotalLimit(p.getDescription()))
                .orElse(null);

        int sum = 0, idx = 0;
        for (var r : reqs) {
            if (r.name()==null || r.name().isBlank())
                throw new IllegalArgumentException("항목 이름은 필수입니다. index=" + idx);
            if (r.maxScore() < 0)
                throw new IllegalArgumentException("만점은 0 이상이어야 합니다. index=" + idx);
            if (r.sortOrder()==null)
                throw new IllegalArgumentException("정렬값(sortOrder)은 필수입니다. index=" + idx);
            sum += r.maxScore();
            idx++;
        }
        if (totalLimit != null && sum > totalLimit) {
            throw new IllegalArgumentException("항목 만점 합(" + sum + ")이 카테고리 총점(" + totalLimit + ")을 초과합니다.");
        }

        itemRepo.deleteAllInBatch(itemRepo.findBySubjectIdOrderBySortOrderAsc(subjectId));
        List<SubjectItem> news = new ArrayList<>(reqs.size());
        for (var r : reqs){
            news.add(SubjectItem.builder()
                    .subjectId(subjectId).kind(r.kind()).name(r.name())
                    .maxScore(r.maxScore()).sortOrder(r.sortOrder())
                    .useYn(r.useYn()==null || r.useYn()).build());
        }
        return itemRepo.saveAll(news);
    }

    @Transactional(readOnly = true)
    public Optional<ScoreCommentSet> latest(Long subjectId, String schoolStage,
                                            ScoreCommentSet.ScopeType scopeType, Long scopeRefId, ScoreCommentSet.PeriodType period){
        return setRepo.findTopBySubjectIdAndSchoolStageAndScopeTypeAndScopeRefIdAndPeriodTypeOrderByVersionDesc(
                subjectId, schoolStage, scopeType, scopeRefId, period);
    }

    /* --------- ✅ 추가: 최신 세트 + 밴드까지 한 번에 반환 --------- */
    @Transactional(readOnly = true)
    public SubjectDtos.BandSetRes latestFull(Long subjectId, String schoolStage,
                                             ScoreCommentSet.ScopeType scopeType, Long scopeRefId,
                                             ScoreCommentSet.PeriodType period){
        var opt = latest(subjectId, schoolStage, scopeType, scopeRefId, period);
        if (opt.isEmpty()) return null;
        var set = opt.get();
        var bands = bandRepo.findByCommentSetIdOrderBySortOrderAsc(set.getId());
        return new SubjectDtos.BandSetRes(
                set.getId(), set.getSubjectId(), set.getSchoolStage(),
                set.getScopeType(), set.getScopeRefId(), set.getPeriodType(), set.getVersion(), set.getMemo(),
                bands.stream().map(e -> new SubjectDtos.BandDto(
                        e.getId(), e.getLabel(), e.getMinScore(), e.getMaxScore(),
                        e.getCommentTemplate(), e.isNotifySms(), e.isNotifyEmail(), e.isNotifyPush(), e.getSortOrder()
                )).toList()
        );
    }
    /* ----------------------------------------------------------- */

    @Transactional
    public SubjectDtos.BandSetRes upsertBandSet(SubjectDtos.BandSetUpsertReq r){
        if (r.scopeType()==ScoreCommentSet.ScopeType.SDL_ITEM || r.scopeType()==ScoreCommentSet.ScopeType.DT_ITEM) {
            if (r.scopeRefId()==null)
                throw new IllegalArgumentException("scopeType이 *_ITEM일 때 scopeRefId는 필수입니다.");
            var item = itemRepo.findById(r.scopeRefId())
                    .orElseThrow(() -> new IllegalArgumentException("scopeRefId에 해당하는 항목을 찾을 수 없습니다."));
            if (!Objects.equals(item.getSubjectId(), r.subjectId())) {
                throw new IllegalArgumentException("scopeRefId가 해당 subject의 항목이 아닙니다.");
            }
        }

        int version = (r.version()!=null) ? r.version()
                : latest(r.subjectId(), r.schoolStage(), r.scopeType(), r.scopeRefId(), r.periodType())
                .map(ScoreCommentSet::getVersion).orElse(0) + 1;

        var set = setRepo.save(ScoreCommentSet.builder()
                .subjectId(r.subjectId()).schoolStage(r.schoolStage())
                .scopeType(r.scopeType()).scopeRefId(r.scopeRefId())
                .periodType(r.periodType()).version(version).memo(r.memo())
                .build());

        var bandsSorted = new ArrayList<>(r.bands());
        bandsSorted.sort(Comparator.comparingInt(SubjectDtos.BandDto::sortOrder));
        int prevMax = Integer.MIN_VALUE;
        for (var b : bandsSorted){
            if (b.minScore() > b.maxScore())
                throw new IllegalArgumentException("minScore ≤ maxScore 위반: " + b.label());
            if (prevMax!=Integer.MIN_VALUE && b.minScore() <= prevMax)
                throw new IllegalArgumentException("밴드 구간 겹침: " + b.label());
            prevMax = b.maxScore();
        }

        List<ScoreCommentBand> saved = new ArrayList<>(bandsSorted.size());
        for (var b : bandsSorted){
            saved.add(bandRepo.save(ScoreCommentBand.builder()
                    .commentSetId(set.getId()).label(b.label())
                    .minScore(b.minScore()).maxScore(b.maxScore())
                    .commentTemplate(b.commentTemplate())
                    .notifySms(b.notifySms()).notifyEmail(b.notifyEmail()).notifyPush(b.notifyPush())
                    .sortOrder(b.sortOrder()).build()));
        }

        return new SubjectDtos.BandSetRes(
                set.getId(), set.getSubjectId(), set.getSchoolStage(),
                set.getScopeType(), set.getScopeRefId(), set.getPeriodType(), set.getVersion(),
                set.getMemo(),
                saved.stream().sorted(Comparator.comparingInt(ScoreCommentBand::getSortOrder))
                        .map(e -> new SubjectDtos.BandDto(
                                e.getId(), e.getLabel(), e.getMinScore(), e.getMaxScore(),
                                e.getCommentTemplate(), e.isNotifySms(), e.isNotifyEmail(), e.isNotifyPush(), e.getSortOrder()
                        )).toList()
        );
    }

    /** ✅ 배치 정렬 저장 (Depth1/Children 공용) */
    @Transactional
    public List<Subject> reorderNodes(SubjectDtos.SubjectOrderReq r){
        // 1) ids로 대상 엔티티 조회(순서 유지 위해 Map 보조)
        List<Subject> list = subjectRepo.findAllById(r.ids());
        Map<Long, Subject> map = new HashMap<>();
        for (Subject s : list) map.put(s.getId(), s);

        // 2) 검증: stage/parent 일치
        for (Long id : r.ids()){
            Subject s = map.get(id);
            if (s == null) throw new IllegalArgumentException("존재하지 않는 subject id: " + id);
            if (!Objects.equals(s.getSchoolStage(), r.schoolStage()))
                throw new IllegalArgumentException("학부(stage) 불일치: id=" + id);
            if (!Objects.equals(s.getParentId(), r.parentId()))
                throw new IllegalArgumentException("parentId 불일치: id=" + id);
        }

        // 3) 순서대로 sortOrder 재부여
        int i = 0;
        for (Long id : r.ids()){
            Subject s = map.get(id);
            s.setSortOrder(i++);
        }
        return subjectRepo.saveAll(r.ids().stream().map(map::get).toList());
    }
}