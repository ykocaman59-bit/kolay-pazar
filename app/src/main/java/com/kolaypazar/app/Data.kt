package com.kolaypazar.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Product(val id: Long, val name: String, val price: Double, val stock: Int)
data class OrderLine(val name: String, val qty: Int, val price: Double)
data class Order(val id: Long, val customer: String, val lines: List<OrderLine>, val status: Int) {
    val total: Double get() = lines.sumOf { it.qty * it.price }
}

val STATUSES = listOf("Yeni", "Hazırlanıyor", "Hazır", "Teslim edildi")

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kolaypazar", Context.MODE_PRIVATE)

    fun loadProducts(): List<Product> {
        val raw = sp.getString("products", null)
        if (raw == null) {
            return listOf(
                Product(1, "Taze Ekmek", 15.0, 40),
                Product(2, "Süt (1 L)", 38.0, 25),
                Product(3, "Yumurta (15'li)", 90.0, 12)
            )
        }
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Product(o.getLong("id"), o.getString("name"), o.getDouble("price"), o.getInt("stock"))
        }
    }

    fun saveProducts(list: List<Product>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("price", it.price).put("stock", it.stock))
        }
        sp.edit().putString("products", arr.toString()).apply()
    }

    fun loadOrders(): List<Order> {
        val arr = JSONArray(sp.getString("orders", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val ls = o.getJSONArray("lines")
            Order(
                o.getLong("id"), o.getString("customer"),
                (0 until ls.length()).map { i ->
                    val l = ls.getJSONObject(i)
                    OrderLine(l.getString("name"), l.getInt("qty"), l.getDouble("price"))
                },
                o.getInt("status")
            )
        }
    }

    fun saveOrders(list: List<Order>) {
        val arr = JSONArray()
        list.forEach { ord ->
            val ls = JSONArray()
            ord.lines.forEach { ls.put(JSONObject().put("name", it.name).put("qty", it.qty).put("price", it.price)) }
            arr.put(JSONObject().put("id", ord.id).put("customer", ord.customer).put("lines", ls).put("status", ord.status))
        }
        sp.edit().putString("orders", arr.toString()).apply()
    }
}

class AppState(private val store: Store) {
    val products = mutableStateListOf<Product>().apply { addAll(store.loadProducts()) }
    val orders = mutableStateListOf<Order>().apply { addAll(store.loadOrders()) }
    val cart = mutableStateMapOf<Long, Int>()
    var customerName by mutableStateOf("")

    fun addProduct(name: String, price: Double, stock: Int) {
        products.add(Product(System.currentTimeMillis(), name, price, stock))
        store.saveProducts(products.toList())
    }

    fun deleteProduct(p: Product) {
        products.remove(p)
        cart.remove(p.id)
        store.saveProducts(products.toList())
    }

    fun cartTotal(): Double = products.sumOf { (cart[it.id] ?: 0) * it.price }
    fun cartCount(): Int = cart.values.sum()

    fun changeCart(p: Product, delta: Int) {
        val n = ((cart[p.id] ?: 0) + delta).coerceIn(0, p.stock)
        if (n == 0) cart.remove(p.id) else cart[p.id] = n
    }

    fun placeOrder() {
        val lines = products.filter { (cart[it.id] ?: 0) > 0 }
            .map { OrderLine(it.name, cart[it.id]!!, it.price) }
        if (lines.isEmpty()) return
        orders.add(0, Order(System.currentTimeMillis(), customerName.trim().ifBlank { "Misafir" }, lines, 0))
        val updated = products.map { it.copy(stock = it.stock - (cart[it.id] ?: 0)) }
        products.clear(); products.addAll(updated)
        cart.clear()
        store.saveProducts(products.toList())
        store.saveOrders(orders.toList())
    }

    fun advance(o: Order) {
        if (o.status >= STATUSES.lastIndex) return
        val i = orders.indexOfFirst { it.id == o.id }
        if (i >= 0) {
            orders[i] = o.copy(status = o.status + 1)
            store.saveOrders(orders.toList())
        }
    }
}
