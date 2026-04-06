package com.monkeycode.ctyunkeepalive.ui

import android.Manifest
import android.content.Intent
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monkeycode.ctyunkeepalive.core.AppConfig
import com.monkeycode.ctyunkeepalive.core.AppSettings
import com.monkeycode.ctyunkeepalive.core.LogEntry
import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.core.StoredAccount
import com.monkeycode.ctyunkeepalive.core.formatTime
import com.monkeycode.ctyunkeepalive.core.maskAccount
import kotlinx.coroutines.flow.collect

private enum class MainTab(val title: String) {
    Home("首页"),
    Accounts("账号"),
    Logs("日志"),
    Config("配置"),
    System("系统"),
}

@Composable
fun CtyunApp(
    viewModel: MainViewModel,
    initialTab: String?,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var currentTab by rememberSaveable {
        mutableStateOf(if (initialTab == "logs") MainTab.Logs else MainTab.Home)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        showSplash = false
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val permissions = buildList {
            addAll(viewModel.requiredLogPermissions())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.distinct().toTypedArray()
        val denied = permissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty() && denied) {
            permissionLauncher.launch(permissions)
        }
    }

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.manualRunCompleted.collect {
            snackbarHostState.showSnackbar("保活测试完成，日志已保存到本地文件夹")
        }
    }

    MaterialTheme {
        if (showSplash) {
            SplashScreen(uiState.dashboard)
            return@MaterialTheme
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    listOf(
                        MainTab.Home to Icons.Default.Dashboard,
                        MainTab.Accounts to Icons.Default.ManageAccounts,
                        MainTab.Logs to Icons.AutoMirrored.Filled.ListAlt,
                        MainTab.Config to Icons.Default.Build,
                        MainTab.System to Icons.Default.Settings,
                    ).forEach { (tab, icon) ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            },
        ) { padding ->
            when (currentTab) {
                MainTab.Home -> HomeScreen(uiState, padding, context, viewModel)
                MainTab.Accounts -> AccountsScreen(uiState.accounts, padding, viewModel)
                MainTab.Logs -> LogsScreen(uiState.logs, uiState.logDirectoryPath, padding, viewModel)
                MainTab.Config -> ConfigScreen(uiState.settings, padding, viewModel)
                MainTab.System -> SystemScreen(uiState, padding, viewModel)
            }
        }
    }
}

