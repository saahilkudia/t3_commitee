package com.nexus.t3_management.services;

import com.nexus.t3_management.models.*;
import com.nexus.t3_management.repositories.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Service
public class T3Services {

    @Autowired private UnitRepository unitRepo;
    @Autowired private ElectricityBillRepository elecRepo;
    @Autowired private MaintenanceBillRepository maintRepo;
    @Autowired private BuildingExpenseRepository expenseRepo;
    @Autowired private StaffSalaryRepository salaryRepo;
    @Autowired private EmployeeRepository employeeRepo;
    @Autowired private ExpenseCategoryRepository categoryRepo;
    @Autowired private ChartOfAccountRepository coaRepo;
    @Autowired private AppUserRepository appUserRepo;
    @Autowired private AccountingEngine accountingEngine;

    // --- 0. AUTHENTICATION & SECURITY (RBAC) ---
    @PostConstruct
    public void initMasterUser() {
        if (appUserRepo.count() == 0) {
            AppUser master = new AppUser();
            master.setUsername("t3"); master.setPassword("t3_2026");
            master.setRole("ADMIN"); master.setPermissions("ALL");
            appUserRepo.save(master);
        }
    }

    public AppUser authenticate(String username, String password) {
        return appUserRepo.findByUsername(username).filter(u -> u.getPassword().equals(password)).orElse(null);
    }
    public List<AppUser> getAllUsers() { return appUserRepo.findAll(); }
    public AppUser saveUser(AppUser user) { return appUserRepo.save(user); }
    public void deleteUser(Long id) { appUserRepo.deleteById(id); }

    // --- DYNAMIC CHART OF ACCOUNTS LOOKUP ---
    private ChartOfAccount getSysAcc(String type) {
        List<ChartOfAccount> accs = coaRepo.findByAccountType(type);
        if (accs.isEmpty()) {
            throw new RuntimeException("Missing System Account: '" + type + "'. Please create this account mapping in the Chart of Accounts first.");
        }
        return accs.get(0); // Uses the first account mapped to this type by the user
    }

