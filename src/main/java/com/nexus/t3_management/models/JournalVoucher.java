package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Data
public class JournalVoucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String voucherNo; // e.g., "SV-2026-0001"

    private LocalDate voucherDate = LocalDate.now();
    private String voucherType; // JV, BP, BR, SV
    private String memo;

    private Boolean isLocked = false;
    private Boolean isReversed = false;

    @OneToMany(mappedBy = "journalVoucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<JournalLine> lines = new ArrayList<>();
}