package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.ElectricityBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ElectricityBillRepository extends JpaRepository<ElectricityBill, Long> {

    // Crucial for continuity: Fetches the last bill to get the previous meter reading
    List<ElectricityBill> findByUnitIdOrderByCreatedAtDesc(Long unitId);

    // Fetches latest entries for the Dashboard
    List<ElectricityBill> findTop10ByOrderByCreatedAtDesc();
}