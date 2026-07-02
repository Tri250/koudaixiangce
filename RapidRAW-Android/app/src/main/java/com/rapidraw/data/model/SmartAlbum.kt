package com.rapidraw.data.model

sealed class SmartAlbum(
    val title: String,
    val filterType: FilterType
) {
    object Favorites : SmartAlbum("收藏夹", FilterType.FAVORITE)
    object Unrated : SmartAlbum("未评级", FilterType.UNRATED)
    object HighRating : SmartAlbum("高评分", FilterType.HIGH_RATING)
    object RecentlyEdited : SmartAlbum("最近编辑", FilterType.RECENTLY_EDITED)
    object RawFiles : SmartAlbum("RAW文件", FilterType.RAW_FILES)
    data class ByDate(val dateLabel: String) : SmartAlbum(dateLabel, FilterType.BY_DATE)

    // ── AI 语义过滤智能相册（AlcedoStudio 对标功能）────────────────
    data class AiSemantic(val semanticTag: String, val overrideTitle: String) : SmartAlbum(overrideTitle, FilterType.AI_SEMANTIC)

    object AiPortraits : SmartAlbum("AI 人像", FilterType.AI_SEMANTIC)
    object AiLandscapes : SmartAlbum("AI 风景", FilterType.AI_SEMANTIC)
    object AiNight : SmartAlbum("AI 夜景", FilterType.AI_SEMANTIC)
    object AiFood : SmartAlbum("AI 美食", FilterType.AI_SEMANTIC)
    object AiArchitecture : SmartAlbum("AI 建筑", FilterType.AI_SEMANTIC)
    object AiWarmTone : SmartAlbum("AI 暖色调", FilterType.AI_SEMANTIC)
    object AiCoolTone : SmartAlbum("AI 冷色调", FilterType.AI_SEMANTIC)
    object AiRomantic : SmartAlbum("AI 浪漫", FilterType.AI_SEMANTIC)
    object AiDramatic : SmartAlbum("AI 戏剧", FilterType.AI_SEMANTIC)

    enum class FilterType {
        FAVORITE,
        UNRATED,
        HIGH_RATING,
        RECENTLY_EDITED,
        RAW_FILES,
        BY_DATE,
        AI_SEMANTIC,
    }

    companion object {
        val predefined: List<SmartAlbum> = listOf(
            Favorites,
            Unrated,
            HighRating,
            RecentlyEdited,
            RawFiles,
        )

        /** AI 语义智能相册（AlcedoStudio 对标） */
        val aiSemanticAlbums: List<SmartAlbum> = listOf(
            AiPortraits,
            AiLandscapes,
            AiNight,
            AiFood,
            AiArchitecture,
            AiWarmTone,
            AiCoolTone,
            AiRomantic,
            AiDramatic,
        )

        /** AI 语义标签 → SmartAlbum 映射 */
        fun fromSemanticTag(tag: String): SmartAlbum? = when (tag) {
            "人像" -> AiPortraits
            "风景", "山野", "海滩", "雪地" -> AiLandscapes
            "夜景", "夜晚" -> AiNight
            "美食" -> AiFood
            "建筑" -> AiArchitecture
            "暖色调" -> AiWarmTone
            "冷色调" -> AiCoolTone
            "浪漫" -> AiRomantic
            "戏剧" -> AiDramatic
            else -> null
        }
    }
}
