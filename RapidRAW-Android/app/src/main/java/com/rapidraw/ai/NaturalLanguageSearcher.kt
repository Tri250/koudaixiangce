package com.rapidraw.ai

import com.rapidraw.data.model.ExifData

/**
 * 自然语言照片搜索 — 灵感来自 AlcedoStudio 的日常语言搜索功能。
 * 用户可以用 "海边日落的人像" 这样的自然语言搜索照片，
 * 系统将文本解析为结构化标签并在图像语义标签库中匹配。
 *
 * 支持：
 * - 中英文关键词映射
 * - EXIF/调整参数过滤条件
 * - 语义标签匹配
 */

data class SearchQuery(
    val rawText: String,
    val sceneTags: Set<String>,      // 提取的场景关键词
    val subjectTags: Set<String>,    // 提取的主体关键词
    val styleTags: Set<String>,      // 提取的风格关键词
    val moodTags: Set<String>,       // 提取的情绪关键词
    val colorToneTags: Set<String>,  // 提取的色调关键词
    val timeOfDayTags: Set<String>,  // 提取的时段关键词
    val exifFilters: Set<ExifFilter>,// EXIF 过滤条件
)

/**
 * EXIF/调整参数过滤条件
 */
sealed class ExifFilter {
    /** 焦距范围 */
    data class FocalLengthRange(val minMm: Float, val maxMm: Float) : ExifFilter()
    /** 方向约束 */
    data class Orientation(val isPortrait: Boolean) : ExifFilter()
    /** ISO 范围 */
    data class IsoRange(val minIso: Int, val maxIso: Int) : ExifFilter()
    /** 快门速度（秒）范围 */
    data class ShutterSpeedRange(val minSec: Float, val maxSec: Float) : ExifFilter()
    /** 闪光灯 */
    data class FlashFired(val fired: Boolean) : ExifFilter()
    /** 拍摄时段（小时范围） */
    data class TimeRange(val startHour: Int, val endHour: Int) : ExifFilter()
    /** 白平衡 */
    data class WhiteBalance(val isAuto: Boolean) : ExifFilter()
}

data class SearchResult(
    val imagePath: String,
    val matchScore: Float,    // 0-1
    val matchedTags: List<SemanticTag>,
)

/**
 * 自然语言搜索器 — 将中文/英文自然语言解析为结构化查询，并在语义标签库中匹配。
 * 同时支持基于 EXIF 数据的过滤。
 */
class NaturalLanguageSearcher {

    companion object {
        // 各类别权重
        private const val WEIGHT_SCENE = 0.25f
        private const val WEIGHT_SUBJECT = 0.25f
        private const val WEIGHT_MOOD = 0.15f
        private const val WEIGHT_STYLE = 0.1f
        private const val WEIGHT_COLOR_TONE = 0.1f
        private const val WEIGHT_TIME_OF_DAY = 0.1f
        private const val WEIGHT_EXIF = 0.05f
    }

    /** 中文关键词 → 标准标签值 映射 */
    private val sceneKeywordMap = mapOf(
        "海边" to "海滩", "海滩" to "海滩",
        "城市" to "城市", "都市" to "城市",
        "山" to "山野", "山野" to "山野", "山地" to "山野",
        "室内" to "室内", "屋内" to "室内", "室内" to "室内",
        "街" to "街拍", "街拍" to "街拍", "街头" to "街拍",
        "雪" to "雪地", "雪地" to "雪地", "雪景" to "雪地",
        "沙漠" to "沙漠",
        "花园" to "花园", "公园" to "花园",
    )

    private val subjectKeywordMap = mapOf(
        "人" to "人像", "人物" to "人像", "女孩" to "人像", "男孩" to "人像",
        "女人" to "人像", "男人" to "人像", "人像" to "人像",
        "猫" to "宠物", "狗" to "宠物", "宠物" to "宠物",
        "建筑" to "建筑", "楼" to "建筑", "大楼" to "建筑",
        "食物" to "美食", "美食" to "美食", "菜" to "美食",
        "花" to "花卉", "花卉" to "花卉",
        "天" to "天空", "天空" to "天空", "云" to "天空",
        "水" to "水面", "湖" to "水面", "河" to "水面", "海" to "水面", "水面" to "水面",
        "车" to "车辆", "汽车" to "车辆",
    )

