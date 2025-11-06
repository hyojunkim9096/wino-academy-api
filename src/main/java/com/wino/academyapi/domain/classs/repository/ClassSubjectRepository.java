package com.wino.academyapi.domain.classs.repository;

import com.wino.academyapi.domain.classs.entity.ClassSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** class_subject 리포지토리 */
public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {

    List<ClassSubject> findByClassIdOrderBySortOrderAsc(Long classId);

    // 과목명 포함 프로젝션(반 단위)
    interface ClassSubjectRow {
        Long getId();
        Long getClassId();
        Long getSubjectId();
        String getSubjectName();
        Long getTeacherId();
        int getSortOrder();
        boolean getUseYn();
    }

    @Query(value = """
        SELECT cs.id,
               cs.class_id       AS classId,
               cs.subject_id     AS subjectId,
               s.name            AS subjectName,
               cs.teacher_id     AS teacherId,
               cs.sort_order     AS sortOrder,
               cs.use_yn         AS useYn
          FROM class_subject cs
          JOIN subject s ON s.id = cs.subject_id
         WHERE cs.class_id = :classId
         ORDER BY cs.sort_order ASC
    """, nativeQuery = true)
    List<ClassSubjectRow> findRowsWithSubject(@Param("classId") Long classId);

    // ✅ 충돌 메시지용 브리프(반/과목/지점)
    interface CsBrief {
        Long getId();
        Long getClassId();
        String getClassName();
        String getWorkLocationCode();
        Long getSubjectId();
        String getSubjectName();
    }

    @Query(value = """
        SELECT cs.id          AS id,
               cm.id          AS classId,
               cm.name        AS className,
               cm.work_location_code AS workLocationCode,
               s.id           AS subjectId,
               s.name         AS subjectName
          FROM class_subject cs
          JOIN class_master cm ON cm.id = cs.class_id
          JOIN subject      s  ON s.id  = cs.subject_id
         WHERE cs.id IN (:ids)
    """, nativeQuery = true)
    List<CsBrief> findBriefsByIdIn(@Param("ids") Collection<Long> ids);
}