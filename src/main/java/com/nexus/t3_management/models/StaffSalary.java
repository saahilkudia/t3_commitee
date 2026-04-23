package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity @Data
public class StaffSalary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name = "employee_id") private Employee employee;
    @ManyToOne @JoinColumn(name = "account_id") private ChartOfAccount account;

    private String salaryMonth; private Double amount; private String remarks;
    private String status = "UNPAID"; private LocalDate paymentDate = LocalDate.now();
}