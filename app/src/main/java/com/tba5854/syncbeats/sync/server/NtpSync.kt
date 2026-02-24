package com.tba5854.syncbeats.sync.server

object NtpSync {

    private val samples = mutableListOf<Long>()

    var offset: Long = 0L
        private set

    val isCalibrated: Boolean
        get() = samples.size >= 3

    fun recordPong(pong: NtpPong, t4: Long) {
        val rtt = (t4 - pong.t1) - (pong.t3 - pong.t2)
        val sampleOffset = ((pong.t2 - pong.t1) + (pong.t3 - t4)) / 2
        samples.add(sampleOffset)
        offset = samples.sorted()[samples.size / 2]
    }

    fun adjustedTime(serverTimestamp: Long): Long = serverTimestamp + offset

    fun reset() {
        samples.clear()
        offset = 0L
    }
}