    private val styleKeywordMap = mapOf(
        "简约" to "简约", "极简" to "简约",
        "戏剧" to "戏剧", "冲击" to "戏剧",
        "复古" to "复古", "怀旧" to "复古", "老" to "复古", "胶片" to "复古",
        "清新" to "清新", "小清新" to "清新",
        "暗调" to "暗调", "低调" to "暗调",
        "高调" to "高调", "明亮" to "高调",
    )

    private val moodKeywordMap = mapOf(
        "温暖" to "温暖", "温馨" to "温暖",
        "宁静" to "宁静", "安静" to "宁静", "平和" to "宁静",
        "神秘" to "神秘",
        "欢快" to "欢快", "开心" to "欢快",
        "忧郁" to "忧郁", "悲伤" to "忧郁",
        "浪漫" to "浪漫",
        "孤独" to "孤独", "寂寞" to "孤独",
    )

    private val colorToneKeywordMap = mapOf(
        "暖色" to "暖色调", "暖调" to "暖色调", "暖" to "暖色调",
        "冷色" to "冷色调", "冷调" to "冷色调", "冷" to "冷色调",
        "柔和" to "柔和", "淡" to "柔和",
        "对比" to "高对比", "强烈" to "高对比",
        "黑白" to "单色",
    )

    private val timeOfDayKeywordMap = mapOf(
        "日出" to "日出", "朝霞" to "日出",
        "日落" to "日落", "夕阳" to "日落", "晚霞" to "日落",
        "夜" to "夜晚", "夜晚" to "夜晚", "夜景" to "夜晚",
        "黄金时刻" to "黄金时刻", "魔幻时刻" to "黄金时刻",
        "正午" to "正午", "中午" to "正午",
        "黄昏" to "黄昏", "暮色" to "黄昏",
        "黎明" to "日出", "拂晓" to "日出",
    )

    // ── 英文关键词映射 ──

    private val sceneKeywordMapEn = mapOf(
        "beach" to "海滩", "seaside" to "海滩", "coast" to "海滩", "ocean" to "海滩",
        "city" to "城市", "urban" to "城市", "downtown" to "城市",
        "mountain" to "山野", "hills" to "山野",
        "indoor" to "室内", "inside" to "室内", "interior" to "室内",
        "street" to "街拍", "road" to "街拍",
        "snow" to "雪地", "winter" to "雪地",
        "desert" to "沙漠",
        "garden" to "花园", "park" to "花园",
    )

    private val subjectKeywordMapEn = mapOf(
        "person" to "人像", "people" to "人像", "woman" to "人像", "man" to "人像",
        "girl" to "人像", "boy" to "人像", "portrait" to "人像",
        "cat" to "宠物", "dog" to "宠物", "pet" to "宠物",
        "building" to "建筑", "architecture" to "建筑",
        "food" to "美食", "meal" to "美食", "dish" to "美食",
        "flower" to "花卉", "flowers" to "花卉",
        "sky" to "天空", "cloud" to "天空", "clouds" to "天空",
        "water" to "水面", "lake" to "水面", "river" to "水面",
        "car" to "车辆", "vehicle" to "车辆",
    )

    private val styleKeywordMapEn = mapOf(
        "minimalist" to "简约", "minimal" to "简约", "simple" to "简约",
        "dramatic" to "戏剧", "dramatic" to "戏剧",
        "vintage" to "复古", "retro" to "复古", "film" to "复古",
        "fresh" to "清新", "light" to "清新",
        "dark" to "暗调", "lowkey" to "暗调", "moody" to "暗调",
        "bright" to "高调", "highkey" to "高调",
    )

