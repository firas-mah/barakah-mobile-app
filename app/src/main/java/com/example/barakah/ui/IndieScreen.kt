package com.example.barakah.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.coach.Coach
import com.example.barakah.model.CurrencyCode
import com.example.barakah.model.Market
import com.example.barakah.model.MoneyEntry
import com.example.barakah.model.MoneyType
import com.example.barakah.model.Product
import com.example.barakah.model.ProductStatus
import com.example.barakah.model.ProjectKind
import com.example.barakah.model.RevenueModel
import com.example.barakah.progress.Progress
import java.time.temporal.ChronoUnit

private val INCOME_GREEN = Color(0xFF2E9E6B)
private val EXPENSE_RED = Color(0xFFE5534B)

@Composable
fun IndieScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Product?>(null) }
    var editingMoney by remember { mutableStateOf<MoneyEntry?>(null) }
    var followersDialog by remember { mutableStateOf(false) }
    val today = vm.today()
    var month by remember { mutableStateOf(Progress.PeriodKey.of(Progress.PeriodType.MONTH, today)) }
    val currentMonth = Progress.PeriodKey.of(Progress.PeriodType.MONTH, today)

    val products = vm.products
    val shipped = products.count { it.status.shipped }
    val thisMonth = vm.moneySummary(currentMonth.start, currentMonth.end)
    val facts = vm.moneyFacts()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Projects", "Many small income sources. Ship, get paid, give sadaqa.")

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Net · ${currentMonth.shortLabel}", tnd(thisMonth.net), Modifier.weight(1.3f),
                if (thisMonth.net < 0) EXPENSE_RED else MaterialTheme.colorScheme.secondary
            )
            StatTile("Income sources", thisMonth.sources.toString(), Modifier.weight(1f))
            StatTile("Shipped", shipped.toString(), Modifier.weight(0.9f))
        }
        Spacer(Modifier.height(8.dp))

        val top = thisMonth.topSource?.let { (id, share) -> vm.projectName(id) to share }
        Coach.indieWarning(products, today, facts.lifetimeRevenue > 0, top)?.let { warning ->
            SectionCard {
                Text("⚠️  $warning", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // ── Projects, grouped by kind ──
        if (products.isEmpty()) {
            SectionCard(title = "YOUR PROJECTS") {
                Text(
                    "No project yet. Add your client apps and your own products — the goal is to ship, then get paid.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ProjectKind.entries.forEach { kind ->
            val list = products.filter { it.kind == kind }
            if (list.isEmpty()) return@forEach
            SectionCard(title = "${kind.emoji} ${kind.label.uppercase()}S") {
                list.forEach { p ->
                    ProjectRow(p, thisMonth.incomeByProject[p.id], vm) { editing = p }
                }
            }
        }
        Button(
            onClick = { editing = Product(id = vm.newProductId(), name = "") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) { Text("+ Add a project") }

        // ── Money ledger ──
        val summary = vm.moneySummary(month.start, month.end)
        SectionCard(title = "💰 MONEY") {
            PeriodSwitcher(month.label, { month = month.previous() }, { month = month.next() }, month != currentMonth)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                MoneyFigure("Revenue", tnd(summary.revenue), INCOME_GREEN, Modifier.weight(1f))
                MoneyFigure("Expenses", tnd(summary.expenses), EXPENSE_RED, Modifier.weight(1f))
                MoneyFigure(
                    "Net", tnd(summary.net),
                    if (summary.net < 0) EXPENSE_RED else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)
                )
            }
            if (summary.revenue > 0) {
                summary.incomeByProject.entries.sortedByDescending { it.value }.forEach { (id, v) ->
                    val share = (v / summary.revenue).toFloat()
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.projectName(id), Modifier.weight(0.4f), fontSize = 13.sp)
                        ProgressBar(share, if (share > 0.6f) MaterialTheme.colorScheme.secondary else INCOME_GREEN, Modifier.weight(0.4f))
                        Text("${(share * 100).toInt()}%", Modifier.weight(0.2f), fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            val entries = vm.money.filter { e -> parseDay(e.date)?.let { it in month } == true }
                .sortedByDescending { it.date }
            if (entries.isEmpty()) {
                Caption("Nothing logged for ${month.label}. Log every payment and every expense — even small ones.")
            }
            entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { editingMoney = e }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(e.type.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.projectName(e.projectId) + if (e.note.isNotBlank()) " · ${e.note}" else "", fontWeight = FontWeight.Medium)
                        Caption("${e.date} · ${e.type.label}")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            (if (e.type.isExpense) "− " else "+ ") + amount(e.amount, e.currency),
                            fontWeight = FontWeight.SemiBold,
                            color = if (e.type.isExpense) EXPENSE_RED else INCOME_GREEN
                        )
                        if (e.currency != CurrencyCode.TND) Caption("≈ " + tnd(vm.toTnd(e)))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { editingMoney = newEntry(vm, MoneyType.CLIENT_PAYMENT, month) },
                    modifier = Modifier.weight(1f)
                ) { Text("+ Income") }
                OutlinedButton(
                    onClick = { editingMoney = newEntry(vm, MoneyType.EXPENSE, month) },
                    modifier = Modifier.weight(1f)
                ) { Text("+ Expense") }
            }
            if (vm.totalMrrTnd > 0) {
                Spacer(Modifier.height(6.dp))
                Caption("Expected recurring (from your projects): ≈ ${tnd(vm.totalMrrTnd)} / month")
            }
        }

        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            StatTile(
                "Followers ✎", vm.latestFollowers.toString(),
                Modifier.weight(1f).clickable { followersDialog = true }
            )
        }

        SectionCard(title = "🏁 MILESTONES") {
            val ms = Coach.milestones(products, vm.latestFollowers, facts)
            val next = ms.firstOrNull { !it.reached }
            ms.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (m.reached) "✅" else m.emoji, modifier = Modifier.padding(end = 10.dp))
                    Text(
                        m.title,
                        Modifier.weight(1f),
                        fontWeight = if (m == next) FontWeight.Bold else FontWeight.Normal,
                        color = if (m.reached) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (m == next) Text("next", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { p ->
        ProductDialog(
            product = p,
            isNew = vm.products.none { it.id == p.id },
            onSave = { vm.saveProduct(it); editing = null },
            onDelete = { vm.deleteProduct(p.id); editing = null },
            onDismiss = { editing = null }
        )
    }

    editingMoney?.let { e ->
        MoneyDialog(
            entry = e,
            isNew = vm.money.none { it.id == e.id },
            products = vm.products,
            usdToTnd = vm.settings.usdToTnd,
            onSave = { vm.saveMoney(it); editingMoney = null },
            onDelete = { vm.deleteMoney(e.id); editingMoney = null },
            onDismiss = { editingMoney = null }
        )
    }

    if (followersDialog) {
        var text by remember { mutableStateOf(vm.latestFollowers.toString()) }
        AlertDialog(
            onDismissRequest = { followersDialog = false },
            title = { Text("Followers today") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Total followers (X + others)") }
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setFollowers(text.toIntOrNull() ?: 0); followersDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { followersDialog = false }) { Text("Cancel") } }
        )
    }
}

/** New entry dated today, or the last day of a past month being viewed. */
private fun newEntry(vm: AppViewModel, type: MoneyType, month: Progress.PeriodKey): MoneyEntry {
    val today = vm.today()
    val date = if (today in month) today else month.end
    return MoneyEntry(id = vm.newId(), date = date.toString(), amount = 0.0, type = type)
}

@Composable
private fun MoneyFigure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 15.sp)
        Caption(label)
    }
}

