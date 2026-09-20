package com.moon.aiphone

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class DoorContact(val id: String, val name: String)

class OfflineContactActivity : AppCompatActivity() {
    private val selected = linkedSetOf<String>()
    private var allList: List<DoorContact> = emptyList()
    private var confirmBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000"))
        }
        val title = TextView(this).apply {
            text = "选择要推开的门"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 60, 0, 10)
            gravity = Gravity.CENTER
        }
        root.addView(title)
        val sub = TextView(this).apply {
            text = "可多选：选多位角色 = 多人线下见面"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 30)
            gravity = Gravity.CENTER
        }
        root.addView(sub)
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@OfflineContactActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(rv)
        val confirm = Button(this).apply {
            text = "🚪 推开门"
            setTextColor(Color.WHITE)
            textSize = 16f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#4A7C59"))
                cornerRadius = 16f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 140
            ).apply { setMargins(50, 24, 50, 60) }
            setOnClickListener { startMeeting() }
        }
        confirmBtn = confirm
        root.addView(confirm)
        setContentView(root)

        val list = mutableListOf<DoorContact>()
        try {
            val db = DatabaseHelper(this).readableDatabase
            val cur = db.rawQuery(
                """
    SELECT userId, realName
    FROM Contacts
    WHERE userId IS NOT NULL
      AND TRIM(userId) <> ''
      AND id IN (
          SELECT MAX(id)
          FROM Contacts
          WHERE userId IS NOT NULL
            AND TRIM(userId) <> ''
          GROUP BY userId
      )
    ORDER BY id DESC
    """.trimIndent(),
                null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(
                        DoorContact(
                            it.getString(0) ?: "",
                            it.getString(1) ?: "未知"
                        )
                    )
                }
            }
        } catch (e: Exception) {}
        allList = list
        rv.adapter = DoorAdapter(list)
    }

    private fun startMeeting() {
        val picked = allList.filter { selected.contains(it.id) }
        if (picked.isEmpty()) {
            Toast.makeText(this, "至少选择一位角色", Toast.LENGTH_SHORT).show()
            return
        }
        val first = picked[0]
        val intent = Intent(this, OfflineSetupActivity::class.java).apply {
            putExtra("AI_ID", first.id)
            putExtra("AI_NAME", first.name)
            putExtra("AI_IDS", picked.map { it.id }.toTypedArray())
            putExtra("AI_NAMES", picked.map { it.name }.toTypedArray())
        }
        startActivity(intent)
    }

    private fun refreshConfirm() {
        confirmBtn?.text = if (selected.isEmpty()) "🚪 推开门"
        else "🚪 推开门（已选 ${selected.size} 位）"
    }

    inner class DoorAdapter(val data: List<DoorContact>) : RecyclerView.Adapter<DoorAdapter.VH>() {
        inner class VH(val row: LinearLayout, val nameTv: TextView, val checkTv: TextView) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(50, 50, 50, 50)
                setBackgroundColor(Color.parseColor("#1A1A1A"))
            }
            val nameTv = TextView(parent.context).apply {
                textSize = 18f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val checkTv = TextView(parent.context).apply {
                textSize = 20f
                setTextColor(Color.parseColor("#4A7C59"))
                text = "☐"
            }
            row.addView(nameTv)
            row.addView(checkTv)
            return VH(row, nameTv, checkTv)
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val item = data[pos]
            val isSel = selected.contains(item.id)
            holder.nameTv.text = "🚪 ${item.name}"
            holder.checkTv.text = if (isSel) "☑" else "☐"
            holder.row.setBackgroundColor(
                if (isSel) Color.parseColor("#26332B") else Color.parseColor("#1A1A1A")
            )
            holder.row.setOnClickListener {
                if (selected.contains(item.id)) selected.remove(item.id) else selected.add(item.id)
                notifyItemChanged(pos)
                refreshConfirm()
            }
        }

        override fun getItemCount() = data.size
    }
}
