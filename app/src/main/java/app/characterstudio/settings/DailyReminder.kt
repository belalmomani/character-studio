package app.characterstudio.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.characterstudio.CharacterStudioApp
import app.characterstudio.MainActivity
import app.characterstudio.R
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** إشعار محلي يومي واحد (WorkManager). لا يستخدم أي خادم ولا FCM. */
object DailyReminder {
    private const val WORK_NAME = "daily_reminder"
    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** replace=true عند تغيير الإعدادات؛ false عند بدء التطبيق (يحافظ على الجدول القائم). */
    fun schedule(context: Context, s: ReminderSettings, policy: ExistingWorkPolicy) {
        val wm = WorkManager.getInstance(context)
        if (!s.enabled) { wm.cancelUniqueWork(WORK_NAME); return }
        val delay = ReminderMath.delayMillis(ZonedDateTime.now(), s.hour, s.minute)
        wm.enqueueUniqueWork(
            WORK_NAME, policy,
            OneTimeWorkRequestBuilder<Worker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    class Worker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val settings = (ctx.applicationContext as CharacterStudioApp).container.settings.state.value
            if (!settings.enabled) return Result.success()
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                val open = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(ctx.getString(R.string.notif_title))
                    .setContentText(ctx.getString(R.string.notif_body))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                @Suppress("MissingPermission") // areNotificationsEnabled() تحقق من الإذن أعلاه
                NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
            }
            schedule(ctx, settings, ExistingWorkPolicy.APPEND_OR_REPLACE) // جدولة اليوم التالي
            return Result.success()
        }
    }
}
