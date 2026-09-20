package com.moon.aiphone

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 档位管理页：编辑 / 添加 / 删除 / 切换档位。 */
class SlotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slots)
        findViewById<TextView>(R.id.btnSlotBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnCreateSlot).setOnClickListener { showCreateDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val box = findViewById<LinearLayout>(R.id.slotListContainer) ?: return
        box.removeAllViews()
        val arr = SlotManager.listSlots(this)
        val current = SlotManager.currentSlotId(this)
        val inflater = LayoutInflater.from(this)
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val name = o.optString("name").ifBlank { "未命名档位" }
            val created = o.optLong("createdAt", 0L)
            val item = inflater.inflate(R.layout.item_slot, box, false)
            item.findViewById<TextView>(R.id.tvSlotName).text = name
            val dateStr = if (created > 0) df.format(Date(created)) else "初始"
            item.findViewById<TextView>(R.id.tvSlotInfo).text =
                if (id == current) "使用中 · 创建于 $dateStr" else "创建于 $dateStr"
            val btnSwitch = item.findViewById<TextView>(R.id.btnSlotSwitch)
            if (id == current) {
                btnSwitch.text = "使用中"
                btnSwitch.alpha = 0.4f
            } else {
                btnSwitch.setOnClickListener { confirmSwitch(id, name) }
            }
            item.findViewById<TextView>(R.id.btnSlotRename).setOnClickListener { showRenameDialog(id, name) }
            item.findViewById<TextView>(R.id.btnSlotDelete).setOnClickListener { confirmDelete(id, name) }
            box.addView(item)
        }
    }

    private fun confirmSwitch(id: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("切换到「$name」")
            .setMessage("切换后应用会自动重启，进入这个档位的独立数据。\n\n当前档位的数据会先安全归档，不会丢失。")
            .setPositiveButton("切换") { _, _ ->
                Toast.makeText(this, "正在切换档位…", Toast.LENGTH_SHORT).show()
                SlotManager.switchTo(this, id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCreateDialog() {
        val input = EditText(this)
        input.hint = "给新档位起个名字"
        val wrap = LinearLayout(this)
        wrap.setPadding(48, 24, 48, 0)
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("新建档位")
            .setMessage("新档位是全新的默认状态：没有角色、记忆、设定，和现有档位完全独立。")
            .setView(wrap)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "新档位" }
                SlotManager.createSlot(this, name)
                refreshList()
                Toast.makeText(this, "已创建「$name」，点“切换”即可进入", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameDialog(id: String, oldName: String) {
        val input = EditText(this)
        input.setText(oldName)
        val wrap = LinearLayout(this)
        wrap.setPadding(48, 24, 48, 0)
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("重命名档位")
            .setView(wrap)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) SlotManager.renameSlot(this, id, name)
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(id: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("删除档位「$name」？")
            .setMessage("该档位的全部数据将被删除，且不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                if (SlotManager.deleteSlot(this, id)) {
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "当前使用中的档位不能删除，请先切换到其他档位", Toast.LENGTH_LONG).show()
                }
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}