    private val moodKeywordMapEn = mapOf(
        "warm" to "温暖", "cozy" to "温暖",
        "calm" to "宁静", "peaceful" to "宁静", "quiet" to "宁静",
        "mysterious" to "神秘", "mystery" to "神秘",
        "joyful" to "欢快", "happy" to "欢快", "cheerful" to "欢快",
        "melancholy" to "忧郁", "sad" to "忧郁", "blue" to "忧郁",
        "romantic" to "浪漫", "romance" to "浪漫",
        "lonely" to "孤独", "solitary" to "孤独", "alone" to "孤独",
    )

    private val colorToneKeywordMapEn = mapOf(
        "warm" to "暖色调", "warmtone" to "暖色调",
        "cool" to "冷色调", "cooltone" to "冷色调",
        "pastel" to "柔和", "soft" to "柔和",
        "contrast" to "高对比", "highcontrast" to "高对比",
        "blackandwhite" to "单色", "monochrome" to "单色", "bw" to "单色",
    )

    private val timeOfDayKeywordMapEn = mapOf(
        "sunrise" to "日出", "dawn" to "日出",
        "sunset" to "日落", "dusk" to "黄昏",
        "night" to "夜晚", "nighttime" to "夜晚",
        "goldenhour" to "黄金时刻", "magic" to "黄金时刻",
        "noon" to "正午", "midday" to "正午",
        "twilight" to "黄昏", "evening" to "黄昏",
    )

    // ── EXIF 关键词映射（中英文） ──

    /** 关键词 → EXIF 过滤条件 */
    private val exifFilterMap: Map<String, ExifFilter> = mapOf(
        // 人像：中长焦 + 竖构图
        "人像" to ExifFilter.FocalLengthRange(50f, 200f),
        "portrait" to ExifFilter.FocalLengthRange(50f, 200f),
        "女孩" to ExifFilter.FocalLengthRange(50f, 200f),
        "女人" to ExifFilter.FocalLengthRange(50f, 200f),
        // 风景：广角 + 横构图
        "风景" to ExifFilter.FocalLengthRange(10f, 35f),
        "landscape" to ExifFilter.FocalLengthRange(10f, 35f),
        "山野" to ExifFilter.FocalLengthRange(10f, 35f),
        // 日落/日出：拍摄时段
        "日落" to ExifFilter.TimeRange(17, 20),
        "夕阳" to ExifFilter.TimeRange(17, 20),
        "sunset" to ExifFilter.TimeRange(17, 20),
        "日出" to ExifFilter.TimeRange(5, 8),
        "sunrise" to ExifFilter.TimeRange(5, 8),
        "黎明" to ExifFilter.TimeRange(4, 7),
        "dawn" to ExifFilter.TimeRange(4, 7),
        // 夜景：高 ISO + 长曝光
        "夜景" to ExifFilter.IsoRange(800, 25600),
        "夜晚" to ExifFilter.IsoRange(400, 25600),
        "night" to ExifFilter.IsoRange(400, 25600),
        // 室内：闪光灯
        "室内" to ExifFilter.FlashFired(true),
        "indoor" to ExifFilter.FlashFired(true),
    )

