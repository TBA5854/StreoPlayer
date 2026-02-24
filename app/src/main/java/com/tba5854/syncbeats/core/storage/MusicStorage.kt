package com.tba5854.syncbeats.core.storage

import android.content.Context
import android.util.Log
import com.tba5854.syncbeats.core.entity.SongEntity
import com.tba5854.syncbeats.core.hash.hasher
import java.io.File

object MusicStorage {

    private const val TAG = "MusicStorage"
    private const val MUSIC_DIR = "music"

    private lateinit var musicDir: File
    private lateinit var songDao: SongDao
    private val fileHasher = hasher()

    val dir: File
        get() = musicDir

    fun init(context: Context) {
        musicDir = File(context.dataDir, MUSIC_DIR)
        if (!musicDir.exists()) {
            musicDir.mkdirs()
        }
        songDao = AppDatabase.getInstance(context).songDao()
        Log.d(TAG, "Music dir: ${musicDir.absolutePath} (${songDao.count()} records)")
    }

    fun importFile(source: File, title: String = source.nameWithoutExtension): ImportResult {
        check(::musicDir.isInitialized) { "MusicStorage.init(context) must be called first" }
        require(source.exists()) { "Source file does not exist: ${source.absolutePath}" }

        val hash = fileHasher.md5Hash(source)
        val ext = source.extension.ifEmpty { "mp3" }
        val dest = File(musicDir, "$hash.$ext")

        if (dest.exists()) {
            Log.d(TAG, "Duplicate skipped: ${source.name} → $hash")
            return ImportResult(hash = hash, file = dest, isDuplicate = true)
        }

        source.copyTo(dest)

        val entity = SongEntity(path = dest.absolutePath, hash = hash, title = title)
        songDao.insert(entity)

        Log.d(TAG, "Imported: ${source.name} → ${dest.name}")
        return ImportResult(hash = hash, file = dest, isDuplicate = false)
    }

    fun getByHash(hash: String): SongEntity? = songDao.getByHash(hash)

    fun getFileByHash(hash: String): File? {
        check(::musicDir.isInitialized) { "MusicStorage.init(context) must be called first" }
        return musicDir.listFiles()?.firstOrNull { it.nameWithoutExtension == hash }
    }

    fun exists(hash: String): Boolean = songDao.exists(hash)

    fun hashFile(file: File): String = fileHasher.md5Hash(file)

    fun getAll(): List<SongEntity> = songDao.getAll()

    fun search(query: String): List<SongEntity> = songDao.searchByTitle(query)

    fun delete(hash: String): Boolean {
        val file = getFileByHash(hash)
        val fileDeleted = file?.delete() ?: false
        songDao.deleteByHash(hash)
        if (fileDeleted) Log.d(TAG, "Deleted: $hash")
        return fileDeleted
    }

    fun count(): Int = songDao.count()

    fun listFiles(): List<File> {
        check(::musicDir.isInitialized) { "MusicStorage.init(context) must be called first" }
        return musicDir.listFiles()?.toList() ?: emptyList()
    }

    fun totalSizeBytes(): Long = listFiles().sumOf { it.length() }

    fun importUri(context: Context, uri: android.net.Uri, title: String? = null): ImportResult {
        check(::musicDir.isInitialized) { "MusicStorage.init(context) must be called first" }

        val resolvedTitle =
                title
                        ?: context.contentResolver
                                .query(uri, null, null, null, null)
                                ?.use { cursor ->
                                    val nameIndex =
                                            cursor.getColumnIndex(
                                                    android.provider.OpenableColumns.DISPLAY_NAME
                                            )
                                    if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                                }
                                ?.substringBeforeLast(".")
                                ?: "Unknown Song"

        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
                ?: throw java.io.IOException("Failed to open input stream for URI: $uri")

        return try {
            val result = importFile(tempFile, resolvedTitle)
            result
        } finally {
            tempFile.delete()
        }
    }

    data class ImportResult(val hash: String, val file: File, val isDuplicate: Boolean)
}
