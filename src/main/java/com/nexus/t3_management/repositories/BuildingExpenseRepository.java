package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.BuildingExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BuildingExpenseRepository extends JpaRepository<BuildingExpense, Long> {
    List<BuildingExpense> findTop10ByOrderByDateDesc();
}
