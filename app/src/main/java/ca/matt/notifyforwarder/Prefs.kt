package ca.matt.notifyforwarder

import android.content.Context
import java.security.SecureRandom

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("forwarder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var server: String
        get() = sp.getString("server", "https://ntfy.sh")!!
        set(v) = sp.edit().putString("server", v.trim()).apply()

    /** Random by default: on the public ntfy.sh server, the topic name is the only "password". */
    var topic: String
        get() = sp.getString("topic", null) ?: randomTopic().also { topic = it }
        set(v) = sp.edit().putString("topic", v.trim()).apply()

    /** Optional access token, for a self-hosted or reserved ntfy topic. */
    var token: String
        get() = sp.getString("token", "")!!
        set(v) = sp.edit().putString("token", v.trim()).apply()

    /** Send only the app name, not the message text. */
    var hideContent: Boolean
        get() = sp.getBoolean("hideContent", false)
        set(v) = sp.edit().putBoolean("hideContent", v).apply()

    /** Package names to never forward, one per line. */
    var blockedText: String
        get() = sp.getString("blocked", "")!!
        set(v) = sp.edit().putString("blocked", v).apply()

    val blockedApps: Set<String>
        get() = blockedText.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun randomTopic(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        return "fwd-" + (1..20).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }
}
