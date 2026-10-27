package com.ruyue.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ruyue.alarm.ui.*
import com.ruyue.alarm.ui.theme.RuyueTheme
import dagger.hilt.android.AndroidEntryPoint

sealed interface Route {
    /** 首页：提醒列表（M1 起） */
    data object List : Route

    /** id == 0 表示新建 */
    data class Edit(val id: Long) : Route

    /** M0 验证台：不再是首页，但完整保留 —— 排障与回归全靠它 */
    data object Dev : Route

    /** 设置：权限引导 / 日志 / M0 验证台入口（v0.8.2 从首页右上角收进来） */
    data object Settings : Route

    data object Wifi : Route
    data object Sentinel : Route
    data object Alarm : Route
    data object Stats : Route
    data object Amap : Route
    data object Guide : Route
    data object Logs : Route
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RuyueTheme {
                RuyueRoot()
            }
        }
    }
}

@Composable
fun RuyueRoot(
    m0vm: M0ViewModel = hiltViewModel(),
    reminderVm: ReminderViewModel = hiltViewModel(),
) {
    var route by remember { mutableStateOf<Route>(Route.List) }

    LaunchedEffect(route) { m0vm.refreshAll() }

    if (route != Route.List) {
        BackHandler { route = Route.List }
    }

    val backToList = { route = Route.List }
    val backToDev = { route = Route.Dev }

    // 统一底色：让"窗口背景"和"Compose 背景"一致，深色模式下不会露出白边/白闪
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val current = route) {
            Route.List -> ReminderListScreen(
                vm = reminderVm,
                onEdit = { id -> route = Route.Edit(id) },
                onOpenSettings = { route = Route.Settings },
            )

            is Route.Edit -> ReminderEditScreen(
                vm = reminderVm,
                id = current.id,
                onBack = backToList,
            )

            Route.Settings -> SettingsScreen(
                onBack = backToList,
                onOpenGuide = { route = Route.Guide },
                onOpenLogs = { route = Route.Logs },
                onOpenDev = { route = Route.Dev },
            )

            Route.Dev -> HomeScreen(vm = m0vm, onOpen = { route = it })
            Route.Wifi -> WifiProbeScreen(vm = m0vm, onBack = backToDev)
            Route.Sentinel -> SentinelScreen(vm = m0vm, onBack = backToDev)
            Route.Alarm -> AlarmTestScreen(vm = m0vm, onBack = backToDev)
            Route.Stats -> DebounceStatsScreen(vm = m0vm, onBack = backToDev)
            Route.Amap -> AmapFenceScreen(onBack = backToDev)
            // 引导页 / 日志页都从设置进入，所以返回也回设置
            Route.Guide -> GuideScreen(onBack = { route = Route.Settings })
            Route.Logs -> LogsScreen(vm = m0vm, onBack = { route = Route.Settings })
        }
    }
}
