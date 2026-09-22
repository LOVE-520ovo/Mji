package com.moon.aiphone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

/**
 * 文字图（极简黑白 · 水玻璃）公用工具：私聊 / 群聊 / 用户 / 角色 共用。
 */
object TextImageUtil {

fun generate(text: String): Bitmap {
        val W = 1080
        val padX = 96
        val padTop = 120
        val padBottom = 150
        val cardPad = 76
        val maxTextW = W - padX * 2 - cardPad * 2
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F5F7.toInt()
            textSize = 50f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val lines = mutableListOf<String>()
        for (raw in text.split("\n")) {
            if (raw.isEmpty()) { lines.add(""); continue }
            var cur = ""
            for (ch in raw) {
                val t = cur + ch
                if (body.measureText(t) > maxTextW && cur.isNotEmpty()) {
                    lines.add(cur)
                    cur = ch.toString()
                } else {
                    cur = t
                }
            }
            if (cur.isNotEmpty()) lines.add(cur)
        }
        val lineH = body.textSize * 1.6f
        val textH = (lines.size * lineH).toInt()
        val cardH = textH + cardPad * 2
        val H = (padTop + cardH + padBottom).coerceAtLeast(900)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                0xFF0B0B0D.toInt(), 0xFF17171C.toInt(), Shader.TileMode.CLAMP)
        })
        fun glow(cx: Float, cy: Float, r: Float, a: Int) {
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, r,
                    intArrayOf((a shl 24) or 0xFFFFFF, 0x00FFFFFF),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            })
        }
        glow(W * 0.18f, H * 0.10f, W * 0.75f, 0x18)
        glow(W * 0.92f, H * 0.60f, W * 0.70f, 0x10)
        glow(W * 0.40f, H * 0.98f, W * 0.65f, 0x0C)
        val left = padX.toFloat()
        val top = padTop.toFloat()
        val right = (W - padX).toFloat()
        val bottom = (padTop + cardH).toFloat()
        val radius = 58f
        val rect = RectF(left, top, right, bottom)
        c.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF })
        c.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = LinearGradient(left, top, right, bottom,
                intArrayOf(0x5AFFFFFF.toInt(), 0x1AFFFFFF, 0x0DFFFFFF),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(RectF(left + radius, top + 1.5f, right - radius, top + 3.5f), 2f, 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(left + radius, 0f, right - radius, 0f,
                    intArrayOf(0x00FFFFFF, 0x45FFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            })
        var y = top + cardPad + body.textSize
        for (ln in lines) {
            if (ln.isNotEmpty()) c.drawText(ln, left + cardPad, y, body)
            y += lineH
        }
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x5AFFFFFF.toInt()
            textSize = 32f
        }
        val label = "·"
        c.drawText(label, W / 2f - foot.measureText(label) / 2f, H - padBottom * 0.45f, foot)
        return bmp
    }

    // ──


    /** 保存到 files/text_images/，返回文件。 */
    fun save(context: Context, bmp: Bitmap): File {
        val dir = File(context.filesDir, "text_images")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "textimg_" + System.currentTimeMillis() + ".png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    /** 判断用户消息是否在请求文字图/字卡。 */
    fun isTextCardRequest(content: String): Boolean {
        if (content.isBlank()) return false
        return listOf("文字图", "字卡", "文字卡").any { content.contains(it) }
    }
}
