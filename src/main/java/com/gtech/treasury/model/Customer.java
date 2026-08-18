package com.gtech.treasury.model;

/**
 * Müşteriyi temsil eden model (POJO) sınıfı.
 * customer tablosundaki bir satıra karşılık gelir.
 *
 * customerNo   : 8 haneli otomatik müşteri numarası (10000001...)
 * customerType : müşteri türü — "GERCEK" veya "TUZEL" (role tablosundan)
 */
public class Customer {

    private int customerId;
    private int customerNo;
    private String customerType;
    private String customerName;
    private String surname;
    private String tc;
    private String phone;
    private String address;
    private String password;
    private int status = 1;    // 1 aktif, 0 pasif
    private String createdAt;  // kayıt zamanı

    public Customer() {
    }

    /** createdAt "2026-08-01 10:24:10.0" -> tarih kısmı. */
    public String getCreatedDate() {
        if (createdAt == null) return "";
        int sp = createdAt.indexOf(' ');
        return sp > 0 ? createdAt.substring(0, sp) : createdAt;
    }

    /** createdAt -> saat kısmı (milisaniyesiz). */
    public String getCreatedTime() {
        if (createdAt == null) return "";
        int sp = createdAt.indexOf(' ');
        if (sp < 0) return "";
        String t = createdAt.substring(sp + 1);
        int dot = t.indexOf('.');
        return dot > 0 ? t.substring(0, dot) : t;
    }

    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getCreatedAt() { return createdAt; }

    /** Veritabanından okurken kullanılan tam constructor. */
    public Customer(int customerId, int customerNo, String customerType,
                    String customerName, String surname, String tc,
                    String phone, String address) {
        this.customerId = customerId;
        this.customerNo = customerNo;
        this.customerType = customerType;
        this.customerName = customerName;
        this.surname = surname;
        this.tc = tc;
        this.phone = phone;
        this.address = address;
    }

    /** Yeni müşteri eklerken kullanılan constructor (id ve no DB'de atanır). */
    public Customer(String customerType, String customerName, String surname,
                    String tc, String phone, String address, String password) {
        this.customerType = customerType;
        this.customerName = customerName;
        this.surname = surname;
        this.tc = tc;
        this.phone = phone;
        this.address = address;
        this.password = password;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(int customerNo) {
        this.customerNo = customerNo;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getTc() {
        return tc;
    }

    public void setTc(String tc) {
        this.tc = tc;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
