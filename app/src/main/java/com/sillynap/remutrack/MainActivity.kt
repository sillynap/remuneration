package com.sillynap.remutrack

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>) = RemuViewModel(RemuRepository(this@MainActivity)) as T
        })[RemuViewModel::class.java]
        setContent { RemuTrackApp(vm) }
    }
}

class RemuViewModel(private val repo: RemuRepository) : ViewModel() {
    private val _entries = MutableStateFlow(repo.entries().sortedByDescending { it.workDate })
    val entries = _entries.asStateFlow()
    private val _settings = MutableStateFlow(repo.settings())
    val settings = _settings.asStateFlow()
    fun upsert(entry: InvigilationEntry) { val next = _entries.value.filterNot { it.id == entry.id } + entry.copy(updatedAt = System.currentTimeMillis()); repo.save(next); _entries.value = next.sortedByDescending { it.workDate } }
    fun delete(entry: InvigilationEntry) { val next = _entries.value - entry; repo.save(next); _entries.value = next }
    fun setPaid(ids: Set<String>, paid: Boolean, paymentDate: LocalDate? = if (paid) LocalDate.now() else null) {
        val next = _entries.value.map { if (it.id in ids) it.copy(paid = paid, paymentDate = paymentDate, updatedAt = System.currentTimeMillis()) else it }
        repo.save(next); _entries.value = next.sortedByDescending { it.workDate }
    }
    fun saveSettings(rate: Long, currency: String) { val s = RemunerationSettings(rate = rate, currency = currency); repo.saveSettings(s); _settings.value = s }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemuTrackApp(vm: RemuViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InvigilationEntry?>(null) }
    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF4355B9))) {
        Scaffold(topBar = { TopAppBar(title = { Text("RemuTrack") }) }, floatingActionButton = {
            if (tab < 2) FloatingActionButton(onClick = { editing = null; showAdd = true }) { Text("+") }
        }, bottomBar = { NavigationBar { listOf("Dashboard", "Unpaid", "Paid", "Settings").forEachIndexed { i, label -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(label.take(1)) }, label = { Text(label) }) } } }) { pad ->
            when (tab) {
                0 -> Dashboard(Modifier.padding(pad), entries, settings, onAdd = { showAdd = true }, onPay = { vm.setPaid(setOf(it), true) })
                1 -> EntryList(Modifier.padding(pad), entries.filterNot { it.paid }, settings, true, vm, onEdit = { editing = it; showAdd = true })
                2 -> EntryList(Modifier.padding(pad), entries.filter { it.paid }, settings, false, vm, onEdit = { editing = it; showAdd = true })
                else -> SettingsScreen(Modifier.padding(pad), settings, vm)
            }
        }
        if (showAdd) EntryEditor(editing, settings, onDismiss = { showAdd = false }, onSave = { vm.upsert(it); showAdd = false })
    }
}

@Composable private fun Dashboard(modifier: Modifier, entries: List<InvigilationEntry>, settings: RemunerationSettings, onAdd: () -> Unit, onPay: (String) -> Unit) {
    val s = RemunerationCalculator.summary(entries)
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Invigilation", style = MaterialTheme.typography.headlineSmall)
        Text("Your remuneration at a glance", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Modifier.weight(1f), "Unpaid", "${s.unpaidCount}", "${settings.currency} ${s.unpaidAmount}")
            StatCard(Modifier.weight(1f), "Paid", "${s.paidCount}", "${settings.currency} ${s.paidAmount}")
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("This month", style = MaterialTheme.typography.titleMedium); Text("${settings.currency} ${s.monthAmount}", style = MaterialTheme.typography.headlineMedium) } }
        if (entries.isEmpty()) Text("No sessions yet. Add your first session to start tracking.", modifier = Modifier.padding(top = 24.dp))
        else { Text("Recent sessions", style = MaterialTheme.typography.titleMedium); entries.take(3).forEach { EntryRow(it, settings.currency, onPay = { if (!it.paid) onPay(it.id) }, onEdit = {}) } }
    }
}

@Composable private fun StatCard(modifier: Modifier, label: String, count: String, amount: String) { Card(modifier) { Column(Modifier.padding(14.dp)) { Text(label); Text(count, style = MaterialTheme.typography.headlineMedium); Text(amount, color = MaterialTheme.colorScheme.primary) } } }

