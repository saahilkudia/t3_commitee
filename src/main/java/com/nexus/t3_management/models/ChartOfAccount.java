package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ChartOfAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountCode; // e.g., "1001", "1200"

    @Column(nullable = false)
    private String accountName; // e.g., "Meezan Bank", "Accounts Receivable"

    private String accountCategory; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE

    // Maps to your legacy UI perfectly (BANK, CASH, AR, AP)
    private String accountType;

    private Double currentBalance = 0.0;
}