    /**
     * 解析自然语言文本为结构化搜索查询。
     * 支持中文和英文关键词映射，同时提取 EXIF 过滤条件。
     */
    fun parseQuery(text: String): SearchQuery {
        val sceneTags = mutableSetOf<String>()
        val subjectTags = mutableSetOf<String>()
        val styleTags = mutableSetOf<String>()
        val moodTags = mutableSetOf<String>()
        val colorToneTags = mutableSetOf<String>()
        val timeOfDayTags = mutableSetOf<String>()
        val exifFilters = mutableSetOf<ExifFilter>()

        val lowerText = text.lowercase()

        // 合并中英文关键词映射
        val allMaps = listOf(
            (sceneKeywordMap + sceneKeywordMapEn) to sceneTags,
            (subjectKeywordMap + subjectKeywordMapEn) to subjectTags,
            (styleKeywordMap + styleKeywordMapEn) to styleTags,
            (moodKeywordMap + moodKeywordMapEn) to moodTags,
            (colorToneKeywordMap + colorToneKeywordMapEn) to colorToneTags,
            (timeOfDayKeywordMap + timeOfDayKeywordMapEn) to timeOfDayTags,
        )

        // 按关键词长度降序匹配，避免短关键词误匹配
        for ((keywordMap, tagSet) in allMaps) {
            val sortedKeywords = keywordMap.keys.sortedByDescending { it.length }
            for (keyword in sortedKeywords) {
                if (text.contains(keyword, ignoreCase = true)) {
                    tagSet.add(keywordMap.getValue(keyword))
                }
            }
        }

        // 提取 EXIF 过滤条件
        for ((keyword, filter) in exifFilterMap) {
            if (text.contains(keyword, ignoreCase = true)) {
                exifFilters.add(filter)
            }
        }

        // 从语义标签推断隐含的 EXIF 过滤
        // "portrait" → 竖构图
        if (subjectTags.contains("人像")) {
            exifFilters.add(ExifFilter.Orientation(isPortrait = true))
        }
        // "landscape" → 横构图
        if (sceneTags.contains("山野") || sceneTags.contains("海滩")) {
            exifFilters.add(ExifFilter.Orientation(isPortrait = false))
        }

        return SearchQuery(
            rawText = text,
            sceneTags = sceneTags,
            subjectTags = subjectTags,
            styleTags = styleTags,
            moodTags = moodTags,
            colorToneTags = colorToneTags,
            timeOfDayTags = timeOfDayTags,
            exifFilters = exifFilters,
        )
    }

