package com.yeab.esnapp.model;

public class MerchantUser {
    public String Email;
    public long MobilePhoneNumber;
    public String Name;
    public String Surname;

    public MerchantUser() {
    }

    public MerchantUser(String email, long mobilePhoneNumber, String name, String surname) {
        Email = email;
        MobilePhoneNumber = mobilePhoneNumber;
        Name = name;
        Surname = surname;
    }
}
