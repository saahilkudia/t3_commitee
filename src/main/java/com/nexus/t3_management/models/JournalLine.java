package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Data
public class JournalLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "jv_id")
    @JsonBackReference
    private JournalVoucher journalVoucher;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private ChartOfAccount account;

    private Double debit = 0.0;
    private Double credit = 0.0;
    private String lineMemo;
}