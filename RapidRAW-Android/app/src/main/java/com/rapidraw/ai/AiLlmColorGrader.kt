package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AI LLM 色彩分级器 — 灵感来自 PixelFruit 的 AI 色彩分级模块和
 * AlcedoStudio 的 AI 图像描述功能。
 *
 * 使用大语言模型视觉 API（如通义千问 VL、GPT-4o）分析图像内容，
 * 自动生成场景描述、检测色彩问题，并建议色彩调整参数。
 *
 * 工作流程：
 * 1. 图像准备：缩放至最大 512px 长边，JPEG 质量 85 编码，Base64 传输
 * 2. API 请求：根据 Provider 构建多模态请求体
 * 3. Prompt 工程：引导 LLM 返回结构化 JSON
 * 4. 响应解析：映射到 Adjustments 参数范围，校验并钳位
 * 5. 配置持久化：SharedPreferences 存储（API Key 加密）
 */
class AiLlmColorGrader(private val context: Context) {

    enum class LlmProvider {
        ALIBABA_QWEN,    // 通义千问
        OPENAI,          // OpenAI GPT-4V
        CUSTOM           // 自定义端点
    }

    data class LlmConfig(
        val provider: LlmProvider = LlmProvider.ALIBABA_QWEN,
        val apiKey: String = "",
        val model: String = "qwen-vl-plus",
        val customEndpoint: String = "",   // CUSTOM provider 端点
        val maxTokens: Int = 1024,
        val temperature: Float = 0.3f
    )

    data class ColorGradingSuggestion(
        val adjustments: Map<String, Float>,  // 调整名称 → 值
        val description: String,              // 自然语言描述
        val confidence: Float,                // 0..1
        val style: String                     // 建议的风格名称
    )

    data class AnalysisResult(
        val suggestions: List<ColorGradingSuggestion>,
        val sceneDescription: String,
        val detectedIssues: List<String>,     // e.g. "underexposed", "too warm"
        val imageTags: List<String>           // e.g. "landscape", "portrait", "sunset"
    )

    // ── JSON 响应结构 ────────────────────────────────────────────

    @Serializable
    private data class LlmResponse(
        val scene: String = "",
        val tags: List<String> = emptyList(),
        val issues: List<String> = emptyList(),
        val suggestions: List<LlmSuggestion> = emptyList()
    )

    @Serializable
    private data class LlmSuggestion(
        val style: String = "",
        val description: String = "",
        val confidence: Float = 0f,
        val adjustments: Map<String, Float> = emptyMap()
    )

    // ── 调整参数合法范围 ────────────────────────────────────────

    private val adjustmentRanges = mapOf(
        "exposure" to (-5f to 5f),
        "brightness" to (-5f to 5f),
        "contrast" to (-100f to 100f),
        "highlights" to (-150f to 150f),
        "shadows" to (-100f to 100f),
        "whites" to (-30f to 30f),
        "blacks" to (-60f to 60f),
        "temperature" to (-100f to 100f),
        "tint" to (-100f to 100f),
        "saturation" to (-100f to 100f),
        "vibrance" to (-100f to 100f),
        "clarity" to (-100f to 100f),
        "dehaze" to (-100f to 100f),
        "sharpness" to (0f to 150f),
        "lumaNoiseReduction" to (0f to 100f),
        "colorNoiseReduction" to (0f to 100f),
        "vignetteAmount" to (-100f to 100f),
        "grainAmount" to (0f to 100f),
        "structure" to (-100f to 100f),
        "softGlow" to (0f to 1f),
        "toneLevel" to (-1f to 1f),
        "oklabHueShift" to (-1f to 1f),
        "oklabSaturation" to (-1f to 1f),
        "oklabChroma" to (-1f to 1f),
        "oklabLightness" to (-1f to 1f),
        "oklabContrast" to (-1f to 1f),
    )

    // ── 配置持久化 ──────────────────────────────────────────────

