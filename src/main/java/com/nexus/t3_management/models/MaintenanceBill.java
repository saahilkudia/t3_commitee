package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity @Data
public class MaintenanceBill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name = "unit_id") private Unit unit;
    @ManyToOne @JoinColumn(name = "account_id") private ChartOfAccount account;

    private Double amount; private Double penalty = 0.0; private Double total;
    private String month; private String status = "UNPAID";
    private LocalDate createdAt = LocalDate.now();
}