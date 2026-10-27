package ca.matt.notifyforwarder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.app.NotificationManagerCompat
import kotlin.concurrent.thread

/** Simple settings screen, built in code so there are no layout files to manage. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad, 0, 0) }
        fun field(v: String, multi: Boolean = false) = EditText(this).apply {
            setText(v)
            inputType = if (multi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            if (multi) minLines = 3
        }

        status = TextView(this).apply { textSize = 16f }
        col.addView(status)

        col.addView(Button(this).apply {
            text = "Grant notification access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        val enabled = Switch(this).apply { text = "Forwarding on"; isChecked = prefs.enabled }
        col.addView(enabled)

        col.addView(label("ntfy server"))
        val server = field(prefs.server); col.addView(server)

        col.addView(label("Topic (subscribe to this exact name on the iPhone)"))
        val topic = field(prefs.topic); col.addView(topic)

        col.addView(label("Access token (optional)"))
        val token = field(prefs.token); col.addView(token)

        val hide = CheckBox(this).apply {
            text = "Hide message text (send app name only)"; isChecked = prefs.hideContent
        }
        col.addView(hide)

        col.addView(label("Never forward these apps (package names, one per line)"))
        val blocked = field(prefs.blockedText, multi = true); col.addView(blocked)

        fun save() {
            prefs.enabled = enabled.isChecked
            prefs.server = server.text.toString()
            prefs.topic = topic.text.toString()
            prefs.token = token.text.toString()
            prefs.hideContent = hide.isChecked
            prefs.blockedText = blocked.text.toString()
        }

        col.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { save(); toast("Saved") }
        })

        col.addView(Button(this).apply {
            text = "Copy topic"
            setOnClickListener {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("topic", topic.text))
                toast("Topic copied")
            }
        })

        col.addView(Button(this).apply {
            text = "Send test to iPhone"
            setOnClickListener {
                save()
                thread {
                    val ok = runCatching {
                        val url = java.net.URL(prefs.server.trimEnd('/') + "/" + prefs.topic)
                        val c = url.openConnection() as java.net.HttpURLConnection
                        c.requestMethod = "POST"; c.doOutput = true
                        c.setRequestProperty("Title", "Notify Forwarder")
                        if (prefs.token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer ${prefs.token}")
                        c.outputStream.use { it.write("Test from your Android phone".toByteArray()) }
                        c.responseCode in 200..299
                    }.getOrDefault(false)
                    runOnUiThread { toast(if (ok) "Test sent" else "Test failed - check server/topic") }
                }
            }
        })

        setContentView(ScrollView(this).apply {
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        val granted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        status.text = if (granted) "✅ Notification access granted"
                      else "⚠️ Notification access NOT granted — tap the button below"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
