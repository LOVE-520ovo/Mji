package com.moon.aiphone

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 7号 商城系统核心：表结构 / 商品与订单 CRUD / 聊天卡片发送 / 代付流转 / AI批量生成
 */
object ShopManager {

    data class Product(
        val id: Long,
        val name: String,
        val emoji: String,
        val price: Double,
        val descr: String,
        val category: String
    )

    data class Order(
        val id: Long,
        val productId: Long,
        val name: String,
        val emoji: String,
        val price: Double,
        val category: String,
        val targetType: String,   // me / ai
        val aiId: String,
        val aiName: String,
        val payMode: String,      // self / ai
        val status: String,       // pending / paid / refused
        val msgTs: Long,
        val createdAt: Long
    )

    data class GenProduct(
        val name: String,
        val emoji: String,
        val price: Double,
        val descr: String,
        val category: String
    )

    val defaultCategories = listOf("数码电子", "美妆护肤", "服饰穿搭", "生活家居", "零食美食", "虚拟物品", "其他")

    fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

    // ── 建表 ────────────────────────────────────────────────
    fun ensureTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ShopProducts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, emoji TEXT DEFAULT '🛍️', price REAL DEFAULT 0, descr TEXT DEFAULT '', category TEXT DEFAULT '其他', createdAt INTEGER, updatedAt INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS ShopCategories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, sortOrder INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS ShopOrders (id INTEGER PRIMARY KEY AUTOINCREMENT, productId INTEGER, name TEXT, emoji TEXT DEFAULT '🛍️', price REAL, category TEXT DEFAULT '', targetType TEXT DEFAULT 'me', aiId TEXT DEFAULT '', aiName TEXT DEFAULT '', payMode TEXT DEFAULT 'self', status TEXT DEFAULT 'paid', msgTs INTEGER DEFAULT 0, createdAt INTEGER)")
        seedCategories(db)
    }

    private fun seedCategories(db: SQLiteDatabase) {
        val count = db.rawQuery("SELECT COUNT(*) FROM ShopCategories", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        if (count == 0) {
            defaultCategories.forEachIndexed { i, name ->
                try {
                    db.execSQL("INSERT INTO ShopCategories (name, sortOrder) VALUES (?, ?)", arrayOf<Any>(name, i))
                } catch (_: Exception) {}
            }
        }
    }

    // ── 分区 ────────────────────────────────────────────────
    fun listCategories(db: SQLiteDatabase): List<String> {
        val list = mutableListOf<String>()
        db.rawQuery("SELECT name FROM ShopCategories ORDER BY sortOrder ASC, id ASC", null).use { c ->
            while (c.moveToNext()) list.add(c.getString(0) ?: "")
        }
        return list
    }

    fun addCategory(db: SQLiteDatabase, name: String): Boolean {
        return try {
            db.execSQL("INSERT INTO ShopCategories (name, sortOrder) VALUES (?, ?)",
                arrayOf<Any>(name, System.currentTimeMillis() % 100000))
            true
        } catch (_: Exception) { false }
    }

    fun renameCategory(db: SQLiteDatabase, oldName: String, newName: String) {
        db.execSQL("UPDATE ShopCategories SET name=? WHERE name=?", arrayOf<Any>(newName, oldName))
        db.execSQL("UPDATE ShopProducts SET category=? WHERE category=?", arrayOf<Any>(newName, oldName))
    }

    fun deleteCategory(db: SQLiteDatabase, name: String) {
        db.execSQL("DELETE FROM ShopCategories WHERE name=?", arrayOf<Any>(name))
        db.execSQL("UPDATE ShopProducts SET category='其他' WHERE category=?", arrayOf<Any>(name))
    }

    // ── 商品 ────────────────────────────────────────────────
    fun listProducts(db: SQLiteDatabase, category: String?): List<Product> {
        val list = mutableListOf<Product>()
        val all = category.isNullOrEmpty() || category == "全部"
        val sql = if (all)
            "SELECT id, name, emoji, price, descr, category FROM ShopProducts ORDER BY updatedAt DESC, id DESC"
        else
            "SELECT id, name, emoji, price, descr, category FROM ShopProducts WHERE category=? ORDER BY updatedAt DESC, id DESC"
        val args = if (all) null else arrayOf(category)
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                list.add(Product(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "🛍️",
                    c.getDouble(3), c.getString(4) ?: "", c.getString(5) ?: "其他"))
            }
        }
        return list
    }

    fun addProduct(db: SQLiteDatabase, name: String, emoji: String, price: Double, descr: String, category: String): Long {
        val now = System.currentTimeMillis()
        db.execSQL("INSERT INTO ShopProducts (name, emoji, price, descr, category, createdAt, updatedAt) VALUES (?,?,?,?,?,?,?)",
            arrayOf<Any>(name, emoji.ifBlank { "🛍️" }, price, descr, category, now, now))
        return db.rawQuery("SELECT last_insert_rowid()", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    fun updateProduct(db: SQLiteDatabase, id: Long, name: String, emoji: String, price: Double, descr: String, category: String) {
        db.execSQL("UPDATE ShopProducts SET name=?, emoji=?, price=?, descr=?, category=?, updatedAt=? WHERE id=?",
            arrayOf<Any>(name, emoji.ifBlank { "🛍️" }, price, descr, category, System.currentTimeMillis(), id))
    }

    fun deleteProduct(db: SQLiteDatabase, id: Long) {
        db.execSQL("DELETE FROM ShopProducts WHERE id=?", arrayOf<Any>(id))
    }

    // ── 订单 ────────────────────────────────────────────────
    fun listOrders(db: SQLiteDatabase): List<Order> {
        val list = mutableListOf<Order>()
        db.rawQuery("SELECT id, productId, name, emoji, price, category, targetType, aiId, aiName, payMode, status, msgTs, createdAt FROM ShopOrders ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(Order(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "🛍️",
                    c.getDouble(4), c.getString(5) ?: "", c.getString(6) ?: "me", c.getString(7) ?: "",
                    c.getString(8) ?: "", c.getString(9) ?: "self", c.getString(10) ?: "paid", c.getLong(11), c.getLong(12)))
            }
        }
        return list
    }

    fun getOrder(db: SQLiteDatabase, id: Long): Order? {
        db.rawQuery("SELECT id, productId, name, emoji, price, category, targetType, aiId, aiName, payMode, status, msgTs, createdAt FROM ShopOrders WHERE id=?",
            arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) {
                return Order(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "🛍️",
                    c.getDouble(4), c.getString(5) ?: "", c.getString(6) ?: "me", c.getString(7) ?: "",
                    c.getString(8) ?: "", c.getString(9) ?: "self", c.getString(10) ?: "paid", c.getLong(11), c.getLong(12))
            }
        }
        return null
    }

    private fun insertOrder(db: SQLiteDatabase, product: Product, targetType: String, aiId: String, aiName: String, payMode: String, status: String): Long {
        db.execSQL("INSERT INTO ShopOrders (productId, name, emoji, price, category, targetType, aiId, aiName, payMode, status, msgTs, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?,0,?)",
            arrayOf<Any>(product.id, product.name, product.emoji, product.price, product.category, targetType, aiId, aiName, payMode, status, System.currentTimeMillis()))
        return db.rawQuery("SELECT last_insert_rowid()", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    // ── 记账 ────────────────────────────────────────────────
    fun addLedger(db: SQLiteDatabase, type: String, category: String, amount: Double, note: String) {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS LedgerRecords (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, category TEXT, amount REAL, note TEXT, dateStr TEXT, timestamp INTEGER)")
            db.insert("LedgerRecords", null, ContentValues().apply {
                put("type", type)
                put("category", category)
                put("amount", amount)
                put("note", note)
                put("dateStr", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                put("timestamp", System.currentTimeMillis())
            })
        } catch (e: Exception) { Log.e("SHOP_LEDGER", e.stackTraceToString()) }
    }

    // ── 发卡片消息到聊天（写 ChatHistory）────────────────────
    fun sendCardToChat(context: Context, aiId: String, content: String, imageDesc: String): Long {
        val ts = System.currentTimeMillis()
        try {
            val db = DatabaseHelper(context).writableDatabase
            db.insert("ChatHistory", null, ContentValues().apply {
                put("aiId", aiId); put("groupId", "")
                put("content", content)
                put("isFromMe", 1)
                put("msgTime", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                put("timestamp", ts)
                put("isVoice", 0); put("voiceDuration", 0)
                put("localVoicePath", ""); put("translatedText", "")
                put("innerThoughts", ""); put("imageDesc", imageDesc)
                put("isRead", 1)
            })
            context.sendBroadcast(Intent("CYBER_NEW_MSG"))
        } catch (e: Exception) { Log.e("SHOP_SEND", e.stackTraceToString()) }
        return ts
    }

    // ── 购买（买给自己 / 送给角色）──────────────────────────
    fun purchase(context: Context, product: Product, targetType: String, aiId: String, aiName: String): Long {
        val db = DatabaseHelper(context).writableDatabase
        ensureTables(db)
        val oid = insertOrder(db, product, targetType, aiId, aiName, "self", "paid")
        addLedger(db, "expense", "商城", product.price,
            if (targetType == "ai") "送${aiName}「${product.name}」" else "购买「${product.name}」")
        if (targetType == "ai" && aiId.isNotEmpty()) {
            val card = JSONObject().apply {
                put("variant", "gift")
                put("name", product.name); put("emoji", product.emoji)
                put("price", product.price); put("descr", product.descr)
                put("aiName", aiName)
            }
            val content = "[礼物]送TA一个「${product.name}」¥${fmt(product.price)}" +
                    (if (product.descr.isNotBlank()) "·${product.descr}" else "")
            sendCardToChat(context, aiId, content, "[SHOP_CARD]" + card.toString())
        }
        return oid
    }

    // ── 分享商品链接 ────────────────────────────────────────
    fun shareProduct(context: Context, product: Product, aiId: String, aiName: String) {
        val card = JSONObject().apply {
            put("variant", "share")
            put("name", product.name); put("emoji", product.emoji)
            put("price", product.price); put("descr", product.descr)
            put("link", "mji.shop/item/" + product.id)
            put("aiName", aiName)
        }
        val content = "[商品分享]看中了「${product.name}」¥${fmt(product.price)}，感觉你会喜欢"
        sendCardToChat(context, aiId, content, "[SHOP_CARD]" + card.toString())
    }

    // ── 求代付 ──────────────────────────────────────────────
    fun requestPay(context: Context, product: Product, aiId: String, aiName: String): Long {
        val db = DatabaseHelper(context).writableDatabase
        ensureTables(db)
        val oid = insertOrder(db, product, "me", aiId, aiName, "ai", "pending")
        val card = JSONObject().apply {
            put("oid", oid)
            put("name", product.name); put("emoji", product.emoji)
            put("price", product.price); put("descr", product.descr)
            put("status", "pending")
            put("aiName", aiName)
        }
        val content = "[代付请求] 「${product.name}」¥${fmt(product.price)}（编号${oid}）想让TA帮我付"
        val ts = sendCardToChat(context, aiId, content, "[SHOP_PAYREQ]" + card.toString())
        db.execSQL("UPDATE ShopOrders SET msgTs=? WHERE id=?", arrayOf<Any>(ts, oid))
        return oid
    }

    /**
     * 处理代付结果（ChatActivity 在检测到 [SHOP_PAY_ACCEPT] / [SHOP_PAY_DECLINE] 后调用）
     * @param result paid / refused
     * @return 更新后的订单（进行 UI 提示与卡片刷新），失败返回 null
     */
    fun markPayResult(context: Context, orderId: Long, result: String): Order? {
        val db = DatabaseHelper(context).writableDatabase
        ensureTables(db)
        val order = getOrder(db, orderId) ?: return null
        if (order.status != "pending") return null
        db.execSQL("UPDATE ShopOrders SET status=? WHERE id=?", arrayOf<Any>(result, orderId))
        if (order.msgTs > 0) {
            val card = JSONObject().apply {
                put("oid", order.id)
                put("name", order.name); put("emoji", order.emoji)
                put("price", order.price); put("status", result); put("aiName", order.aiName)
            }
            db.execSQL("UPDATE ChatHistory SET imageDesc=? WHERE aiId=? AND timestamp=?",
                arrayOf<Any>("[SHOP_PAYREQ]" + card.toString(), order.aiId, order.msgTs))
        }
        if (result == "paid") {
            addLedger(db, "income", "代付", order.price, "${order.aiName}代付「${order.name}」")
        }
        return order.copy(status = result)
    }

    // ── AI 批量生成商品 ─────────────────────────────────────
    fun generateProducts(context: Context, theme: String, count: Int, categories: List<String>, callback: (List<GenProduct>) -> Unit) {
        Thread {
            try {
                val pref = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
                val apiKey = pref.getString("apiKey", "") ?: ""
                var apiUrl = (pref.getString("apiUrl", "") ?: "").trimEnd('/')
                val model = pref.getString("modelName", "")?.ifBlank { "gpt-4o" } ?: "gpt-4o"
                if (apiKey.isEmpty() || apiUrl.isEmpty()) { callback(emptyList()); return@Thread }
                if (!apiUrl.endsWith("/chat/completions"))
                    apiUrl += if (apiUrl.contains("/v1")) "/chat/completions" else "/v1/chat/completions"
                val catList = categories.joinToString("、")
                val prompt = """
你是电商商城的商品生成器。根据用户需求生成 $count 个商品，输出 JSON 数组（不要任何解释文字，直接输出数组）。
每个商品对象字段：
- "name": 商品名称（12字内，吸引人）
- "emoji": 一个表意的 emoji
- "price": 价格数字（单位元，符合商品类型，最多两位小数）
- "descr": 一句卖点描述（20字内）
- "category": 从以下分区中选择最合适的一个：$catList
用户需求：$theme
要求：商品多样化、不重复、贴合需求、可以适当有趣。只输出 JSON 数组本身。
""".trimIndent()
                val client = Http.client.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .build()
                val body = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 2000)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                    })
                }.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder().url(apiUrl).addHeader("Authorization", "Bearer $apiKey").post(body).build()
                val resp = client.newCall(req).execute()
                val result = resp.body?.string() ?: ""
                val content = JSONObject(result).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                callback(parseGenProducts(content))
            } catch (e: Exception) {
                Log.e("SHOP_GEN", e.stackTraceToString())
                callback(emptyList())
            }
        }.start()
    }

    private fun parseGenProducts(raw: String): List<GenProduct> {
        val out = mutableListOf<GenProduct>()
        var text = raw.trim()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start >= 0 && end > start) text = text.substring(start, end + 1)
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name", "").trim()
                if (name.isEmpty()) continue
                out.add(GenProduct(
                    name,
                    o.optString("emoji", "🛍️").trim().ifEmpty { "🛍️" },
                    o.optDouble("price", 9.9),
                    o.optString("descr", "").trim(),
                    o.optString("category", "").trim()
                ))
            }
        } catch (_: Exception) {}
        return out
    }
}
