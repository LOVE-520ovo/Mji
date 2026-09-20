package com.moon.aiphone

import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShopActivity : AppCompatActivity() {

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private val mainColor = Color.parseColor("#FF5A3C")
    private val softColor = Color.parseColor("#FFF0EC")
    private val textDark = Color.parseColor("#222222")
    private val textGray = Color.parseColor("#999999")
    private val bgColor = Color.parseColor("#F2F2F6")

    private val db: SQLiteDatabase get() = DatabaseHelper(this).writableDatabase

    private lateinit var catScroll: HorizontalScrollView
    private lateinit var catBar: LinearLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var tvSwitch: TextView
    private lateinit var tvTitle: TextView
    private lateinit var bottomBar: LinearLayout

    private var currentCategory = "全部"
    private var showingOrders = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { ShopManager.ensureTables(DatabaseHelper(this).writableDatabase) } catch (_: Exception) {}
        setContentView(buildRoot())
        refreshAll()
    }

    // ── 界面骨架 ────────────────────────────────────────────
    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(bgColor)
        }
        // 顶栏
        val topBar = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(50))
            setBackgroundColor(Color.WHITE)
        }
        val btnBack = TextView(this).apply {
            text = "‹"; textSize = 30f; setTextColor(textDark)
            setPadding(dp(16), 0, dp(16), dp(6))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RelativeLayout.LayoutParams(-2, -1).also {
                it.addRule(RelativeLayout.ALIGN_PARENT_START)
                it.addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener { finish() }
        }
        tvTitle = TextView(this).apply {
            text = "商城"; textSize = 17f
            setTypeface(null, Typeface.BOLD); setTextColor(textDark)
            layoutParams = RelativeLayout.LayoutParams(-2, -2).also {
                it.addRule(RelativeLayout.CENTER_IN_PARENT)
            }
        }
        tvSwitch = TextView(this).apply {
            text = "📋 订单"; textSize = 14f; setTextColor(mainColor)
            setPadding(0, 0, dp(16), 0); gravity = Gravity.CENTER_VERTICAL
            layoutParams = RelativeLayout.LayoutParams(-2, -1).also {
                it.addRule(RelativeLayout.ALIGN_PARENT_END)
                it.addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener { toggleView() }
        }
        topBar.addView(btnBack); topBar.addView(tvTitle); topBar.addView(tvSwitch)
        root.addView(topBar)
        // 分区栏
        catScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(50))
            setBackgroundColor(Color.WHITE)
            isHorizontalScrollBarEnabled = false
        }
        catBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
        }
        catScroll.addView(catBar)
        root.addView(catScroll)
        // 内容区
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        scroll.addView(contentLayout)
        root.addView(scroll)
        // 底栏
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val bAdd = makeButton("＋ 添加商品", false, 46)
        bAdd.setOnClickListener { showEditProduct(null) }
        val bGen = makeButton("✨ AI批量生成", true, 46)
        bGen.setOnClickListener { showGenerateDialog() }
        val lp1 = LinearLayout.LayoutParams(0, dp(46), 1f).also { it.marginEnd = dp(8) }
        val lp2 = LinearLayout.LayoutParams(0, dp(46), 1f)
        bAdd.layoutParams = lp1; bGen.layoutParams = lp2
        bottomBar.addView(bAdd); bottomBar.addView(bGen)
        root.addView(bottomBar)
        return root
    }

    // ── 通用小部件 ──────────────────────────────────
    private fun makeButton(text: String, filled: Boolean, heightDp: Int = 44, textSize: Float = 14f): TextView {
        return TextView(this).apply {
            this.text = text
            this.textSize = textSize
            gravity = Gravity.CENTER
            setTextColor(if (filled) Color.WHITE else mainColor)
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (filled) mainColor else softColor)
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp))
            isClickable = true
        }
    }

    private fun makeChip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text; textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.WHITE else Color.parseColor("#666666"))
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (selected) mainColor else Color.WHITE)
                if (!selected) setStroke(dp(1), Color.parseColor("#E5E5EA"))
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dp(8) }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun emojiBox(emoji: String): TextView = TextView(this).apply {
        text = emoji; textSize = 24f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(softColor)
        }
    }

    private fun addEmptyHint(t: String) {
        contentLayout.addView(TextView(this).apply {
            text = t; textSize = 13f; setTextColor(textGray)
            gravity = Gravity.CENTER
            setPadding(0, dp(60), 0, dp(20))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        })
    }

    private fun toast(t: String) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    }

    private fun toggleView() {
        showingOrders = !showingOrders
        tvTitle.text = if (showingOrders) "我的订单" else "商城"
        tvSwitch.text = if (showingOrders) "🛍️ 商城" else "📋 订单"
        catScroll.visibility = if (showingOrders) View.GONE else View.VISIBLE
        bottomBar.visibility = if (showingOrders) View.GONE else View.VISIBLE
        refreshContent()
    }

    private fun refreshAll() {
        renderCategories()
        refreshContent()
    }

    private fun refreshContent() {
        contentLayout.removeAllViews()
        try {
            if (showingOrders) renderOrders() else renderProducts()
        } catch (_: Exception) {}
    }

    private fun renderCategories() {
        catBar.removeAllViews()
        val cats = mutableListOf("全部")
        cats += ShopManager.listCategories(db)
        cats.forEach { name ->
            catBar.addView(makeChip(name, name == currentCategory) {
                currentCategory = name
                renderCategories()
                refreshContent()
            })
        }
        catBar.addView(makeChip("⚙ 管理", false) { showManageCategories() })
    }

    // ── 商品列表 ──────────────────────────────────
    private fun renderProducts() {
        val products = ShopManager.listProducts(db, currentCategory)
        if (products.isEmpty()) {
            addEmptyHint(if (currentCategory == "全部") "商城还空着，点下面的按钮添加商品吧" else "这个分区还没有商品")
            return
        }
        products.forEach { p -> contentLayout.addView(productRow(p)) }
    }

    private fun productRow(p: ShopManager.Product): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.WHITE)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.setMargins(dp(14), dp(5), dp(14), dp(5)) }
            isClickable = true
        }
        row.addView(emojiBox(p.emoji))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(12) }
        }
        col.addView(TextView(this).apply {
            text = p.name; textSize = 15f; setTextColor(textDark)
            setTypeface(null, Typeface.BOLD); maxLines = 1
        })
        if (p.descr.isNotBlank()) {
            col.addView(TextView(this).apply {
                text = p.descr; textSize = 12f; setTextColor(textGray)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        col.addView(TextView(this).apply {
            text = p.category; textSize = 10f; setTextColor(mainColor)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(softColor)
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        })
        row.addView(col)
        row.addView(TextView(this).apply {
            text = "¥" + ShopManager.fmt(p.price); textSize = 16f; setTextColor(mainColor)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8) }
        })
        row.setOnClickListener { showProductDetail(p) }
        return row
    }

    // ── 订单列表 ──────────────────────────────────
    private fun renderOrders() {
        val orders = ShopManager.listOrders(db)
        if (orders.isEmpty()) {
            addEmptyHint("还没有订单记录")
            return
        }
        val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        orders.forEach { o ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.WHITE)
                }
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.setMargins(dp(14), dp(5), dp(14), dp(5)) }
            }
            row.addView(emojiBox(o.emoji))
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(12) }
            }
            col.addView(TextView(this).apply {
                text = o.name; textSize = 15f; setTextColor(textDark)
                setTypeface(null, Typeface.BOLD); maxLines = 1
            })
            val who = when {
                o.targetType == "ai" -> "送给${o.aiName}"
                o.payMode == "ai" -> "求${o.aiName}代付"
                else -> "买给我"
            }
            col.addView(TextView(this).apply {
                text = "$who · " + df.format(Date(o.createdAt))
                textSize = 11f; setTextColor(textGray)
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(col)
            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            right.addView(TextView(this).apply {
                text = "¥" + ShopManager.fmt(o.price); textSize = 15f; setTextColor(mainColor)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END
            })
            val (stText, stColor) = orderStatus(o)
            right.addView(TextView(this).apply {
                text = stText; textSize = 11f; setTextColor(stColor)
                gravity = Gravity.END
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(right)
            contentLayout.addView(row)
        }
    }

    private fun orderStatus(o: ShopManager.Order): Pair<String, Int> {
        return when {
            o.status == "pending" -> "待TA回应" to Color.parseColor("#FF9500")
            o.status == "refused" -> "被婉拒了" to Color.parseColor("#AAAAAA")
            o.payMode == "ai" -> "TA已代付" to Color.parseColor("#34C759")
            o.targetType == "ai" -> "已送出" to mainColor
            else -> "已购买" to Color.parseColor("#007AFF")
        }
    }

    // ── 商品详情弹窗 ──────────────────────────────────
    private fun showProductDetail(p: ShopManager.Product) {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        v.addView(TextView(this).apply {
            text = p.emoji; textSize = 40f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        })
        v.addView(TextView(this).apply {
            text = p.name; textSize = 18f
            setTypeface(null, Typeface.BOLD); setTextColor(textDark)
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        })
        v.addView(TextView(this).apply {
            text = "¥" + ShopManager.fmt(p.price); textSize = 22f; setTextColor(mainColor)
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        v.addView(TextView(this).apply {
            text = "分区：" + p.category; textSize = 12f; setTextColor(textGray)
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
        })
        if (p.descr.isNotBlank()) {
            v.addView(TextView(this).apply {
                text = p.descr; textSize = 13f; setTextColor(Color.parseColor("#555555"))
                gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
            })
        }
        val dlg = AlertDialog.Builder(this).setView(v).create()
        val bBuy = makeButton("🛒 买给自己", true, 46)
        bBuy.setOnClickListener { dlg.dismiss(); confirmBuySelf(p) }
        v.addView(bBuy, LinearLayout.LayoutParams(-1, dp(46)).also { it.topMargin = dp(14) })
        val bGift = makeButton("🎁 送给TA", false, 42)
        bGift.setOnClickListener { dlg.dismiss(); pickContact { id, name -> confirmGift(p, id, name) } }
        val bShare = makeButton("↗ 分享给TA", false, 42)
        bShare.setOnClickListener { dlg.dismiss(); pickContact { id, name -> doShare(p, id, name) } }
        v.addView(rowBtns(bGift, bShare))
        val bPay = makeButton("🙏 求TA代付", false, 40)
        bPay.setOnClickListener { dlg.dismiss(); pickContact { id, name -> confirmRequestPay(p, id, name) } }
        val bEdit = makeButton("✏️ 编辑", false, 40)
        bEdit.setOnClickListener { dlg.dismiss(); showEditProduct(p) }
        val bDel = makeButton("🗑 删除", false, 40)
        bDel.setOnClickListener { dlg.dismiss(); confirmDelete(p) }
        v.addView(rowBtns(bPay, bEdit, bDel))
        dlg.show()
    }

    private fun rowBtns(vararg btns: TextView): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(10) }
        }
        btns.forEachIndexed { i, b ->
            val lp = LinearLayout.LayoutParams(0, b.layoutParams.height, 1f)
            if (i > 0) lp.marginStart = dp(8)
            b.layoutParams = lp
            row.addView(b)
        }
        return row
    }

    // ── 购买 / 送礼 / 分享 / 代付 ───────────────────────────
    private fun confirmBuySelf(p: ShopManager.Product) {
        AlertDialog.Builder(this)
            .setTitle("买给自己")
            .setMessage("确认购买「${p.name}」¥${ShopManager.fmt(p.price)}？")
            .setPositiveButton("确认购买") { _, _ ->
                ShopManager.purchase(this, p, "me", "", "")
                toast("已购买～可在订单里查看")
                refreshContent()
            }
            .setNegativeButton("再想想", null)
            .show()
    }

    private fun confirmGift(p: ShopManager.Product, aiId: String, aiName: String) {
        AlertDialog.Builder(this)
            .setTitle("送给 $aiName")
            .setMessage("把「${p.name}」¥${ShopManager.fmt(p.price)}送给TA？\nTA会在聊天里收到这份礼物")
            .setPositiveButton("送出") { _, _ ->
                ShopManager.purchase(this, p, "ai", aiId, aiName)
                toast("已送出，去和TA聊聊吧❤️")
                refreshContent()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doShare(p: ShopManager.Product, aiId: String, aiName: String) {
        ShopManager.shareProduct(this, p, aiId, aiName)
        toast("已分享给$aiName，去聊天里看看吧")
        refreshContent()
    }

    private fun confirmRequestPay(p: ShopManager.Product, aiId: String, aiName: String) {
        AlertDialog.Builder(this)
            .setTitle("求 $aiName 代付")
            .setMessage("向TA发一张代付请求卡：\n「${p.name}」¥${ShopManager.fmt(p.price)}\nTA同意后就会显示已代付")
            .setPositiveButton("发送请求") { _, _ ->
                ShopManager.requestPay(this, p, aiId, aiName)
                toast("已发送，等TA回应吧")
                refreshContent()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 选角色 ────────────────────────────────────
    private fun loadContacts(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            db.rawQuery("SELECT userId, realName FROM Contacts WHERE userId IS NOT NULL AND TRIM(userId) <> '' AND id IN (SELECT MAX(id) FROM Contacts WHERE userId IS NOT NULL AND TRIM(userId) <> '' GROUP BY userId) ORDER BY realName", null).use { c ->
                while (c.moveToNext()) {
                    list.add((c.getString(0) ?: "") to (c.getString(1) ?: ""))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun pickContact(callback: (String, String) -> Unit) {
        val contacts = loadContacts()
        if (contacts.isEmpty()) {
            toast("还没有角色，先去添加一个吧")
            return
        }
        val names = contacts.map { it.second.ifBlank { it.first } }
        AlertDialog.Builder(this)
            .setTitle("选择角色")
            .setItems(names.toTypedArray()) { _, w ->
                val (id, nm) = contacts[w]
                callback(id, names[w].ifBlank { nm })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 删除商品 ──────────────────────────────────
    private fun confirmDelete(p: ShopManager.Product) {
        AlertDialog.Builder(this)
            .setTitle("删除商品")
            .setMessage("确定把「${p.name}」下架删除吗？")
            .setPositiveButton("删除") { _, _ ->
                ShopManager.deleteProduct(db, p.id)
                toast("已删除")
                refreshContent()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 添加 / 编辑商品 ─────────────────────────────────
    private fun showEditProduct(existing: ShopManager.Product?) {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val etEmoji = EditText(this).apply {
            hint = "🛍️"; textSize = 20f
            layoutParams = LinearLayout.LayoutParams(dp(76), -2)
        }
        val etName = EditText(this).apply {
            hint = "商品名称"; textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        if (existing != null) { etEmoji.setText(existing.emoji); etName.setText(existing.name) }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(etEmoji); row1.addView(etName)
        val etPrice = EditText(this).apply {
            hint = "价格（元）"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        if (existing != null) etPrice.setText(ShopManager.fmt(existing.price))
        val etDescr = EditText(this).apply { hint = "卖点描述（可选）" }
        if (existing != null) etDescr.setText(existing.descr)
        var category = existing?.category ?: "其他"
        val cats0 = ShopManager.listCategories(db)
        if (!cats0.contains(category)) category = cats0.firstOrNull() ?: "其他"
        val tvCat = TextView(this).apply {
            text = "分区：$category"
            textSize = 14f; setTextColor(mainColor)
            setPadding(0, dp(14), 0, dp(6))
            isClickable = true
            setOnClickListener {
                val cats = ShopManager.listCategories(db)
                AlertDialog.Builder(this@ShopActivity)
                    .setTitle("选择分区")
                    .setItems(cats.toTypedArray()) { _, w ->
                        category = cats[w]
                        text = "分区：$category"
                    }
                    .show()
            }
        }
        v.addView(row1); v.addView(etPrice); v.addView(etDescr); v.addView(tvCat)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "添加商品" else "编辑商品")
            .setView(v)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) { toast("商品名称不能为空"); return@setPositiveButton }
                val price = etPrice.text.toString().trim().toDoubleOrNull() ?: 0.0
                val emoji = etEmoji.text.toString().trim().ifBlank { "🛍️" }
                val descr = etDescr.text.toString().trim()
                if (existing == null) ShopManager.addProduct(db, name, emoji, price, descr, category)
                else ShopManager.updateProduct(db, existing.id, name, emoji, price, descr, category)
                refreshContent()
                toast("已保存")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 管理分区 ────────────────────────────────────
    private fun showManageCategories() {
        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(300))
            addView(listLayout)
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("管理分区")
            .setView(scroll)
            .setNeutralButton("＋添加分区", null)
            .setPositiveButton("完成", null)
            .create()
        val cats = ShopManager.listCategories(db)
        cats.forEach { c ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(10), dp(20), dp(10))
            }
            row.addView(TextView(this).apply {
                text = c; textSize = 15f; setTextColor(textDark)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            if (c != "其他") {
                row.addView(TextView(this).apply {
                    text = "改名"; textSize = 13f; setTextColor(mainColor)
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    setOnClickListener { dlg.dismiss(); renameCategoryFlow(c) }
                })
                row.addView(TextView(this).apply {
                    text = "删除"; textSize = 13f; setTextColor(Color.parseColor("#C0C0C0"))
                    setPadding(dp(4), dp(4), 0, dp(4))
                    setOnClickListener { dlg.dismiss(); deleteCategoryFlow(c) }
                })
            }
            listLayout.addView(row)
        }
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dlg.dismiss()
                addCategoryFlow()
            }
        }
        dlg.show()
    }

    private fun addCategoryFlow() {
        val et = EditText(this).apply { hint = "分区名称" }
        AlertDialog.Builder(this)
            .setTitle("添加分区")
            .setView(et)
            .setPositiveButton("添加") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isEmpty()) return@setPositiveButton
                if (ShopManager.addCategory(db, n)) {
                    renderCategories()
                    toast("已添加分区「$n」")
                } else {
                    toast("这个分区已经存在啦")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renameCategoryFlow(old: String) {
        val et = EditText(this).apply { setText(old) }
        AlertDialog.Builder(this)
            .setTitle("重命名分区")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                val nn = et.text.toString().trim()
                if (nn.isNotEmpty() && nn != old) {
                    ShopManager.renameCategory(db, old, nn)
                    if (currentCategory == old) currentCategory = nn
                    renderCategories()
                    refreshContent()
                    toast("已重命名")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteCategoryFlow(c: String) {
        AlertDialog.Builder(this)
            .setTitle("删除分区")
            .setMessage("删除「$c」后，里面的商品会移到「其他」。确定吗？")
            .setPositiveButton("删除") { _, _ ->
                ShopManager.deleteCategory(db, c)
                if (currentCategory == c) currentCategory = "全部"
                renderCategories()
                refreshContent()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── AI 批量生成 ───────────────────────────────────
    private fun showGenerateDialog() {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val etTheme = EditText(this).apply { hint = "想要什么类型的商品？（例：冬天的保暖好物）" }
        val etCount = EditText(this).apply {
            hint = "数量"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("8")
        }
        v.addView(etTheme); v.addView(etCount)
        AlertDialog.Builder(this)
            .setTitle("✨ AI批量生成商品")
            .setView(v)
            .setPositiveButton("开始生成") { _, _ ->
                val theme = etTheme.text.toString().trim()
                if (theme.isEmpty()) {
                    toast("先说说想要什么类型的商品")
                    return@setPositiveButton
                }
                val count = etCount.text.toString().trim().toIntOrNull()?.coerceIn(1, 20) ?: 8
                doGenerate(theme, count)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doGenerate(theme: String, count: Int) {
        val loading = AlertDialog.Builder(this).setMessage("✨ 生成中，请稍候…").setCancelable(false).create()
        loading.show()
        ShopManager.generateProducts(this, theme, count, ShopManager.listCategories(db)) { list ->
            runOnUiThread {
                try { loading.dismiss() } catch (_: Exception) {}
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (list.isEmpty()) toast("生成失败，检查一下API设置或稍后重试")
                else showGenPreview(list)
            }
        }
    }

    private fun showGenPreview(list: List<ShopManager.GenProduct>) {
        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(320))
            addView(listLayout)
        }
        list.forEach { g ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(6), dp(16), dp(6))
            }
            val cb = CheckBox(this).apply { isChecked = true; tag = g }
            row.addView(cb)
            row.addView(TextView(this).apply {
                text = g.emoji; textSize = 20f
                setPadding(dp(4), 0, dp(8), 0)
            })
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            col.addView(TextView(this).apply {
                text = g.name; textSize = 14f; setTextColor(textDark); maxLines = 1
            })
            col.addView(TextView(this).apply {
                text = if (g.descr.isBlank()) (if (g.category.isBlank()) "自动分区" else g.category) else g.descr
                textSize = 11f; setTextColor(textGray); maxLines = 1
            })
            row.addView(col)
            row.addView(TextView(this).apply {
                text = "¥" + ShopManager.fmt(g.price); textSize = 14f; setTextColor(mainColor)
                setTypeface(null, Typeface.BOLD)
            })
            listLayout.addView(row)
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("已生成 ${list.size} 件，勾选要上架的")
            .setView(scroll)
            .setPositiveButton("导入选中", null)
            .setNegativeButton("取消", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cats = ShopManager.listCategories(db)
                var n = 0
                for (i in 0 until listLayout.childCount) {
                    val row = listLayout.getChildAt(i) as? android.view.ViewGroup ?: continue
                    val cb = row.getChildAt(0) as? CheckBox ?: continue
                    if (!cb.isChecked) continue
                    val g = cb.tag as? ShopManager.GenProduct ?: continue
                    val cat = if (cats.contains(g.category)) g.category else "其他"
                    ShopManager.addProduct(db, g.name, g.emoji, g.price, g.descr, cat)
                    n++
                }
                dlg.dismiss()
                if (n > 0) {
                    refreshContent()
                    toast("已上架 $n 件商品")
                }
            }
        }
        dlg.show()
    }
}
