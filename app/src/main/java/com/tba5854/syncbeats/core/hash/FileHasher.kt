package com.tba5854.syncbeats.core.hash

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class hasher {
    fun md5Hash(file: File): String {
        val buffer = ByteArray(1024 * 4)  // 4KB buffer
        val digest = MessageDigest.getInstance("MD5")
        val fis = FileInputStream(file)

        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        fis.close()

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun compareHash(str1:String,str2:String): Boolean{
        return str1 == str2
    }
}