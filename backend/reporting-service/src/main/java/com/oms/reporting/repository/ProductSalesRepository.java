package com.oms.reporting.repository;

import com.oms.reporting.entity.ProductSalesStatistics;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSalesRepository extends JpaRepository<ProductSalesStatistics, String> {
    Optional<ProductSalesStatistics> findByProductId(String productId);
    
    @Query("SELECT p FROM ProductSalesStatistics p ORDER BY p.totalSoldQuantity DESC")
    List<ProductSalesStatistics> findTopSellingProducts(Pageable pageable);
}
