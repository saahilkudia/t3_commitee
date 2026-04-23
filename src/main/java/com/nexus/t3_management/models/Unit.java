package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "units")
public class Unit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String unitNumber;
    private String ownerName;
    private String cnic;
    private String mobile;
    private String address;
}