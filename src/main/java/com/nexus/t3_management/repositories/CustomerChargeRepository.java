package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.CustomerCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerChargeRepository extends JpaRepository<CustomerCharge, Long> {
    List<CustomerCharge> findTop10ByOrderByCreatedAtDesc();
}