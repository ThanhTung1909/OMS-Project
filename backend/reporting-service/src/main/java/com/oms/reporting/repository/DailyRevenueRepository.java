package com.oms.reporting.repository;

import com.oms.reporting.entity.DailyRevenueStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyRevenueRepository extends JpaRepository<DailyRevenueStatistics, String> {
    Optional<DailyRevenueStatistics> findByStatDate(LocalDate statDate);
    List<DailyRevenueStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);
}
