package com.apollof.protocoltracker.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.apollof.protocoltracker.MainActivity
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.container
import com.apollof.protocoltracker.domain.schedule.AgendaStatus
import com.apollof.protocoltracker.domain.schedule.WidgetLine
import com.apollof.protocoltracker.domain.schedule.agendaLogsFrom
import com.apollof.protocoltracker.domain.schedule.buildAgenda
import com.apollof.protocoltracker.domain.schedule.nextDose
import com.apollof.protocoltracker.domain.schedule.widgetLines
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.ui.theme.materialScheme
import com.apollof.protocoltracker.ui.theme.trackerColors

private enum class Mark { DUE, MISSED, TAKEN, SKIPPED }

private data class WidgetRow(val key: String, val name: String, val detail: String, val mark: Mark) {
    val checkable: Boolean get() = mark == Mark.DUE || mark == Mark.MISSED
}

private data class WidgetState(val rows: List<WidgetRow>, val next: WidgetRow?, val done: Int, val total: Int)

class TodayWidget : GlanceAppWidget() {
    // One row (2x1 and wider single rows), the next dose under a header (2x2), the day's list (3x2 and up).
    override val sizeMode = SizeMode.Responsive(setOf(ROW, CARD, LIST))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val now = c.clock()
        val protocol = c.repository.protocolNow()
        val zone = c.zone()
        val prefs = c.settings.current()
        val logs = c.repository.logsSinceNow(agendaLogsFrom(now, zone, prefs.slotTimes))
        val agenda = buildAgenda(protocol.phases, protocol.items, logs, now, zone, c.repository.anchorsNow(), prefs.slotTimes)
        val lines = agenda.widgetLines { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
        fun row(line: WidgetLine): WidgetRow? {
            val occ = line.entry.occurrence ?: return null
            val compound = protocol.compounds[occ.item.compoundId] ?: return null
            val mark = when (line.entry.status) {
                AgendaStatus.MISSED -> Mark.MISSED
                AgendaStatus.PENDING -> Mark.DUE
                AgendaStatus.TAKEN -> Mark.TAKEN
                AgendaStatus.SKIPPED -> Mark.SKIPPED
            }
            val status = when (mark) {
                Mark.MISSED -> "Missed · "
                Mark.TAKEN -> "Taken · "
                Mark.SKIPPED -> "Skipped · "
                Mark.DUE -> ""
            }
            val dose = describeDose(occ.dose, compound.baseUnit, occ.item.formulation)
            return WidgetRow(occ.key, compound.displayName, "$status${line.label} · $dose", mark)
        }
        val state = WidgetState(lines.mapNotNull(::row), lines.nextDose()?.let(::row), agenda.doneToday, agenda.scheduledToday)
        // Wallpaper colours on Android 12+, like the launcher's own widgets; before that the app's colour scheme.
        val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) null else ColorProviders(
            light = materialScheme(trackerColors(prefs.palette, dark = false)),
            dark = materialScheme(trackerColors(prefs.palette, dark = true, pureBlack = prefs.pureBlack)),
        )
        provideContent {
            if (colors == null) GlanceTheme { Content(state) } else GlanceTheme(colors = colors) { Content(state) }
        }
    }

    @Composable
    private fun Content(state: WidgetState) {
        val size = LocalSize.current
        Box(modifier = GlanceModifier.fillMaxSize().widgetCard()) {
            when {
                size.width >= LIST.width && size.height >= LIST.height -> ListLayout(state)
                size.height >= CARD.height -> CardLayout(state)
                else -> RowLayout(state)
            }
        }
    }

    @Composable
    private fun ListLayout(state: WidgetState) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(start = 8.dp, top = 12.dp, end = 12.dp)) {
            Header(state)
            Spacer(GlanceModifier.height(4.dp))
            if (state.rows.isEmpty()) {
                Text(
                    "Nothing left today", modifier = GlanceModifier.padding(horizontal = 8.dp),
                    style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(state.rows, itemId = { it.key.hashCode().toLong() }) { row -> DoseRow(row) }
                }
            }
        }
    }

    @Composable
    private fun CardLayout(state: WidgetState) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(start = 8.dp, top = 12.dp, end = 12.dp, bottom = 8.dp)) {
            Header(state)
            Spacer(GlanceModifier.defaultWeight())
            NextOrDone(state)
        }
    }

    @Composable
    private fun RowLayout(state: WidgetState) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.defaultWeight()) { NextOrDone(state) }
            Text(
                "${state.done}/${state.total}", modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.primary),
            )
        }
    }

    @Composable
    private fun NextOrDone(state: WidgetState) {
        val next = state.next
        if (next != null) {
            DoseRow(next)
        } else {
            val detail = if (state.total == 0) "No doses planned" else "${state.done} of ${state.total} done"
            DoseRow(WidgetRow("", "Nothing left today", detail, Mark.TAKEN))
        }
    }

    @Composable
    private fun Header(state: WidgetState) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Today", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "${state.done} of ${state.total} done", maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = GlanceTheme.colors.primary),
            )
        }
    }

    @Composable
    private fun DoseRow(row: WidgetRow) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusMark(row)
            Spacer(GlanceModifier.width(4.dp))
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Text(row.name, maxLines = 1, style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = GlanceTheme.colors.onSurface))
                Text(
                    row.detail, maxLines = 1,
                    style = TextStyle(fontSize = 12.sp, color = if (row.mark == Mark.MISSED) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }

    /** Taken: filled accent with a check. Due: accent ring. Missed: error ring. Skipped: muted ring. The text says the same. */
    @Composable
    private fun StatusMark(row: WidgetRow) {
        val target = if (row.checkable) {
            GlanceModifier.clickable(actionRunCallback<CheckAction>(actionParametersOf(CheckAction.KEY to row.key)))
                .semantics { contentDescription = "Mark ${row.name} taken" }
        } else {
            GlanceModifier
        }
        Box(modifier = GlanceModifier.size(48.dp).then(target), contentAlignment = Alignment.Center) {
            if (row.mark == Mark.TAKEN) {
                Glyph(R.drawable.widget_check_dot, GlanceTheme.colors.primary, 28)
                Glyph(R.drawable.widget_check, GlanceTheme.colors.onPrimary, 18)
            } else {
                val tint = when (row.mark) {
                    Mark.MISSED -> GlanceTheme.colors.error
                    Mark.SKIPPED -> GlanceTheme.colors.outline
                    else -> GlanceTheme.colors.primary
                }
                Glyph(R.drawable.widget_check_ring, tint, 28)
            }
        }
    }

    @Composable
    private fun Glyph(drawable: Int, tint: ColorProvider, sizeDp: Int) {
        Image(ImageProvider(drawable), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = GlanceModifier.size(sizeDp.dp))
    }

    /** The launcher's widget card: system corner radius and android:id/background, so launchers can restyle it. */
    @Composable
    private fun GlanceModifier.widgetCard(): GlanceModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appWidgetBackground().background(GlanceTheme.colors.widgetBackground).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        appWidgetBackground().background(ImageProvider(R.drawable.widget_background), colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant))
    }

    companion object {
        private val ROW = DpSize(110.dp, 40.dp)
        private val CARD = DpSize(110.dp, 100.dp)
        private val LIST = DpSize(180.dp, 150.dp)

        suspend fun refresh(context: Context) = TodayWidget().updateAll(context)
    }
}

class CheckAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY] ?: return
        context.container.doseActions.takeKeys(listOf(key))
        TodayWidget().update(context, glanceId)
    }

    companion object {
        val KEY = ActionParameters.Key<String>("occurrence_key")
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
