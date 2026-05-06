package com.nexus.t3_management.controllers;

import com.nexus.t3_management.models.*;
import com.nexus.t3_management.services.T3Services;
import com.nexus.t3_management.services.AccountingEngine;
import com.nexus.t3_management.repositories.ChartOfAccountRepository;
import com.nexus.t3_management.repositories.JournalVoucherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class T3Controller {

    // Note: Autowiring T3Services (with an 's') to match your file structure perfectly
    @Autowired private T3Services t3Service;

    // Core Accounting Injections for the GL UI
    @Autowired private AccountingEngine accountingEngine;
    @Autowired private ChartOfAccountRepository coaRepo;
    @Autowired private JournalVoucherRepository jvRepo;

    // --- 0. AUTHENTICATION & USERS ---
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        AppUser user = t3Service.authenticate(credentials.get("username"), credentials.get("password"));
        if (user != null) return ResponseEntity.ok(user);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @GetMapping("/users")
    public List<AppUser> getUsers() { return t3Service.getAllUsers(); }

    @PostMapping("/users")
    public AppUser saveUser(@RequestBody AppUser user) { return t3Service.saveUser(user); }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        t3Service.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    // --- 1. DASHBOARD & ACCOUNTS (Legacy mapping to CoA) ---
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return t3Service.getFinancialSummary();
    }

    @GetMapping("/accounts")
    public List<ChartOfAccount> getAccounts() {
        return t3Service.getBanksAndCash();
    }

    @PostMapping("/accounts")
    public ChartOfAccount saveAccount(@RequestBody ChartOfAccount account) {
        return t3Service.saveAccount(account);
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long id) {
        try {
            t3Service.deleteAccount(id);
            return ResponseEntity.ok().build();
        } catch(Exception e) {
            return ResponseEntity.badRequest().body("Cannot delete: Account is linked to existing transactions.");
        }
    }

    // --- NEW: GENERAL LEDGER (GL) ENDPOINTS ---
    @GetMapping("/coa")
    public List<ChartOfAccount> getChartOfAccounts() {
        return coaRepo.findAll();
    }

    @GetMapping("/jvs")
    public List<JournalVoucher> getJournalVouchers() {
        return jvRepo.findAllByOrderByVoucherDateDesc();
    }

    @PostMapping("/jvs")
    public ResponseEntity<?> postManualJournal(@RequestBody JournalVoucher jv) {
        try {
            return ResponseEntity.ok(accountingEngine.postVoucher(jv));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("JV Posting Error: " + e.getMessage());
        }
    }

    // --- 2. UNITS ---
    @GetMapping("/units")
    public List<Unit> getUnits(@RequestParam(required = false) String keyword) {
        return t3Service.getUnits(keyword);
    }

    @PostMapping("/units")
    public Unit saveUnit(@RequestBody Unit unit) {
        return t3Service.saveUnit(unit);
    }

    @DeleteMapping("/units/{id}")
    public ResponseEntity<?> deleteUnit(@PathVariable Long id) {
        try {
            t3Service.deleteUnit(id);
            return ResponseEntity.ok().build();
        } catch(Exception e) {
            return ResponseEntity.badRequest().body("Cannot delete: Property has existing billing records.");
        }
    }

    // --- 3. ACCRUAL GENERATION ---
    @GetMapping("/bills/last-reading/{id}")
    public Integer getLastReading(@PathVariable Long id) {
        return t3Service.getPreviousReading(id);
    }

    @PostMapping("/bills/electricity")
    public ResponseEntity<?> saveElectricity(@RequestBody ElectricityBill bill) {
        try {
            return ResponseEntity.ok(t3Service.saveElec(bill));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Validation Error: " + e.getMessage());
        }
    }

    @PostMapping("/bills/maintenance")
    public ResponseEntity<?> saveMaintenance(@RequestBody MaintenanceBill bill) {
        try {
            return ResponseEntity.ok(t3Service.saveMaint(bill));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Validation Error: " + e.getMessage());
        }
    }

    @PutMapping("/bills/maintenance/{id}/penalty")
    public ResponseEntity<?> updateMaintenancePenalty(@PathVariable Long id, @RequestParam Double amount) {
        try {
            return ResponseEntity.ok(t3Service.applyMaintPenalty(id, amount));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating penalty: " + e.getMessage());
        }
    }

    @PostMapping("/bills/maintenance/bulk")
    public ResponseEntity<String> postBulkMaint(@RequestParam Double amount, @RequestParam String month) {
        try {
            t3Service.saveBulkMaint(amount, month);
            return ResponseEntity.ok("Bulk billing processed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing bulk billing: " + e.getMessage());
        }
    }

    @GetMapping("/expenses")
    public List<BuildingExpense> getExpenses() {
        return t3Service.getAllExpenses();
    }

    @PostMapping("/expenses")
    public ResponseEntity<?> saveExpense(@RequestBody BuildingExpense expense) {
        try {
            return ResponseEntity.ok(t3Service.saveExpense(expense));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Validation Error: " + e.getMessage());
        }
    }

    @GetMapping("/salaries")
    public List<StaffSalary> getSalaries() {
        return t3Service.getAllSalaries();
    }

    @PostMapping("/salaries")
    public ResponseEntity<?> saveSalary(@RequestBody StaffSalary salary) {
        try {
            return ResponseEntity.ok(t3Service.saveSalary(salary));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Validation Error: " + e.getMessage());
        }
    }

    @PostMapping("/payroll/pay-all")
    public ResponseEntity<?> processPayAll(@RequestParam String month) {
        List<Employee> employees = t3Service.getAllEmployees();
        for (Employee e : employees) {
            StaffSalary s = new StaffSalary();
            s.setEmployee(e);
            s.setAmount(e.getFixedSalary());
            s.setSalaryMonth(month);
            s.setRemarks("Monthly Salary Accrual");
            t3Service.saveSalary(s);
        }
        return ResponseEntity.ok(Map.of("message", "Accrual Generated"));
    }

    // --- 4. SETTLEMENT CENTER ---
    @PostMapping("/settle")
    public ResponseEntity<?> settleTransaction(
            @RequestParam String type,
            @RequestParam Long recordId,
            @RequestParam Long accountId,
            @RequestParam Double amountPaid) {
        try {
            t3Service.settleTransaction(type, recordId, accountId, amountPaid);
            return ResponseEntity.ok(Map.of("message", "Settlement Successful"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Settlement Error: " + e.getMessage());
        }
    }

    // --- 5. PARAMETERS & LEDGERS ---
    @GetMapping("/employees")
    public List<Employee> getEmployees() {
        return t3Service.getAllEmployees();
    }

    @PostMapping("/employees")
    public Employee saveEmployee(@RequestBody Employee employee) {
        return t3Service.saveEmployee(employee);
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<?> deleteEmployee(@PathVariable Long id) {
        try {
            t3Service.deleteEmployee(id);
            return ResponseEntity.ok().build();
        } catch(Exception e) {
            return ResponseEntity.badRequest().body("Cannot delete: Linked to records.");
        }
    }

    @GetMapping("/categories")
    public List<ExpenseCategory> getCategories() {
        return t3Service.getAllCategories();
    }

    @PostMapping("/categories")
    public ExpenseCategory saveCategory(@RequestBody ExpenseCategory category) {
        return t3Service.saveCategory(category);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            t3Service.deleteCategory(id);
            return ResponseEntity.ok().build();
        } catch(Exception e) {
            return ResponseEntity.badRequest().body("Cannot delete: Linked to records.");
        }
    }

    @GetMapping("/ledgers/elec")
    public List<ElectricityBill> getElecLedger() {
        return t3Service.getAllElec();
    }

    @GetMapping("/ledgers/maint")
    public List<MaintenanceBill> getMaintLedger() {
        return t3Service.getAllMaint();
    }

    // New: Trigger SQL Backup Download
    @GetMapping("/system/backup")
    public ResponseEntity<?> generateBackup() {
        try {
            // The service now returns the file OR the specific Aiven/Linux error
            return t3Service.generateBackup(); 
        } catch (Exception e) {
            // This is a 'System Error' fallback if the Java code itself crashes
            return ResponseEntity.status(500).body("Controller Error (Backup): " + e.getMessage());
        }
    }

    @PostMapping("/system/restore")
    public ResponseEntity<?> restoreDatabase(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            // The service handles the 'apk' tools and shell execution
            return t3Service.restoreDatabase(file);
        } catch (Exception e) {
            // This catches file-upload or unexpected Java interruptions
            return ResponseEntity.status(500).body("Controller Error (Restore): " + e.getMessage());
        }
    }
}