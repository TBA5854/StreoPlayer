package com.tba5854.syncbeats.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    var name: String = ""
)