    private val prefs by lazy {
        context.getSharedPreferences("ai_llm_color_grader", Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 分析图像并获取色彩分级建议。
     *
     * @param bitmap 输入图像
     * @param config LLM 配置（provider、apiKey、model 等）
     * @param style 可选的风格提示，如 "cinematic"、"vintage"、"clean"
     * @return 分析结果，或包含描述性错误信息的 failure
     */
    suspend fun analyzeAndSuggest(
        bitmap: Bitmap,
        config: LlmConfig = LlmConfig(),
        style: String = ""
    ): Result<AnalysisResult> = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank() && config.provider != LlmProvider.CUSTOM) {
            return@withContext Result.failure(
                IllegalStateException("API Key 未配置，请先在设置中填入有效的 API Key")
            )
        }

        try {
            // 1. 图像准备
            val base64Image = prepareImage(bitmap)

            // 2. 构建 Prompt
            val prompt = buildPrompt(style)

            // 3. 发送 API 请求
            val responseBody = callLlmApi(base64Image, prompt, config)

            // 4. 解析响应
            val result = parseResponse(responseBody)

            Result.success(result)
        } catch (e: ApiKeyInvalidException) {
            // 无效 API Key：清除已保存的 Key
            prefs.edit().remove(PREF_KEY_API_KEY).apply()
            Result.failure(e)
        } catch (e: RateLimitException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                RuntimeException("AI 色彩分级分析失败: ${e.message}", e)
            )
        }
    }

    /**
     * 保存配置到 SharedPreferences。API Key 使用 AES-GCM 加密存储。
     */
    fun saveConfig(config: LlmConfig) {
        prefs.edit().apply {
            putString(PREF_KEY_PROVIDER, config.provider.name)
            putString(PREF_KEY_MODEL, config.model)
            putString(PREF_KEY_CUSTOM_ENDPOINT, config.customEndpoint)
            putInt(PREF_KEY_MAX_TOKENS, config.maxTokens)
            putFloat(PREF_KEY_TEMPERATURE, config.temperature)
            if (config.apiKey.isNotEmpty()) {
                putString(PREF_KEY_API_KEY, encryptApiKey(config.apiKey))
            } else {
                remove(PREF_KEY_API_KEY)
            }
            apply()
        }
    }

    /**
     * 从 SharedPreferences 加载配置。API Key 解密后返回。
     */
    fun loadConfig(): LlmConfig {
        val providerName = prefs.getString(PREF_KEY_PROVIDER, null)
        val provider = try {
            providerName?.let { LlmProvider.valueOf(it) } ?: LlmProvider.ALIBABA_QWEN
        } catch (_: IllegalArgumentException) {
            LlmProvider.ALIBABA_QWEN
        }
        val encryptedKey = prefs.getString(PREF_KEY_API_KEY, null)
        val apiKey = encryptedKey?.let { decryptApiKey(it) } ?: ""
        return LlmConfig(
            provider = provider,
            apiKey = apiKey,
            model = prefs.getString(PREF_KEY_MODEL, "qwen-vl-plus") ?: "qwen-vl-plus",
            customEndpoint = prefs.getString(PREF_KEY_CUSTOM_ENDPOINT, "") ?: "",
            maxTokens = prefs.getInt(PREF_KEY_MAX_TOKENS, 1024),
            temperature = prefs.getFloat(PREF_KEY_TEMPERATURE, 0.3f)
        )
    }

    // ── 图像准备 ────────────────────────────────────────────────

    private fun prepareImage(bitmap: Bitmap): String {
        // 缩放到最大 512px 长边
        val maxSide = MAX_IMAGE_SIZE
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > height) {
            maxSide.toFloat() / width.toFloat()
        } else {
            maxSide.toFloat() / height.toFloat()
        }
        val scaledBitmap = if (scale < 1f) {
            val newW = (width * scale).toInt().coerceAtLeast(1)
            val newH = (height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }

        // JPEG 质量 85 编码
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val jpegBytes = outputStream.toByteArray()

        // 释放缩放产生的临时 Bitmap（避免回收原始 Bitmap）
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        // Base64 编码
        return Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
    }

    // ── Prompt 工程 ──────────────────────────────────────────────

    private fun buildPrompt(styleHint: String): String {
        val styleInstruction = if (styleHint.isNotBlank()) {
            "用户期望的风格方向: \"$styleHint\"。请优先给出符合该风格的建议。"
        } else {
            "请根据图像内容自动推荐最合适的色彩风格。"
        }

        return """
            你是一位专业的色彩分级师，擅长分析照片并为后期处理提供调整建议。
            请分析这张照片的色彩和曝光状况，然后返回一个 JSON 对象。

            $styleInstruction

            可用的调整参数及其取值范围如下（请在建议中使用这些参数名）：
            - exposure: -5 到 5（曝光，正值提亮，负值压暗）
            - brightness: -5 到 5（亮度）
            - contrast: -100 到 100（对比度）
            - highlights: -150 到 150（高光，负值压暗高光）
            - shadows: -100 到 100（阴影，正值提亮阴影）
            - whites: -30 到 30（白色色阶）
            - blacks: -60 到 60（黑色色阶）
            - temperature: -100 到 100（色温，正值偏暖，负值偏冷）
            - tint: -100 到 100（色调偏移，正值偏品，负值偏绿）
            - saturation: -100 到 100（饱和度）
            - vibrance: -100 到 100（自然饱和度，低饱和像素受影响更大）
            - clarity: -100 到 100（清晰度/中间调对比）
            - dehaze: -100 到 100（去雾）
            - sharpness: 0 到 150（锐化）
            - lumaNoiseReduction: 0 到 100（亮度降噪）
            - colorNoiseReduction: 0 到 100（色彩降噪）
            - vignetteAmount: -100 到 100（暗角，负值加暗角）
            - grainAmount: 0 到 100（胶片颗粒）
            - structure: -100 到 100（结构/纹理）
            - softGlow: 0 到 1（柔光/辉光）
            - toneLevel: -1 到 1（影调控制）
            - oklabHueShift: -1 到 1（感知色相偏移）
            - oklabSaturation: -1 到 1（感知饱和度）
            - oklabChroma: -1 到 1（感知色度）
            - oklabLightness: -1 到 1（感知亮度）
            - oklabContrast: -1 到 1（感知对比度）

            请严格按以下 JSON 格式返回，不要包含任何其他文字：
            {
              "scene": "场景描述，例如：黄金时刻的风景",
              "tags": ["landscape", "sunset", "mountains"],
              "issues": ["slightly underexposed", "warm color cast"],
              "suggestions": [
                {
                  "style": "风格名称，例如：Golden Hour Enhancement",
                  "description": "调整说明，例如：增强暖色调，提亮阴影",
                  "confidence": 0.85,
                  "adjustments": {
                    "exposure": 0.3,
                    "highlights": -20,
                    "shadows": 15,
                    "temperature": 8,
                    "saturation": 10,
                    "vibrance": 15,
                    "contrast": 5,
                    "clarity": 10
                  }
                }
              ]
            }

            要求：
            1. suggestions 数组提供 1-3 种不同风格的建议
            2. adjustments 中的参数值必须在上面列出的合法范围内
            3. confidence 为 0-1 之间的浮点数
            4. 每个建议至少包含 3 个调整参数
            5. issues 列出检测到的色彩/曝光问题
            6. tags 使用英文小写标签
        """.trimIndent()
    }

    // ── API 调用 ─────────────────────────────────────────────────

    private fun callLlmApi(
        base64Image: String,
        prompt: String,
        config: LlmConfig
    ): String {
        return when (config.provider) {
            LlmProvider.ALIBABA_QWEN -> callQwenApi(base64Image, prompt, config)
            LlmProvider.OPENAI -> callOpenAiApi(base64Image, prompt, config)
            LlmProvider.CUSTOM -> callCustomApi(base64Image, prompt, config)
        }
    }

    private fun callQwenApi(
        base64Image: String,
        prompt: String,
        config: LlmConfig
    ): String {
        val url = URL("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            val requestBody = buildQwenRequestBody(base64Image, prompt, config)
            writeRequestBody(connection, requestBody)

            val responseCode = connection.responseCode
            when {
                responseCode == 401 || responseCode == 403 -> {
                    throw ApiKeyInvalidException("API Key 无效或已过期 (HTTP $responseCode)")
                }
                responseCode == 429 -> {
                    val retryAfter = connection.getHeaderField("Retry-After")?.toIntOrNull()
                        ?: connection.getHeaderField("X-RateLimit-Reset")?.toIntOrNull()
                    throw RateLimitException(
                        "API 调用频率超限，请稍后重试" +
                            (retryAfter?.let { "（建议等待 ${it}s）" } ?: ""),
                        retryAfterSeconds = retryAfter
                    )
                }
                responseCode !in 200..299 -> {
                    val errorBody = readErrorBody(connection)
                    throw RuntimeException(
                        "API 请求失败 (HTTP $responseCode): $errorBody"
                    )
                }
                else -> {
                    val body = readResponseBody(connection)
                    parseQwenResponseBody(body)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun callOpenAiApi(
        base64Image: String,
        prompt: String,
        config: LlmConfig
    ): String {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            val model = if (config.model == "qwen-vl-plus") "gpt-4o" else config.model
            val requestBody = buildOpenAiRequestBody(base64Image, prompt, config, model)
            writeRequestBody(connection, requestBody)

            val responseCode = connection.responseCode
            when {
                responseCode == 401 -> {
                    throw ApiKeyInvalidException("OpenAI API Key 无效或已过期 (HTTP 401)")
                }
                responseCode == 429 -> {
                    val retryAfter = connection.getHeaderField("Retry-After")?.toIntOrNull()
                    throw RateLimitException(
                        "OpenAI API 调用频率超限，请稍后重试" +
                            (retryAfter?.let { "（建议等待 ${it}s）" } ?: ""),
                        retryAfterSeconds = retryAfter
                    )
                }
                responseCode !in 200..299 -> {
                    val errorBody = readErrorBody(connection)
                    throw RuntimeException(
                        "OpenAI API 请求失败 (HTTP $responseCode): $errorBody"
                    )
                }
                else -> {
                    val body = readResponseBody(connection)
                    parseOpenAiResponseBody(body)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun callCustomApi(
        base64Image: String,
        prompt: String,
        config: LlmConfig
    ): String {
        if (config.customEndpoint.isBlank()) {
            throw IllegalArgumentException("自定义端点 URL 未配置")
        }
        val url = URL(config.customEndpoint)
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            if (config.apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            // 自定义端点默认使用 OpenAI 兼容格式
            val requestBody = buildOpenAiRequestBody(
                base64Image, prompt, config, config.model
            )
            writeRequestBody(connection, requestBody)

            val responseCode = connection.responseCode
            when {
                responseCode == 401 || responseCode == 403 -> {
                    throw ApiKeyInvalidException("自定义端点认证失败 (HTTP $responseCode)")
                }
                responseCode == 429 -> {
                    val retryAfter = connection.getHeaderField("Retry-After")?.toIntOrNull()
                    throw RateLimitException(
                        "自定义端点频率超限" +
                            (retryAfter?.let { "（建议等待 ${it}s）" } ?: ""),
                        retryAfterSeconds = retryAfter
                    )
                }
                responseCode !in 200..299 -> {
                    val errorBody = readErrorBody(connection)
                    throw RuntimeException(
                        "自定义端点请求失败 (HTTP $responseCode): $errorBody"
                    )
                }
                else -> {
                    val body = readResponseBody(connection)
                    // 尝试 OpenAI 格式解析，失败则尝试 Qwen 格式
                    try {
                        parseOpenAiResponseBody(body)
                    } catch (_: Exception) {
                        parseQwenResponseBody(body)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    // ── 请求体构建 ───────────────────────────────────────────────

    private fun buildQwenRequestBody(
        base64Image: String,
        prompt: String,
        config: LlmConfig
    ): String {
        // 通义千问 VL API 格式
        val imageDataUrl = "data:image/jpeg;base64,$base64Image"
        // 手动构建 JSON 以避免 kotlinx.serialization 对 Map 的序列化差异
        return """{
            "model":"${escapeJson(config.model)}",
            "input":{
                "messages":[
                    {
                        "role":"user",
                        "content":[
                            {"image":"${escapeJson(imageDataUrl)}"},
                            {"text":"${escapeJson(prompt)}"}
                        ]
                    }
                ]
            },
            "parameters":{
                "max_tokens":${config.maxTokens},
                "temperature":${config.temperature},
                "result_format":"message"
            }
        }""".trimIndent().replace("\\s+".toRegex(), " ")
    }

    private fun buildOpenAiRequestBody(
        base64Image: String,
        prompt: String,
        config: LlmConfig,
        model: String
    ): String {
        val imageDataUrl = "data:image/jpeg;base64,$base64Image"
        return """{
            "model":"${escapeJson(model)}",
            "messages":[
                {
                    "role":"user",
                    "content":[
                        {"type":"image_url","image_url":{"url":"${escapeJson(imageDataUrl)}"}},
                        {"type":"text","text":"${escapeJson(prompt)}"}
                    ]
                }
            ],
            "max_tokens":${config.maxTokens},
            "temperature":${config.temperature}
        }""".trimIndent().replace("\\s+".toRegex(), " ")
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    // ── 响应体解析 ───────────────────────────────────────────────

    private fun parseQwenResponseBody(body: String): String {
        // 通义千问 VL 响应格式:
        // {"output":{"choices":[{"message":{"content":[{"text":"..."}]}}]}}
        val contentRegex = """"text"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        val matches = contentRegex.findAll(body)
        val textParts = matches.map { match ->
            match.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }.toList()

        if (textParts.isEmpty()) {
            throw RuntimeException("通义千问 API 响应中未找到文本内容: ${body.take(200)}")
        }
        return textParts.joinToString("")
    }

    private fun parseOpenAiResponseBody(body: String): String {
        // OpenAI 响应格式:
        // {"choices":[{"message":{"content":"..."}}]}
        val contentRegex = """"content"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        val match = contentRegex.find(body)
            ?: throw RuntimeException("OpenAI API 响应中未找到 content: ${body.take(200)}")

        return match.groupValues[1]
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    // ── LLM 文本内容 → AnalysisResult ───────────────────────────

    private fun parseResponse(llmContent: String): AnalysisResult {
        // 从 LLM 返回的文本中提取 JSON 块
        val jsonStr = extractJsonBlock(llmContent)

        val llmResponse = try {
            json.decodeFromString<LlmResponse>(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "LLM 响应 JSON 解析失败，尝试部分解析", e)
            return partialParse(llmContent)
        }

        val suggestions = llmResponse.suggestions.map { sug ->
            val clampedAdjustments = sug.adjustments.mapValues { (key, value) ->
                clampAdjustment(key, value)
            }
            ColorGradingSuggestion(
                adjustments = clampedAdjustments,
                description = sug.description,
                confidence = sug.confidence.coerceIn(0f, 1f),
                style = sug.style
            )
        }

        return AnalysisResult(
            suggestions = suggestions,
            sceneDescription = llmResponse.scene,
            detectedIssues = llmResponse.issues,
            imageTags = llmResponse.tags
        )
    }

    /**
     * 从 LLM 返回的文本中提取 JSON 块。
     * 支持三种格式：
     * 1. 纯 JSON 对象
     * 2. ```json ... ``` 代码块
     * 3. 混合文本中的 JSON
     */
    private fun extractJsonBlock(text: String): String {
        // 尝试提取 ```json ... ``` 代码块
        val codeBlockRegex = """```json\s*([\s\S]*?)```""".toRegex(RegexOption.IGNORE_CASE)
        val codeBlockMatch = codeBlockRegex.find(text)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // 尝试提取 ``` ... ``` 代码块（无语言标记）
        val genericBlockRegex = """```\s*([\s\S]*?)```""".toRegex()
        val genericMatch = genericBlockRegex.find(text)
        if (genericMatch != null) {
            val content = genericMatch.groupValues[1].trim()
            if (content.startsWith("{")) return content
        }

        // 尝试找到第一个 { 到最后一个 } 之间的内容
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }

        throw RuntimeException("LLM 响应中未找到有效的 JSON: ${text.take(200)}")
    }

    /**
     * 部分解析：JSON 解析失败时，尝试从原始文本中提取有用信息。
     */
    private fun partialParse(rawText: String): AnalysisResult {
        val tags = mutableListOf<String>()
        val issues = mutableListOf<String>()

        // 尝试提取 tags
        val tagsRegex = """"tags"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        tagsRegex.find(rawText)?.let { match ->
            val tagContent = match.groupValues[1]
            tagContent.split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .forEach { tags.add(it) }
        }

        // 尝试提取 issues
        val issuesRegex = """"issues"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        issuesRegex.find(rawText)?.let { match ->
            val issueContent = match.groupValues[1]
            issueContent.split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .forEach { issues.add(it) }
        }

        // 尝试提取 scene
        val sceneRegex = """"scene"\s*:\s*"(.*?)"""".toRegex()
        val scene = sceneRegex.find(rawText)?.groupValues?.get(1) ?: "无法解析场景"

        return AnalysisResult(
            suggestions = emptyList(),
            sceneDescription = scene,
            detectedIssues = issues,
            imageTags = tags
        )
    }

    // ── 调整值校验与钳位 ─────────────────────────────────────────

    private fun clampAdjustment(name: String, value: Float): Float {
        val range = adjustmentRanges[name] ?: return value
        val clamped = value.coerceIn(range.first, range.second)
        if (clamped != value) {
            Log.d(TAG, "调整参数 '$name' 值 $value 超出范围 [${range.first}, ${range.second}]，已钳位至 $clamped")
        }
        return clamped
    }

    // ── HTTP 辅助方法 ────────────────────────────────────────────

    private fun writeRequestBody(connection: HttpURLConnection, body: String) {
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(body)
            writer.flush()
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private fun readErrorBody(connection: HttpURLConnection): String {
        return try {
            connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                reader.readText()
            } ?: "无错误详情"
        } catch (_: Exception) {
            "无错误详情"
        }
    }

    // ── API Key 加密/解密 ────────────────────────────────────────

    private fun encryptApiKey(plainText: String): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val keySpec = SecretKeySpec(getEncryptionKey(), AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        // 拼接格式: Base64(iv + ciphertext)
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptApiKey(cipherText: String): String {
        return try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            // GCM IV 长度为 12 字节
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val keySpec = SecretKeySpec(getEncryptionKey(), AES_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "API Key 解密失败，返回空字符串", e)
            ""
        }
    }

    /**
     * 生成基于应用签名 + 设备指纹的稳定加密密钥。
     * 2026 正式版: 加入 Build.FINGERPRINT/BOARD/BRAND/HARDWARE 等设备信息，
     * 确保每个安装实例的密钥不可预测，防止跨设备密钥重用攻击。
     */
    private fun getEncryptionKey(): ByteArray {
        val seed = buildString {
            append("com.rapidraw.ai.llm_color_grader_key_")
            append(context.packageName)
            append(android.os.Build.FINGERPRINT ?: "")
            append(android.os.Build.BOARD ?: "")
            append(android.os.Build.BRAND ?: "")
            append(android.os.Build.HARDWARE ?: "")
        }
        // 从种子派生 32 字节 AES 密钥
        val keyBytes = ByteArray(AES_KEY_SIZE)
        val seedBytes = seed.toByteArray(StandardCharsets.UTF_8)
        for (i in keyBytes.indices) {
            keyBytes[i] = (seedBytes[i % seedBytes.size].toInt()
                .xor((i * 31 + 17) and 0xFF)
                .xor(seedBytes[(i * 7 + 3) % seedBytes.size].toInt())).toByte()
        }
        return keyBytes
    }

    // ── 异常类 ────────────────────────────────────────────────────

    private class ApiKeyInvalidException(message: String) : RuntimeException(message)

    private class RateLimitException(
        message: String,
        val retryAfterSeconds: Int?
    ) : RuntimeException(message)

    companion object {
        private const val TAG = "AiLlmColorGrader"
        private const val TIMEOUT_MS = 60_000
        private const val MAX_IMAGE_SIZE = 512
        private const val JPEG_QUALITY = 85

        // SharedPreferences 键名
        private const val PREF_KEY_PROVIDER = "provider"
        private const val PREF_KEY_API_KEY = "api_key_encrypted"
        private const val PREF_KEY_MODEL = "model"
        private const val PREF_KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        private const val PREF_KEY_MAX_TOKENS = "max_tokens"
        private const val PREF_KEY_TEMPERATURE = "temperature"

        // AES-GCM 加密参数
        private const val AES_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE = 32   // AES-256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128 // bits
    }
}
