package com.w11mobile.service

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.w11mobile.R
import com.w11mobile.core.environment.EnvironmentSetupOrchestrator
import com.w11mobile.core.environment.QemuBoot0001AutoKey
import com.w11mobile.core.environment.QemuEfiShellAutoKey
import com.w11mobile.core.environment.QemuMonitorClient
import com.w11mobile.core.environment.QemuProcessSession
import com.w11mobile.core.environment.QemuRuntimeEvents
import com.w11mobile.core.environment.SetupPreferences
import com.w11mobile.ui.MainActivity

/**
 * Runs libqemu.so in a foreground service so Android LMK does not kill the VM when the UI is backgrounded.
 */
class QemuService : Service() {

    private val binder = LocalBinder()
    private var qemuThread: Thread? = null

    @Volatile
    private var launchInProgress = false

    @Volatile
    private var userStopRequested = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var orchestrator: EnvironmentSetupOrchestrator? = null

    inner class LocalBinder : Binder() {
        fun getService(): QemuService = this@QemuService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        QemuServiceController.registerService(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_QEMU -> {
                userStopRequested = false
                val isoBootMode = intent.getBooleanExtra(EXTRA_ISO_BOOT_MODE, false)
                startForegroundSession(isoBootMode)
            }
            ACTION_STOP_QEMU -> stopByUser()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        QemuServiceController.unregisterService(this)
        super.onDestroy()
    }

    fun isLaunchInProgress(): Boolean = launchInProgress

    fun isQemuAlive(): Boolean = QemuProcessSession.isAlive()

    private fun startForegroundSession(isoBootMode: Boolean) {
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
        if (launchInProgress || qemuThread?.isAlive == true) {
            return
        }
        launchInProgress = true
        qemuThread = Thread(
            { runQemuSession(isoBootMode) },
            "qemu-service",
        ).apply {
            isDaemon = false
            start()
        }
    }

    private fun stopByUser() {
        userStopRequested = true
        QemuBoot0001AutoKey.reset()
        QemuEfiShellAutoKey.reset()
        QemuProcessSession.destroyActiveProcess()
        QemuMonitorClient.closeSharedSession()
        qemuThread?.interrupt()
        QemuRuntimeEvents.publishStatus("Windows / QEMU зупинено користувачем")
        QemuRuntimeEvents.publishSessionEnded(QemuProcessSession.FORCED_STOP_EXIT_CODE)
        launchInProgress = false
        stopForegroundSession()
    }

    private fun runQemuSession(@Suppress("UNUSED_PARAMETER") isoBootMode: Boolean) {
        val orchestrator = obtainOrchestrator()
        try {
            val result = kotlinx.coroutines.runBlocking {
                orchestrator.launchWindows()
            }
            if (userStopRequested) {
                return
            }
            if (result.exitCode != 0) {
                QemuRuntimeEvents.publishFatal(
                    "QEMU завершився з кодом ${result.exitCode}",
                )
            }
            QemuRuntimeEvents.publishSessionEnded(result.exitCode)
        } catch (error: Exception) {
            if (!userStopRequested) {
                QemuRuntimeEvents.publishFatal(error.message ?: "Помилка запуску QEMU")
                QemuRuntimeEvents.publishSessionEnded(QemuProcessSession.FORCED_STOP_EXIT_CODE)
            }
        } finally {
            launchInProgress = false
            if (!userStopRequested) {
                stopForegroundSession()
            }
        }
    }

    private fun obtainOrchestrator(): EnvironmentSetupOrchestrator {
        orchestrator?.let { return it }
        val app = application as Application
        val preferences = SetupPreferences(app)
        return EnvironmentSetupOrchestrator(
            application = app,
            preferences = preferences,
            onStepChanged = { _ -> },
            onProgressChanged = { _, _ -> },
            onLog = { line -> QemuRuntimeEvents.publishTerminalLine(line) },
        ).also { orchestrator = it }
    }

    private fun stopForegroundSession() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "w11mobile:QemuService",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.qemu_service_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.qemu_service_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, QemuService::class.java).apply {
                action = ACTION_STOP_QEMU
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.qemu_service_notification))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_windows),
                stopIntent,
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START_QEMU = "com.w11mobile.action.START_QEMU"
        const val ACTION_STOP_QEMU = "com.w11mobile.action.STOP_QEMU"
        const val EXTRA_ISO_BOOT_MODE = "iso_boot_mode"

        private const val CHANNEL_ID = "qemu_foreground"
        private const val NOTIFICATION_ID = 1001
    }
}