    // --- 1. DASHBOARD OVERVIEW ---
    public Map<String, Object> getFinancialSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("accounts", getBanksAndCash());
        summary.put("recentElec", elecRepo.findTop10ByOrderByCreatedAtDesc());
        summary.put("recentMaint", maintRepo.findTop10ByOrderByCreatedAtDesc());
        summary.put("recentSalaries", salaryRepo.findTop10ByOrderByPaymentDateDesc());
        summary.put("recentExpenses", expenseRepo.findTop10ByOrderByDateDesc());
        return summary;
    }

    // --- 2. ACCOUNTS (Fully User-Driven Now) ---
    public List<ChartOfAccount> getBanksAndCash() { return coaRepo.findByAccountTypeIn(Arrays.asList("BANK", "CASH")); }

    public ChartOfAccount saveAccount(ChartOfAccount acc) {
        if (acc.getId() == null || acc.getAccountCode() == null || acc.getAccountCode().isEmpty()) {
            // Smart numbering based on standard accounting principles
            String prefix = "1"; // Default: ASSET
            if ("LIABILITY".equals(acc.getAccountCategory())) prefix = "2";
            else if ("EQUITY".equals(acc.getAccountCategory())) prefix = "3";
            else if ("REVENUE".equals(acc.getAccountCategory())) prefix = "4";
            else if ("EXPENSE".equals(acc.getAccountCategory())) prefix = "5";

            long count = coaRepo.count() + 1;
            acc.setAccountCode(prefix + String.format("%03d", count));
        }
        return coaRepo.save(acc);
    }

    public void deleteAccount(Long id) { coaRepo.deleteById(id); }

    // --- 3. UNITS ---
    public List<Unit> getUnits(String keyword) {
        if (keyword != null && !keyword.isEmpty()) return unitRepo.searchUnits(keyword);
        return unitRepo.findAll();
    }
    public Unit saveUnit(Unit unit) { return unitRepo.save(unit); }
    public void deleteUnit(Long id) { unitRepo.deleteById(id); }

    // --- 4. ACCRUAL GENERATION ---
    public Integer getPreviousReading(Long unitId) {
        List<ElectricityBill> bills = elecRepo.findByUnitIdOrderByCreatedAtDesc(unitId);
        return (!bills.isEmpty() && bills.get(0).getCurrReading() != null) ? bills.get(0).getCurrReading() : 0;
    }

    @Transactional
    public ElectricityBill saveElec(ElectricityBill bill) {
        if (bill.getCurrReading() < bill.getPrevReading()) throw new RuntimeException("Invalid meter reading.");

        int consumed = bill.getCurrReading() - bill.getPrevReading();
        bill.setConsumed(consumed);

        // Dynamically calculate using the rate provided by the frontend settings (with a safe fallback)
        double rate = (bill.getUnitPrice() != null && bill.getUnitPrice() > 0) ? bill.getUnitPrice() : 35.0;

        // Penalty is now added directly as a flat manual amount per your requirement
        double total = (consumed * rate) +
                (bill.getFixedCharges() != null ? bill.getFixedCharges() : 0.0) +
                (bill.getPenalty() != null ? bill.getPenalty() : 0.0);

        bill.setTotal(total); bill.setStatus("UNPAID");
        if (bill.getCreatedAt() == null) bill.setCreatedAt(LocalDate.now());
        bill.setUnit(unitRepo.findById(bill.getUnit().getId()).orElseThrow());
        bill = elecRepo.save(bill);

        JournalVoucher jv = new JournalVoucher();
        jv.setVoucherType("SV");
        jv.setMemo("Electricity Accrual for Unit " + bill.getUnit().getUnitNumber());
        jv.getLines().add(accountingEngine.createLine(getSysAcc("AR"), bill.getTotal(), 0.0, "A/R Created"));
        jv.getLines().add(accountingEngine.createLine(getSysAcc("REV"), 0.0, bill.getTotal(), "Utility Revenue"));
        accountingEngine.postVoucher(jv);

        return bill;
    }

    @Transactional
    public MaintenanceBill saveMaint(MaintenanceBill bill) {
        double total = bill.getAmount() + (bill.getPenalty() != null ? bill.getPenalty() : 0.0);
        bill.setTotal(total); bill.setStatus("UNPAID");
        if (bill.getCreatedAt() == null) bill.setCreatedAt(LocalDate.now());
        bill.setUnit(unitRepo.findById(bill.getUnit().getId()).orElseThrow());
        bill = maintRepo.save(bill);

        JournalVoucher jv = new JournalVoucher();
        jv.setVoucherType("SV");
        jv.setMemo("Maintenance Accrual for Unit " + bill.getUnit().getUnitNumber());
        jv.getLines().add(accountingEngine.createLine(getSysAcc("AR"), bill.getTotal(), 0.0, "A/R Created"));
        jv.getLines().add(accountingEngine.createLine(getSysAcc("REV"), 0.0, bill.getTotal(), "Maintenance Revenue"));
        accountingEngine.postVoucher(jv);

        return bill;
    }

    @Transactional
    public MaintenanceBill applyMaintPenalty(Long id, Double penaltyAmount) {
        MaintenanceBill bill = maintRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Maintenance Bill not found with ID: " + id));

        if ("PAID".equals(bill.getStatus())) {
            throw new RuntimeException("Cannot add penalty to a settled bill.");
        }

        bill.setPenalty(penaltyAmount);
        bill.setTotal(bill.getAmount() + penaltyAmount);

        return maintRepo.save(bill);
    }

    @Transactional
    public StaffSalary saveSalary(StaffSalary s) {
        s.setStatus("UNPAID");
        if (s.getPaymentDate() == null) s.setPaymentDate(LocalDate.now());
        s = salaryRepo.save(s);

        JournalVoucher jv = new JournalVoucher();
        jv.setVoucherType("JV");
        jv.setMemo("Salary Accrual for " + s.getEmployee().getName());
        jv.getLines().add(accountingEngine.createLine(getSysAcc("EXP"), s.getAmount(), 0.0, "Salary Expense"));
        jv.getLines().add(accountingEngine.createLine(getSysAcc("AP"), 0.0, s.getAmount(), "A/P Created"));
        accountingEngine.postVoucher(jv);

        return s;
    }

    @Transactional
    public BuildingExpense saveExpense(BuildingExpense e) {
        e.setStatus("UNPAID");
        if (e.getDate() == null) e.setDate(LocalDate.now());
        e = expenseRepo.save(e);

        JournalVoucher jv = new JournalVoucher();
        jv.setVoucherType("JV");
        jv.setMemo("Operating Expense: " + e.getCategory().getName());
        jv.getLines().add(accountingEngine.createLine(getSysAcc("EXP"), e.getAmount(), 0.0, "Expense Logged"));
        jv.getLines().add(accountingEngine.createLine(getSysAcc("AP"), 0.0, e.getAmount(), "A/P Created"));
        accountingEngine.postVoucher(jv);

        return e;
    }

    // --- 5. THE SETTLEMENT ENGINE ---
    @Transactional
    public void settleTransaction(String type, Long recordId, Long accountId, Double amountPaid) {
        ChartOfAccount bankAcc = coaRepo.findById(accountId).orElseThrow(() -> new RuntimeException("Financial Account not found"));
        ChartOfAccount arAcc = getSysAcc("AR");
        ChartOfAccount apAcc = getSysAcc("AP");

        if (amountPaid <= 0) throw new RuntimeException("Payment amount must be greater than zero.");

        JournalVoucher jv = new JournalVoucher();
        jv.setVoucherDate(LocalDate.now());

        switch (type) {
            case "ELEC":
                ElectricityBill eb = elecRepo.findById(recordId).orElseThrow();
                if (eb.getStatus().equals("PAID")) throw new RuntimeException("Voucher already settled.");
                if (amountPaid > eb.getTotal()) throw new RuntimeException("Cannot pay more than billed.");

                jv.setVoucherType("BR"); jv.setMemo("Receipt for Elec Bill U-" + eb.getUnit().getUnitNumber());
                jv.getLines().add(accountingEngine.createLine(bankAcc, amountPaid, 0.0, "Funds Received"));
                jv.getLines().add(accountingEngine.createLine(arAcc, 0.0, amountPaid, "A/R Cleared"));
                accountingEngine.postVoucher(jv);

                if (amountPaid < eb.getTotal()) {
                    ElectricityBill remainder = new ElectricityBill();
                    remainder.setUnit(eb.getUnit()); remainder.setMonth(eb.getMonth() + " (Arrears)");
                    remainder.setTotal(eb.getTotal() - amountPaid); remainder.setConsumed(0); remainder.setStatus("UNPAID");
                    elecRepo.save(remainder);
                    eb.setTotal(amountPaid);
                }
                eb.setStatus("PAID"); eb.setAccount(bankAcc); elecRepo.save(eb);
                break;

            case "MAINT":
                MaintenanceBill mb = maintRepo.findById(recordId).orElseThrow();
                if (mb.getStatus().equals("PAID")) throw new RuntimeException("Voucher already settled.");
                if (amountPaid > mb.getTotal()) throw new RuntimeException("Cannot pay more than billed.");

                jv.setVoucherType("BR"); jv.setMemo("Receipt for Maint Fee U-" + mb.getUnit().getUnitNumber());
                jv.getLines().add(accountingEngine.createLine(bankAcc, amountPaid, 0.0, "Funds Received"));
                jv.getLines().add(accountingEngine.createLine(arAcc, 0.0, amountPaid, "A/R Cleared"));
                accountingEngine.postVoucher(jv);

                if (amountPaid < mb.getTotal()) {
                    MaintenanceBill remainder = new MaintenanceBill();
                    remainder.setUnit(mb.getUnit()); remainder.setMonth(mb.getMonth() + " (Arrears)");
                    remainder.setAmount(mb.getTotal() - amountPaid); remainder.setTotal(mb.getTotal() - amountPaid); remainder.setStatus("UNPAID");
                    maintRepo.save(remainder);
                    mb.setTotal(amountPaid);
                }
                mb.setStatus("PAID"); mb.setAccount(bankAcc); maintRepo.save(mb);
                break;

            case "EXP":
                BuildingExpense exp = expenseRepo.findById(recordId).orElseThrow();
                if (exp.getStatus().equals("PAID")) throw new RuntimeException("Voucher already settled.");
                if (amountPaid > exp.getAmount()) throw new RuntimeException("Cannot pay more than billed.");

                jv.setVoucherType("BP"); jv.setMemo("Payment for Expense: " + exp.getCategory().getName());
                jv.getLines().add(accountingEngine.createLine(apAcc, amountPaid, 0.0, "A/P Cleared"));
                jv.getLines().add(accountingEngine.createLine(bankAcc, 0.0, amountPaid, "Funds Disbursed"));
                accountingEngine.postVoucher(jv);

                if (amountPaid < exp.getAmount()) {
                    BuildingExpense remainder = new BuildingExpense();
                    remainder.setCategory(exp.getCategory()); remainder.setDate(exp.getDate());
                    remainder.setAmount(exp.getAmount() - amountPaid); remainder.setDescription(exp.getDescription() + " (Pending Balance)"); remainder.setStatus("UNPAID");
                    expenseRepo.save(remainder);
                    exp.setAmount(amountPaid);
                }
                exp.setStatus("PAID"); exp.setAccount(bankAcc); expenseRepo.save(exp);
                break;

            case "SAL":
                StaffSalary sal = salaryRepo.findById(recordId).orElseThrow();
                if (sal.getStatus().equals("PAID")) throw new RuntimeException("Voucher already settled.");
                if (amountPaid > sal.getAmount()) throw new RuntimeException("Cannot pay more than accrued.");

                jv.setVoucherType("BP"); jv.setMemo("Salary Payout for " + sal.getEmployee().getName());
                jv.getLines().add(accountingEngine.createLine(apAcc, amountPaid, 0.0, "A/P Cleared"));
                jv.getLines().add(accountingEngine.createLine(bankAcc, 0.0, amountPaid, "Funds Disbursed"));
                accountingEngine.postVoucher(jv);

                if (amountPaid < sal.getAmount()) {
                    StaffSalary remainder = new StaffSalary();
                    remainder.setEmployee(sal.getEmployee()); remainder.setSalaryMonth(sal.getSalaryMonth());
                    remainder.setAmount(sal.getAmount() - amountPaid); remainder.setRemarks(sal.getRemarks() + " (Pending Balance)"); remainder.setStatus("UNPAID");
                    salaryRepo.save(remainder);
                    sal.setAmount(amountPaid); sal.setRemarks(sal.getRemarks() + " (Partial Clearance)");
                }
                sal.setStatus("PAID"); sal.setAccount(bankAcc); salaryRepo.save(sal);
                break;

            default: throw new RuntimeException("Invalid settlement type.");
        }
    }

    // --- 6. REGISTRIES & PARAMETERS ---
    public Employee saveEmployee(Employee emp) { if (emp.getRegisteredAt() == null) emp.setRegisteredAt(LocalDate.now()); return employeeRepo.save(emp); }
    public List<Employee> getAllEmployees() { return employeeRepo.findAll(); }
    public void deleteEmployee(Long id) { employeeRepo.deleteById(id); }

    public ExpenseCategory saveCategory(ExpenseCategory cat) { return categoryRepo.save(cat); }
    public List<ExpenseCategory> getAllCategories() { return categoryRepo.findAll(); }
    public void deleteCategory(Long id) { categoryRepo.deleteById(id); }

    public List<ElectricityBill> getAllElec() { return elecRepo.findAll(); }
    public List<MaintenanceBill> getAllMaint() { return maintRepo.findAll(); }
    @Transactional
    public void saveBulkMaint(Double amount, String month) {
        List<Unit> allUnits = unitRepo.findAll();
        for (Unit u : allUnits) {
            MaintenanceBill bill = new MaintenanceBill();
            bill.setUnit(u);
            bill.setAmount(amount);
            bill.setPenalty(0.0);
            bill.setMonth(month);
            saveMaint(bill); // Reuses your existing logic + JV generation
        }
    }

    public List<StaffSalary> getAllSalaries() { return salaryRepo.findAll(); }
    public List<BuildingExpense> getAllExpenses() { return expenseRepo.findAll(); }

    @Transactional
    public ResponseEntity<?> generateBackup() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String dumpPath = os.contains("win") ? "mysqldump.exe" : "mysqldump";

            // Environment Variables from Render
            String dbHost = System.getenv("DB_HOST");
            String dbPort = System.getenv("DB_PORT");
            String dbUser = System.getenv("DB_USER");
            String dbPass = System.getenv("DB_PASS");
            String dbName = System.getenv("DB_NAME");

            // Safety check for Environment Variables
            if (dbHost == null || dbPass == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Missing Environment Variables: Ensure DB_HOST and DB_PASS are set in Render.");
            }

            String[] command;
            if (os.contains("win")) {
                command = new String[]{dumpPath, "-h", dbHost, "-P", dbPort, "-u", dbUser, "-p" + dbPass, dbName};
            } else {
                // Protects '*' and handles Aiven's SSL requirement
                // Updated command for MariaDB/MySQL compatibility in Alpine Linux
                String cmdString = String.format("%s -h %s -P %s -u %s -p'%s' --ssl --ssl-verify-server-cert=OFF --default-auth=mysql_native_password %s", 
                    dumpPath, dbHost, dbPort, dbUser, dbPass, dbName);
                command = new String[]{"/bin/sh", "-c", cmdString};
            }

            Process process = Runtime.getRuntime().exec(command);

            // Capture both Output (the SQL) and Error (the Culprit)
            try (java.io.InputStream is = process.getInputStream();
                 java.io.InputStream es = process.getErrorStream()) {
                 
                byte[] data = is.readAllBytes();
                byte[] errorData = es.readAllBytes();

                if (data.length == 0 && errorData.length > 0) {
                    String errorMsg = new String(errorData);
                    System.err.println("DATABASE BACKUP CULPRIT: " + errorMsg);
                    // Returns the exact error to your frontend instead of a 500 error
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("MySQL Error: " + errorMsg);
                }
                
                if (data.length == 0) {
                    return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Backup generated an empty file.");
                }

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentDispositionFormData("attachment", "t3_backup.sql");
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
                return new ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("System Error: " + e.getMessage());
        }
    }

    @Transactional
    public ResponseEntity<?> restoreDatabase(org.springframework.web.multipart.MultipartFile file) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String mysqlPath = os.contains("win") ? "mysql.exe" : "mysql";

            String dbHost = System.getenv("DB_HOST");
            String dbPort = System.getenv("DB_PORT");
            String dbUser = System.getenv("DB_USER");
            String dbPass = System.getenv("DB_PASS");
            String dbName = System.getenv("DB_NAME");

            String[] command;
            if (os.contains("win")) {
                command = new String[]{mysqlPath, "-h", dbHost, "-P", dbPort, "-u", dbUser, "-p" + dbPass, dbName};
            } else {
                // Updated restore command for Alpine compatibility
                String cmdString = String.format("%s -h %s -P %s -u %s -p'%s' --ssl --ssl-verify-server-cert=OFF --default-auth=mysql_native_password %s", 
                    mysqlPath, dbHost, dbPort, dbUser, dbPass, dbName);
                command = new String[]{"/bin/sh", "-c", cmdString};
            }

            Process process = Runtime.getRuntime().exec(command);

            try (java.io.OutputStream osStream = process.getOutputStream()) {
                osStream.write(file.getBytes());
                osStream.flush();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                java.io.InputStream es = process.getErrorStream();
                String error = new String(es.readAllBytes());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Restore Culprit: " + error);
            }
            return ResponseEntity.ok("Database restored successfully!");
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("System Error: " + e.getMessage());
        }
    }
}