    /**
     * 计算搜索查询与图片语义标签 + EXIF 数据的匹配分数。
     * 分数为各类别加权匹配置信度之和。
     */
    fun match(query: SearchQuery, tags: List<SemanticTag>, exif: ExifData? = null): Float {
        var score = 0f

        val categoryTagMaps = tags.groupBy { it.category }

        // 场景匹配
        score += matchCategory(
            query.sceneTags,
            categoryTagMaps[TagCategory.SCENE],
            WEIGHT_SCENE
        )

        // 主体匹配
        score += matchCategory(
            query.subjectTags,
            categoryTagMaps[TagCategory.SUBJECT],
            WEIGHT_SUBJECT
        )

        // 情绪匹配
        score += matchCategory(
            query.moodTags,
            categoryTagMaps[TagCategory.MOOD],
            WEIGHT_MOOD
        )

        // 风格匹配
        score += matchCategory(
            query.styleTags,
            categoryTagMaps[TagCategory.STYLE],
            WEIGHT_STYLE
        )

        // 色调匹配
        score += matchCategory(
            query.colorToneTags,
            categoryTagMaps[TagCategory.COLOR_TONE],
            WEIGHT_COLOR_TONE
        )

        // 时段匹配
        score += matchCategory(
            query.timeOfDayTags,
            categoryTagMaps[TagCategory.TIME_OF_DAY],
            WEIGHT_TIME_OF_DAY
        )

        // EXIF 过滤匹配
        if (exif != null && query.exifFilters.isNotEmpty()) {
            val exifScore = matchExifFilters(query.exifFilters, exif)
            score += WEIGHT_EXIF * exifScore
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * 单类别匹配：查询标签与图片标签的最大置信度 × 权重。
     */
    private fun matchCategory(
        queryTags: Set<String>,
        imageTags: List<SemanticTag>?,
        weight: Float,
    ): Float {
        if (queryTags.isEmpty() || imageTags.isNullOrEmpty()) return 0f

        var bestConfidence = 0f
        for (queryTag in queryTags) {
            val matched = imageTags.find { it.value.equals(queryTag, ignoreCase = true) }
            if (matched != null && matched.confidence > bestConfidence) {
                bestConfidence = matched.confidence
            }
        }

        return weight * bestConfidence
    }

    /**
     * 匹配 EXIF 过滤条件。
     * 返回满足的过滤条件比例（0..1）。
     */
    private fun matchExifFilters(filters: Set<ExifFilter>, exif: ExifData): Float {
        if (filters.isEmpty()) return 0f

        var matched = 0
        for (filter in filters) {
            val satisfied = when (filter) {
                is ExifFilter.FocalLengthRange -> {
                    val fl = exif.focalLength?.toFloatOrNull()
                    fl != null && fl in filter.minMm..filter.maxMm
                }
                is ExifFilter.Orientation -> {
                    if (exif.width > 0 && exif.height > 0) {
                        if (filter.isPortrait) exif.height > exif.width
                        else exif.width > exif.height
                    } else false
                }
                is ExifFilter.IsoRange -> {
                    val iso = exif.iso?.toIntOrNull()
                    iso != null && iso in filter.minIso..filter.maxIso
                }
                is ExifFilter.ShutterSpeedRange -> {
                    val ss = parseShutterSpeed(exif.shutterSpeed)
                    ss != null && ss in filter.minSec..filter.maxSec
                }
                is ExifFilter.FlashFired -> {
                    val fired = exif.flash?.contains("Fired", ignoreCase = true) == true ||
                        exif.flash?.contains("On", ignoreCase = true) == true
                    fired == filter.fired
                }
                is ExifFilter.TimeRange -> {
                    val hour = parseDateTimeHour(exif.dateTime)
                    hour != null && hour in filter.startHour..filter.endHour
                }
                is ExifFilter.WhiteBalance -> {
                    val isAuto = exif.whiteBalance?.contains("Auto", ignoreCase = true) == true
                    isAuto == filter.isAuto
                }
            }
            if (satisfied) matched++
        }

        return matched.toFloat() / filters.size
    }

    /**
     * 解析快门速度字符串。
     * 支持格式："1/125", "0.5", "2", "30s"
     */
    private fun parseShutterSpeed(shutterSpeed: String?): Float? {
        if (shutterSpeed.isNullOrBlank()) return null
        return runCatching {
            val trimmed = shutterSpeed.trim().removeSuffix("s").removeSuffix("S")
            if (trimmed.contains("/")) {
                val parts = trimmed.split("/")
                if (parts.size == 2) {
                    val num = parts[0].toFloatOrNull() ?: return null
                    val den = parts[1].toFloatOrNull() ?: return null
                    if (den == 0f) return null
                    num / den
                } else null
            } else {
                trimmed.toFloatOrNull()
            }
        }.getOrNull()
    }

    /**
     * 从 EXIF 日期时间字符串解析小时。
     * 支持格式："2024:01:15 17:30:00", "2024-01-15 17:30:00"
     */
    private fun parseDateTimeHour(dateTime: String?): Int? {
        if (dateTime.isNullOrBlank()) return null
        return runCatching {
            // 提取时间部分
            val timePart = dateTime.substringAfter(' ').trim()
            val hourStr = timePart.substringBefore(':')
            hourStr.toIntOrNull()
        }.getOrNull()
    }

    /**
     * 在所有图片标签库中搜索匹配项。
     * 返回按匹配分数降序排列的搜索结果，最多返回 limit 条。
     */
    fun search(
        query: SearchQuery,
        imageTags: Map<String, List<SemanticTag>>,
        exifData: Map<String, ExifData> = emptyMap(),
        limit: Int = 50,
    ): List<SearchResult> {
        return imageTags.mapNotNull { (imagePath, tags) ->
            val exif = exifData[imagePath]
            val score = match(query, tags, exif)
            if (score <= 0f) return@mapNotNull null

            val matchedTags = collectMatchedTags(query, tags)

            SearchResult(
                imagePath = imagePath,
                matchScore = score,
                matchedTags = matchedTags,
            )
        }
            .sortedByDescending { it.matchScore }
            .take(limit)
    }

    /**
     * 收集查询与图片标签之间的所有匹配标签。
     */
    private fun collectMatchedTags(query: SearchQuery, tags: List<SemanticTag>): List<SemanticTag> {
        val queryAllTags = buildSet {
            addAll(query.sceneTags)
            addAll(query.subjectTags)
            addAll(query.styleTags)
            addAll(query.moodTags)
            addAll(query.colorToneTags)
            addAll(query.timeOfDayTags)
        }

        return tags.filter { tag ->
            queryAllTags.any { queryTag ->
                tag.value.equals(queryTag, ignoreCase = true)
            }
        }
    }
}
