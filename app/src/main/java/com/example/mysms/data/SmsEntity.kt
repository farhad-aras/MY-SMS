package com.example.mysms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_table")
data class SmsEntity(
    @PrimaryKey val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val subId: Int,
    val read: Boolean
)