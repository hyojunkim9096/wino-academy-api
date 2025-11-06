package com.wino.academyapi.domain.tuition.repository;

import com.wino.academyapi.domain.tuition.entity.TuitionPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TuitionPriceRepository extends JpaRepository<TuitionPrice, Long> {

    List<TuitionPrice> findByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);

    void deleteByCategoryId(Long categoryId);

    boolean existsByCategoryId(Long categoryId);

    @Query("""
        select p from TuitionPrice p
         where p.enabled = true
           and p.categoryId in :categoryIds
         order by p.sortOrder asc, p.id asc
    """)
    List<TuitionPrice> findEnabledByCategoryIds(Collection<Long> categoryIds);
}