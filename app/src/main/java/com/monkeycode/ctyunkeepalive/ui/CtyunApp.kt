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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.monkeycode.ctyunkeepalive.R
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VaporBackground)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = VaporPanelBg,
                        tonalElevation = 0.dp,
                        modifier = Modifier,
                    ) {
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
                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = VaporAccent,
                                    selectedTextColor = VaporAccent,
                                    indicatorColor = VaporInset,
                                    unselectedIconColor = VaporMuted,
                                    unselectedTextColor = VaporMuted,
                                ),
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
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SplashScreen(dashboard: com.monkeycode.ctyunkeepalive.core.DashboardState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        VaporPanel(Modifier.padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("天翼云手机保活", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = VaporInk)
                Text("Version 2.0.7", color = VaporAccent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("ROOT", if (dashboard.rootGranted) "已授权" else "检测中", if (dashboard.rootGranted) VaporSuccess else VaporWarning)
                    StatusChip("PY", if (dashboard.pythonReady) "已加载" else "加载中", VaporAccent)
                    StatusChip("OCR", if (dashboard.ocrReady) "已初始化" else "初始化中", VaporTeal)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HomeScreen(
    uiState: MainUiState,
    padding: PaddingValues,
    context: Context,
    viewModel: MainViewModel,
) {
    val dashboard = uiState.dashboard
    val statusText = when {
        dashboard.runStats.running -> "运行中"
        dashboard.runStats.currentProgress.contains("后台") -> "后台待命"
        else -> "已停止"
    }
    val lastResult = when {
        dashboard.runStats.failedAccounts > 0 -> "最近一轮含失败"
        dashboard.runStats.successAccounts > 0 -> "最近一轮成功"
        else -> "尚未执行"
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            VaporPanel {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("后台保活总状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VaporInk)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusChip("状态", statusText, if (statusText == "运行中") VaporAccent else VaporTeal)
                        StatusChip("ROOT", if (dashboard.rootGranted) "已授权" else "未授权", if (dashboard.rootGranted) VaporSuccess else VaporWarning)
                        StatusChip("OCR", if (dashboard.ocrReady) "已就绪" else "未初始化", VaporTeal)
                        StatusChip("下次执行", formatTime(uiState.dashboard.runStats.nextRunAt), VaporAccent)
                        StatusChip("智能保活", uiState.dashboard.runStats.smartKeepAliveState, if (uiState.settings.smartKeepAliveEnabled) VaporAccent else VaporMuted)
                    }
                }
            }
        }
        item {
            VaporPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("控制台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VaporInk)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        PixelPrimaryButton(onClick = { viewModel.startService(context) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("启动后台保活")
                        }
                        PixelAccentButton(
                            onClick = { viewModel.runImmediateTest(context) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("立即测试保活")
                        }
                    }
                    PixelOutlineButton(onClick = { viewModel.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("停止后台保活")
                    }
                    if (uiState.settings.smartKeepAliveEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            PixelPrimaryButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("智能保活中") }
                            PixelOutlineButton(onClick = { viewModel.stopSmartKeepAlive() }, modifier = Modifier.weight(1f)) { Text("停止智能保活") }
                        }
                    } else {
                        PixelOutlineButton(onClick = { viewModel.startSmartKeepAlive() }, modifier = Modifier.fillMaxWidth()) {
                            Text("启用智能保活")
                        }
                    }
                }
            }
        }
        item {
            VaporPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("最近结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VaporInk)
                    Text(lastResult, color = VaporAccent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard("成功账号", uiState.dashboard.runStats.successAccounts.toString(), Modifier.weight(1f))
                        StatCard("失败账号", uiState.dashboard.runStats.failedAccounts.toString(), Modifier.weight(1f))
                    }
                    Text("当前进度: ${uiState.dashboard.runStats.currentProgress}", color = VaporInk)
                    Text("最近执行: ${formatTime(uiState.dashboard.runStats.lastRunAt)}", color = VaporMuted)
                    Text("最近传感器 xyz 数据: ${formatTime(uiState.dashboard.runStats.lastSensorActivityAt)}", color = VaporMuted)
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    VaporPanel(modifier = modifier, innerPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = VaporMuted)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = VaporAccent)
        }
    }
}

