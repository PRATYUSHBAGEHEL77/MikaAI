package com.mika.assistant.agent

import android.Manifest
import android.provider.AlarmClock
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject

class ToolExecutor(private val context: Context, private val safety: SafetyGate) {
    fun execute(name: String, input: JSONObject): String = runCatching {
        when (name) {
            "open_app" -> openApp(input.optString("app_name"))
            "set_alarm" -> setAlarm(input.optInt("hour", -1), input.optInt("minute", -1), input.optString("label"))
            "call_contact" -> "Call request must pass through the confirmation card."
            "send_sms" -> "SMS request must pass through confirmation; Stage 1 does not send SMS."
            "search_web" -> searchWeb(input.optString("query"))
            "open_settings" -> openSettings(input.optString("setting"))
            "media_control" -> mediaControl(input.optString("action"))
            "set_volume" -> setVolume(input.optInt("level", -1))
            "flashlight" -> "Flashlight control is not enabled in this build; use Quick Settings."
            else -> "Tool is not implemented: $name"
        }
    }.getOrElse { "Action failed: ${it.message ?: "unknown error"}" }

    fun executeConfirmed(action: PendingAction): String {
        if (action.tool != "call_contact") return "This confirmed action is not available in the current build."
        val input = JSONObject(action.inputJson)
        val number = resolveNumber(input.optString("name_or_number"))
            ?: return "I couldn't find a phone number. Grant Contacts permission or use a phone number."
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "The dialer opened with the number. No call was placed."
    }

    private fun openApp(name: String): String {
        require(name.isNotBlank()) { "App name is empty." }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).firstOrNull {
            it.loadLabel(pm).toString().equals(name, true)
        } ?: pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).firstOrNull {
            it.loadLabel(pm).toString().contains(name, true)
        } ?: return "I couldn't find an installed app called $name."
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "Android did not provide a launch action for $name."
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return "Android launched ${match.loadLabel(pm)}."
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        require(hour in 0..23 && minute in 0..59) { "Time is invalid." }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Mika alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        require(intent.resolveActivity(context.packageManager) != null) { "No compatible clock app found." }
        context.startActivity(intent)
        return "Alarm creation screen opened. Save it in your Clock app to finish."
    }

    private fun resolveNumber(nameOrNumber: String): String? {
        val query = nameOrNumber.trim()
        if (query.count { it.isDigit() } >= 5) return query
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val n = cursor.getString(0) ?: continue
                if (n.equals(query, true) || n.contains(query, true)) return cursor.getString(1)
            }
        }
        return null
    }

    private fun searchWeb(query: String): String {
        require(query.isNotBlank()) { "Search query is empty." }
        val uri = Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q", query).build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opened a web search for $query."
    }

    private fun openSettings(which: String): String {
        val action = when (which.lowercase()) {
            "wifi" -> Settings.Panel.ACTION_WIFI
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "internet" -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opened the relevant Android settings panel."
    }

    private fun mediaControl(action: String): String {
        val key = when (action.lowercase()) {
            "play", "pause", "play_pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "Unknown media action."
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
        return "Sent media command: $action."
    }

    private fun setVolume(level: Int): String {
        require(level in 0..100) { "Volume must be between 0 and 100." }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * level / 100f).toInt(), 0)
        return "Media volume set to approximately $level percent."
    }
}
