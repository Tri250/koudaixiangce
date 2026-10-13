package com.rawlab.android

data class LensCandidate(val id: String, val maker: String, val model: String,
    val hasDistortion: Boolean, val hasTca: Boolean, val isMatch: Boolean) {
    val title: String get() = if (model.contains(maker, ignoreCase = true)) model else "$maker $model"
}

data class LensInfo(val state: Int, val detectedLens: String, val selectedLens: String,
    val appliedDistortion: Boolean, val appliedTca: Boolean, val cropScale: Float,
    val candidates: List<LensCandidate>) {
    val message: String get() = when (state) {
        0 -> "镜头校正已关闭"
        1 -> if (appliedDistortion || appliedTca) "已校正" + listOfNotNull(
            "畸变".takeIf { appliedDistortion }, "横向色差".takeIf { appliedTca }).joinToString("、") else "已匹配镜头"
        2 -> "镜头数据库不可用"
        3 -> "未找到机身或传感器尺寸信息"
        5 -> "有多个匹配镜头，请手动选择"
        6 -> "此镜头没有所选校正的标定数据"
        7 -> "照片缺少有效焦距，未应用校正"
        8 -> "DNG 已执行内嵌 Stage3 处理，未叠加校正"
        9 -> "此输入不支持镜头校正"
        else -> "未匹配镜头，请手动选择"
    }
}
