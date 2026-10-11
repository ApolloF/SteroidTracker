package com.apollof.protocoltracker

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The upkeep every start runs (preset refresh and migration, log key repair). Each step runs on its own, so one
 * failure (full disk, an undecodable row) neither crashes the start nor skips the other steps; failed steps stay
 * in [failed] for Today to show.
 */
class StartupUpkeep(private val steps: List<Step>) {
    class Step(val label: String, val run: suspend () -> Unit)

    private val _failed = MutableStateFlow<List<String>>(emptyList())

    /** Labels of the steps that failed in this process, in run order. */
    val failed: StateFlow<List<String>> = _failed

    suspend fun run(steps: List<Step> = this.steps) {
        steps.forEach { step ->
            try {
                step.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StartupUpkeep", "Startup step failed: ${step.label}", e)
                _failed.update { it + step.label }
            }
        }
    }
}
