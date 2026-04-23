package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {

    Optional<ChartOfAccount> findByAccountCode(String code);

    // THIS IS THE MISSING METHOD FIXING YOUR ERROR!
    List<ChartOfAccount> findByAccountType(String type);

    List<ChartOfAccount> findByAccountTypeIn(List<String> types);
}