@Composable
private fun ProjectRow(p: Product, incomeThisMonth: Double?, vm: AppViewModel, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(p.status.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold)
            val model = if (p.revenueModel != RevenueModel.NONE) " · ${p.revenueModel.label}" else ""
            Caption("${p.status.label} · ${p.market.label}$model" + if (p.users > 0) " · ${p.users} users" else "")
            if (p.nextStep.isNotBlank() || p.deadline.isNotBlank()) {
                val deadline = parseDay(p.deadline)?.let { d ->
                    val days = ChronoUnit.DAYS.between(vm.today(), d)
                    when {
                        p.status.shipped -> ""
                        days < 0 -> "⚠️ ${-days} d late"
                        days == 0L -> "⏳ due today"
                        else -> "⏳ $days d left"
                    }
                }.orEmpty()
                val next = if (p.nextStep.isNotBlank()) "Next: ${p.nextStep}" else ""
                Caption(
                    listOf(next, deadline).filter { it.isNotBlank() }.joinToString(" · "),
                    if (deadline.startsWith("⚠️")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (incomeThisMonth != null && incomeThisMonth > 0) tnd(incomeThisMonth) else "—",
                fontWeight = FontWeight.SemiBold, color = INCOME_GREEN
            )
            Caption("this month")
        }
    }
}

