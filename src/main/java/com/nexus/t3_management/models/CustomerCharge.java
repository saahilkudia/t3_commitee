package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class CustomerCharge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne 
    @JoinColumn(name = "account_id") 
    private ChartOfAccount account;

    private Double amount;
    private String memo; // Reason for the charge
    
    private String status = "UNPAID";
    private LocalDate createdAt = LocalDate.now();
}