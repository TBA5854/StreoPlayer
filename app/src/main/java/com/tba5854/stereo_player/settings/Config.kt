package com.tba5854.stereo_player.settings

import android.content.Context
import android.content.SharedPreferences
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


enum class Mode {
    SERVER,
    P2P
}

data class Config @OptIn(ExperimentalUuidApi::class) constructor(
    var userName: String = "Guest",
    var userId: String = Uuid.random().toString(),
    var mode: Mode = Mode.SERVER,
    var port: Int = 55555,
    var serverIp: String = "http://10.55.32.72:3000",
    var p2pClients: List<String> = listOf()
)

/**
 * Global settings singleton.
 *
 * Call [Settings.init] once from Application.onCreate(),
 * then access from anywhere via [Settings.config].
 *
 * Persists to SharedPreferences — call [Settings.store] after mutations,
 * or use [Settings.update] for atomic read-modify-write.
 */
object Settings {

    private const val PREFS_NAME = "streo_settings"

    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_MODE = "mode"
    private const val KEY_PORT = "port"
    private const val KEY_SERVER_IP = "server_ip"
    private const val KEY_P2P_CLIENTS = "p2p_clients"

    private lateinit var prefs: SharedPreferences

    /** Current in-memory config. Mutate fields then call [store], or use [update]. */
    var config: Config = Config()
        private set

    /**
     * Initialize from SharedPreferences.
     * Call once in Application.onCreate().
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    /** Reload config from disk into memory. */
    fun load() {
        check(::prefs.isInitialized) { "Settings.init(context) must be called first" }

        @OptIn(ExperimentalUuidApi::class)
        val defaults = Config()
        android.util.Log.d("TWSt","Loading")

        config = Config(
            userName = prefs.getString(KEY_USER_NAME, defaults.userName) ?: defaults.userName,
            userId = prefs.getString(KEY_USER_ID, defaults.userId) ?: defaults.userId,
            mode = prefs.getString(KEY_MODE, null)
                ?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
                ?: defaults.mode,
            port = prefs.getInt(KEY_PORT, defaults.port),
            serverIp = prefs.getString(KEY_SERVER_IP, defaults.serverIp) ?: defaults.serverIp,
            p2pClients = prefs.getString(KEY_P2P_CLIENTS, null)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: defaults.p2pClients
        )
        store()
    }

    /** Persist current in-memory config to disk. */
    fun store() {
        check(::prefs.isInitialized) { "Settings.init(context) must be called first" }

        prefs.edit()
            .putString(KEY_USER_NAME, config.userName)
            .putString(KEY_USER_ID, config.userId)
            .putString(KEY_MODE, config.mode.name)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_SERVER_IP, config.serverIp)
            .putString(KEY_P2P_CLIENTS, config.p2pClients.joinToString(","))
            .apply()
    }

    /**
     * Atomic read-modify-write.
     * Mutate the config in the lambda, then it auto-stores.
     *
     * Usage:
     * ```
     * Settings.update { it.userName = "Alice" }
     * ```
     */
    inline fun update(block: (Config) -> Unit) {
        block(config)
        store()
    }
}