@Composable
private fun ProductDialog(
    product: Product,
    isNew: Boolean,
    onSave: (Product) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var kind by remember { mutableStateOf(product.kind) }
    var market by remember { mutableStateOf(product.market) }
    var status by remember { mutableStateOf(product.status) }
    var revenueModel by remember { mutableStateOf(product.revenueModel) }
    var mrr by remember { mutableStateOf(if (product.mrr == 0.0) "" else product.mrr.toString()) }
    var mrrCurrency by remember { mutableStateOf(if (isNew) CurrencyCode.TND else product.mrrCurrency) }
    var users by remember { mutableStateOf(if (product.users == 0) "" else product.users.toString()) }
    var url by remember { mutableStateOf(product.url) }
    var nextStep by remember { mutableStateOf(product.nextStep) }
    var deadline by remember { mutableStateOf(product.deadline) }
    val deadlineOk = deadline.isBlank() || parseDay(deadline) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New project" else "Edit project") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Caption("Type")
                ChipRow(ProjectKind.entries, kind, { "${it.emoji} ${it.label}" }) {
                    kind = it
                    if (isNew) market = if (it == ProjectKind.CLIENT_APP) Market.PRIVATE_CLIENT else Market.WORLD
                }
                Caption("Audience")
                ChipRow(Market.entries, market, { it.label }) { market = it }
                Caption("Status")
                ChipRow(ProductStatus.entries, status, { "${it.emoji} ${it.label}" }) { status = it }
                Caption("How it makes money")
                ChipRow(RevenueModel.entries, revenueModel, { it.label }) { revenueModel = it }
                OutlinedTextField(nextStep, { nextStep = it }, label = { Text("Next step") }, singleLine = true)
                OutlinedTextField(
                    deadline, { deadline = it }, label = { Text("Deadline (yyyy-mm-dd, optional)") },
                    singleLine = true, isError = !deadlineOk
                )
                OutlinedTextField(
                    mrr, { mrr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Expected recurring / month (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                ChipRow(CurrencyCode.entries, mrrCurrency, { it.name }) { mrrCurrency = it }
                OutlinedTextField(
                    users, { users = it.filter(Char::isDigit) },
                    label = { Text("Users / clients") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(url, { url = it }, label = { Text("Link (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && deadlineOk,
                onClick = {
                    onSave(
                        product.copy(
                            name = name.trim(), kind = kind, market = market, status = status,
                            revenueModel = revenueModel,
                            mrr = mrr.toDoubleOrNull() ?: 0.0, mrrCurrency = mrrCurrency,
                            users = users.toIntOrNull() ?: 0, url = url.trim(),
                            nextStep = nextStep.trim(), deadline = deadline.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun MoneyDialog(
    entry: MoneyEntry,
    isNew: Boolean,
    products: List<Product>,
    usdToTnd: Double,
    onSave: (MoneyEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isExpense = entry.type.isExpense
    var amountText by remember { mutableStateOf(if (entry.amount == 0.0) "" else entry.amount.toString()) }
    var currency by remember { mutableStateOf(entry.currency) }
    var type by remember { mutableStateOf(entry.type) }
    var projectId by remember { mutableStateOf(entry.projectId) }
    var date by remember { mutableStateOf(entry.date) }
    var note by remember { mutableStateOf(entry.note) }
    val amountValue = amountText.toDoubleOrNull()
    val valid = amountValue != null && amountValue > 0 && parseDay(date) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExpense) (if (isNew) "New expense" else "Edit expense") else (if (isNew) "New income" else "Edit income")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                ChipRow(CurrencyCode.entries, currency, { it.name }) { currency = it }
                if (currency == CurrencyCode.USD && amountValue != null) {
                    Caption("≈ ${tnd(Progress.toTnd(amountValue, currency, usdToTnd))} (rate in Settings)")
                }
                if (!isExpense) {
                    Caption("Type")
                    ChipRow(MoneyType.entries.filterNot { it.isExpense }, type, { "${it.emoji} ${it.label}" }) { type = it }
                }
                Caption("Project")
                ChipRow(listOf("") + products.map { it.id }, projectId,
                    { id -> products.firstOrNull { it.id == id }?.name ?: "General" }) { projectId = it }
                OutlinedTextField(
                    date, { date = it }, label = { Text("Date (yyyy-mm-dd)") },
                    singleLine = true, isError = parseDay(date) == null
                )
                OutlinedTextField(
                    note, { note = it },
                    label = { Text(if (isExpense) "What for (domain, Play fee, server…)" else "Note (client, invoice…)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        entry.copy(
                            amount = amountValue ?: 0.0, currency = currency, type = type,
                            projectId = projectId, date = date.trim(), note = note.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
