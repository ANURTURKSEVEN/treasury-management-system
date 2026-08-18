package com.gtech.treasury.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.model.OverdueInstallment;
import com.gtech.treasury.util.DBConnection;

public class OverdueInstallment {

    private final int customerNo;
    private final String customerName;
    private final int lendingId;
    private final int seqNo;
    private final String dueDate;
    private final double amount;
    private final String currency;
    private final double interestRate;

    public OverdueInstallment(int customerNo, String customerName, int lendingId, int seqNo,
                              String dueDate, double amount, String currency, double interestRate) {
        this.customerNo = customerNo;
        this.customerName = customerName;
        this.lendingId = lendingId;
        this.seqNo = seqNo;
        this.dueDate = dueDate;
        this.amount = amount;
        this.currency = currency;
        this.interestRate = interestRate;
    }

    public int getCustomerNo()      { return customerNo; }
    public String getCustomerName() { return customerName; }
    public int getLendingId()       { return lendingId; }
    public int getSeqNo()           { return seqNo; }
    public String getDueDate()      { return dueDate; }
    public double getAmount()       { return amount; }
    public String getCurrency()     { return currency; }
    public double getInterestRate() { return interestRate; }

    /** Geciken (vadesi geçmiş, ödenmemiş) taksitleri getirir. */
    public List<OverdueInstallment> getOverdue() {
        String sql =
            "SELECT c.customer_no, CONCAT(c.customer_name,' ',IFNULL(c.surname,'')) AS ad, " +
            "       l.id AS lending_id, li.seq_no, li.due_date, li.amount, l.currency, l.interest_rate " +
            "FROM loan_installment li " +
            "JOIN lending l  ON li.lending_id = l.id " +
            "JOIN customer c ON l.customer_id = c.customer_id " +
            "WHERE li.status = 0 AND l.status = 1 AND li.due_date < CURDATE() " +
            "ORDER BY li.due_date";

        List<OverdueInstallment> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new OverdueInstallment(
                        rs.getInt("customer_no"),
                        rs.getString("ad"),
                        rs.getInt("lending_id"),
                        rs.getInt("seq_no"),
                        String.valueOf(rs.getDate("due_date")),
                        rs.getDouble("amount"),
                        rs.getString("currency"),
                        rs.getDouble("interest_rate")));
            }
        } catch (SQLException e) {
            System.err.println("Geciken taksitler getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Geciken taksitler");
        }
        return list;
    }

}