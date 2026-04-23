package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ExpenseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
}