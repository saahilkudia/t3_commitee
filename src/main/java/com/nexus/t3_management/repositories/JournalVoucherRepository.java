package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.JournalVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JournalVoucherRepository extends JpaRepository<JournalVoucher, Long> {
    List<JournalVoucher> findAllByOrderByVoucherDateDesc();
}