@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example.barakah.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.coach.Coach
import com.example.barakah.model.Product
import com.example.barakah.model.ProductStatus

@Composable
fun IndieScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Product?>(null) }
    var followersDialog by remember { mutableStateOf(false) }
    val products = vm.products
    val shipped = products.count { it.status.shipped }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Indie", "Ship fast. Talk to users. Stay halal.")

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Total MRR", money(vm.totalMrr), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            StatTile("Shipped", shipped.toString(), Modifier.weight(1f))
            StatTile(
                "Followers ✎", vm.latestFollowers.toString(),
                Modifier.weight(1f).clickable { followersDialog = true }
            )
        }
        Spacer(Modifier.height(8.dp))

        Coach.indieWarning(products, vm.today())?.let { warning ->
            SectionCard {
                Text("⚠️  $warning", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
            }
        }

        SectionCard(title = "YOUR PRODUCTS") {
            if (products.isEmpty()) {
                Text(
                    "No product yet. Add your first idea — the goal is to ship it, not to make it perfect.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            products.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = p }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.status.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${p.status.label} · ${p.users} users" + if (p.url.isNotBlank()) " · ${p.url}" else "",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(money(p.mrr) + "/mo", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { editing = Product(id = vm.newProductId(), name = "") }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Add a product")
            }
        }

        SectionCard(title = "🏁 MILESTONES") {
            val ms = Coach.milestones(products, vm.latestFollowers)
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

        val history = vm.mrrHistory.toSortedMap().entries.toList().takeLast(8)
        if (history.isNotEmpty()) {
            SectionCard(title = "MRR HISTORY") {
                history.reversed().forEach { (date, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(date, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(value), fontWeight = FontWeight.SemiBold)
                    }
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

@Composable
private fun ProductDialog(
    product: Product,
    isNew: Boolean,
    onSave: (Product) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var status by remember { mutableStateOf(product.status) }
    var mrr by remember { mutableStateOf(if (product.mrr == 0.0) "" else product.mrr.toString()) }
    var users by remember { mutableStateOf(if (product.users == 0) "" else product.users.toString()) }
    var url by remember { mutableStateOf(product.url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New product" else "Edit product") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProductStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s, onClick = { status = s },
                            label = { Text("${s.emoji} ${s.label}") }
                        )
                    }
                }
                OutlinedTextField(
                    mrr, { mrr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("MRR (\$ per month)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    users, { users = it.filter(Char::isDigit) },
                    label = { Text("Users") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(url, { url = it }, label = { Text("Link (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        product.copy(
                            name = name.trim(), status = status,
                            mrr = mrr.toDoubleOrNull() ?: 0.0,
                            users = users.toIntOrNull() ?: 0, url = url.trim()
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
