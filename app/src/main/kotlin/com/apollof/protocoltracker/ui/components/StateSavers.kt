package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import kotlinx.serialization.json.Json

/** Keep a sheet's dialog content in its caller's saved state, together with the open-sheet target. */
@Composable
internal fun rememberSaveableSheetContent(
    content: @Composable ColumnScope.() -> Unit,
): @Composable ColumnScope.() -> Unit {
    val holder = rememberSaveableStateHolder()
    return { holder.SaveableStateProvider("content") { content() } }
}

/** Keep serializable sheet targets as Bundle-compatible strings, including their edit snapshots. */
internal inline fun <reified T> jsonSaver(): Saver<T, String> = Saver(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<T>(it) },
)

/** Wrap null so clearing an existing value restores null instead of running its initializer again. */
internal fun <T> nullableSaver(): Saver<T?, Any> = listSaver<T?, T?>(
    save = { listOf(it) }, restore = { it.single() },
)

internal inline fun <reified V> stateMapSaver(): Saver<SnapshotStateMap<String, V>, String> = Saver(
    save = { Json.encodeToString(it.toMap()) },
    restore = { Json.decodeFromString<Map<String, V>>(it).toList().toMutableStateMap() },
)
