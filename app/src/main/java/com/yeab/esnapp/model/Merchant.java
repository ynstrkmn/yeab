package com.yeab.esnapp.model;

public class Merchant {
    public String BirthDate;
    public String City;
    public String Coordinates;
    public String District;
    public String MerchantName;
    public String MerchantType;
    public long MobilePhoneNumber;
    public String Name;
    public String Surname;
    public String PushToken; // FCM token

    public Merchant() {
    }

    public Merchant(String birthDate, String city, String coordinates, String district,
                    String merchantName, String merchantType, long mobilePhoneNumber,
                    String name, String surname, String pushToken) {
        BirthDate = birthDate;
        City = city;
        Coordinates = coordinates;
        District = district;
        MerchantName = merchantName;
        MerchantType = merchantType;
        MobilePhoneNumber = mobilePhoneNumber;
        Name = name;
        Surname = surname;
        PushToken = pushToken;
    }
}
