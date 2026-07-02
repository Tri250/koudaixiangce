package com.rapidraw.core

import android.content.Context
import android.util.Log
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.internal.tls.OkHostnameVerifier
import okio.buffer
import okio.source
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 网络缓存管理器 — v1.8.0 正式版性能优化新增。
 *
 * 提供：
 * 1. OkHttp 磁盘缓存（50MB HTTP 缓存）
 * 2. 连接池复用（最大 5 个空闲连接，保持 5 分钟）
 * 3. GZIP 解压支持
 * 4. ETag/If-None-Match 条件请求
 * 5. 超时控制（连接 10s、读取 30s、写入 30s）
 *
 * 使用方式：
 * val client = NetworkCache.getClient(context)
 * val response = client.newCall(request).execute()
 *
 * @since v1.8.0
 */
object NetworkCache {

    private const val TAG = "NetworkCache"
    private const val CACHE_DIR = "http_cache"
    private const val CACHE_SIZE = 50L * 1024 * 1024  // 50MB
    private const val CONNECT_TIMEOUT_S = 10L
    private const val READ_TIMEOUT_S = 30L
    private const val WRITE_TIMEOUT_S = 30L
    private const val MAX_IDLE_CONNECTIONS = 5
    private const val KEEP_ALIVE_DURATION_MINUTES = 5L

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    /** 获取全局 OkHttpClient 实例（线程安全，懒加载） */
    fun getClient(context: Context): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: buildClient(context).also { okHttpClient = it }
        }
    }

    private fun buildClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        val cache = Cache(cacheDir, CACHE_SIZE)

        return OkHttpClient.Builder()
            .cache(cache)
            .connectionPool(
                ConnectionPool(
                    MAX_IDLE_CONNECTIONS,
                    KEEP_ALIVE_DURATION_MINUTES,
                    TimeUnit.MINUTES,
                )
            )
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .addInterceptor(gzipInterceptor())
            .addInterceptor(cacheControlInterceptor())
            .addNetworkInterceptor(loggingInterceptor())
            .build()
            .also {
                Log.i(TAG, "OkHttpClient built: cache=${cacheDir.absolutePath} (${CACHE_SIZE / 1024 / 1024}MB), pool=${MAX_IDLE_CONNECTIONS} connections")
            }
    }

    /** GZIP 解压拦截器 */
    private fun gzipInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val newRequest = request.newBuilder()
            .header("Accept-Encoding", "gzip")
            .build()

        val response = chain.proceed(newRequest)
        val contentEncoding = response.header("Content-Encoding")
        val url = request.url.toString()

        if (contentEncoding != null && contentEncoding.contains("gzip", ignoreCase = true)) {
            val body = response.body ?: return@Interceptor response
            val gzipStream = GZIPInputStream(body.source().inputStream())
            val decompressedBody = okhttp3.ResponseBody.create(
                body.contentType(),
                body.contentLength(),
                gzipStream.source().buffer(),
            )
            response.newBuilder()
                .body(decompressedBody)
                .removeHeader("Content-Encoding")
                .build()
        } else {
            response
        }
    }

    /** 缓存控制拦截器 */
    private fun cacheControlInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        // 缓存 GET 请求响应（最多 1 小时）
        if (request.method == "GET") {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=3600")
                .build()
        } else {
            response
        }
    }

    /** 请求日志拦截器（仅 Debug 构建） */
    private fun loggingInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val start = System.currentTimeMillis()
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - start
            if (com.rapidraw.BuildConfig.DEBUG) {
                Log.d(TAG, "${request.method} ${request.url} → ${response.code} (${duration}ms, ${response.body?.contentLength() ?: 0} bytes)")
            }
            response
        }
    }

    /** 获取缓存统计信息 */
    fun getCacheStats(context: Context): CacheStats {
        val cacheFile = File(context.cacheDir, CACHE_DIR)
        val hitCount = 0L // 需要从 OkHttpClient 获取，此处为估算
        val missCount = 0L
        val totalSize = if (cacheFile.exists()) {
            cacheFile.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
        return CacheStats(
            requestCount = hitCount + missCount,
            hitCount = hitCount,
            missCount = missCount,
            cacheSize = totalSize,
        )
    }

    data class CacheStats(
        val requestCount: Long,
        val hitCount: Long,
        val missCount: Long,
        val cacheSize: Long,
    )

    /** 清除缓存 */
    fun clearCache(context: Context) {
        runCatching {
            val cacheFile = File(context.cacheDir, CACHE_DIR)
            cacheFile.deleteRecursively()
            // 重建 client
            okHttpClient = null
            getClient(context)
            Log.i(TAG, "HTTP cache cleared")
        }.onFailure { Log.e(TAG, "Failed to clear HTTP cache", it) }
    }
}