@Composable private fun EntryList(modifier: Modifier, entries: List<InvigilationEntry>, settings: RemunerationSettings, unpaid: Boolean, vm: RemuViewModel, onEdit: (InvigilationEntry) -> Unit) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<InvigilationEntry?>(null) }
    var showRange by remember { mutableStateOf(false) }
    val shown = entries.filter { it.examSeries.contains(query, true) || it.displayExamType.contains(query, true) }
    Column(modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (unpaid) "Unpaid sessions" else "Paid history", style = MaterialTheme.typography.headlineSmall); Text("${shown.size}") }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("Search series or exam type") }, singleLine = true)
        if (unpaid && selected.isNotEmpty()) Button({ vm.setPaid(selected, true); selected = emptySet() }) { Text("Mark ${selected.size} paid") }
        if (unpaid) OutlinedButton({ showRange = true }) { Text("Mark unpaid by date range") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(shown, key = { it.id }) { e ->
            Row { if (unpaid) Checkbox(e.id in selected, { selected = if (it) selected + e.id else selected - e.id }) ; EntryRow(e, settings.currency, onPay = { vm.setPaid(setOf(e.id), !e.paid) }, onEdit = { onEdit(e) }, onDelete = { confirmDelete = e }) }
        } }
    }
    confirmDelete?.let { e -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete session?") }, text = { Text("This cannot be undone.") }, confirmButton = { TextButton({ vm.delete(e); confirmDelete = null }) { Text("Delete") } }, dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } }) }
    if (showRange) {
        var fromText by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
        var toText by remember { mutableStateOf(LocalDate.now().toString()) }
        var rangeError by remember { mutableStateOf<String?>(null) }
        val rangeEntries = runCatching { RemunerationCalculator.payableInRange(entries, LocalDate.parse(fromText), LocalDate.parse(toText)) }.getOrDefault(emptyList())
        AlertDialog(onDismissRequest = { showRange = false }, title = { Text("Pay by work-date range") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Only currently unpaid sessions are included. Dates are inclusive.")
            OutlinedTextField(fromText, { fromText = it }, label = { Text("From (YYYY-MM-DD)") }, singleLine = true)
            OutlinedTextField(toText, { toText = it }, label = { Text("To (YYYY-MM-DD)") }, singleLine = true)
            Text("Matching: ${rangeEntries.size} sessions • ${settings.currency} ${rangeEntries.sumOf { it.rate }}")
            rangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton({
            try {
                val from = LocalDate.parse(fromText); val to = LocalDate.parse(toText)
                if (from > to) throw IllegalArgumentException("From date must not be after to date")
                vm.setPaid(RemunerationCalculator.payableInRange(entries, from, to).map { it.id }.toSet(), true); showRange = false
            } catch (_: Exception) { rangeError = "Enter valid dates in YYYY-MM-DD order" }
        }) { Text("Confirm payment") } }, dismissButton = { TextButton({ showRange = false }) { Text("Cancel") } })
    }
}

@Composable private fun EntryRow(e: InvigilationEntry, currency: String, onPay: () -> Unit, onEdit: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(e.examSeries, style = MaterialTheme.typography.titleMedium); Text("$currency ${e.rate}") }
        Text("${e.workDate} • ${e.examYear} • ${e.displayExamType}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (e.paid) "Paid ${e.paymentDate ?: ""}" else "Unpaid", color = if (e.paid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); Spacer(Modifier.weight(1f)); TextButton(onEdit) { Text("Edit") }; TextButton(onPay) { Text(if (e.paid) "Unpaid" else "Paid") }; onDelete?.let { TextButton(it) { Text("Delete") } } }
    } }
}

@Composable private fun SettingsScreen(modifier: Modifier, settings: RemunerationSettings, vm: RemuViewModel) {
    var rate by remember(settings) { mutableStateOf(settings.rate.toString()) }; var currency by remember(settings) { mutableStateOf(settings.currency) }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Settings", style = MaterialTheme.typography.headlineSmall); Text("Invigilation", style = MaterialTheme.typography.titleMedium); Text("Changes apply only to new sessions.")
        OutlinedTextField(rate, { rate = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Current rate") }); OutlinedTextField(currency, { currency = it.take(6).uppercase() }, Modifier.fillMaxWidth(), label = { Text("Currency") }); Button({ vm.saveSettings(rate.toLongOrNull() ?: 0, currency.ifBlank { "BDT" }) }) { Text("Save settings") }
    }
}

@Composable private fun EntryEditor(existing: InvigilationEntry?, settings: RemunerationSettings, onDismiss: () -> Unit, onSave: (InvigilationEntry) -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(existing?.workDate ?: LocalDate.now()) }; var series by remember { mutableStateOf(existing?.examSeries ?: "") }; var year by remember { mutableStateOf(existing?.examYear?.toString() ?: date.year.toString()) }; var type by remember { mutableStateOf(existing?.examType ?: ExamType.SEMESTER) }; var custom by remember { mutableStateOf(existing?.customExamType ?: "") }; var note by remember { mutableStateOf(existing?.paymentNote ?: "") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Add session" else "Edit session") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text("Work date: $date") }
        OutlinedTextField(series, { series = it }, label = { Text("Exam series *") }); OutlinedTextField(year, { year = it.filter(Char::isDigit) }, label = { Text("Exam year *") })
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ExamType.values().toList().chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { examType ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = type == examType,
                            onClick = { type = examType },
                            label = { Text(examType.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                        )
                    }
                }
            }
        }
        if (type == ExamType.OTHER) OutlinedTextField(custom, { custom = it }, label = { Text("Custom exam type *") })
        OutlinedTextField(note, { note = it }, label = { Text("Payment note (optional)") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton({ val y = year.toIntOrNull(); if (series.isBlank() || y == null || (type == ExamType.OTHER && custom.isBlank())) error = "Complete all required fields" else onSave(InvigilationEntry(existing?.id ?: java.util.UUID.randomUUID().toString(), date, series.trim(), y, type, custom.takeIf { type == ExamType.OTHER }, existing?.rate ?: settings.rate, existing?.paid ?: false, existing?.paymentDate, note.trim().ifBlank { null }, existing?.createdAt ?: System.currentTimeMillis())) }) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
