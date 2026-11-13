package com.kolaypazar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = AppState(Store(applicationContext))
        setContent { MaterialTheme { App(state) } }
    }
}

fun money(v: Double) = String.format("%.2f ₺", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(s: AppState) {
    var seller by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val labels = if (seller) listOf("Ürünlerim", "Siparişler")
    else listOf("Ürünler", "Sepet (${s.cartCount()})", "Siparişlerim")
    val icons = listOf("🛍", "🧺", "📦")

    Scaffold(
        topBar = { TopAppBar(title = { Text("KolayPazar") }) },
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { i, l ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icons[i]) },
                        label = { Text(l) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !seller, onClick = { seller = false; tab = 0 }, label = { Text("Müşteri") })
                FilterChip(selected = seller, onClick = { seller = true; tab = 0 }, label = { Text("Satıcı") })
            }
            if (seller) {
                when (tab) {
                    0 -> SellerProducts(s)
                    else -> SellerOrders(s)
                }
            } else {
                when (tab) {
                    0 -> CustomerProducts(s)
                    1 -> CartScreen(s) { tab = 2 }
                    else -> MyOrders(s)
                }
            }
        }
    }
}

@Composable
fun CustomerProducts(s: AppState) {
    if (s.products.isEmpty()) { Empty("Henüz ürün yok."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.products, key = { it.id }) { p ->
            val inCart = s.cart[p.id] ?: 0
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(money(p.price))
                        Text(if (p.stock > 0) "Stok: ${p.stock}" else "Tükendi", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { s.changeCart(p, 1) }, enabled = inCart < p.stock) {
                        Text(if (inCart > 0) "Sepette: $inCart" else "Ekle")
                    }
                }
            }
        }
    }
}

@Composable
fun CartScreen(s: AppState, onOrdered: () -> Unit) {
    val items = s.products.filter { (s.cart[it.id] ?: 0) > 0 }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (items.isEmpty()) { Empty("Sepetin boş."); return }
        items.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(money(p.price * (s.cart[p.id] ?: 0)))
                }
                OutlinedButton(onClick = { s.changeCart(p, -1) }) { Text("-") }
                Text("  ${s.cart[p.id]}  ")
                OutlinedButton(onClick = { s.changeCart(p, 1) }) { Text("+") }
            }
        }
        HorizontalDivider()
        Text("Toplam: ${money(s.cartTotal())}", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = s.customerName,
            onValueChange = { s.customerName = it },
            label = { Text("Adın") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { s.placeOrder(); onOrdered() }, modifier = Modifier.fillMaxWidth()) {
            Text("Sipariş Ver")
        }
    }
}

@Composable
fun MyOrders(s: AppState) {
    val mine = s.orders.filter {
        s.customerName.isBlank() || it.customer.equals(s.customerName.trim(), ignoreCase = true)
    }
    if (mine.isEmpty()) { Empty("Henüz siparişin yok."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mine, key = { it.id }) { OrderCard(it, null) }
    }
}

@Composable
fun SellerProducts(s: AppState) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Yeni ürün", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(name, { name = it }, label = { Text("Ürün adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(price, { price = it }, label = { Text("Fiyat") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(stock, { stock = it }, label = { Text("Stok") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = {
                        val pr = price.replace(',', '.').toDoubleOrNull()
                        val st = stock.toIntOrNull()
                        if (name.isNotBlank() && pr != null && st != null) {
                            s.addProduct(name.trim(), pr, st)
                            name = ""; price = ""; stock = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ürün Ekle") }
                }
            }
        }
        items(s.products, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${money(p.price)} • Stok: ${p.stock}")
                    }
                    TextButton(onClick = { s.deleteProduct(p) }) { Text("Sil") }
                }
            }
        }
    }
}

@Composable
fun SellerOrders(s: AppState) {
    if (s.orders.isEmpty()) { Empty("Henüz sipariş gelmedi."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.orders, key = { it.id }) { o -> OrderCard(o) { s.advance(o) } }
    }
}

@Composable
fun OrderCard(o: Order, onAdvance: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${o.customer} • ${STATUSES[o.status]}", style = MaterialTheme.typography.titleMedium)
            o.lines.forEach { Text("${it.qty} x ${it.name}") }
            Text("Toplam: ${money(o.total)}")
            if (onAdvance != null && o.status < STATUSES.lastIndex) {
                Button(onClick = onAdvance) { Text("→ ${STATUSES[o.status + 1]}") }
            }
        }
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(text) }
}
