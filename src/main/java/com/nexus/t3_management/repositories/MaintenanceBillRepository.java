package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.MaintenanceBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MaintenanceBillRepository extends JpaRepository<MaintenanceBill, Long> {

    // Fetches latest entries for the Dashboard (Module A - Maintenance)
    List<MaintenanceBill> findTop10ByOrderByCreatedAtDesc();
}