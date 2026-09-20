package com.moon.aiphone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 档位系统：每个档位 = 一套完全独立的 App 数据（数据库 / 设置 / 文件 / 虚拟时间）。
 * 切换原理：把当前 live 数据归档到 files/slots/<旧档位>/，再把目标档位数据铺回 live 位置，然后重启进程。
 * 唯一不参与隔离的是 SlotGlobal（记录档位列表和当前档位），它始终留在 shared_prefs 里。
 */
object SlotManager {
    const val GLOBAL_PREFS = "SlotGlobal"
    private const val KEY_SLOTS = "slots_json"
    private const val KEY_CURRENT = "current_slot"
    const val DEFAULT_ID = "default"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE)

    fun currentSlotId(ctx: Context): String =
        prefs(ctx).getString(KEY_CURRENT, DEFAULT_ID) ?: DEFAULT_ID

    fun slotsRoot(ctx: Context): File {
        val d = File(ctx.filesDir, "slots")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun slotDir(ctx: Context, id: String): File = File(slotsRoot(ctx), id)

    /** 读取档位列表；首次使用时自动登记「默认档位」。 */
    fun listSlots(ctx: Context): JSONArray {
        val raw = prefs(ctx).getString(KEY_SLOTS, null)
        if (raw.isNullOrEmpty()) {
            val arr = JSONArray()
            val o = JSONObject()
            o.put("id", DEFAULT_ID)
            o.put("name", "默认档位")
            o.put("createdAt", 0L)
            arr.put(o)
            saveSlots(ctx, arr)
            return arr
        }
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }

    fun saveSlots(ctx: Context, arr: JSONArray) {
        prefs(ctx).edit().putString(KEY_SLOTS, arr.toString()).commit()
    }

    fun createSlot(ctx: Context, name: String) {
        val id = "slot_" + System.currentTimeMillis()
        slotDir(ctx, id).mkdirs()
        val arr = listSlots(ctx)
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("createdAt", System.currentTimeMillis())
        arr.put(o)
        saveSlots(ctx, arr)
    }

    fun renameSlot(ctx: Context, id: String, newName: String) {
        val arr = listSlots(ctx)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == id) { o.put("name", newName); break }
        }
        saveSlots(ctx, arr)
    }

    /** 删除档位（当前使用中的档位不可删）。 */
    fun deleteSlot(ctx: Context, id: String): Boolean {
        if (id == currentSlotId(ctx)) return false
        slotDir(ctx, id).deleteRecursively()
        val arr = listSlots(ctx)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") != id) out.put(o)
        }
        saveSlots(ctx, out)
        return true
    }

    /** 切换档位：归档 → 清空 → 铺开 → 记录 → 重启。 */
    fun switchTo(ctx: Context, targetId: String) {
        val current = currentSlotId(ctx)
        if (targetId == current) return
        archiveToSlot(ctx, current)
        clearLive(ctx)
        restoreFromSlot(ctx, targetId)
        prefs(ctx).edit().putString(KEY_CURRENT, targetId).commit()
        restartApp(ctx)
    }

    /** 把当前 live 数据归档到 slots/<slotId>/（先清空旧归档）。 */
    fun archiveToSlot(ctx: Context, slotId: String) {
        flushPrefs(ctx)
        checkpointDb(ctx)
        val dst = slotDir(ctx, slotId)
        dst.deleteRecursively()
        dst.mkdirs()
        val dataDir = ctx.applicationInfo.dataDir
        copyDir(File(dataDir, "databases"), File(dst, "databases"))
        copyDir(File(dataDir, "shared_prefs"), File(dst, "shared_prefs")) { it == GLOBAL_PREFS + ".xml" }
        copyDir(ctx.filesDir, File(dst, "files")) { it == "slots" || it.startsWith("slots/") }
    }

    /** 清空 live 数据（保留 slots 仓库与 SlotGlobal.xml）。 */
    fun clearLive(ctx: Context) {
        val dataDir = ctx.applicationInfo.dataDir
        File(dataDir, "databases").listFiles()?.forEach { it.deleteRecursively() }
        File(dataDir, "shared_prefs").listFiles()?.forEach {
            if (it.name != GLOBAL_PREFS + ".xml") it.deleteRecursively()
        }
        ctx.filesDir.listFiles()?.forEach {
            if (it.name != "slots") it.deleteRecursively()
        }
    }

    /** 把某个档位的数据铺回 live 位置。 */
    fun restoreFromSlot(ctx: Context, slotId: String) {
        val src = slotDir(ctx, slotId)
        if (!src.exists()) return
        val dataDir = ctx.applicationInfo.dataDir
        copyDir(File(src, "databases"), File(dataDir, "databases"))
        copyDir(File(src, "shared_prefs"), File(dataDir, "shared_prefs"))
        copyDir(File(src, "files"), ctx.filesDir) { it == "slots" || it.startsWith("slots/") }
    }

    /** 强制把所有 SharedPreferences 写盘（SlotGlobal 除外）。 */
    fun flushPrefs(ctx: Context) {
        val dir = File(ctx.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles()?.forEach { f ->
            if (!f.name.endsWith(".xml")) return@forEach
            val name = f.name.removeSuffix(".xml")
            if (name == GLOBAL_PREFS) return@forEach
            try { ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit() } catch (_: Exception) {}
        }
    }

    /** 让 SQLite 把 WAL 合并回主库，保证复制出的数据库完整。 */
    fun checkpointDb(ctx: Context) {
        try {
            if (!ctx.getDatabasePath("AiPhone.db").exists()) return
            DatabaseHelper(ctx).writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null)?.use { it.moveToFirst() }
        } catch (_: Exception) {}
    }

    /** 递归复制目录。exclude 收到的是相对路径（用 / 分隔）。 */
    fun copyDir(src: File, dst: File, rel: String = "", exclude: (String) -> Boolean = { false }) {
        if (exclude(rel)) return
        if (!src.exists()) return
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            src.listFiles()?.forEach { f ->
                val r = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                copyDir(f, File(dst, f.name), r, exclude)
            }
        } else {
            try {
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
            } catch (_: Exception) {}
        }
    }

    /** 重启 App（AlarmManager 兜底拉起 + 自杀当前进程）。 */
    fun restartApp(ctx: Context) {
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val pi = PendingIntent.getActivity(
                    ctx, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.set(AlarmManager.RTC, System.currentTimeMillis() + 600, pi)
            }
        } catch (_: Exception) {}
        android.os.Process.killProcess(android.os.Process.myPid())
        kotlin.system.exitProcess(0)
    }
}

