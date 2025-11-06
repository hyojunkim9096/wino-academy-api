// src/main/java/com/wino/academyapi/domain/admin/staff/repository/AdminUserRepository.java
package com.wino.academyapi.domain.admin.staff.repository;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.entity.EmployeeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;                       // ✅ Spring Data Page
import org.springframework.data.domain.Pageable;               // ✅ Spring Data Pageable
import org.springframework.data.jpa.repository.*;              // @Query, @Lock 등
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 관리자(직원/선생님) 리포지토리
 * - 로그인/목록/동시성 처리 + 유저명(표시용) 단건 조회 제공
 */
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    /* ========== 기본 조회/존재 확인 ========== */
    Optional<AdminUser> findByUserId(String userId);
    boolean existsByUserId(String userId);
    boolean existsByEmail(String email);
    Optional<AdminUser> findByEmail(String email);

    /* ========== 동시성 제어가 필요한 로그인 시나리오용 ========== */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AdminUser u where u.userId = :userId")
    Optional<AdminUser> findByUserIdForUpdate(@Param("userId") String userId);

    /* ========== 목록 검색(필터 + 정렬) ========== */
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
            Pageable pageable
    );

    /* ========== ✅ 추가: PK(id)로 user_name만 투영 조회 ========== */
    @Query("select u.userName from AdminUser u where u.id = :id")
    Optional<String> findUserNameById(@Param("id") Long id);
}