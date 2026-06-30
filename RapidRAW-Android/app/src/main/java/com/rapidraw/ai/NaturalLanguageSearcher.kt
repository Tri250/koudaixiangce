package com.rapidraw.ai

/**
 * 自然语言照片搜索 — 灵感来自 AlcedoStudio 的日常语言搜索功能。
 * 用户可以用 "海边日落的人像" 这样的自然语言搜索照片，
 * 系统将文本解析为结构化标签并在图像语义标签库中匹配。
 */

data class SearchQuery(
    val rawText: String,
    val sceneTags: Set<String>,      // 提取的场景关键词
    val subjectTags: Set<String>,    // 提取的主体关键词
    val styleTags: Set<String>,      // 提取的风格关键词
    val moodTags: Set<String>,       // 提取的情绪关键词
    val colorToneTags: Set<String>,  // 提取的色调关键词
    val timeOfDayTags: Set<String>,  // 提取的时段关键词
)

data class SearchResult(
    val imagePath: String,
    val matchScore: Float,    // 0-1
    val matchedTags: List<SemanticTag>,
)

/**
 * 自然语言搜索器 — 将中文自然语言解析为结构化查询，并在语义标签库中匹配。
 */
class NaturalLanguageSearcher {

    companion object {
        // 各类别权重：场景最重要，时段最次要
        private const val WEIGHT_SCENE = 0.3f
        private const val WEIGHT_SUBJECT = 0.25f
        private const val WEIGHT_MOOD = 0.2f
        private const val WEIGHT_STYLE = 0.1f
        private const val WEIGHT_COLOR_TONE = 0.1f
        private const val WEIGHT_TIME_OF_DAY = 0.05f
    }

    /** 中文关键词 → 标准标签值 映射 */
    private val sceneKeywordMap = mapOf(
        "海边" to "海滩",
        "城市" to "城市",
        "山" to "山野",
        "室内" to "室内",
        "街" to "街拍",
        "雪" to "雪地",
        "沙漠" to "沙漠",
        "花园" to "花园",
        "公园" to "花园",
    )

    private val subjectKeywordMap = mapOf(
        "人" to "人像",
        "人物" to "人像",
        "女孩" to "人像",
        "男孩" to "人像",
        "猫" to "宠物",
        "狗" to "宠物",
        "宠物" to "宠物",
        "建筑" to "建筑",
        "楼" to "建筑",
        "食物" to "美食",
        "美食" to "美食",
        "花" to "花卉",
        "花卉" to "花卉",
        "天" to "天空",
        "云" to "天空",
        "水" to "水面",
        "湖" to "水面",
        "河" to "水面",
        "海" to "水面",
        "车" to "车辆",
    )

    private val styleKeywordMap = mapOf(
        "简约" to "简约",
        "极简" to "简约",
        "戏剧" to "戏剧",
        "冲击" to "戏剧",
        "复古" to "复古",
        "怀旧" to "复古",
        "老" to "复古",
        "清新" to "清新",
        "小清新" to "清新",
        "暗调" to "暗调",
        "低调" to "暗调",
        "高调" to "高调",
        "明亮" to "高调",
    )

    private val moodKeywordMap = mapOf(
        "温暖" to "温暖",
        "温馨" to "温暖",
        "宁静" to "宁静",
        "安静" to "宁静",
        "平和" to "宁静",
        "神秘" to "神秘",
        "欢快" to "欢快",
        "开心" to "欢快",
        "忧郁" to "忧郁",
        "悲伤" to "忧郁",
        "浪漫" to "浪漫",
        "孤独" to "孤独",
        "寂寞" to "孤独",
    )

    private val colorToneKeywordMap = mapOf(
        "暖色" to "暖色调",
        "暖调" to "暖色调",
        "冷色" to "冷色调",
        "冷调" to "冷色调",
        "柔和" to "柔和",
        "淡" to "柔和",
        "对比" to "高对比",
        "强烈" to "高对比",
        "黑白" to "单色",
    )

    private val timeOfDayKeywordMap = mapOf(
        "日出" to "日出",
        "朝霞" to "日出",
        "日落" to "日落",
        "夕阳" to "日落",
        "晚霞" to "日落",
        "夜" to "夜晚",
        "夜晚" to "夜晚",
        "夜景" to "夜晚",
        "黄金时刻" to "黄金时刻",
        "魔幻时刻" to "黄金时刻",
        "正午" to "正午",
        "黄昏" to "黄昏",
        "暮色" to "黄昏",
    )

