package dz.mawashi.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val mawashiDateDisplayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("ar", "DZ"))

private fun mawashiDateMillis(value: String): Long? =
    runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MawashiDatePickerField(
    value: String,
    onDateSelected: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val parsedDate = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    val displayValue = parsedDate?.format(mawashiDateDisplayFormatter).orEmpty()

    OutlinedTextField(
        value = displayValue,
        onValueChange = {},
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text("يوم/شهر/سنة") },
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Outlined.DateRange, contentDescription = "اختيار التاريخ")
            }
        }
    )

    if (showPicker) {
        val baseConfiguration = LocalConfiguration.current
        val arabicConfiguration = remember(baseConfiguration) { Configuration(baseConfiguration).apply { setLocale(Locale("ar", "DZ")) } }
        val initialMillis = remember(value) { mawashiDateMillis(value) }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        CompositionLocalProvider(LocalConfiguration provides arabicConfiguration, LocalLayoutDirection provides LayoutDirection.Rtl) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
        }
    }
}
