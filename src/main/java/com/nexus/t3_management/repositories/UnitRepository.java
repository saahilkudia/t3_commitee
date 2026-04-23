package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    @Query("SELECT u FROM Unit u WHERE u.unitNumber LIKE %:kw% OR u.ownerName LIKE %:kw% OR u.cnic LIKE %:kw%")
    List<Unit> searchUnits(@Param("kw") String kw);
}
