package com.example.missioncontrol.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TermuxBuildOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val internalError: Int,
    val errorMessage: String
) {
    val succeeded: Boolean get() = exitCode == 0 && internalError == -1
}

object TermuxBuildState {
    private val _latest = MutableStateFlow<TermuxBuildOutput?>(null)
    val latest = _latest.asStateFlow()
    internal fun publish(output: TermuxBuildOutput) { _latest.value = output }
}

/** Receives the explicit result callback from Termux RUN_COMMAND (Termux >= 0.109). */
class TermuxBuildResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val result = intent?.getBundleExtra("result") ?: return
        TermuxBuildState.publish(
            TermuxBuildOutput(
                exitCode = result.getInt("exitCode", -1),
                stdout = result.getString("stdout").orEmpty().takeLast(50000),
                stderr = result.getString("stderr").orEmpty().takeLast(50000),
                internalError = result.getInt("err", -1),
                errorMessage = result.getString("errmsg").orEmpty()
            )
        )
    }
}
