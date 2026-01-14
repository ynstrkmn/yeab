package com.yeab.esnapp.model;

import java.util.List;

public class Order {

    private boolean IsFinished;
    private  boolean IsPaymentDone;
    private String PaymentDate;
    private String ProductImageUrl;
    private String ProductName;
    private List<ProductStatus> ProductStatus;
    private String CreatedDate; // YENİ EKLEDİĞİMİZ ALAN
    private String PhoneNumber;
    private String OrderNumber;

    // Boş constructor (Firebase için zorunlu)
    public Order() {
    }

    public Order(boolean isFinished,
                 String productImageUrl,
                 String productName,
                 List<ProductStatus> productStatus,
                 String createdDate,
                 boolean isPaymentDone,
                 String PaymentDate,
                 String PhoneNumber,
                 String OrderNumber) {
        IsFinished = isFinished;
        ProductImageUrl = productImageUrl;
        ProductName = productName;
        ProductStatus = productStatus;
        CreatedDate = createdDate;
        this.IsPaymentDone = isPaymentDone;
        this.PaymentDate = PaymentDate;
        this.PhoneNumber = PhoneNumber;
        this.OrderNumber = OrderNumber;
    }

    public String getOrderNumber() {
        return OrderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        OrderNumber = orderNumber;
    }

    public boolean isFinished() {
        return IsFinished;
    }

    public void setFinished(boolean finished) {
        IsFinished = finished;
    }

    public boolean isPaymentDone() {
        return IsPaymentDone;
    }

    public void setPaymentDone(boolean paymentDone) {
        IsPaymentDone = paymentDone;
    }

    public String getPaymentDate() {
        return PaymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        PaymentDate = paymentDate;
    }



    public String getProductImageUrl() {
        return ProductImageUrl;
    }

    public void setProductImageUrl(String productImageUrl) {
        ProductImageUrl = productImageUrl;
    }

    public String getProductName() {
        return ProductName;
    }

    public void setProductName(String productName) {
        ProductName = productName;
    }

    public List<ProductStatus> getProductStatus() {
        return ProductStatus;
    }

    public void setProductStatus(List<ProductStatus> productStatus) {
        ProductStatus = productStatus;
    }

    public String getCreatedDate() {
        return CreatedDate;
    }

    public void setCreatedDate(String createdDate) {
        CreatedDate = createdDate;
    }

    public String getPhoneNumber() {
        return PhoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        PhoneNumber = phoneNumber;
    }
}