/**
 * 全量备份导出 / 导入（zip 整包：数据库 + 设置 + 文件；不含档位仓库与档位列表）。
 */
object AppBackup {

    /** 导出到用户选定的文件位置。 */
    fun exportTo(ctx: Context, uri: android.net.Uri) {
        try {
            SlotManager.flushPrefs(ctx)
            SlotManager.checkpointDb(ctx)
            val dataDir = ctx.applicationInfo.dataDir
            val os = ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("无法写入所选位置")
            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(os)).use { zos ->
                zipDir(zos, File(dataDir, "databases"), "databases")
                zipDir(zos, File(dataDir, "shared_prefs"), "shared_prefs") { rel -> rel == SlotManager.GLOBAL_PREFS + ".xml" }
                zipDir(zos, ctx.filesDir, "files") { rel -> rel == "slots" || rel.startsWith("slots/") }
            }
            toast(ctx, "✅ 全量备份已导出到所选位置")
        } catch (e: Exception) {
            toast(ctx, "导出失败：${e.message}")
        }
    }

    /** 从用户选定的文件导入（覆盖当前档位）。 */
    fun importFrom(ctx: Context, uri: android.net.Uri) {
        toast(ctx, "正在读取备份文件…")
        Thread {
            try {
                // 1) 解压到缓存
                val tmp = File(ctx.cacheDir, "mji_restore_tmp")
                tmp.deleteRecursively()
                tmp.mkdirs()
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    java.util.zip.ZipInputStream(java.io.BufferedInputStream(ins)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (!entry.isDirectory && !name.isNullOrBlank()) {
                                val out = File(tmp, name)
                                if (out.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) {
                                    out.parentFile?.mkdirs()
                                    out.outputStream().use { zis.copyTo(it) }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("无法读取所选文件")
                // 2) 校验
                if (!File(tmp, "databases/AiPhone.db").exists()) {
                    tmp.deleteRecursively()
                    toast(ctx, "这个文件不是有效的 Mji 全量备份（缺少数据库）")
                    return@Thread
                }
                // 3) 当前数据先安全备份到缓存
                val safety = File(ctx.cacheDir, "mji_restore_safety")
                safety.deleteRecursively()
                safety.mkdirs()
                val dataDir = ctx.applicationInfo.dataDir
                SlotManager.copyDir(File(dataDir, "databases"), File(safety, "databases"))
                SlotManager.copyDir(File(dataDir, "shared_prefs"), File(safety, "shared_prefs")) { rel -> rel == SlotManager.GLOBAL_PREFS + ".xml" }
                // 4) 清空并写入
                SlotManager.clearLive(ctx)
                SlotManager.copyDir(File(tmp, "databases"), File(dataDir, "databases"))
                SlotManager.copyDir(File(tmp, "shared_prefs"), File(dataDir, "shared_prefs"))
                SlotManager.copyDir(File(tmp, "files"), ctx.filesDir) { rel -> rel == "slots" || rel.startsWith("slots/") }
                tmp.deleteRecursively()
                toast(ctx, "🔥 导入完成，应用即将重启…")
                Thread.sleep(700)
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(0)
            } catch (e: Exception) {
                toast(ctx, "导入失败：${e.message}")
            }
        }.start()
    }

    private fun zipDir(zos: java.util.zip.ZipOutputStream, dir: File, prefix: String, rel: String = "", exclude: (String) -> Boolean = { false }) {
        if (exclude(rel)) return
        if (!dir.exists()) return
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { f ->
                val r = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                zipDir(zos, f, prefix, r, exclude)
            }
        } else {
            try {
                zos.putNextEntry(java.util.zip.ZipEntry("$prefix/$rel"))
                dir.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            } catch (_: Exception) {}
        }
    }

    private fun toast(ctx: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
