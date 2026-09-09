package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 저장소 인스턴스를 앱 전역에서 하나만 유지한다.
 * 화면(Compose)과 백그라운드 워커가 같은 인스턴스를 봐야 알림 중복이 안 생긴다.
 */
object AppGraph {

    @Volatile
    private var repository: NetworkPaperRepository? = null

    fun repository(context: Context): NetworkPaperRepository {
        repository?.let { return it }
        return synchronized(this) {
            repository ?: NetworkPaperRepository(
                PaperStore(context.applicationContext)
            ).also { repository = it }
        }
    }
}

object Notifications {

    const val CHANNEL_NEW_PAPERS = "new_papers"

    /** 기본 조용 시간. 이 구간에는 알림을 보류하고 다음 사이클에 합산해 보낸다. */
    private const val QUIET_START_HOUR = 23
    private const val QUIET_END_HOUR = 8

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_NEW_PAPERS) != null) return
        val channel = NotificationChannel(
            CHANNEL_NEW_PAPERS,
            "새 논문",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "관심 분야에 새 논문이 올라오면 알려줍니다."
        manager.createNotificationChannel(channel)
    }

    fun isQuietHour(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR
    }

    fun notifyNewPapers(context: Context, papers: List<Paper>) {
        if (papers.isEmpty()) return
        ensureChannel(context)

        val fields = papers.flatMap { it.fields }
            .filter { it != Field.OTHER }
            .distinct()
        val fieldText = when {
            fields.isEmpty() -> "관심 분야"
            fields.size == 1 -> fields.first().labelKo
            else -> fields.first().labelKo + " 외 " + (fields.size - 1) + "개 분야"
        }

        val preview = papers.take(3).joinToString("\n") { "· " + it.displayTitle }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_NEW_PAPERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("새 논문 " + papers.size + "건 · " + fieldText)
            .setContentText(papers.first().displayTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (pendingIntent != null) builder.setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS 권한이 아직 없을 때. 조용히 넘긴다.
            Log.w(TAG, "notification blocked: " + e.message)
        }
    }

    private const val NOTIFICATION_ID = 1001
}

/**
 * 1시간마다 돌면서 새 논문을 받아오고, 관심 분야에 걸리는 게 있으면 로컬 알림을 띄운다.
 * 서버 없이 기기에서만 도는 구조라 FCM이 필요 없다.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = AppGraph.repository(applicationContext)

        val outcome = repository.refresh()
        if (outcome.isFailure) {
            Log.w(TAG, "sync failed: " + outcome.exceptionOrNull()?.message)
            return Result.retry()
        }

        if (Notifications.isQuietHour()) {
            Log.i(TAG, "quiet hour - holding notification")
            return Result.success()
        }

        val pending = repository.consumeUnnotified()
        if (pending.isNotEmpty()) {
            Notifications.notifyNewPapers(applicationContext, pending)
        }
        return Result.success()
    }
}

object PaperRadarWork {

    private const val UNIQUE_NAME = "paper-radar-sync"

    fun schedule(context: Context) {
        Notifications.ensureChannel(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "background sync scheduled (1h)")
    }
}
