package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /* =========================================================
     * SPRING BOOT MAGIC:
     * By extending JpaRepository, this file ALREADY has:
     * - findAll() -> Used by T3Service to get all staff for "Pay All"
     * - save()    -> Used by T3Service to register new staff
     * - delete()  -> To remove staff
     * ========================================================= */

    // Custom Method: Find a specific employee by their exact CNIC
    Employee findByCnic(String cnic);

    // Custom Method: Search for an employee by a partial name (useful for search bars)
    List<Employee> findByNameContainingIgnoreCase(String name);

    // Custom Method: Check if an employee already exists before saving
    boolean existsByCnic(String cnic);
}