    /**
     * 解析自然语言文本为结构化搜索查询。
     * 通过中文关键词映射将输入拆解为各类标签集合。
     */
    fun parseQuery(text: String): SearchQuery {
        val sceneTags = mutableSetOf<String>()
        val subjectTags = mutableSetOf<String>()
        val styleTags = mutableSetOf<String>()
        val moodTags = mutableSetOf<String>()
        val colorToneTags = mutableSetOf<String>()
        val timeOfDayTags = mutableSetOf<String>()

        // 按关键词长度降序匹配，避免短关键词误匹配（如 "人" 先于 "人物" 匹配）
        val allMaps = listOf(
            sceneKeywordMap to sceneTags,
            subjectKeywordMap to subjectTags,
            styleKeywordMap to styleTags,
            moodKeywordMap to moodTags,
            colorToneKeywordMap to colorToneTags,
            timeOfDayKeywordMap to timeOfDayTags,
        )

        for ((keywordMap, tagSet) in allMaps) {
            val sortedKeywords = keywordMap.keys.sortedByDescending { it.length }
            for (keyword in sortedKeywords) {
                if (text.contains(keyword)) {
                    tagSet.add(keywordMap.getValue(keyword))
                }
            }
        }

        return SearchQuery(
            rawText = text,
            sceneTags = sceneTags,
            subjectTags = subjectTags,
            styleTags = styleTags,
            moodTags = moodTags,
            colorToneTags = colorToneTags,
            timeOfDayTags = timeOfDayTags,
        )
    }

    /**
     * 计算搜索查询与图片语义标签的匹配分数。
     * 分数为各类别加权匹配置信度之和。
     * 对于每个类别，若查询中任一标签与图片标签匹配（大小写不敏感/中文精确匹配），
     * 则累加该标签的加权置信度。
     */
    fun match(query: SearchQuery, tags: List<SemanticTag>): Float {
        var score = 0f

        val categoryTagMaps = tags.groupBy { it.category }

        // 场景匹配
        val sceneImageValues = categoryTagMaps[TagCategory.SCENE]?.map { it.value.lowercase() } ?: emptyList()
        for (queryTag in query.sceneTags) {
            val matched = sceneImageValues.find { it == queryTag.lowercase() }
            if (matched != null) {
                val confidence = categoryTagMaps[TagCategory.SCENE]!!
                    .first { it.value.lowercase() == matched }.confidence
                score += WEIGHT_SCENE * confidence
                break // 每个类别只计一次最高匹配
            }
        }

        // 主体匹配
        val subjectImageValues = categoryTagMaps[TagCategory.SUBJECT]?.map { it.value.lowercase() } ?: emptyList()
        for (queryTag in query.subjectTags) {
            val matched = subjectImageValues.find { it == queryTag.lowercase() }
            if (matched != null) {
                val confidence = categoryTagMaps[TagCategory.SUBJECT]!!
                    .first { it.value.lowercase() == matched }.confidence
                score += WEIGHT_SUBJECT * confidence
                break
            }
        }

        // 情绪匹配
        val moodImageValues = categoryTagMaps[TagCategory.MOOD]?.map { it.value.lowercase() } ?: emptyList()
        for (queryTag in query.moodTags) {
            val matched = moodImageValues.find { it == queryTag.lowercase() }
            if (matched != null) {
                val confidence = categoryTagMaps[TagCategory.MOOD]!!
                    .first { it.value.lowercase() == matched }.confidence
                score += WEIGHT_MOOD * confidence
                break
            }
        }

        // 风格匹配
        val styleImageValues = categoryTagMaps[TagCategory.STYLE]?.map { it.value.lowercase() } ?: emptyList()
        for (queryTag in query.styleTags) {
            val matched = styleImageValues.find { it == queryTag.lowercase() }
            if (matched != null) {
                val confidence = categoryTagMaps[TagCategory.STYLE]!!
                    .first { it.value.lowercase() == matched }.confidence
                score += WEIGHT_STYLE * confidence
                break
            }
        }

        // 色调匹配
        val colorToneImageValues = categoryTagMaps[TagCategory.COLOR_TONE]?.map { it.value.lowercase() } ?: emptyList()
        for (queryTag in query.colorToneTags) {
            val matched = colorToneImageValues.find { it == queryTag.lowercase() }
            if (matched != null) {
                val confidence = categoryTagMaps[TagCategory.COLOR_TONE]!!
                    .first { it.value.lowercase() == matched }.confidence
                score += WEIGHT_COLOR_TONE * confidence
                break
            }
        }

        // 时段匹配
        val timeOfDayImageValues = categoryTagMaps[TagCategory.TIME_OF_DAY]?.map { it.value.lowercase() } ?: emptyList()
        for (queryTag in query.timeOfDayTags) {
            val matched = timeOfDayImageValues.find { it == queryTag.lowercase() }
            if (matched != null) {
                val confidence = categoryTagMaps[TagCategory.TIME_OF_DAY]!!
                    .first { it.value.lowercase() == matched }.confidence
                score += WEIGHT_TIME_OF_DAY * confidence
                break
            }
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * 在所有图片标签库中搜索匹配项。
     * 返回按匹配分数降序排列的搜索结果，最多返回 limit 条。
     */
    fun search(query: SearchQuery, imageTags: Map<String, List<SemanticTag>>, limit: Int = 50): List<SearchResult> {
        return imageTags.mapNotNull { (imagePath, tags) ->
            val score = match(query, tags)
            if (score <= 0f) return@mapNotNull null

            // 收集所有匹配的标签
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
