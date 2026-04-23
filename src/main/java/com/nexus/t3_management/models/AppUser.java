package com.nexus.t3_management.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String role; // "ADMIN" or "STAFF"

    // A comma-separated list of allowed tabs (e.g., "dashboard,elec,maint")
    private String permissions;
}