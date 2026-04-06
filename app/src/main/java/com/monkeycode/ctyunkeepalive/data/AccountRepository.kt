package com.monkeycode.ctyunkeepalive.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.monkeycode.ctyunkeepalive.core.AccountCredential
import com.monkeycode.ctyunkeepalive.core.AuthCache
import com.monkeycode.ctyunkeepalive.core.StoredAccount
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountRepository(
    private val mmkv: MMKV = MMKV.mmkvWithID("ctyun_accounts", MMKV.MULTI_PROCESS_MODE),
    private val gson: Gson = Gson(),
) {
    private val key = "accounts"
    private val state = MutableStateFlow(load())

    fun accounts(): StateFlow<List<StoredAccount>> = state.asStateFlow()

    fun saveAccounts(accounts: List<StoredAccount>) {
        mmkv.encode(key, gson.toJson(accounts))
        state.value = accounts
    }

    fun addOrReplace(credential: AccountCredential) {
        val current = state.value.toMutableList()
        val index = current.indexOfFirst { it.credential.username == credential.username }
        val next = StoredAccount(credential = credential)
        if (index >= 0) current[index] = next else current += next
        saveAccounts(current)
    }

    fun addOrReplace(account: StoredAccount) {
        val current = state.value.toMutableList()
        val index = current.indexOfFirst { it.credential.username == account.credential.username }
        if (index >= 0) current[index] = account else current += account
        saveAccounts(current)
    }

    fun updateAccount(accountId: String, username: String, password: String, deviceCode: String, useCustomDeviceCode: Boolean) {
        saveAccounts(state.value.map {
            if (it.credential.id != accountId) return@map it
            it.copy(
                credential = it.credential.copy(username = username, password = password),
                deviceCode = if (useCustomDeviceCode) deviceCode.trim() else "",
                useCustomDeviceCode = useCustomDeviceCode,
                auth = null,
                updatedAt = System.currentTimeMillis(),
            )
        })
    }

    fun removeAccount(accountId: String) {
        saveAccounts(state.value.filterNot { it.credential.id == accountId })
    }

    fun clearAll() {
        saveAccounts(emptyList())
    }

    fun updateDeviceCode(username: String, deviceCode: String) {
        saveAccounts(state.value.map {
            if (it.credential.username != username) return@map it
            it.copy(deviceCode = deviceCode, updatedAt = System.currentTimeMillis())
        })
    }

    fun updateAuth(username: String, auth: AuthCache?) {
        saveAccounts(state.value.map {
            if (it.credential.username != username) return@map it
            it.copy(auth = auth, updatedAt = System.currentTimeMillis())
        })
    }

    private fun load(): List<StoredAccount> {
        val raw = mmkv.decodeString(key).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<StoredAccount>>() {}.type
            gson.fromJson<List<StoredAccount>>(raw, type)
        }.getOrDefault(emptyList())
    }
}
