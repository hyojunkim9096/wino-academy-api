// src/main/java/com/wino/academyapi/domain/syslog/repository/AdminSystemLogRepository.java
package com.wino.academyapi.domain.syslog.repository;

import com.wino.academyapi.domain.syslog.entity.AdminSystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 시스템 로그 저장소
 *
 * ✅ 핵심 포인트
 *  - 기본적으로 "최신순(created_at DESC)" 페이징 조회를 많이 쓰므로 메서드 제공.
 *  - 프런트에서 size=100 같은 요청이 오면 findRecent(size) 같은 헬퍼로도 즉시 대응 가능.
 *  - 인덱스 구성(created_at, method+created_at, actor_user_id+created_at)이 이미 되어 있어
 *    아래 쿼리들이 효율적으로 동작.
 *
 * 사용 예:
 *   // 최신 100건 단순 조회
 *   List<AdminSystemLog> logs = repo.findRecent(100);
 *
 *   // 페이징(최신순)
 *   Page<AdminSystemLog> page = repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50));
 *
 *   // 메서드별 필터(최신순)
 *   Page<AdminSystemLog> postOnly = repo.findByMethodOrderByCreatedAtDesc("POST", PageRequest.of(0, 50));
 *
 *   // 행위자 부분검색(최신순)
 *   Page<AdminSystemLog> byActor = repo.findByActorUserIdContainingIgnoreCaseOrderByCreatedAtDesc("admin", PageRequest.of(0, 50));
 */
public interface AdminSystemLogRepository extends JpaRepository<AdminSystemLog, Long> {

    /** 최신순(created_at DESC) 페이징 */
    Page<AdminSystemLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 메서드별 최신순 */
    Page<AdminSystemLog> findByMethodOrderByCreatedAtDesc(String method, Pageable pageable);

    /** 행위자(부분 일치, 대소문자 무시) 최신순 */
    Page<AdminSystemLog> findByActorUserIdContainingIgnoreCaseOrderByCreatedAtDesc(String actorUserId, Pageable pageable);

    // ───────────── 편의 메서드(동적 size 지원) ─────────────

    /**
     * 최신 N건 단순 조회(페이지 객체가 필요 없을 때 사용).
     *  - 내부적으로 created_at DESC 정렬로 1페이지를 뽑아 content만 반환.
     */
    default List<AdminSystemLog> findRecent(int size) {
        Pageable pageable = PageRequest.of(0, Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
        return findAllByOrderByCreatedAtDesc(pageable).getContent();
    }
}
