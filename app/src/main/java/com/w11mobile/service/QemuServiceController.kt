package com.w11mobile.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.w11mobile.core.environment.QemuMonitorClient
import com.w11mobile.core.environment.QemuProcessSession

object QemuServiceController {

    @Volatile
    private var boundService: QemuService? = null

    fun registerService(service: QemuService) {
        boundService = service
    }

    fun unregisterService(service: QemuService) {
        if (boundService === service) {
            boundService = null
        }
    }

    fun getService(): QemuService? = boundService

    fun isQemuRunning(): Boolean {
        if (QemuProcessSession.isAlive()) {
            return true
        }
        if (boundService?.isLaunchInProgress() == true) {
            return true
        }
        return QemuProcessSession.isLaunchStarted() &&
            QemuProcessSession.resolvedExitCodeOrNull() == null
    }

    fun startLaunch(context: Context, isoBootMode: Boolean) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, QemuService::class.java).apply {
            action = QemuService.ACTION_START_QEMU
            putExtra(QemuService.EXTRA_ISO_BOOT_MODE, isoBootMode)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun bind(context: Context, connection: ServiceConnection) {
        context.applicationContext.bindService(
            Intent(context.applicationContext, QemuService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun unbind(context: Context, connection: ServiceConnection) {
        try {
            context.applicationContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // Already unbound.
        }
    }

    fun stopLaunch(context: Context) {
        val appContext = context.applicationContext
        QemuProcessSession.destroyActiveProcess()
        QemuMonitorClient.closeSharedSession()
        appContext.startService(
            Intent(appContext, QemuService::class.java).apply {
                action = QemuService.ACTION_STOP_QEMU
            },
        )
    }

    fun createConnection(
        onConnected: (QemuService) -> Unit = {},
        onDisconnected: () -> Unit = {},
    ): ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as QemuService.LocalBinder).getService()
                boundService = service
                onConnected(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
                onDisconnected()
            }
        }
}
