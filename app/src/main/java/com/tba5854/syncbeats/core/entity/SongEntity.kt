package com.tba5854.syncbeats.core.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    val path: String,
    @PrimaryKey() val hash: String,
    val title: String,
)
