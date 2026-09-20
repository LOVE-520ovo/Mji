package com.moon.aiphone

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 虚拟时间统一工具。虚拟时间 = 真实时间 + 偏移（AppConfig.virtualOffsetMs）。将来档位系统只需把 KEY 按档位隔离。 */
object VirtualTime {

    private const val PREF = "AppConfig"
    private const val KEY = "virtualOffsetMs"

    fun offsetMs(ctx: Context): Long =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY, 0L)

    fun setOffsetMs(ctx: Context, value: Long) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong(KEY, value).apply()
    }

    fun nowMs(ctx: Context): Long = System.currentTimeMillis() + offsetMs(ctx)

    fun now(ctx: Context): Date = Date(nowMs(ctx))

    fun formatFull(ms: Long): String =
        SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm", Locale.CHINA).format(Date(ms))

    fun formatDate(ms: Long): String =
        SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(Date(ms))

    fun formatClock(ms: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(ms))

    fun fmtOffset(ms: Long): String {
        if (ms == 0L) return "0"
        var rest = Math.abs(ms)
        val d = rest / 86400000L
        rest %= 86400000L
        val h = rest / 3600000L
        rest %= 3600000L
        val m = rest / 60000L
        val sign = if (ms > 0) "+" else "-"
        var s = sign
        if (d > 0) s += d.toString() + "天"
        if (h > 0) s += h.toString() + "小时"
        if (m > 0) s += m.toString() + "分钟"
        if (s == sign) s = "0"
        return s
    }

    /** 按日历单位推进，field 用 Calendar.HOUR_OF_DAY / DAY_OF_MONTH / MONTH / YEAR，amount 可为负。 */
    fun advance(ctx: Context, field: Int, amount: Int): String {
        val base = Calendar.getInstance()
        base.timeInMillis = nowMs(ctx)
        base.add(field, amount)
        val newVirtual = base.timeInMillis
        setOffsetMs(ctx, newVirtual - System.currentTimeMillis())
        return formatFull(newVirtual)
    }

    /** 跳转到指定日期（保留当前虚拟时刻的时分秒） */
    fun jumpToDate(ctx: Context, year: Int, month: Int, day: Int): String {
        val base = Calendar.getInstance()
        base.timeInMillis = nowMs(ctx)
        base.set(year, month, day)
        val newVirtual = base.timeInMillis
        setOffsetMs(ctx, newVirtual - System.currentTimeMillis())
        return formatFull(newVirtual)
    }

    fun reset(ctx: Context) {
        setOffsetMs(ctx, 0L)
    }
}
