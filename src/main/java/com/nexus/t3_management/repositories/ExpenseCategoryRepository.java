package com.nexus.t3_management.repositories;

import com.nexus.t3_management.models.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    /* =========================================================
     * SPRING BOOT MAGIC:
     * By extending JpaRepository, this file ALREADY has:
     * - findAll() -> Used to populate the dropdown in expenses
     * - save()    -> Used to register new categories
     * - delete()  -> To remove categories
     * ========================================================= */

    // Custom Method: Find a specific category by its exact name
    ExpenseCategory findByName(String name);

    // Custom Method: Check if a category already exists before saving
    boolean existsByName(String name);
}