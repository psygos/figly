package app.figly.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figly.data.FigRepository
import app.figly.data.UsageReadings
import app.figly.export.PlateExport
import app.figly.probe.Reminder
import app.figly.ui.Ink
import app.figly.ui.Label
import app.figly.ui.Micro
import java.util.Locale
import kotlinx.coroutines.launch

/** Settings — a field instrument's quiet underside. */
@Composable
fun SettingsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FigRepository.get(context) }
    val scope = rememberCoroutineScope()

    var budget by remember { mutableIntStateOf(repo.screenBudgetMin) }
    var effortLabel by remember { mutableStateOf(repo.effortLabel) }
    var reminder by remember { mutableStateOf(repo.reminderOn) }
    var whyMonday by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    var locationOn by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationOn = granted }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        reminder = granted
        repo.reminderOn = granted
        Reminder.schedule(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Ink.s4),
    ) {
        Micro("BACK", Modifier.clickable(onClick = back).padding(Ink.s2))
        Spacer(Modifier.height(Ink.s6))
        Label("settings", Ink.display(20.sp, Ink.ink))
        Spacer(Modifier.height(Ink.s6))

        // Screen budget.
        SettingRow("SCREEN BUDGET") {
            Micro(
                "−",
                Modifier.clickable {
                    budget = (budget - 15).coerceAtLeast(30)
                    repo.screenBudgetMin = budget
                }.padding(horizontal = Ink.s3, vertical = Ink.s2),
                color = Ink.ink,
                size = 12.sp,
            )
            Label(formatBudget(budget), Ink.data(12.sp, Ink.ink))
            Micro(
                "+",
                Modifier.clickable {
                    budget = (budget + 15).coerceAtMost(12 * 60)
                    repo.screenBudgetMin = budget
                }.padding(horizontal = Ink.s3, vertical = Ink.s2),
                color = Ink.ink,
                size = 12.sp,
            )
        }

        // Week start — locked, with its reason.
        SettingRow("WEEK STARTS", onTap = { whyMonday = !whyMonday }) {
            Micro("MONDAY · LOCKED", color = Ink.inkDim)
        }
        if (whyMonday) {
            Micro(
                "FIGS PRESS SUNDAY NIGHT. EVERY DRAWER ALIGNS.",
                Modifier.padding(start = Ink.s2, bottom = Ink.s3),
                color = Ink.inkFaint,
                size = 8.sp,
            )
        }

        // The thorn's practice, renameable.
        SettingRow("THORN MARKS") {
            BasicTextField(
                value = effortLabel,
                onValueChange = {
                    effortLabel = it.uppercase(Locale.ROOT).take(12)
                    repo.effortLabel = effortLabel
                },
                textStyle = Ink.data(12.sp, Ink.ink),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink.ink),
                modifier = Modifier.width(120.dp),
            )
        }

        // Usage access.
        val hasUsage = UsageReadings.hasPermission(context)
        SettingRow(
            "USAGE ACCESS",
            onTap = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        ) {
            Micro(
                if (hasUsage) "GRANTED" else "NO USAGE ACCESS — SCREEN READING IS MANUAL",
                color = if (hasUsage) Ink.inkDim else Ink.ink,
                size = if (hasUsage) 10.sp else 8.sp,
            )
        }

        // Collected city.
        SettingRow(
            "COLLECTED CITY",
            onTap = {
                if (!locationOn) askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
        ) {
            Micro(
                if (locationOn) "ON NEW PLATES" else "OFF — PLATES READ “—”",
                color = Ink.inkDim,
                size = 9.sp,
            )
        }

        // Reminder, default off.
        SettingRow(
            "REMINDER 21:30",
            onTap = {
                if (reminder) {
                    reminder = false
                    repo.reminderOn = false
                    Reminder.schedule(context)
                } else if (Build.VERSION.SDK_INT >= 33) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    reminder = true
                    repo.reminderOn = true
                    Reminder.schedule(context)
                }
            },
        ) {
            Micro(if (reminder) "ON" else "OFF", color = if (reminder) Ink.ink else Ink.inkDim)
        }

        // Loom.
        var toysLinkFailed by remember { mutableStateOf(false) }
        SettingRow("LOOM", onTap = { toysLinkFailed = !openGlyphToys(context) }) {
            Micro("OPEN GLYPH TOYS", color = Ink.inkDim)
        }
        if (toysLinkFailed) {
            Micro(
                GLYPH_TOYS_PATH_BY_HAND,
                Modifier.padding(start = Ink.s2, bottom = Ink.s3),
                color = Ink.inkFaint,
                size = 8.sp,
            )
        }

        Spacer(Modifier.height(Ink.s6))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.hairline))
        Spacer(Modifier.height(Ink.s6))

        // Export all.
        SettingRow(
            "EXPORT ALL",
            onTap = {
                scope.launch {
                    val n = PlateExport.exportAll(context)
                    exportNote = if (n == 0) "THE DRAWER IS EMPTY" else "$n PLATES → PICTURES/FIGLY"
                }
            },
        ) {
            Micro(exportNote ?: "PNG + SVG", color = Ink.inkDim, size = 9.sp)
        }

        // Erase all — two steps, no dialog.
        if (!erasing) {
            SettingRow("ERASE ALL", onTap = { erasing = true }) {
                Micro("EVERYTHING, FOREVER", color = Ink.inkDim, size = 9.sp)
            }
        } else {
            SettingRow("BURN THE DRAWER?") {
                Micro(
                    "YES",
                    Modifier.clickable {
                        scope.launch {
                            repo.eraseEverything()
                            erasing = false
                            back()
                        }
                    }.padding(Ink.s2),
                    color = Ink.ink,
                )
                Spacer(Modifier.width(Ink.s2))
                Micro("KEEP", Modifier.clickable { erasing = false }.padding(Ink.s2), color = Ink.inkDim)
            }
        }

        Spacer(Modifier.height(Ink.s12))
        Micro(
            "FIGLY KEEPS EVERYTHING ON THIS PHONE.\nTHERE IS NO ACCOUNT, NO CLOUD, NO NETWORK.",
            color = Ink.inkFaint,
            size = 8.sp,
        )
        Spacer(Modifier.height(Ink.s8))
    }
}

@Composable
private fun SettingRow(
    title: String,
    onTap: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onTap != null) it.clickable(onClick = onTap) else it }
            .padding(vertical = Ink.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Micro(title, color = Ink.inkDim)
        Spacer(Modifier.weight(1f))
        trailing()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.hairline.copy(alpha = 0.5f)))
}

private fun formatBudget(min: Int): String =
    if (min % 60 == 0) "${min / 60}H" else "${min / 60}H${"%02d".format(min % 60)}"
