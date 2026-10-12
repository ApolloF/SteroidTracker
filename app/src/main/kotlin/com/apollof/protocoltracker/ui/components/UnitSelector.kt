package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

/** Dose units that can be converted for a compound with the given formulation data. */
fun unitsFor(base: BaseUnit, formulation: Formulation): List<DoseUnit> = buildList {
    if (base == BaseUnit.MG) { add(DoseUnit.MG); add(DoseUnit.MCG) } else add(DoseUnit.IU)
    if (formulation.perMl != null) add(DoseUnit.ML)
    if (base == BaseUnit.MG && formulation.perTablet != null) add(DoseUnit.TABLET)
}

/**
 * One choice of a few: a segmented row, or (POL-25) radio rows when a label would not fit its segment even at
 * [TrackerType.fitMin] (a long word with a large font, such as "Collapsible" in a row of four at 130 %).
 * With [narrowTitle], a row that does not fit becomes a one-line [MenuChoice] instead, for controls above a chart.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    narrowTitle: String? = null,
    onSelect: (T) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val smallest = TrackerType.label.copy(fontSize = TrackerType.fitMin)
        // Each segment pads its label by 12 dp a side, and the selected one adds an 18 dp check and an 8 dp gap.
        val room = constraints.maxWidth / options.size - with(LocalDensity.current) { SEGMENT_CHROME.roundToPx() }
        if (options.all { measurer.measure(label(it), smallest).size.width <= room }) SegmentedRow(options, selected, label, onSelect)
        else if (narrowTitle != null) MenuChoice(narrowTitle, options, selected, label, onSelect = onSelect)
        else RadioRows(options, selected, label, onSelect)
    }
}

/** One choice of a few as a single line, "[title]  value ▾", that opens a menu; the chosen item carries a check. */
@Composable
fun <T> MenuChoice(title: String, options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val c = Tracker.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FitRow(
            start = { Text(title, style = TrackerType.label, color = c.muted) },
            end = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label(selected), style = TrackerType.label, color = c.ink)
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = c.ink)
                }
            },
            modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(Radii.medium))
                .clickable(role = Role.DropdownList, onClickLabel = "Change ${title.lowercase()}") { open = true }
                .semantics { stateDescription = label(selected) },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = { open = false; onSelect(option) },
                    // The check marks the choice, so it is never shown by colour alone.
                    trailingIcon = { if (option == selected) Icon(Icons.Outlined.Check, contentDescription = "Selected") },
                )
            }
        }
    }
}

private val SEGMENT_CHROME = 50.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = Tracker.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.accentSoft, activeContentColor = c.accentText, activeBorderColor = c.accentMid,
                    inactiveContainerColor = c.surface, inactiveContentColor = c.ink, inactiveBorderColor = c.line,
                ),
                label = {
                    Text(
                        label(option), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TrackerType.label,
                        autoSize = TextAutoSize.StepBased(minFontSize = TrackerType.fitMin, maxFontSize = TrackerType.label.fontSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun <T> RadioRows(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = Tracker.colors
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        for (option in options) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.muted))
                Text(label(option), style = TrackerType.body, color = c.ink, modifier = Modifier.padding(start = Spacing.md))
            }
        }
    }
}

@Composable
fun UnitSelector(units: List<DoseUnit>, selected: DoseUnit, onSelect: (DoseUnit) -> Unit) =
    Segmented(units, selected, { it.label }, onSelect = onSelect)