@Composable
private fun SplashScreen(dashboard: com.monkeycode.ctyunkeepalive.core.DashboardState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("天翼云手机保活", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Version 1.0.6")
            Text("ROOT: ${if (dashboard.rootGranted) "已授权" else "检测中"}")
            Text("Python: ${if (dashboard.pythonReady) "已加载" else "加载中"}")
            Text("OCR: ${if (dashboard.ocrReady) "已初始化" else "初始化中"}")
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: MainUiState,
    padding: PaddingValues,
    context: Context,
    viewModel: MainViewModel,
) {
    val dashboard = uiState.dashboard
    val clipboard = LocalClipboardManager.current
    val statusText = when {
        dashboard.runStats.running -> "运行中"
        dashboard.runStats.currentProgress.contains("后台") -> "后台待命"
        else -> "已停止"
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("天翼云手机保活", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("状态: $statusText")
                    Text("ROOT: ${if (dashboard.rootGranted) "已授权" else "未授权"}")
                    Text("OCR: ${if (dashboard.ocrReady) "离线模型已就绪" else "未初始化"}")
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("控制区", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.startService(context) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("启动后台保活")
                        }
                        Button(
                            onClick = { viewModel.runImmediateTest(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00)),
                        ) {
                            Text("立即测试保活")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { viewModel.stop(context) }, modifier = Modifier.weight(1f)) {
                            Text("停止服务")
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(uiState.logDirectoryPath))
                                Toast.makeText(context, "日志路径已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("复制日志路径")
                        }
                    }
                    Text("日志已保存到: ${uiState.logDirectoryPath}", color = MaterialTheme.colorScheme.primary)
                    Text("说明: “立即测试保活”只执行一次；“启动后台保活”只启动后台保活服务和定时任务。", style = MaterialTheme.typography.bodySmall)
                    if (!dashboard.rootGranted) {
                        Text("提示: 当前 ROOT 未授权，系统级保活能力不会生效。", color = MaterialTheme.colorScheme.error)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("定时任务")
                            Text("固定 Cron: ${AppConfig.cronExpression}（每20分钟执行一次）", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.settings.cronEnabled,
                            onCheckedChange = { viewModel.saveSettings(uiState.settings.copy(cronEnabled = it)) },
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("今日执行", uiState.dashboard.runStats.todayRuns.toString(), Modifier.weight(1f))
                StatCard("成功账号", uiState.dashboard.runStats.successAccounts.toString(), Modifier.weight(1f))
                StatCard("失败账号", uiState.dashboard.runStats.failedAccounts.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("快捷信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("当前进度: ${uiState.dashboard.runStats.currentProgress}")
                    Text("上次执行: ${formatTime(uiState.dashboard.runStats.lastRunAt)}")
                    Text("下次执行: ${formatTime(uiState.dashboard.runStats.nextRunAt)}")
                    Text("账号总数: ${uiState.accounts.size}")
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountsScreen(accounts: List<StoredAccount>, padding: PaddingValues, viewModel: MainViewModel) {
    var batchText by remember { mutableStateOf("") }
    var addDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoredAccount?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    if (addDialog) {
        AccountDialog(onDismiss = { addDialog = false }) { username, password ->
            viewModel.addAccount(username, password)
            addDialog = false
        }
    }
    editing?.let { item ->
        AccountDialog(item, onDismiss = { editing = null }) { username, password ->
            viewModel.updateAccount(item.credential.id, username, password)
            editing = null
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部账号") },
            text = { Text("该操作会删除全部本地账号，请确认。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAccounts()
                    confirmClear = false
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("批量导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = batchText,
                    onValueChange = { batchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("账号#密码&账号#密码") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { if (batchText.isNotBlank()) viewModel.importAccounts(batchText) }, modifier = Modifier.weight(1f)) { Text("一键解析导入") }
                    OutlinedButton(onClick = { addDialog = true }, modifier = Modifier.weight(1f)) { Text("单个添加") }
                }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("清空所有") }
            }
        }

        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请添加天翼云手机账号", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(accounts, key = { it.credential.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(maskAccount(item.credential.username), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("DeviceCode: ${item.deviceCode.ifBlank { "未生成" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { editing = item }, modifier = Modifier.weight(1f)) { Text("编辑") }
                                OutlinedButton(onClick = { viewModel.removeAccount(item.credential.id) }, modifier = Modifier.weight(1f)) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDialog(
    account: StoredAccount? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var username by remember(account) { mutableStateOf(account?.credential?.username.orEmpty()) }
    var password by remember(account) { mutableStateOf(account?.credential?.password.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新增账号" else "编辑账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("账号") })
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(username, password) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LogsScreen(logs: List<LogEntry>, logDirectoryPath: String, padding: PaddingValues, viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val allLogs = remember(logs) { logs.joinToString("\n") { "${formatTime(it.timestamp)} ${it.message}" } }
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { viewModel.clearLogs() }, modifier = Modifier.weight(1f)) { Text("清空日志") }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(allLogs))
                Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) { Text("复制全部") }
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, allLogs)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "导出日志"))
            }, modifier = Modifier.weight(1f)) { Text("导出日志") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                val opened = viewModel.openLogFolder(context)
                if (!opened) {
                    Toast.makeText(context, "无法直接打开文件夹，请手动前往 $logDirectoryPath", Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("打开日志文件夹")
            }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(logDirectoryPath))
                Toast.makeText(context, "日志路径已复制", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("复制日志路径")
            }
        }
        Text("当前日志目录: $logDirectoryPath", style = MaterialTheme.typography.bodySmall)
        Card(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101418))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(logs, key = { it.id }) { item ->
                    Text(
                        text = "${formatTime(item.timestamp)} ${item.message}",
                        color = when (item.level) {
                            LogLevel.DEBUG -> Color(0xFF9EC1FF)
                            LogLevel.INFO -> Color.White
                            LogLevel.SUCCESS -> Color(0xFF5CE27B)
                            LogLevel.WARNING -> Color(0xFFFFD54F)
                            LogLevel.ERROR -> Color(0xFFFF6E6E)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(settings: AppSettings, padding: PaddingValues, viewModel: MainViewModel) {
    var concurrency by remember(settings) { mutableStateOf(settings.concurrency.toString()) }
    var clinkHold by remember(settings) { mutableStateOf(settings.clinkHoldMs.toString()) }
    var retryCount by remember(settings) { mutableStateOf(settings.networkRetryCount.toString()) }
    var retryDelay by remember(settings) { mutableStateOf(settings.networkRetryDelayMs.toString()) }
    var debug by remember(settings) { mutableStateOf(settings.debug) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("参数配置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = concurrency, onValueChange = { concurrency = it }, label = { Text("并发执行数") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = clinkHold, onValueChange = { clinkHold = it }, label = { Text("Clink 保活时长(ms)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = retryCount, onValueChange = { retryCount = it }, label = { Text("网络重试次数") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = retryDelay, onValueChange = { retryDelay = it }, label = { Text("重试等待(ms)") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("调试模式")
            Switch(checked = debug, onCheckedChange = { debug = it })
        }
        Button(onClick = {
            viewModel.saveSettings(
                AppSettings(
                    concurrency = concurrency.toIntOrNull() ?: settings.concurrency,
                    clinkHoldMs = clinkHold.toLongOrNull() ?: settings.clinkHoldMs,
                    networkRetryCount = retryCount.toIntOrNull() ?: settings.networkRetryCount,
                    networkRetryDelayMs = retryDelay.toLongOrNull() ?: settings.networkRetryDelayMs,
                    debug = debug,
                    cronEnabled = settings.cronEnabled,
                )
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("保存并立即生效") }
        OutlinedButton(onClick = { viewModel.saveSettings(AppSettings(cronEnabled = settings.cronEnabled)) }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认参数") }
    }
}

@Composable
private fun SystemScreen(uiState: MainUiState, padding: PaddingValues, viewModel: MainViewModel) {
    val context = LocalContext.current
    var confirmExit by remember { mutableStateOf(false) }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("清空所有数据") },
            text = { Text("将清空账号、配置、缓存和日志，是否继续？") },
            confirmButton = { TextButton(onClick = { viewModel.clearAllData(); confirmExit = false }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("取消") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("权限管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("ROOT 授权状态: ${if (uiState.dashboard.rootGranted) "已授权" else "未授权"}")
                Text("Python 环境: ${if (uiState.dashboard.pythonReady) "已加载" else "未加载"}")
                Text("OCR 模型: ${if (uiState.dashboard.ocrReady) "已初始化" else "未初始化"}")
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("服务管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("固定 Cron 表达式: ${AppConfig.cronExpression}")
                Text("服务说明: 前台服务 + AlarmManager + ROOT 守护进程")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.refreshEnvironment() }, modifier = Modifier.weight(1f)) { Text("重新检测权限") }
                    OutlinedButton(onClick = { viewModel.startService(context) }, modifier = Modifier.weight(1f)) { Text("重启后台保活服务") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { viewModel.clearLogs() }, modifier = Modifier.weight(1f)) { Text("清理缓存日志") }
                    OutlinedButton(onClick = { viewModel.refreshEnvironment() }, modifier = Modifier.weight(1f)) { Text("重置 OCR 模型") }
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关于应用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("版本: 1.0.6")
                Text("技术栈: Kotlin + Compose + MMKV + OkHttp + Chaquopy + ddddocr")
                Text("运行方式: 安装后授权 ROOT，先启动后台保活，再按需执行立即测试")
            }
        }
        OutlinedButton(onClick = { confirmExit = true }, modifier = Modifier.fillMaxWidth()) { Text("退出登录并清空所有本地数据") }
    }
}
