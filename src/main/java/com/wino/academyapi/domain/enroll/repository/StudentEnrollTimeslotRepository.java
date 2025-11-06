// src/main/java/com/wino/academyapi/domain/enroll/repository/StudentEnrollTimeslotRepository.java
package com.wino.academyapi.domain.enroll.repository;

import com.wino.academyapi.domain.enroll.entity.StudentEnrollTimeslot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * student_enroll_timeslot 레포지토리
 *
 * - 파생 쿼리 메서드로 삭제/조회/존재검사 제공
 * - 서비스 트랜잭션 내에서 사용 (서비스 @Transactional 관리)
 */
public interface StudentEnrollTimeslotRepository extends JpaRepository<StudentEnrollTimeslot, Long> {

    /** 특정 배정(enroll)의 매핑 전체 조회 */
    List<StudentEnrollTimeslot> findByEnrollId(Long enrollId);

    /** 특정 배정에서 주어진 타임슬롯 ID들만 제거 */
    void deleteByEnrollIdAndTimeslotIdIn(Long enrollId, Collection<Long> timeslotIds);

    /** 특정 배정에 특정 타임슬롯 매핑이 존재하는지 */
    boolean existsByEnrollIdAndTimeslotId(Long enrollId, Long timeslotId);

    /** 특정 배정의 매핑 전부 제거 (빈 배열 치환 시 사용) */
    void deleteByEnrollId(Long enrollId);
}