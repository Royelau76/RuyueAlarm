package com.ruyue.alarm.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 各品牌的「必须手动开的四样东西」叫什么、在哪里。
 *
 * 为什么需要这一层（2026-11-11 加入，阶段 1：只做引导与自检）：
 *  · 自启动 / 后台弹出界面（悬浮窗）**没有任何公开 API 能检测**，只能引导用户手动开；
 *  · 各家的叫法、路径、甚至有没有这个开关都不一样（三星根本没有自启动开关）；
 *  · **小米（HyperOS）分支的文字与之前完全一致** —— 这一层不改变小米机上的任何行为。
 *
 * 约定：这里只提供「文案 + 尽力而为的跳转 Intent」，不碰触发逻辑。
 * 跳转失败时调用方应降级到 [appDetails]（各家都能打开应用详情页）。
 */
object OemPermission {

    data class Brand(
        /** 机器识别用的 key（xiaomi/huawei/oppo/vivo/samsung/other） */
        val key: String,
        /** 给用户看的品牌名 */
        val label: String,
        /** 自启动的设置路径 */
        val autostart: String,
        /** 后台弹出界面 / 悬浮窗的路径 */
        val popup: String,
        /** 电池 / 省电策略的路径 */
        val battery: String,
        /** 这个品牌额外要注意的一句 */
        val note: String,
    )

    fun current(): Brand {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") -> Brand(
                key = "xiaomi",
                label = "小米 / HyperOS",
                autostart = "手机管家 → 应用管理 → 权限 → 自启动管理",
                popup = "应用管理 → 权限管理 → 其他权限 → 后台弹出界面",
                battery = "应用管理 → 如约 → 省电策略 → 无限制",
                note = "自启动和「后台弹出界面」是小米私有权限，adb 授不了，只能手动点。",
            )
            m.contains("huawei") || m.contains("honor") -> Brand(
                key = "huawei",
                label = "华为 / 荣耀",
                autostart = "手机管家 → 应用启动管理 → 如约 → 手动管理（三个开关全开）",
                popup = "设置 → 应用 → 权限管理 → 悬浮窗 / 后台弹窗",
                battery = "设置 → 电池 → 应用启动管理 → 允许后台活动",
                note = "华为杀后台最狠：把「应用启动管理」从自动改成手动，三项全开。",
            )
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> Brand(
                key = "oppo",
                label = "OPPO / 一加 / 真我",
                autostart = "设置 → 应用管理 → 自启动管理",
                popup = "设置 → 应用管理 → 特殊应用权限 → 悬浮窗",
                battery = "设置 → 电池 → 耗电保护 → 如约 → 允许后台运行",
                note = "ColorOS 还要开「关联启动」，否则被系统拉起时会被拦。",
            )
            m.contains("vivo") || m.contains("iqoo") -> Brand(
                key = "vivo",
                label = "vivo / iQOO",
                autostart = "设置 → 应用与权限 → 权限管理 → 自启动",
                popup = "设置 → 应用管理 → 权限 → 后台弹出界面",
                battery = "设置 → 电池 → 后台高耗电 → 允许",
                note = "OriginOS 不开「后台高耗电」，息屏后会被断 WiFi。",
            )
            m.contains("samsung") -> Brand(
                key = "samsung",
                label = "三星 / One UI",
                autostart = "三星没有自启动开关，不用设",
                popup = "设置 → 应用 → 特殊访问权限 → 在其他应用上层显示",
                battery = "设置 → 电池 → 背景使用限制 → 不受限制",
                note = "还要关掉「自适应电池」和「未使用应用休眠」。",
            )
            else -> Brand(
                key = "other",
                label = "其他机型",
                autostart = "系统设置里搜「自启动」或「开机启动」",
                popup = "系统设置里搜「悬浮窗」或「在其他应用上层显示」",
                battery = "系统设置 → 电池 → 如约 → 不受限制",
                note = "各厂商叫法不同，按上面关键词在设置里搜最快。",
            )
        }
    }

    /** 应用详情页：所有品牌都有，作为跳转失败后的兜底 */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationSettings(context: Context): Intent =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimization(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android 14+ 才有「全屏通知」的独立开关页；更早的系统打开应用详情页 */
    fun fullScreenIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            appDetails(context)
        }

    /** 自启动管理页：只有小米/OPPO/vivo/华为有可尝试的直连，其余返回 null（由调用方降级） */
    fun autostart(context: Context): Intent? {
        val candidates = when (current().key) {
            "xiaomi" -> listOf(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            )
            "huawei" -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            "oppo" -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
            )
            "vivo" -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull()?.let {
            Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 悬浮窗 / 后台弹出界面：能直连的直连，不能的由调用方打开应用详情页 */
    fun overlay(context: Context): Intent? = when (current().key) {
        "xiaomi" -> Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        "samsung" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else -> null
    }
}
