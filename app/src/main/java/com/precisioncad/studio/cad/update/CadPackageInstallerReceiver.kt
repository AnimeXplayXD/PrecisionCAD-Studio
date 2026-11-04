package com.precisioncad.studio.cad.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Phase 12.8 — BroadcastReceiver for Android `PackageInstaller.Session` status callbacks.
 * Forwards `STATUS_PENDING_USER_ACTION` confirmation intents and terminal install results
 * to [CadInstallSessionCallbacks] so the UI state machine updates deterministically.
 */
class CadPackageInstallerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_COMMIT) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    CadInstallSessionCallbacks.onUserConfirmationRequired(sessionId, confirmationIntent)
                    runCatching { context.startActivity(confirmationIntent) }
                        .onFailure { err ->
                            CadInstallSessionCallbacks.onInstallResult(
                                sessionId = sessionId,
                                succeeded = false,
                                cancelled = false,
                                message = "Unable to launch system package installer confirmation: ${err.message}"
                            )
                        }
                } else {
                    CadInstallSessionCallbacks.onInstallResult(
                        sessionId = sessionId,
                        succeeded = false,
                        cancelled = false,
                        message = "PackageInstaller did not provide a confirmation intent."
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                CadInstallSessionCallbacks.onInstallResult(
                    sessionId = sessionId,
                    succeeded = true,
                    cancelled = false,
                    message = "Update installed successfully."
                )
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                CadInstallSessionCallbacks.onInstallResult(
                    sessionId = sessionId,
                    succeeded = false,
                    cancelled = true,
                    message = statusMessage.ifBlank { "Installation cancelled by user." }
                )
            }

            else -> {
                CadInstallSessionCallbacks.onInstallResult(
                    sessionId = sessionId,
                    succeeded = false,
                    cancelled = false,
                    message = statusMessage.ifBlank { "Package installation failed (status code $status)." }
                )
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_COMMIT = "com.precisioncad.studio.action.PACKAGE_INSTALL_COMMIT"
    }
}

object CadInstallSessionCallbacks {
    @Volatile
    var listener: Listener? = null

    interface Listener {
        fun onUserConfirmationRequired(sessionId: Int, confirmationIntent: Intent)
        fun onInstallResult(sessionId: Int, succeeded: Boolean, cancelled: Boolean, message: String)
    }

    fun onUserConfirmationRequired(sessionId: Int, confirmationIntent: Intent) {
        listener?.onUserConfirmationRequired(sessionId, confirmationIntent)
    }

    fun onInstallResult(sessionId: Int, succeeded: Boolean, cancelled: Boolean, message: String) {
        listener?.onInstallResult(sessionId, succeeded, cancelled, message)
    }
}
