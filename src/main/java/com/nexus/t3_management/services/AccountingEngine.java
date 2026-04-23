package com.nexus.t3_management.services;

import com.nexus.t3_management.models.*;
import com.nexus.t3_management.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class AccountingEngine {

    @Autowired private JournalVoucherRepository jvRepo;
    @Autowired private ChartOfAccountRepository coaRepo;

    /**
     * CORE DOUBLE-ENTRY POSTING ENGINE
     */
    @Transactional
    public JournalVoucher postVoucher(JournalVoucher jv) {
        // 1. Enforce Period Locking (Prevents changing historical, closed data)
        if (jv.getIsLocked() != null && jv.getIsLocked()) {
            throw new RuntimeException("Period is locked. Cannot post voucher.");
        }

        double totalDr = 0;
        double totalCr = 0;

        // 2. Tally up all Debits and Credits
        for (JournalLine line : jv.getLines()) {
            totalDr += (line.getDebit() != null ? line.getDebit() : 0.0);
            totalCr += (line.getCredit() != null ? line.getCredit() : 0.0);
        }

        // 3. The Golden Rule of Accounting: Debits MUST equal Credits
        // Using a small epsilon to safely handle floating-point math
        if (Math.abs(totalDr - totalCr) > 0.01) {
            throw new RuntimeException("CRITICAL ACCOUNTING ERROR: Debits (" + totalDr + ") do not equal Credits (" + totalCr + "). Voucher rejected.");
        }

        // 4. Generate sequential voucher numbers automatically
        if (jv.getVoucherNo() == null || jv.getVoucherNo().isEmpty()) {
            long count = jvRepo.count() + 1;
            jv.setVoucherNo(jv.getVoucherType() + "-" + LocalDate.now().getYear() + "-" + String.format("%04d", count));
        }

        // 5. Update Live Balances in the Chart of Accounts depending on Account Category
        for (JournalLine line : jv.getLines()) {
            ChartOfAccount acc = coaRepo.findById(line.getAccount().getId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            double dr = line.getDebit() != null ? line.getDebit() : 0.0;
            double cr = line.getCredit() != null ? line.getCredit() : 0.0;

            if ("ASSET".equals(acc.getAccountCategory()) || "EXPENSE".equals(acc.getAccountCategory())) {
                // Assets & Expenses go UP with Debits, DOWN with Credits
                acc.setCurrentBalance(acc.getCurrentBalance() + dr - cr);
            } else {
                // Liabilities, Equity & Revenue go UP with Credits, DOWN with Debits
                acc.setCurrentBalance(acc.getCurrentBalance() + cr - dr);
            }
            coaRepo.save(acc);
            line.setJournalVoucher(jv);
        }

        return jvRepo.save(jv);
    }

    /**
     * REVERSAL JOURNAL VOUCHER LOGIC (Safe Deletion Alternative)
     */
    @Transactional
    public JournalVoucher reverseVoucher(Long jvId) {
        JournalVoucher original = jvRepo.findById(jvId).orElseThrow(() -> new RuntimeException("Journal Voucher not found"));
        if (original.getIsReversed() != null && original.getIsReversed()) throw new RuntimeException("Voucher is already reversed.");
        if (original.getIsLocked() != null && original.getIsLocked()) throw new RuntimeException("Period is locked. Cannot reverse.");

        JournalVoucher reversal = new JournalVoucher();
        reversal.setVoucherType("REV");
        reversal.setVoucherDate(LocalDate.now());
        reversal.setMemo("Reversal of " + original.getVoucherNo());

        // Swap Dr and Cr to perfectly cancel out the original transaction
        for(JournalLine line : original.getLines()) {
            JournalLine revLine = new JournalLine();
            revLine.setAccount(line.getAccount());
            revLine.setDebit(line.getCredit() != null ? line.getCredit() : 0.0);
            revLine.setCredit(line.getDebit() != null ? line.getDebit() : 0.0);
            revLine.setLineMemo("Reversal of Line: " + (line.getLineMemo() != null ? line.getLineMemo() : ""));
            reversal.getLines().add(revLine);
        }

        original.setIsReversed(true);
        jvRepo.save(original);

        return postVoucher(reversal); // Safely process the reversal through the engine
    }

    // Helper method to quickly generate lines inside T3Service
    public JournalLine createLine(ChartOfAccount acc, Double dr, Double cr, String memo) {
        JournalLine line = new JournalLine();
        line.setAccount(acc);
        line.setDebit(dr != null ? dr : 0.0);
        line.setCredit(cr != null ? cr : 0.0);
        line.setLineMemo(memo);
        return line;
    }
}