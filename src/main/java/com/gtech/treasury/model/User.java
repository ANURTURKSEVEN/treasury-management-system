package com.gtech.treasury.model;

/**
 * Kullanıcıyı temsil eden model (POJO) sınıfı.
 * users tablosundaki bir satıra karşılık gelir.
 * Aşama 1 (Class/Encapsulation) + Aşama 15 (model katmanı).
 */
public class User {

    private int id;
    private String username;
    private String role;      // ADMIN / TRADER / VIEWER / CUSTOMER
    private String fullName;
    private int customerId;   // müşteri kullanıcısıysa bağlı customer.id, değilse 0

    public User() {
    }

    public User(int id, String username, String role, String fullName) {
        this(id, username, role, fullName, 0);
    }

    public User(int id, String username, String role, String fullName, int customerId) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.fullName = fullName;
        this.customerId = customerId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
