package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.StaffSalary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StaffSalaryRepository extends JpaRepository<StaffSalary, Long> {
    List<StaffSalary> findTop10ByOrderByPaymentDateDesc();
}
