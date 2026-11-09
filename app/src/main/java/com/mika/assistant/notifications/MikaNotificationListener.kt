package com.mika.assistant.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MikaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Notifications are not forwarded to Claude automatically.
        // Read only when the user explicitly asks and has enabled Notification Access.
    }

    fun visibleNotificationSummaries(): List<String> {
        return activeNotifications.orEmpty().take(30).mapNotNull { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) null
            else "${sbn.packageName}: ${title.take(100)} — ${text.take(180)}"
        }
    }
}
