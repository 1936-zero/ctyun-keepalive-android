package com.monkeycode.ctyunkeepalive.core

import android.util.Base64
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun maskAccount(account: String): String {
    val value = account.trim()
    return when {
        Regex("^\\d{11}$").matches(value) -> "${value.take(3)}****${value.takeLast(4)}"
        value.contains("@") -> {
            val parts = value.split("@")
            val name = parts.firstOrNull().orEmpty()
            val domain = parts.getOrNull(1).orEmpty()
            if (name.length <= 2) "${name.firstOrNull() ?: '*'}***@$domain" else "${name.take(2)}***@$domain"
        }
        value.length <= 4 -> "${value.firstOrNull() ?: '*'}***"
        else -> "${value.take(2)}***${value.takeLast(2)}"
    }
}

fun parseBatchAccounts(raw: String): List<AccountCredential> {
    return raw.split("&")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, item ->
            val split = item.indexOf('#')
            require(split in 1 until item.lastIndex) { "第 ${index + 1} 组账号格式不正确，应为 账号#密码" }
            AccountCredential(
                id = UUID.randomUUID().toString(),
                username = item.substring(0, split).trim(),
                password = item.substring(split + 1).trim(),
            )
        }
}

fun buildDeviceCode(username: String): String {
    val digest = md5Hex("ctyun_phone_keepalive:${username.lowercase(Locale.ROOT)}")
    return "web_phone_$digest"
}

fun sha256Hex(text: String): String = digest("SHA-256", text).lowercase(Locale.ROOT)

fun md5Upper(text: String): String = digest("MD5", text).uppercase(Locale.ROOT)

private fun md5Hex(text: String): String = digest("MD5", text).lowercase(Locale.ROOT)

private fun digest(algorithm: String, text: String): String {
    val bytes = MessageDigest.getInstance(algorithm).digest(text.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

fun nowText(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())

fun formatTime(time: Long): String {
    if (time <= 0L) return "未执行"
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(time))
}

fun base64NoWrap(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
