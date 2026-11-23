package com.example.il2patch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * PatchService — Foreground service for long jobs.
 *
 * Why it exists:
 *  - Analyze scans ALL classes + methods (10-60s on big games)
 *  - Build patched APK = extract -> patch .so -> rebuild -> sign (1-3 min)
 * Without a foreground service, Android kills the app if the user
 * switches apps / locks the screen, and the job dies halfway.
 *
 * What it does:
 *  - Shows a progress notification: "Il2Patch — Analyzing... 45%"
 *  - Keeps the job alive while it runs
 *  - Stops itself + removes the notification when done
 *
 * What it does NOT do:
 *  - No patching itself (Patcher.kt / Asm.kt do that)
 *  - No UI, no internet, never runs forever
 *
 * Manifest: AndroidManifest.xml ->
 *   <service android:name=".PatchService"
 *            android:foregroundServiceType="dataSync" />
 *
 * Usage from MainActivity:
 *   PatchService.start(this, PatchService.JOB_ANALYZE)
 *   PatchService.updateProgress(this, 45, "Scanning methods...")
 *   PatchService.stop(this)
 */
class PatchService : Service() {

    companion object {
        private const val TAG = "PatchService"
        private const val CHANNEL_ID = "il2patch_jobs"
        private const val NOTIFICATION_ID = 1001

        // Actions
        const val ACTION_START = "com.example.il2patch.ACTION_START"
        const val ACTION_PROGRESS = "com.example.il2patch.ACTION_PROGRESS"
        const val ACTION_STOP = "com.example.il2patch.ACTION_STOP"

        // Extras
        const val EXTRA_JOB = "job"
        const val EXTRA_PROGRESS = "progress" // 0..100, -1 = indeterminate
        const val EXTRA_MESSAGE = "message"

        // Job types
        const val JOB_ANALYZE = "analyze"
        const val JOB_BUILD_APK = "build_apk"
        const val JOB_PATCH_LIST = "patch_list"

        /** Start the service for a job. Call BEFORE the long job starts. */
        fun start(context: Context, job: String, message: String = "") {
            val intent = Intent(context, PatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_JOB, job)
                putExtra(EXTRA_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Update progress bar + message. progress = 0..100, -1 = spinning. */
        fun updateProgress(context: Context, progress: Int, message: String) {
            val intent = Intent(context, PatchService::class.java).apply {
                action = ACTION_PROGRESS
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_MESSAGE, message)
            }
            context.startService(intent)
        }

        /** Stop the service. Call AFTER the job finishes / fails / is cancelled. */
        fun stop(context: Context) {
            val intent = Intent(context, PatchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var currentJob: String = JOB_ANALYZE
    private var currentProgress: Int = -1
    private var currentMessage: String = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentJob = intent.getStringExtra(EXTRA_JOB) ?: JOB_ANALYZE
                currentMessage = intent.getStringExtra(EXTRA_MESSAGE) ?: jobTitle(currentJob)
                currentProgress = -1
                startForeground(NOTIFICATION_ID, buildNotification())
                Log.i(TAG, "Started job=$currentJob")
            }
            ACTION_PROGRESS -> {
                currentProgress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                val msg = intent.getStringExtra(EXTRA_MESSAGE)
                if (!msg.isNullOrEmpty()) currentMessage = msg
                // Update the notification in place
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopped job=$currentJob")
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // If Android kills us, do NOT restart automatically — the job is dead anyway,
        // MainActivity will show "job interrupted" and the user can retry.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------

    private fun jobTitle(job: String): String = when (job) {
        JOB_ANALYZE -> "Analyzing game..."
        JOB_BUILD_APK -> "Building patched APK..."
        JOB_PATCH_LIST -> "Applying patch list..."
        else -> "Working..."
    }

    private fun buildNotification(): Notification {
        // Tap notification -> back to MainActivity
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Il2Patch — ${jobTitle(currentJob)}")
            .setContentText(if (currentMessage.isEmpty()) jobTitle(currentJob) else currentMessage)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (currentProgress in 0..100) {
            builder.setProgress(100, currentProgress, false)
        } else {
            builder.setProgress(0, 0, true) // spinning
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Il2Patch jobs",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for Analyze and Build patched APK"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
