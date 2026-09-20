package com.moon.aiphone

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale

class VirtualTimeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var spanField = Calendar.DAY_OF_MONTH
    private var spanLabel = "1天"
    private val chips = mutableListOf<Pair<TextView, Int>>()
    private var lastDayKey = -1

    private val tick = object : Runnable {
        override fun run() {
            refreshHeader()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_virtual_time)
        supportActionBar?.hide()

        findViewById<TextView>(R.id.tvVtBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvVtRoleSchedule).setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        val defs = listOf(
            R.id.chipSpanHour to Calendar.HOUR_OF_DAY,
            R.id.chipSpanDay to Calendar.DAY_OF_MONTH,
            R.id.chipSpanMonth to Calendar.MONTH,
            R.id.chipSpanYear to Calendar.YEAR
        )
        for ((id, field) in defs) {
            val tv = findViewById<TextView>(id)
            chips.add(tv to field)
            tv.setOnClickListener {
                spanField = field
                spanLabel = when (field) {
                    Calendar.HOUR_OF_DAY -> "1小时"
                    Calendar.DAY_OF_MONTH -> "1天"
                    Calendar.MONTH -> "1个月"
                    else -> "1年"
                }
                styleChips()
            }
        }

        findViewById<TextView>(R.id.btnVtForward).setOnClickListener {
            val r = VirtualTime.advance(this, spanField, 1)
            afterChange("⏩ 推进 $spanLabel，当前虚拟时间：$r")
        }
        findViewById<TextView>(R.id.btnVtBackward).setOnClickListener {
            val r = VirtualTime.advance(this, spanField, -1)
            afterChange("⏪ 回退 $spanLabel，当前虚拟时间：$r")
        }
        findViewById<TextView>(R.id.btnVtJump).setOnClickListener { showDatePicker() }
        findViewById<TextView>(R.id.btnVtReset).setOnClickListener {
            VirtualTime.reset(this)
            afterChange("↩️ 已重置为真实时间")
        }

        styleChips()
        styleButtons()
        refreshAll()
        handler.post(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
    }

    private fun afterChange(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        refreshAll()
    }

    private fun refreshAll() {
        refreshHeader()
        renderCalendar()
    }

    private fun refreshHeader() {
        val ms = VirtualTime.nowMs(this)
        findViewById<TextView>(R.id.tvVtDate).text = VirtualTime.formatDate(ms)
        findViewById<TextView>(R.id.tvVtClock).text = VirtualTime.formatClock(ms)
        val off = VirtualTime.offsetMs(this)
        findViewById<TextView>(R.id.tvVtOffset).text = if (off == 0L) "与真实时间一致"
        else "偏移：" + VirtualTime.fmtOffset(off) + "　（真实时间 " +
                java.text.SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(java.util.Date()) + "）"
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        val dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        if (dayKey != lastDayKey) {
            lastDayKey = dayKey
            renderCalendar()
        }
    }

    private fun renderCalendar() {
        try {
            val gl = findViewById<GridLayout>(R.id.glVtDays)
            gl.removeAllViews()
            val cal = Calendar.getInstance()
            cal.timeInMillis = VirtualTime.nowMs(this)
            val vYear = cal.get(Calendar.YEAR)
            val vMonth = cal.get(Calendar.MONTH)
            val vDay = cal.get(Calendar.DAY_OF_MONTH)
            findViewById<TextView>(R.id.tvVtCalendarMonth).text = "${vYear}年${vMonth + 1}月"

            val first = Calendar.getInstance()
            first.timeInMillis = VirtualTime.nowMs(this)
            first.set(Calendar.DAY_OF_MONTH, 1)
            val firstDow = first.get(Calendar.DAY_OF_WEEK)
            val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)

            val sizePx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics
            ).toInt()

            fun addCell(text: String, highlight: Boolean, day: Int) {
                val tv = TextView(this)
                tv.text = text
                tv.gravity = Gravity.CENTER
                tv.textSize = 15f
                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                )
                lp.width = sizePx
                lp.height = sizePx
                tv.layoutParams = lp
                if (highlight) {
                    tv.setTextColor(Color.WHITE)
                    tv.setTypeface(null, Typeface.BOLD)
                    val bg = GradientDrawable()
                    bg.shape = GradientDrawable.OVAL
                    bg.setColor(Color.parseColor("#4A4A4A"))
                    tv.background = bg
                } else {
                    tv.setTextColor(Color.parseColor("#CCCCCC"))
                }
                if (day > 0) {
                    tv.setOnClickListener { jumpToDay(day) }
                }
                gl.addView(tv)
            }

            for (i in 1 until firstDow) addCell("", false, 0)
            for (d in 1..daysInMonth) addCell(d.toString(), d == vDay, d)
        } catch (e: Exception) {
        }
    }

    private fun jumpToDay(day: Int) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = VirtualTime.nowMs(this)
        val r = VirtualTime.jumpToDate(this, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), day)
        afterChange("📅 已跳转到 $r")
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = VirtualTime.nowMs(this)
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val r = VirtualTime.jumpToDate(this, y, m, d)
                afterChange("📅 已跳转到 $r")
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun roundBg(tv: TextView, colorHex: String, radius: Float) {
        val bg = GradientDrawable()
        bg.cornerRadius = radius
        bg.setColor(Color.parseColor(colorHex))
        tv.background = bg
    }

    private fun styleChips() {
        for ((tv, field) in chips) {
            val sel = field == spanField
            tv.setTextColor(if (sel) Color.WHITE else Color.parseColor("#999999"))
            roundBg(tv, if (sel) "#4A4A4A" else "#1E1E1E", 24f)
        }
    }

    private fun styleButtons() {
        roundBg(findViewById(R.id.btnVtForward), "#4A4A4A", 28f)
        roundBg(findViewById(R.id.btnVtBackward), "#2E2E2E", 28f)
        roundBg(findViewById(R.id.btnVtJump), "#1E1E1E", 24f)
        roundBg(findViewById(R.id.btnVtReset), "#1E1E1E", 24f)
    }
}