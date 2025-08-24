// src/main/java/com/wino/academyapi/domain/admin/repository/AdminUserRepository.java
package com.wino.academyapi.domain.admin.repository;

import com.wino.academyapi.domain.admin.entity.AdminUser;
import com.wino.academyapi.domain.admin.entity.EmployeeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;                       // ✅ Spring Data Page
import org.springframework.data.domain.Pageable;               // ✅ 여기! AWT가 아니라 Spring Data Pageable
import org.springframework.data.jpa.repository.*;              // @Query, @Lock 등
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUserId(String userId);

    boolean existsByUserId(String userId);

    boolean existsByEmail(String email);

    Optional<AdminUser> findByEmail(String email);

    /** 로그인 실패/성공 처리에 사용: 동시성 보장을 위해 비관적 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AdminUser u where u.userId = :userId")
    Optional<AdminUser> findByUserIdForUpdate(@Param("userId") String userId);

    // ✅ 목록 검색(필터 적용 + 이름 오름차순, 동명이인일 때 id 내림차순)
    @Query(
            value = """
            select u from AdminUser u
             where (:type is null or u.employeeType = :type)
               and (:wl   is null or u.workLocation = :wl)
               and (:kw   is null
                    or lower(u.userName) like lower(concat('%', :kw, '%'))
                    or lower(u.userId)   like lower(concat('%', :kw, '%'))
                    or lower(u.email)    like lower(concat('%', :kw, '%')))
             order by lower(u.userName) asc, u.id desc
        """,
            countQuery = """
            select count(u) from AdminUser u
             where (:type is null or u.employeeType = :type)
               and (:wl   is null or u.workLocation = :wl)
               and (:kw   is null
                    or lower(u.userName) like lower(concat('%', :kw, '%'))
                    or lower(u.userId)   like lower(concat('%', :kw, '%'))
                    or lower(u.email)    like lower(concat('%', :kw, '%')))
        """
    )
    Page<AdminUser> search(
            @Param("type") EmployeeType type,
            @Param("wl")   String wl,
            @Param("kw")   String kw,
            Pageable pageable   // ✅ Spring Data Pageable (page/size만 사용; 정렬은 JPQL 고정)
    );
}
