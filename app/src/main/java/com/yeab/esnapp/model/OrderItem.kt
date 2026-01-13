package com.yeab.esnapp.ui.model

data class OrderItem(
    val id: String,
    val imageUrl: String,
    val ownerName: String,
    val ownerSurname: String,
    val ownerPhone: String,
    val productName: String,
    val lastStatus: String,
    val createdDate: String? // yeni alan
)
