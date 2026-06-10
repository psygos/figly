package app.figly.probe

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.figly.data.FigRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking

/**
 * The optional 21:30 reminder — default OFF. Probe asks by existing;
 * it never nags. No exclamation marks live here.
 */
object Reminder {

    private const val CHANNEL = "probe"
    private const val REQUEST = 2130

    fun schedule(context: Context) {
        val repo = FigRepository.get(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        am.cancel(pi)
        if (!repo.reminderOn) return

        val now = ZonedDateTime.now()
        var at = ZonedDateTime.of(LocalDateTime.of(LocalDate.now(), LocalTime.of(21, 30)), now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        am.setInexactRepeating(
            AlarmManager.RTC,
            at.toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            pi,
        )
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notifyNow(context: Context) {
        val repo = FigRepository.get(context)
        if (!repo.reminderOn) return
        val today = LocalDate.now()
        runBlocking {
            if (repo.todayRow() != null) return@runBlocking // sealed; stay quiet
            val taken = repo.draft(today).answeredCount
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "probe", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "readings open"
                    setShowBadge(false)
                },
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_probe_mark)
                .setContentTitle("readings open")
                .setContentText(if (taken == 0) "five to take" else "$taken of 5 taken")
                .setSilent(true)
                .setAutoCancel(true)
                .build()
            nm.notify(REQUEST, n)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.notifyNow(context)
    }
}
