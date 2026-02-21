package com.tba5854.stereo_player.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tba5854.stereo_player.core.entity.SongEntity

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(song: SongEntity)

    @Query("SELECT * FROM songs WHERE hash = :hash LIMIT 1")
    fun getByHash(hash: String): SongEntity?

    @Query("SELECT * FROM songs ORDER BY title ASC") fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%'")
    fun searchByTitle(query: String): List<SongEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM songs WHERE hash = :hash)")
    fun exists(hash: String): Boolean

    @Query("DELETE FROM songs WHERE hash = :hash") fun deleteByHash(hash: String)

    @Query("SELECT COUNT(*) FROM songs") fun count(): Int
}
