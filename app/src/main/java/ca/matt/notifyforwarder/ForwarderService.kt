package ca.matt.notifyforwarder

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Listens to every notification posted on this Android phone and forwards
 * the title + text to an ntfy topic. The iPhone subscribes to that topic in
 * the ntfy app, so it shows a plain notification. Nothing is sent back:
 * replies are done on the Android phone itself.
 */
class ForwarderService : NotificationListenerService() {

    private val executor = Executors.newSingleThreadExecutor()

    // Remembers the last text forwarded per notification key, so apps that
    // re-post the same notification (progress, re-sorting) don't spam the iPhone.
    private val lastSent = object : LinkedHashMap<String, String>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        if (!prefs.enabled || prefs.topic.isBlank()) return
        if (sbn.packageName == packageName) return

        val n = sbn.notification
        // Skip things that aren't real alerts.
        if (sbn.isOngoing) return                                   // music, navigation, downloads
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return // "3 new messages" summaries
        if (n.category == Notification.CATEGORY_PROGRESS) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return
        if (sbn.packageName in prefs.blockedApps) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val signature = "$title|$text"
        synchronized(lastSent) {
            if (lastSent[sbn.key] == signature) return
            lastSent[sbn.key] = signature
        }

        val appName = appLabel(sbn.packageName)
        val body = if (prefs.hideContent) "New notification" else text.ifBlank { title }
        val heading = if (prefs.hideContent) appName else "$appName: $title".trimEnd(':', ' ')

        executor.execute { send(prefs.server, prefs.topic, prefs.token, heading, body) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(lastSent) { lastSent.remove(sbn.key) }
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun send(server: String, topic: String, token: String, title: String, message: String) {
        val url = URL(server.trimEnd('/') + "/" + URLEncoder.encode(topic, "UTF-8"))
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            // Title header must be ASCII-safe; ntfy decodes RFC 2047 encoded words.
            conn.setRequestProperty("Title", encodeHeader(title.take(200)))
            conn.setRequestProperty("Tags", "iphone")
            if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(message.take(3000).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) Log.w(TAG, "ntfy returned $code")
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Forward failed: ${e.message}")
        }
    }

    private fun encodeHeader(s: String): String =
        if (s.all { it.code in 32..126 }) s
        else "=?UTF-8?B?" + android.util.Base64.encodeToString(
            s.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP) + "?="

    companion object { private const val TAG = "NotifyForwarder" }
}
