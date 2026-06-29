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

    enum class FilterType {
        FAVORITE,
        UNRATED,
        HIGH_RATING,
        RECENTLY_EDITED,
        RAW_FILES,
        BY_DATE
    }

    companion object {
        val predefined: List<SmartAlbum> = listOf(
            Favorites,
            Unrated,
            HighRating,
            RecentlyEdited,
            RawFiles
        )
    }
}