@Composable
private fun AccountsScreen(accounts: List<StoredAccount>, padding: PaddingValues, viewModel: MainViewModel) {
    var addDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoredAccount?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    if (addDialog) {
        AccountDialog(onDismiss = { addDialog = false }) { username, password, deviceCode, useCustomDeviceCode ->
            viewModel.addAccount(username, password, deviceCode, useCustomDeviceCode)
            addDialog = false
        }
    }
    editing?.let { item ->
        AccountDialog(item, onDismiss = { editing = null }) { username, password, deviceCode, useCustomDeviceCode ->
            viewModel.updateAccount(item.credential.id, username, password, deviceCode, useCustomDeviceCode)
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PixelPrimaryButton(onClick = { addDialog = true }, modifier = Modifier.weight(1f)) { Text("添加账号") }
            PixelOutlineButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f)) { Text("清空所有") }
        }

        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请添加天翼云手机账号", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(accounts, key = { it.credential.id }) { item ->
                    VaporPanel(modifier = Modifier.fillMaxWidth(), innerPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(maskAccount(item.credential.username), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VaporInk)
                                StatusChip("deviceCode", if (item.useCustomDeviceCode) "自定义" else "自动", if (item.useCustomDeviceCode) VaporWarning else VaporAccent)
                            }
                            Text(item.deviceCode.ifBlank { "未生成" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VaporMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                PixelOutlineButton(onClick = { editing = item }, modifier = Modifier.weight(1f)) { Text("编辑") }
                                PixelOutlineButton(onClick = { viewModel.removeAccount(item.credential.id) }, modifier = Modifier.weight(1f)) { Text("删除") }
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
    onSave: (String, String, String, Boolean) -> Unit,
) {
    var username by remember(account) { mutableStateOf(account?.credential?.username.orEmpty()) }
    var password by remember(account) { mutableStateOf(account?.credential?.password.orEmpty()) }
    var useCustomDeviceCode by remember(account) { mutableStateOf(account?.useCustomDeviceCode ?: false) }
    var deviceCode by remember(account) { mutableStateOf(account?.deviceCode.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新增账号" else "编辑账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("账号") })
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("使用自定义 deviceCode")
                    Switch(checked = useCustomDeviceCode, onCheckedChange = { useCustomDeviceCode = it })
                }
                if (useCustomDeviceCode) {
                    OutlinedTextField(value = deviceCode, onValueChange = { deviceCode = it }, label = { Text("deviceCode") })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(username, password, deviceCode, useCustomDeviceCode) }) { Text("保存") } },
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
            PixelPrimaryButton(onClick = { viewModel.clearLogs() }, modifier = Modifier.weight(1f)) { Text("清空日志") }
            PixelOutlineButton(onClick = {
                clipboard.setText(AnnotatedString(allLogs))
                Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) { Text("复制全部") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PixelOutlineButton(onClick = {
                val opened = viewModel.openLogFolder(context)
                if (!opened) {
                    Toast.makeText(context, "无法直接打开文件夹，请手动前往 $logDirectoryPath", Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("打开日志文件夹")
            }
            PixelOutlineButton(onClick = {
                clipboard.setText(AnnotatedString(logDirectoryPath))
                Toast.makeText(context, "日志路径已复制", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("复制日志路径")
            }
        }
        Text("当前日志目录: $logDirectoryPath", style = MaterialTheme.typography.bodySmall, color = VaporMuted)
        VaporPanel(modifier = Modifier.fillMaxSize(), innerPadding = 0.dp) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(VaporPanelBg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(logs, key = { it.id }) { item ->
                    Text(
                        text = "${formatTime(item.timestamp)} ${item.message}",
                        color = when (item.level) {
                            LogLevel.DEBUG -> VaporAccent
                            LogLevel.INFO -> VaporInk
                            LogLevel.SUCCESS -> VaporSuccess
                            LogLevel.WARNING -> VaporWarning
                            LogLevel.ERROR -> VaporRed
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val VaporBackground = Color(0xFFE0E5EC)
private val VaporPanelBg = Color(0xFFE0E5EC)
private val VaporInset = Color(0xFFD4DAE3)
private val VaporInk = Color(0xFF3D4852)
private val VaporMuted = Color(0xFF6B7280)
private val VaporAccent = Color(0xFF6C63FF)
private val VaporTeal = Color(0xFF38B2AC)
private val VaporSuccess = Color(0xFF22C55E)
private val VaporWarning = Color(0xFFF59E0B)
private val VaporRed = Color(0xFFEF4444)
private val VaporShadowDark = Color(0x99A3B1C6)
private val VaporShadowLight = Color(0x99FFFFFF)

@Composable
private fun VaporPanel(
    modifier: Modifier = Modifier,
    innerPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(VaporPanelBg, RoundedCornerShape(32.dp))
            .border(1.dp, VaporInset, RoundedCornerShape(32.dp))
            .padding(innerPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun InsetWell(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier = modifier
            .background(VaporInset, RoundedCornerShape(20.dp))
            .border(1.dp, VaporPanelBg, RoundedCornerShape(20.dp))
            .padding(8.dp)
    ) {
        content()
    }
}

@Composable
private fun StatusChip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color)
            .padding(2.dp)
            .clip(RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PixelPrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.pixelShadow(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VaporAccent, contentColor = Color.White),
    ) { content() }
}

@Composable
private fun PixelAccentButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.pixelShadow(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VaporTeal, contentColor = Color.White),
    ) { content() }
}

@Composable
private fun PixelOutlineButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pixelShadow(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = VaporPanelBg, contentColor = VaporInk),
    ) { content() }
}

@Composable
private fun PixelTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = VaporInk,
            unfocusedTextColor = VaporInk,
            focusedLabelColor = VaporAccent,
            unfocusedLabelColor = VaporMuted,
            focusedContainerColor = VaporPanelBg,
            unfocusedContainerColor = VaporPanelBg,
            cursorColor = VaporAccent,
        )
    )
}

private fun Modifier.pixelShadow(shape: Shape = RoundedCornerShape(0.dp)): Modifier {
    return this
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
        VaporPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("参数配置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = VaporInk)
                PixelTextField(value = concurrency, onValueChange = { concurrency = it }, label = "并发执行数")
                PixelTextField(value = clinkHold, onValueChange = { clinkHold = it }, label = "Clink 保活时长(ms)")
                PixelTextField(value = retryCount, onValueChange = { retryCount = it }, label = "网络重试次数")
                PixelTextField(value = retryDelay, onValueChange = { retryDelay = it }, label = "重试等待(ms)")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("调试模式", color = VaporInk)
                    Switch(checked = debug, onCheckedChange = { debug = it })
                }
                PixelPrimaryButton(onClick = {
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
                PixelOutlineButton(onClick = { viewModel.saveSettings(AppSettings(cronEnabled = settings.cronEnabled)) }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认参数") }
            }
        }
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
        VaporPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("权限管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = VaporInk)
                Text("ROOT 授权状态: ${if (uiState.dashboard.rootGranted) "已授权" else "未授权"}", color = VaporInk)
                Text("Python 环境: ${if (uiState.dashboard.pythonReady) "已加载" else "未加载"}", color = VaporInk)
                Text("OCR 模型: ${if (uiState.dashboard.ocrReady) "已初始化" else "未初始化"}", color = VaporInk)
            }
        }
        VaporPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("服务管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = VaporInk)
                Text("固定 Cron 表达式: ${AppConfig.cronExpression}", color = VaporInk)
                Text("服务说明: ROOT watchdog 负责维持 app 在线，后台保活服务负责保活天翼云手机", color = VaporMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PixelPrimaryButton(onClick = { viewModel.refreshEnvironment() }, modifier = Modifier.weight(1f)) { Text("重新检测权限") }
                    PixelOutlineButton(onClick = { viewModel.startService(context) }, modifier = Modifier.weight(1f)) { Text("重启后台保活服务") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PixelOutlineButton(onClick = { viewModel.clearLogs() }, modifier = Modifier.weight(1f)) { Text("清理缓存日志") }
                    PixelOutlineButton(onClick = { viewModel.refreshEnvironment() }, modifier = Modifier.weight(1f)) { Text("重置 OCR 模型") }
                }
            }
        }
        VaporPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("智能保活", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = VaporInk)
                Text("监控来源: 加速度传感器", color = VaporInk)
                Text("智能保活启用后：每 10 分钟运行一次保活；加速度传感器数据每 5 秒节流记录一次运动日志；使用加速度向量模长判断静止与运动，模长在 9.5 到 10.5 之间判定为静止，不在该区间时判定为运动；若最近 5 分钟内有运动，则跳过本次天翼云手机保活；连续 5 分钟无运动，则立即执行一次保活，然后继续每 10 分钟运行。", color = VaporMuted)
            }
        }
        VaporPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关于应用", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = VaporInk)
                Text("版本: 2.0.7", color = VaporInk)
                Text("技术栈: Kotlin + Compose + MMKV + OkHttp + Chaquopy + ddddocr", color = VaporInk)
                Text("运行方式: 安装后授权 ROOT，先启动后台保活，再按需执行立即测试", color = VaporMuted)
            }
        }
        VaporPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("支持项目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = VaporInk)
                Text("如果这个项目对你有帮助，可以通过下方收款码支持持续维护。", color = VaporMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    DonationCard(
                        title = "微信打赏",
                        imageRes = R.drawable.wechat_donate,
                        modifier = Modifier.weight(1f),
                    )
                    DonationCard(
                        title = "支付宝打赏",
                        imageRes = R.drawable.alipay_donate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        PixelOutlineButton(onClick = { confirmExit = true }, modifier = Modifier.fillMaxWidth()) { Text("退出登录并清空所有本地数据") }
    }
}

@Composable
private fun DonationCard(title: String, imageRes: Int, modifier: Modifier = Modifier) {
    VaporPanel(modifier = modifier, innerPadding = 12.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = VaporInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AsyncImage(
                model = imageRes,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
