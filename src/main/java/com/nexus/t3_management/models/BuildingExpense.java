package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity @Data
public class BuildingExpense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name = "category_id") private ExpenseCategory category;
    @ManyToOne @JoinColumn(name = "account_id") private ChartOfAccount account;

    private Double amount; private String description; private String status = "UNPAID";
    private LocalDate date = LocalDate.now();
}