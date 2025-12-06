package com.yeab.esnapp.model;

import java.util.List;

public class Order {

    private boolean IsFinished;
    private  boolean IsPaymentDone;
    private String ProductImageUrl;
    private String ProductName;
    private List<ProductStatus> ProductStatus;
    private String CreatedDate; // YENİ EKLEDİĞİMİZ ALAN

    // Boş constructor (Firebase için zorunlu)
    public Order() {
    }

    public Order(boolean isFinished,
                 String productImageUrl,
                 String productName,
                 List<ProductStatus> productStatus,
                 String createdDate,
                 boolean isPaymentDone) {
        IsFinished = isFinished;
        ProductImageUrl = productImageUrl;
        ProductName = productName;
        ProductStatus = productStatus;
        CreatedDate = createdDate;
        this.IsPaymentDone = isPaymentDone;
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
}
