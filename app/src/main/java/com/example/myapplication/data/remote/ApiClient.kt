package com.example.myapplication.data.remote

import com.example.myapplication.BuildConfig
import com.example.myapplication.base.AppException
import com.example.myapplication.base.Result as AppResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 网络请求客户端（单例）
 * 基于 OkHttp + 协程封装
 * 支持 GET/POST 请求，统一异常处理
 */
object ApiClient {

    /**
     * Gson 实例
     * 注册 Int/Long 类型适配器，兼容服务端返回字符串数字的情况（如 "802" 而非 802）
     * 关键：原语适配器绝不返回 null，避免 Gson 给原语字段赋 null 时抛异常
     */
    @PublishedApi
    internal val gson = com.google.gson.GsonBuilder()
        // -- 原语 int：绝不返回 null，异常时返回 0 --
        .registerTypeAdapter(
            Int::class.javaPrimitiveType ?: Int::class.java,
            object : com.google.gson.TypeAdapter<Int>() {
                override fun write(out: com.google.gson.stream.JsonWriter, value: Int) {
                    out.value(value)
                }
                override fun read(reader: com.google.gson.stream.JsonReader): Int {
                    return when (reader.peek()) {
                        com.google.gson.stream.JsonToken.NUMBER -> reader.nextInt()
                        com.google.gson.stream.JsonToken.STRING -> reader.nextString().toIntOrNull() ?: 0
                        com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); 0 }
                        else -> { reader.skipValue(); 0 }
                    }
                }
            })
        // -- 装箱 Integer：可 null --
        .registerTypeAdapter(
            Int::class.javaObjectType,
            object : com.google.gson.TypeAdapter<Int?>() {
                override fun write(out: com.google.gson.stream.JsonWriter, value: Int?) {
                    value?.let { out.value(it) } ?: out.nullValue()
                }
                override fun read(reader: com.google.gson.stream.JsonReader): Int? {
                    return when (reader.peek()) {
                        com.google.gson.stream.JsonToken.NUMBER -> reader.nextInt()
                        com.google.gson.stream.JsonToken.STRING -> reader.nextString().toIntOrNull()
                        com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); null }
                        else -> { reader.skipValue(); null }
                    }
                }
            })
        // -- 原语 long：绝不返回 null --
        .registerTypeAdapter(
            Long::class.javaPrimitiveType ?: Long::class.java,
            object : com.google.gson.TypeAdapter<Long>() {
                override fun write(out: com.google.gson.stream.JsonWriter, value: Long) {
                    out.value(value)
                }
                override fun read(reader: com.google.gson.stream.JsonReader): Long {
                    return when (reader.peek()) {
                        com.google.gson.stream.JsonToken.NUMBER -> reader.nextLong()
                        com.google.gson.stream.JsonToken.STRING -> reader.nextString().toLongOrNull() ?: 0L
                        com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); 0L }
                        else -> { reader.skipValue(); 0L }
                    }
                }
            })
        // -- 装箱 Long：可 null --
        .registerTypeAdapter(
            Long::class.javaObjectType,
            object : com.google.gson.TypeAdapter<Long?>() {
                override fun write(out: com.google.gson.stream.JsonWriter, value: Long?) {
                    value?.let { out.value(it) } ?: out.nullValue()
                }
                override fun read(reader: com.google.gson.stream.JsonReader): Long? {
                    return when (reader.peek()) {
                        com.google.gson.stream.JsonToken.NUMBER -> reader.nextLong()
                        com.google.gson.stream.JsonToken.STRING -> reader.nextString().toLongOrNull()
                        com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); null }
                        else -> { reader.skipValue(); null }
                    }
                }
            })
        .create()
    @PublishedApi
    internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * OkHttpClient 实例
     * - 日志拦截器（Debug 模式）
     * - 超时配置
     * - 统一请求头
     */
    @PublishedApi
    internal val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        // Debug 构建才输出完整请求体，避免大响应体拖慢 Release 性能
        if (com.example.myapplication.BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(logging)
        }
        builder.addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                // 携带登录 Cookie，确保需要登录态的接口能正常工作
                val cookie = com.example.myapplication.manager.AccountManager.cookie
                if (!cookie.isNullOrBlank()) {
                    builder.addHeader("Cookie", cookie)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    /**
     * 基础 URL
     * NetEaseCloudMusicApiBackup 默认端口 3000
     * 本地开发使用 10.0.2.2 映射到宿主机的 localhost
     */
    @PublishedApi
    internal const val BASE_URL = "http://192.168.55.245:3000"

    // ==================== GET 请求 ====================

    /**
     * 通用 GET 请求
     * @param path API 路径（如 "/top/playlist"）
     * @param params 查询参数 Map
     * @return Result<T>
     */
    suspend inline fun <reified T> get(
        path: String,
        params: Map<String, String> = emptyMap()
    ): AppResult<T> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$BASE_URL$path")
            if (params.isNotEmpty()) {
                urlBuilder.append("?")
                params.forEach { (key, value) ->
                    urlBuilder.append(URLEncoder.encode(key, "UTF-8"))
                    urlBuilder.append("=")
                    urlBuilder.append(URLEncoder.encode(value, "UTF-8"))
                    urlBuilder.append("&")
                }
                urlBuilder.deleteCharAt(urlBuilder.lastIndex)
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            val response = client.newCall(request).await()
            handleResponse(response)
        } catch (e: IOException) {
            AppResult.Error(AppException.NetworkException(cause = e))
        } catch (e: Exception) {
            AppResult.Error(AppException.UnknownException(cause = e))
        }
    }

    // ==================== POST 请求 ====================

    /**
     * 通用 POST 请求（JSON Body）
     * @param path API 路径
     * @param body 请求体（任意类型，会被序列化为 JSON）
     * @return Result<T>
     */
    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null
    ): AppResult<T> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = if (body != null) {
                gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
            } else {
                "{}".toRequestBody(JSON_MEDIA_TYPE)
            }

            val request = Request.Builder()
                .url("$BASE_URL$path")
                .post(jsonBody)
                .build()

            val response = client.newCall(request).await()
            handleResponse(response)
        } catch (e: IOException) {
            AppResult.Error(AppException.NetworkException(cause = e))
        } catch (e: Exception) {
            AppResult.Error(AppException.UnknownException(cause = e))
        }
    }

    // ==================== 原始响应（用于兜底手动解析） ====================

    /**
     * 通用 GET 请求，返回原始响应体字符串
     * 用于 Gson 类型映射失败时的手动 JSON 解析兜底
     */
    suspend fun getRaw(
        path: String,
        params: Map<String, String> = emptyMap()
    ): String? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = buildUrl(path, params)
            val request = Request.Builder()
                .url(urlBuilder)
                .get()
                .build()
            val response = client.newCall(request).await()
            if (response.isSuccessful) {
                response.body?.string()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 通用 POST 请求，返回原始响应体字符串
     * 用于需要 POST 但响应格式不固定的接口（如 /search/hot）
     */
    suspend fun postRaw(
        path: String,
        params: Map<String, String> = emptyMap()
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bodyJson = gson.toJson(params).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .post(bodyJson)
                .build()
            val response = client.newCall(request).await()
            if (response.isSuccessful) {
                response.body?.string()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 通用 GET 请求，返回原始响应体字符串 + Set-Cookie 响应头
     * 用于二维码登录等需要从 HTTP 头提取 Cookie 的场景
     */
    suspend fun getRawWithHeaders(
        path: String,
        params: Map<String, String> = emptyMap()
    ): RawHttpResponse? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = buildUrl(path, params)
            val request = Request.Builder()
                .url(urlBuilder)
                .get()
                .build()
            val response = client.newCall(request).await()
            if (response.isSuccessful) {
                RawHttpResponse(
                    body = response.body?.string() ?: "",
                    headers = response.headers.toMultimap()
                        .mapValues { it.value.joinToString("; ") }
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // ==================== QR 轮询专用快速客户端 ====================

    /**
     * QR 轮询专用 OkHttpClient
     * - 无日志拦截器（避免轮询日志刷屏导致 IO 卡顿）
     * - 连接/读取超时 5 秒（防止单个请求卡死）
     * - 携带 Cookie 头（与主客户端一致），确保代理服务器能追踪轮询会话
     */
    private val qrPollingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                val cookie = com.example.myapplication.manager.AccountManager.cookie
                if (!cookie.isNullOrBlank()) {
                    builder.addHeader("Cookie", cookie)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    /**
     * QR 轮询专用 GET 请求（快速超时 + 无日志）
     * 返回原始响应体 + Set-Cookie 响应头
     *
     * 注意：CancellationException 必须重新抛出，否则协程取消会失效
     */
    suspend fun qrPollGet(
        path: String,
        params: Map<String, String> = emptyMap()
    ): RawHttpResponse? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = buildUrl(path, params)
            val request = Request.Builder()
                .url(urlBuilder)
                .get()
                .build()
            val response = qrPollingClient.newCall(request).await()
            if (response.isSuccessful) {
                RawHttpResponse(
                    body = response.body?.string() ?: "",
                    headers = response.headers.toMultimap()
                        .mapValues { it.value.joinToString("; ") }
                )
            } else null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 构建请求 URL
     */
    private fun buildUrl(path: String, params: Map<String, String>): String {
        val urlBuilder = StringBuilder("$BASE_URL$path")
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            params.forEach { (key, value) ->
                urlBuilder.append(URLEncoder.encode(key, "UTF-8"))
                urlBuilder.append("=")
                urlBuilder.append(URLEncoder.encode(value, "UTF-8"))
                urlBuilder.append("&")
            }
            urlBuilder.deleteCharAt(urlBuilder.lastIndex)
        }
        return urlBuilder.toString()
    }

    // ==================== Multipart 上传 ====================

    /**
     * Multipart POST 请求（用于头像等文件上传）
     * @param path API 路径
     * @param fileBytes 文件字节数组
     * @param fileName 文件名
     * @param formFieldName 表单字段名（默认 "imgFile"）
     * @param mimeType 文件 MIME 类型
     * @param extraParams 额外表单参数（如 cookie、csrf 等）
     */
    suspend inline fun <reified T> postMultipart(
        path: String,
        fileBytes: ByteArray,
        fileName: String = "avatar.jpg",
        formFieldName: String = "imgFile",
        mimeType: String = "image/jpeg",
        extraParams: Map<String, String> = emptyMap()
    ): AppResult<T> = withContext(Dispatchers.IO) {
        try {
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .apply {
                    // 添加文件
                    addFormDataPart(
                        formFieldName, fileName,
                        fileBytes.toRequestBody(mimeType.toMediaType())
                    )
                    // 添加额外参数
                    extraParams.forEach { (key, value) ->
                        addFormDataPart(key, value)
                    }
                }
                .build()

            val request = Request.Builder()
                .url("$BASE_URL$path")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).await()
            handleResponse(response)
        } catch (e: IOException) {
            AppResult.Error(AppException.NetworkException(cause = e))
        } catch (e: Exception) {
            AppResult.Error(AppException.UnknownException(cause = e))
        }
    }

    // ==================== 响应处理 ====================

    /**
     * 从 JSON 字符串中提取业务 code 字段
     * 用于在反序列化为具体类型之前拦截需要登录（301）等全局错误
     */
    @PublishedApi
    internal fun extractBizCode(bodyString: String): Int? {
        if (bodyString.isBlank()) return null
        return try {
            com.google.gson.JsonParser.parseString(bodyString)
                .asJsonObject?.get("code")?.asInt
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 统一处理 HTTP 响应
     * - 2xx：先检查业务 code，301 直接返回 Error 并清除登录态
     * - 4xx/5xx：封装为 ServerException
     */
    @PublishedApi
    internal inline fun <reified T> handleResponse(response: Response): AppResult<T> {
        return try {
            val bodyString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                // 全局拦截业务 code=301（需要登录），在反序列化之前处理
                val bizCode = extractBizCode(bodyString)
                if (bizCode == 301) {
                    com.example.myapplication.manager.AccountManager.clearLoginState()
                    return AppResult.Error(
                        AppException.ServerException(
                            code = 301,
                            message = "登录已过期，请重新登录"
                        )
                    )
                }
                val data: T = gson.fromJson(bodyString, object : TypeToken<T>() {}.type)
                AppResult.Success(data)
            } else {
                AppResult.Error(
                    AppException.ServerException(
                        code = response.code,
                        message = "服务器返回错误 [${response.code}]"
                    )
                )
            }
        } catch (e: com.google.gson.JsonSyntaxException) {
            AppResult.Error(AppException.ParseException("JSON 解析失败", cause = e))
        }
    }
}

// ==================== 原始响应数据类 ====================

/**
 * 原始 HTTP 响应（用于手动 JSON 解析 + 提取 Set-Cookie）
 */
data class RawHttpResponse(
    val body: String,
    val headers: Map<String, String> = emptyMap()
) {
    /** 提取 Set-Cookie 头中所有 cookie 拼接为单个字符串 */
    fun extractCookies(): String? {
        val setCookie = headers["Set-Cookie"] ?: headers["set-cookie"] ?: return null
        // Set-Cookie 可能有多条，用 "; " 拼接
        return setCookie.takeIf { it.isNotBlank() }
    }
}

// ==================== OkHttp 协程扩展 ====================

/**
 * 将 OkHttp 的异步回调转换为 Kotlin 挂起函数
 * 利用 suspendCancellableCoroutine 实现协程与回调的桥接
 */
@PublishedApi
internal suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWith(Result.failure(e))
            }
        })
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Exception) {
            }
        }
    }
}
