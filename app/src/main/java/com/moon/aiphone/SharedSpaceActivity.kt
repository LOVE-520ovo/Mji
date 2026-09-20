package com.moon.aiphone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SharedSpaceActivity : AppCompatActivity() {

    data class SharedEntry(
        val id: Int,
        val aiId: String,
        val memoryText: String,
        val shareTargets: String,
        val insertTime: Long,
        val sourceName: String,
        val targetsText: String
    )

    private lateinit var rv: RecyclerView
    private var contacts = listOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_space)
        supportActionBar?.hide()
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnAddShared).setOnClickListener { showAddFlow() }
        rv = findViewById(R.id.rvSharedMemories)
        rv.layoutManager = LinearLayoutManager(this)
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        loadShared()
    }

    private fun loadContacts() {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val db = DatabaseHelper(this).readableDatabase
            db.rawQuery(
                "SELECT userId, realName FROM Contacts WHERE userId IS NOT NULL AND TRIM(userId)<>'' ORDER BY id ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val uid = c.getString(0) ?: continue
                    list.add(uid to (c.getString(1) ?: uid))
                }
            }
        } catch (_: Exception) {}
        contacts = list
    }

    private fun nameOf(aiId: String): String =
        if (aiId == "__shared__") "共享空间" else contacts.firstOrNull { it.first == aiId }?.second ?: aiId

    private fun targetsLabel(t: String): String = when {
        t == "all" -> "全部角色"
        t.isEmpty() -> "（未设置）"
        else -> t.split(",").filter { it.isNotBlank() }.joinToString("、") { nameOf(it) }
    }

    private fun loadShared() {
        val list = mutableListOf<SharedEntry>()
        try {
            val db = DatabaseHelper(this).readableDatabase
            db.rawQuery(
                "SELECT id, aiId, memoryText, IFNULL(shareTargets,''), IFNULL(insertTime,0) FROM MemoryBank WHERE IFNULL(shareTargets,'')<>'' ORDER BY insertTime DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        SharedEntry(
                            c.getInt(0),
                            c.getString(1) ?: "",
                            c.getString(2) ?: "",
                            c.getString(3) ?: "",
                            c.getLong(4),
                            nameOf(c.getString(1) ?: ""),
                            targetsLabel(c.getString(3) ?: "")
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        val emptyTv = findViewById<TextView>(R.id.tvEmptyShared)
        emptyTv.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        rv.adapter = SharedAdapter(list) { entry -> showManageDialog(entry) }
    }

    // 多选生效角色（"🌟全部角色" + 各联系人），回调返回 shareTargets 格式字符串
    private fun showPickTargets(pre: String, onPicked: (String) -> Unit) {
        val items = arrayOf("🌟 全部角色") + contacts.map { it.second }.toTypedArray()
        val checked = BooleanArray(items.size)
        if (pre == "all") {
            checked[0] = true
        } else if (pre.isNotEmpty()) {
            val selIds = pre.split(",").filter { it.isNotBlank() }
            for (i in contacts.indices) if (selIds.contains(contacts[i].first)) checked[i + 1] = true
        }
        AlertDialog.Builder(this)
            .setTitle("选择生效角色")
            .setMultiChoiceItems(items, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("确定") { _, _ ->
                val targets: String = if (checked[0]) "all"
                else {
                    val sel = contacts.filterIndexed { i, _ -> checked[i + 1] }.map { it.first }
                    if (sel.isEmpty()) "" else "," + sel.joinToString(",") + ","
                }
                onPicked(targets)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddFlow() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val etContent = EditText(this).apply {
            hint = "输入要让多个角色都记住的事…"
            minLines = 3
            maxLines = 6
        }
        layout.addView(etContent)
        AlertDialog.Builder(this)
            .setTitle("➕ 手动添加共享记忆")
            .setView(layout)
            .setPositiveButton("下一步：选角色") { _, _ ->
                val content = etContent.text.toString().trim()
                if (content.isEmpty()) {
                    android.widget.Toast.makeText(this, "内容不能为空", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                showPickTargets("") { targets ->
                    if (targets.isEmpty()) {
                        android.widget.Toast.makeText(this, "至少要选一个角色", android.widget.Toast.LENGTH_SHORT).show()
                        return@showPickTargets
                    }
                    try {
                        val cv = android.content.ContentValues().apply {
                            put("aiId", "__shared__")
                            put("memoryText", content)
                            put("category", "shared")
                            put("insertTime", System.currentTimeMillis())
                            put("shareTargets", targets)
                        }
                        DatabaseHelper(this).writableDatabase.insert("MemoryBank", null, cv)
                        android.widget.Toast.makeText(this, "已添加共享记忆", android.widget.Toast.LENGTH_SHORT).show()
                        loadShared()
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showManageDialog(entry: SharedEntry) {
        val isManual = entry.aiId == "__shared__"
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(entry.insertTime))
        val info = buildString {
            append(entry.memoryText)
            append("\n\n—— 来源：").append(entry.sourceName)
            append("\n—— 生效：").append(entry.targetsText)
            append("\n—— 时间：").append(timeStr)
        }
        val b = AlertDialog.Builder(this)
            .setTitle("🔗 共享记忆")
            .setMessage(info)
            .setPositiveButton("修改角色") { _, _ ->
                showPickTargets(entry.shareTargets) { targets ->
                    if (targets.isEmpty()) {
                        android.widget.Toast.makeText(this, "已取消共享", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    try {
                        val cv = android.content.ContentValues().apply { put("shareTargets", targets) }
                        DatabaseHelper(this).writableDatabase.update("MemoryBank", cv, "id=?", arrayOf(entry.id.toString()))
                    } catch (_: Exception) {}
                    loadShared()
                }
            }
        if (isManual) {
            b.setNeutralButton("改内容") { _, _ -> showEditContent(entry) }
            b.setNegativeButton("删除") { _, _ ->
                try {
                    DatabaseHelper(this).writableDatabase.delete("MemoryBank", "id=?", arrayOf(entry.id.toString()))
                } catch (_: Exception) {}
                loadShared()
            }
        } else {
            b.setNeutralButton("取消共享") { _, _ ->
                try {
                    val cv = android.content.ContentValues().apply { put("shareTargets", "") }
                    DatabaseHelper(this).writableDatabase.update("MemoryBank", cv, "id=?", arrayOf(entry.id.toString()))
                } catch (_: Exception) {}
                loadShared()
            }
            b.setNegativeButton("关闭", null)
        }
        b.show()
    }

    private fun showEditContent(entry: SharedEntry) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val etContent = EditText(this).apply {
            setText(entry.memoryText)
            minLines = 3
            maxLines = 6
        }
        layout.addView(etContent)
        AlertDialog.Builder(this)
            .setTitle("编辑内容")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val newText = etContent.text.toString().trim()
                if (newText.isNotEmpty()) {
                    try {
                        val cv = android.content.ContentValues().apply {
                            put("memoryText", newText)
                            put("embedding", "")
                        }
                        DatabaseHelper(this).writableDatabase.update("MemoryBank", cv, "id=?", arrayOf(entry.id.toString()))
                        loadShared()
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    class SharedAdapter(
        private val items: List<SharedEntry>,
        private val onClick: (SharedEntry) -> Unit
    ) : RecyclerView.Adapter<SharedAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.tvSharedText)
            val tvInfo: TextView = v.findViewById(R.id.tvSharedInfo)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_shared_memory, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(item.insertTime))
            holder.tvText.text = item.memoryText
            holder.tvInfo.text = "来源：" + item.sourceName + "｜生效：" + item.targetsText + "\n" + timeStr
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener { onClick(item); true }
        }
        override fun getItemCount() = items.size
    }
}