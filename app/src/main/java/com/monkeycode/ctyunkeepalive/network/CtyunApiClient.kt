package com.monkeycode.ctyunkeepalive.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.monkeycode.ctyunkeepalive.core.AppConfig
import com.monkeycode.ctyunkeepalive.core.AuthCache
import com.monkeycode.ctyunkeepalive.core.ChallengeData
import com.monkeycode.ctyunkeepalive.core.ConnectionSummary
import com.monkeycode.ctyunkeepalive.core.DesktopDevice
import com.monkeycode.ctyunkeepalive.core.StoredAccount
import com.monkeycode.ctyunkeepalive.core.buildDeviceCode
import com.monkeycode.ctyunkeepalive.core.md5Upper
import com.monkeycode.ctyunkeepalive.core.sha256Hex
import com.monkeycode.ctyunkeepalive.data.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, override val message: String) : IllegalStateException(message)

class CtyunApiClient(
    private val logRepository: LogRepository,
    private val gson: Gson = Gson(),
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(AppConfig.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(AppConfig.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(AppConfig.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    suspend fun login(account: StoredAccount, captchaCode: String = ""): AuthCache = withContext(Dispatchers.IO) {
        val credential = account.credential
        val challenge = getChallengeData()
        val deviceCode = account.deviceCode.ifBlank { buildDeviceCode(credential.username) }
        val plainSha256 = sha256Hex(credential.password)
        val password = if (challenge.challengeCode.isNotBlank()) {
            sha256Hex(credential.password + challenge.challengeCode)
        } else {
            plainSha256
        }
        val sha256Password = if (challenge.challengeCode.isNotBlank()) {
            sha256Hex(plainSha256 + challenge.challengeCode)
        } else {
            plainSha256
        }

        val body = FormBody.Builder()
            .add("userAccount", credential.username)
            .add("password", password)
            .add("sha256Password", sha256Password)
            .add("challengeId", challenge.challengeId)
            .add("deviceCode", deviceCode)
            .add("deviceName", AppConfig.deviceName)
            .add("deviceType", AppConfig.deviceType.toString())
            .add("deviceModel", AppConfig.deviceModel)
            .add("appVersion", AppConfig.appVersion)
            .add("sysVersion", AppConfig.sysVersion)
            .add("clientVersion", AppConfig.version.toString())
            .apply {
                if (captchaCode.isNotBlank()) add("captchaCode", captchaCode)
            }
            .build()

        val json = requestJson(
            path = "/api/auth/client/login",
            method = "POST",
            body = body,
            headers = baseHeaders(deviceCode),
        )

        AuthCache(
            tenantId = json["tenantId"].asString,
            userId = json["userId"].asString,
            secretKey = json["secretKey"].asString,
            userAccount = json["userAccount"].asString,
            userName = json["userName"].asString,
            bondedDevice = json["bondedDevice"]?.asString.orEmpty(),
            timestamp = json["timestamp"]?.asString.orEmpty(),
        )
    }

    suspend fun fetchCaptcha(username: String, deviceCode: String): ByteArray = withContext(Dispatchers.IO) {
        val url = AppConfig.apiHost.toHttpUrl().newBuilder()
            .addEncodedPathSegments("api/auth/client/captcha")
            .addQueryParameter("width", "200")
            .addQueryParameter("height", "80")
            .addQueryParameter("mode", "auto")
            .addQueryParameter("userInfo", username)
            .addQueryParameter("_t", System.currentTimeMillis().toString())
            .build()
        val request = Request.Builder().url(url).headers(baseHeaders(deviceCode).build()).get().build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "验证码请求失败: ${response.code}" }
            return@withContext response.body?.bytes() ?: byteArrayOf()
        }
    }

    suspend fun listDevices(auth: AuthCache, deviceCode: String): List<DesktopDevice> = withContext(Dispatchers.IO) {
        val data = requestJson(
            path = "/api/desktop/client/list",
            method = "GET",
            headers = authHeaders(deviceCode, auth),
        )
        data["desktopList"]?.asJsonArray?.map { item ->
            val obj = item.asJsonObject
            DesktopDevice(
                objId = obj["objId"]?.asString.orEmpty(),
                objType = obj["objType"]?.asString.orEmpty(),
                objName = obj["objName"]?.asString ?: obj["desktopName"]?.asString.orEmpty(),
                desktopId = obj["desktopId"]?.asString ?: obj["objId"]?.asString.orEmpty(),
                connectMaster = obj["connectMaster"]?.asInt ?: 0,
            )
        }.orEmpty()
    }

    suspend fun getDesktopFeature(auth: AuthCache, deviceCode: String, device: DesktopDevice): JsonObject = withContext(Dispatchers.IO) {
        requestJson(
            path = "/api/desktop/client/feature",
            method = "GET",
            query = mapOf("objId" to device.objId, "objType" to device.objType),
            headers = authHeaders(deviceCode, auth),
        )
    }

    suspend fun getDesktopExtraInfo(auth: AuthCache, deviceCode: String, device: DesktopDevice): JsonObject = withContext(Dispatchers.IO) {
        requestJson(
            path = "/api/desktop/client/getDesktopExtraInfo",
            method = "GET",
            query = mapOf("objId" to device.objId, "objType" to device.objType),
            headers = authHeaders(deviceCode, auth),
        )
    }

    suspend fun connectDevice(auth: AuthCache, deviceCode: String, device: DesktopDevice): JsonObject = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("objId", device.objId)
            .add("objType", device.objType)
            .add("osType", AppConfig.osType.toString())
            .add("deviceId", AppConfig.deviceType.toString())
            .add("deviceCode", deviceCode)
            .add("deviceName", AppConfig.deviceName)
            .add("sysVersion", AppConfig.sysVersion)
            .add("appVersion", AppConfig.appVersion)
            .add("hostName", AppConfig.deviceName)
            .add("vdCommand", "")
            .add("ipAddress", "")
            .add("macAddress", "")
            .add("hardwareFeatureCode", deviceCode)
            .add("specifiedCertCategory", "1")
            .build()
        requestJson(
            path = if (device.connectMaster == 1) "/api/desktop/client/connectMaster" else "/api/desktop/client/connect",
            method = "POST",
            body = body,
            headers = authHeaders(deviceCode, auth),
        )
    }

    suspend fun getDesktopStatus(auth: AuthCache, deviceCode: String, device: DesktopDevice, desktopId: String): JsonObject = withContext(Dispatchers.IO) {
        requestJson(
            path = if (device.connectMaster == 1) "/api/desktop/client/statusMaster" else "/api/desktop/client/status",
            method = "GET",
            query = mapOf("desktopId" to desktopId, "specifiedCertCategory" to "1"),
            headers = authHeaders(deviceCode, auth),
        )
    }

    suspend fun getDesktopState(auth: AuthCache, deviceCode: String, desktopId: String): JsonObject = withContext(Dispatchers.IO) {
        val payload = desktopId.toLongOrNull()?.let { "[$it]" } ?: "[\"$desktopId\"]"
        requestJson(
            path = "/api/desktop/client/state",
            method = "POST",
            body = payload.toRequestBody("application/json".toMediaType()),
            headers = authHeaders(deviceCode, auth),
        )
    }

    suspend fun getDesktopStrategy(auth: AuthCache, deviceCode: String, desktopId: String, token: String): JsonObject = withContext(Dispatchers.IO) {
        val headers = authHeaders(deviceCode, auth).add(AppConfig.desktopTokenHeader, token)
        requestJson(
            path = "/api/desktop/client/strategy/desktopBasicInfo",
            method = "GET",
            query = mapOf("desktopId" to desktopId),
            headers = headers,
        )
    }

    fun summarizeConnection(data: JsonObject): ConnectionSummary {
        val desktopInfo = data["desktopInfo"]?.asJsonObject ?: JsonObject()
        return ConnectionSummary(
            desktopId = data["desktopId"]?.asString ?: desktopInfo["desktopId"]?.asString.orEmpty(),
            token = desktopInfo["token"]?.asString.orEmpty(),
            internalIp = desktopInfo["internalIp"]?.asString.orEmpty(),
            internalPort = desktopInfo["internalPort"]?.asString.orEmpty(),
            clientCert = desktopInfo["clientCert"]?.asString.orEmpty(),
            clientKey = desktopInfo["clientKey"]?.asString.orEmpty(),
            caCert = desktopInfo["caCert"]?.asString.orEmpty(),
        )
    }

    suspend fun networkRetry(block: suspend () -> Unit) {
        var error: Throwable? = null
        repeat(AppConfig.networkRetryCount + 1) { index ->
            runCatching { block(); return }.onFailure { throwable ->
                error = throwable
                if (index < AppConfig.networkRetryCount) delay(AppConfig.networkRetryDelayMs * (index + 1))
            }
        }
        throw error ?: IllegalStateException("未知网络错误")
    }

    private suspend fun getChallengeData(): ChallengeData = withContext(Dispatchers.IO) {
        val data = runCatching {
            requestJson(path = "/api/auth/client/genChallengeData", method = "POST", headers = baseHeaders("bootstrap"))
        }.getOrNull()
        ChallengeData(
            challengeId = data?.get("challengeId")?.asString ?: data?.get("id")?.asString.orEmpty(),
            challengeCode = data?.get("challengeCode")?.asString ?: data?.get("code")?.asString.orEmpty(),
        )
    }

    private suspend fun requestJson(
        path: String,
        method: String,
        headers: okhttp3.Headers.Builder,
        query: Map<String, String> = emptyMap(),
        body: okhttp3.RequestBody? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val urlBuilder = AppConfig.apiHost.toHttpUrl().newBuilder().addEncodedPathSegments(path.removePrefix("/"))
        query.forEach { (key, value) -> if (value.isNotBlank()) urlBuilder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(headers.build())
            .method(method, if (method == "GET") null else body ?: ByteArray(0).toRequestBody(null))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val root = gson.fromJson(raw, JsonObject::class.java)
            val code = root["code"]?.asInt ?: -1
            if (code != 0) {
                val message = root["msg"]?.asString ?: "unknown error"
                throw ApiException(code, "$method $path 失败: $message")
            }
            return@withContext root["data"]?.asJsonObject ?: JsonObject()
        }
    }

    private fun baseHeaders(deviceCode: String): okhttp3.Headers.Builder {
        val timestamp = System.currentTimeMillis()
        val requestId = timestamp + (0..999).random()
        return okhttp3.Headers.Builder()
            .add("CTG-DEVICECODE", deviceCode)
            .add("CTG-DEVICETYPE", AppConfig.deviceType.toString())
            .add("CTG-REQUESTID", requestId.toString())
            .add("CTG-TIMESTAMP", timestamp.toString())
            .add("CTG-VERSION", AppConfig.version.toString())
            .add("CTG-APPMODEL", AppConfig.appModel.toString())
            .add("Origin", "https://pc.ctyun.cn")
            .add("Referer", "https://pc.ctyun.cn/")
            .add("User-Agent", AppConfig.userAgent)
    }

    private fun authHeaders(deviceCode: String, auth: AuthCache): okhttp3.Headers.Builder {
        val headers = baseHeaders(deviceCode)
        val timestamp = headers.build()["CTG-TIMESTAMP"].orEmpty()
        val requestId = headers.build()["CTG-REQUESTID"].orEmpty()
        val signature = md5Upper(
            "${AppConfig.deviceType}${requestId}${auth.tenantId}${timestamp}${auth.userId}${AppConfig.version}${auth.secretKey}"
        )
        return headers
            .add("CTG-TENANTID", auth.tenantId)
            .add("CTG-USERID", auth.userId)
            .add("CTG-SIGNATURESTR", signature)
    }
}
