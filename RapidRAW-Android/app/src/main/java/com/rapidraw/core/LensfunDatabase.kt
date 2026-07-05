package com.rapidraw.core

import android.content.Context
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

/**
 * Lensfun 镜头校正数据库解析器（Android 端）。
 *
 * 移植自原 RapidRAW 项目（Rust `src-tauri/src/lens_correction.rs`）的逻辑：
 *  - 从 assets/lensfun/ 下加载标准 lensfun XML 数据库（按厂商拆分）。
 *  - 解析每个 `<lens>` 节点的 distortion / tca / vignetting 校准数据。
 *  - 给定焦距/光圈/距离对相邻校准点做线性插值（边界外使用最近两点斜率外推）。
 *  - 模糊匹配镜头型号：剥离 maker 前缀 + 归一化 + token 覆盖率评分。
 *  - 按 EXIF maker/model/焦距/光圈自动检测镜头。
 *
 * 数据格式参考 https://lensfun.github.io/manual/latest/dbformat.html
 */
class LensfunDatabase(context: Context) {

    // ── 数据模型 ───────────────────────────────────────────────────

    /** 单个畸变校准点。对 ptlens 模型，a/b/c 映射到 k1/k2/k3。 */
    data class DistortionCalib(
        val focal: Float,
        val model: String,
        val k1: Float,
        val k2: Float = 0f,
        val k3: Float = 0f,
    )

    /** 横向色差（TCA）校准点（poly3 模型 6 参数）。 */
    data class TcaCalib(
        val focal: Float,
        val vr: Float,
        val vb: Float,
        val cr: Float,
        val cb: Float,
        val br: Float,
        val bb: Float,
    )

    /** 暗角校准点（pa 模型，按焦距/光圈/距离三维采样）。 */
    data class VignettingCalib(
        val focal: Float,
        val aperture: Float,
        val distance: Float,
        val k1: Float,
        val k2: Float,
        val k3: Float,
    )

    /** 单个镜头条目。 */
    data class LensEntry(
        val maker: String,
        val model: String,
        val mount: String,
        val cropFactor: Float,
        val distortions: List<DistortionCalib>,
        val tcas: List<TcaCalib>,
        val vignettings: List<VignettingCalib>,
    )

    /** 插值后的畸变参数。 */
    data class DistortionParams(
        val k1: Float,
        val k2: Float,
        val k3: Float,
        val model: String,
    )

    /** 插值后的 TCA 参数（红/蓝通道径向缩放，1.0 = 无校正）。 */
    data class TcaParams(
        val vr: Float,
        val vb: Float,
    )

    /** 插值后的暗角参数（3 阶多项式系数）。 */
    data class VignettingParams(
        val k1: Float,
        val k2: Float,
        val k3: Float,
    )

    // ── 状态 ───────────────────────────────────────────────────────

    private val appContext = context.applicationContext
    private val byMaker = LinkedHashMap<String, MutableList<LensEntry>>()
    private val all = mutableListOf<LensEntry>()
    @Volatile private var loaded = false

    // ── 加载 ───────────────────────────────────────────────────────

    /**
     * 从 assets/lensfun/ 加载所有 XML 文件到内存。启动时调用一次。
     * 线程安全；重复调用为空操作。
     */
    fun loadFromAssets() {
        synchronized(this) {
            if (loaded) return
            val names = try {
                appContext.assets.list(ASSET_DIR) ?: emptyArray()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to list lensfun assets", e)
                emptyArray<String>()
            }
            for (name in names.sorted()) {
                if (!name.endsWith(".xml", ignoreCase = true)) continue
                val path = "$ASSET_DIR/$name"
                try {
                    appContext.assets.open(path).use { input -> parseXml(input) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse lensfun file: $path", e)
                }
            }
            loaded = true
            Log.i(TAG, "Loaded ${all.size} lenses from ${names.size} files; makers=${byMaker.keys}")
        }
    }

    /** 是否已加载完成。 */
    fun isLoaded(): Boolean = loaded

    /** 数据库镜头总数。 */
    fun totalEntries(): Int = synchronized(this) { all.size }

    // ── XML 解析（XmlPullParser）──────────────────────────────────

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseXml(input: InputStream) {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "lens") {
                val lens = try {
                    parseLens(parser)
                } catch (e: Exception) {
                    Log.e(TAG, "lens parse error", e)
                    null
                }
                if (lens != null) addLens(lens)
            }
            event = parser.next()
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseLens(parser: XmlPullParser): LensEntry? {
        // 当前位于 START_TAG "lens"
        val makers = mutableListOf<Pair<String?, String>>()  // (lang, value)
        val models = mutableListOf<Pair<String?, String>>()
        val mounts = mutableListOf<String>()
        var cropFactor = 1f
        val distortions = mutableListOf<DistortionCalib>()
        val tcas = mutableListOf<TcaCalib>()
        val vignettings = mutableListOf<VignettingCalib>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val ev = parser.eventType
            if (ev == XmlPullParser.END_TAG && parser.name == "lens") break
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "maker" -> {
                        val lang = parser.getAttributeValue(null, "lang")
                        val text = parser.nextText()
                        if (text.isNotBlank()) makers.add(lang to text.trim())
                    }
                    "model" -> {
                        val lang = parser.getAttributeValue(null, "lang")
                        val text = parser.nextText()
                        if (text.isNotBlank()) models.add(lang to text.trim())
                    }
                    "mount" -> {
                        val text = parser.nextText()
                        if (text.isNotBlank()) mounts.add(text.trim())
                    }
                    "cropfactor" -> {
                        cropFactor = parser.nextText().trim().toFloatOrNull() ?: 1f
                    }
                    "calibration" ->
                        parseCalibration(parser, distortions, tcas, vignettings)
                    else -> skipTag(parser)
                }
            }
        }

        if (models.isEmpty()) return null
        val maker = pickName(makers, default = "Misc")
        val model = pickCanonical(models)
        val mount = mounts.firstOrNull() ?: ""
        return LensEntry(
            maker = maker,
            model = model,
            mount = mount,
            cropFactor = cropFactor,
            distortions = distortions.toList(),
            tcas = tcas.toList(),
            vignettings = vignettings.toList(),
        )
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseCalibration(
        parser: XmlPullParser,
        distortions: MutableList<DistortionCalib>,
        tcas: MutableList<TcaCalib>,
        vignettings: MutableList<VignettingCalib>,
    ) {
        // 当前位于 START_TAG "calibration"
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val ev = parser.eventType
            if (ev == XmlPullParser.END_TAG && parser.name == "calibration") break
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "distortion" -> distortions.add(parseDistortion(parser))
                    "tca" -> tcas.add(parseTca(parser))
                    "vignetting" -> vignettings.add(parseVignetting(parser))
                    else -> skipTag(parser)
                }
            }
        }
    }

    private fun parseDistortion(parser: XmlPullParser): DistortionCalib {
        val focal = parser.attrFloat("focal", 0f)
        val model = parser.getAttributeValue(null, "model") ?: "poly3"
        // poly3/poly5 使用 k1/k2/k3；ptlens 使用 a/b/c，统一映射到 k1/k2/k3
        val k1 = parser.attrFloat("k1", parser.attrFloat("a", 0f))
        val k2 = parser.attrFloat("k2", parser.attrFloat("b", 0f))
        val k3 = parser.attrFloat("k3", parser.attrFloat("c", 0f))
        return DistortionCalib(focal, model, k1, k2, k3)
    }

    private fun parseTca(parser: XmlPullParser): TcaCalib {
        val focal = parser.attrFloat("focal", 0f)
        return TcaCalib(
            vr = parser.attrFloat("vr", 1f),
            vb = parser.attrFloat("vb", 1f),
            cr = parser.attrFloat("cr", 0f),
            cb = parser.attrFloat("cb", 0f),
            br = parser.attrFloat("br", 0f),
            bb = parser.attrFloat("bb", 0f),
            focal = focal,
        )
    }

    private fun parseVignetting(parser: XmlPullParser): VignettingCalib {
        return VignettingCalib(
            focal = parser.attrFloat("focal", 0f),
            aperture = parser.attrFloat("aperture", 0f),
            distance = parser.attrFloat("distance", 1000f),
            k1 = parser.attrFloat("k1", 0f),
            k2 = parser.attrFloat("k2", 0f),
            k3 = parser.attrFloat("k3", 0f),
        )
    }

    /** 跳过当前元素及其全部子元素，定位到对应的 END_TAG。 */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }

    private fun XmlPullParser.attrFloat(name: String, default: Float): Float =
        getAttributeValue(null, name)?.trim()?.toFloatOrNull() ?: default

    /** 取 lang="en" 的名称，否则第一个。 */
    private fun pickName(list: List<Pair<String?, String>>, default: String): String {
        if (list.isEmpty()) return default
        return list.firstOrNull { it.first == "en" }?.second ?: list.first().second
    }

    /** 取 canonical（无 lang 属性）的名称，否则第一个。 */
    private fun pickCanonical(list: List<Pair<String?, String>>): String {
        if (list.isEmpty()) return "Unknown Model"
        return list.firstOrNull { it.first == null }?.second ?: list.first().second
    }

    private fun addLens(lens: LensEntry) {
        all.add(lens)
        byMaker.getOrPut(lens.maker) { mutableListOf() }.add(lens)
    }

    // ── 查询 API ──────────────────────────────────────────────────

    /** 所有厂商列表（已排序）。 */
    fun getAllMakers(): List<String> = synchronized(this) {
        byMaker.keys.sorted()
    }

    /** 指定厂商的所有镜头。 */
    fun getLensesByMaker(maker: String): List<LensEntry> = synchronized(this) {
        val m = maker.trim()
        byMaker.entries.firstOrNull { it.key.equals(m, ignoreCase = true) }?.value?.toList()
            ?: emptyList()
    }

    /**
     * 模糊匹配镜头：按 maker 过滤后对 model 做 token 覆盖率 + 子串评分，
     * 处理 maker 前缀剥离与变体（如 "Canon EF 50mm f/1.8 STM" → "Canon EF 50mm f/1.8"）。
     * 无同厂商匹配时回退到全局模糊匹配。
     */
    fun findLens(maker: String, model: String): LensEntry? = synchronized(this) {
        val cleanMaker = maker.trim().trim('"')
        val cleanModel = model.trim().trim('"')
        if (cleanModel.isBlank()) return@synchronized null

        val makerLenses = if (cleanMaker.isNotEmpty()) lensesForMakerUnlocked(cleanMaker) else emptyList()
        var best: LensEntry? = null
        var bestScore = 0
        for (lens in makerLenses) {
            val score = scoreLens(lens, cleanModel)
            if (score > bestScore) { bestScore = score; best = lens }
        }
        if (best != null) return@synchronized best

        // 全局回退
        for (lens in all) {
            val score = scoreLens(lens, cleanModel)
            if (score > bestScore) { bestScore = score; best = lens }
        }
        best
    }

    /**
     * 按 EXIF 信息自动检测镜头。
     * 优先按 maker + model 模糊匹配；若失败，按焦距在同厂商镜头中找覆盖范围最接近者。
     */
    fun autodetectLens(
        maker: String?,
        model: String?,
        focalLength: Float,
        aperture: Float?,
    ): LensEntry? = synchronized(this) {
        if (!model.isNullOrBlank()) {
            findLens(maker ?: "", model)?.let { return@synchronized it }
        }
        // 名称匹配失败：按焦距在同厂商镜头中兜底
        val m = maker?.trim().orEmpty()
        val pool = if (m.isNotEmpty()) lensesForMakerUnlocked(m) else all
        pool.minByOrNull { lens ->
            val focals = lens.distortions.map { it.focal }.sorted()
            if (focals.isEmpty()) Float.MAX_VALUE
            else {
                val center = (focals.first() + focals.last()) / 2f
                abs(center - focalLength)
            }
        }
    }

    private fun lensesForMakerUnlocked(maker: String): List<LensEntry> {
        val m = maker.trim()
        return byMaker.entries
            .firstOrNull { it.key.equals(m, ignoreCase = true) }?.value?.toList()
            ?: emptyList()
    }

    // ── 参数插值（参照 Rust get_distortion_params / get_tca_params / get_vig_params）──

    /** 给定焦距，对 distortion 校准点线性插值（边界外用最近两点斜率外推）。 */
    fun getDistortionParams(lens: LensEntry, focal: Float): DistortionParams? =
        computeDistortionParams(lens, focal)

    /** 给定焦距，对 TCA 的 vr/vb 线性插值（边界外用最近两点斜率外推）。 */
    fun getTcaParams(lens: LensEntry, focal: Float): TcaParams? =
        computeTcaParams(lens, focal)

    /** 给定焦距/光圈/距离，对暗角系数插值（先按光圈/距离选最佳采样点，再按焦距插值/外推）。 */
    fun getVignettingParams(
        lens: LensEntry,
        focal: Float,
        aperture: Float,
        distance: Float = 10f,
    ): VignettingParams? = computeVignettingParams(lens, focal, aperture, distance)

    private fun scoreLens(lens: LensEntry, query: String): Int {
        val full = lens.model
        val stripped = stripMakerPrefix(full, lens.maker)
        val qStripped = stripMakerPrefix(query, lens.maker)
        return maxOf(
            fuzzyScore(full, query),
            fuzzyScore(stripped, query),
            fuzzyScore(full, qStripped),
            fuzzyScore(stripped, qStripped),
        )
    }

    companion object {
        private const val TAG = "LensfunDatabase"
        private const val ASSET_DIR = "lensfun"

        /**
         * 剥离名称中的 maker 前缀（大小写不敏感）。
         * 参照 Rust `strip_maker_prefix`。
         */
        fun stripMakerPrefix(name: String, maker: String): String {
            val n = name.trim()
            val m = maker.trim()
            if (m.isNotEmpty() && n.lowercase().startsWith(m.lowercase())) {
                val rest = n.substring(m.length).trim()
                if (rest.isNotEmpty()) return rest
            }
            return n
        }

        /**
         * 模糊评分（0 表示不相关，越大越匹配）。
         * 综合 token 覆盖率、子串包含与长度惩罚。
         */
        fun fuzzyScore(candidate: String, query: String): Int {
            if (query.isBlank()) return 0
            val c = candidate.lowercase()
            val q = query.lowercase()
            val qt = tokenize(q)
            if (qt.isEmpty()) return 0
            val ct = tokenize(c)
            if (ct.isEmpty()) return 0
            val cset = ct.toHashSet()
            var covered = 0
            for (t in qt) if (cset.contains(t)) covered++
            val coverage = covered.toFloat() / qt.size
            var subBonus = 0
            if (c.contains(q) || q.contains(c)) subBonus = 30
            val exactBonus = if (c == q) 200 else 0
            val lenPenalty = (c.length - q.length).coerceAtLeast(0) / 3
            return (coverage * 100f).toInt() + subBonus + exactBonus - lenPenalty
        }

        private fun tokenize(s: String): List<String> =
            s.split(Regex("\\s+")).filter { it.isNotBlank() }

        // ── 纯函数插值实现（供 LensCorrector.fromLensfun 直接调用，无需 db 实例）──

        /** 畸变参数插值。无数据返回 null。 */
        fun computeDistortionParams(lens: LensEntry, focal: Float): DistortionParams? {
            val ds = lens.distortions.sortedBy { it.focal }
            if (ds.isEmpty()) return null

            ds.firstOrNull { abs(it.focal - focal) < 1e-4f }?.let {
                return DistortionParams(it.k1, it.k2, it.k3, it.model)
            }
            val first = ds.first()
            val last = ds.last()

            if (focal < first.focal) {
                if (ds.size >= 2) {
                    val a = ds[0]; val b = ds[1]
                    val t = (focal - a.focal) / (b.focal - a.focal)
                    return DistortionParams(
                        a.k1 + t * (b.k1 - a.k1),
                        a.k2 + t * (b.k2 - a.k2),
                        a.k3 + t * (b.k3 - a.k3),
                        a.model,
                    )
                }
                return DistortionParams(first.k1, first.k2, first.k3, first.model)
            }
            if (focal > last.focal) {
                if (ds.size >= 2) {
                    val a = ds[ds.size - 2]; val b = ds[ds.size - 1]
                    val t = (focal - a.focal) / (b.focal - a.focal)
                    return DistortionParams(
                        a.k1 + t * (b.k1 - a.k1),
                        a.k2 + t * (b.k2 - a.k2),
                        a.k3 + t * (b.k3 - a.k3),
                        a.model,
                    )
                }
                return DistortionParams(last.k1, last.k2, last.k3, last.model)
            }
            for (i in 0 until ds.size - 1) {
                val a = ds[i]; val b = ds[i + 1]
                if (focal >= a.focal && focal <= b.focal) {
                    val range = b.focal - a.focal
                    if (abs(range) < 1e-5f) return DistortionParams(a.k1, a.k2, a.k3, a.model)
                    val t = (focal - a.focal) / range
                    return DistortionParams(
                        a.k1 + t * (b.k1 - a.k1),
                        a.k2 + t * (b.k2 - a.k2),
                        a.k3 + t * (b.k3 - a.k3),
                        a.model,
                    )
                }
            }
            return DistortionParams(last.k1, last.k2, last.k3, last.model)
        }

        /** TCA 参数（vr/vb）插值。无数据返回 null。 */
        fun computeTcaParams(lens: LensEntry, focal: Float): TcaParams? {
            val ts = lens.tcas.sortedBy { it.focal }
            if (ts.isEmpty()) return null

            ts.firstOrNull { abs(it.focal - focal) < 1e-4f }?.let {
                return TcaParams(it.vr, it.vb)
            }
            val first = ts.first()
            val last = ts.last()

            if (focal < first.focal) {
                if (ts.size >= 2) {
                    val a = ts[0]; val b = ts[1]
                    val t = (focal - a.focal) / (b.focal - a.focal)
                    return TcaParams(a.vr + t * (b.vr - a.vr), a.vb + t * (b.vb - a.vb))
                }
                return TcaParams(first.vr, first.vb)
            }
            if (focal > last.focal) {
                if (ts.size >= 2) {
                    val a = ts[ts.size - 2]; val b = ts[ts.size - 1]
                    val t = (focal - a.focal) / (b.focal - a.focal)
                    return TcaParams(a.vr + t * (b.vr - a.vr), a.vb + t * (b.vb - a.vb))
                }
                return TcaParams(last.vr, last.vb)
            }
            for (i in 0 until ts.size - 1) {
                val a = ts[i]; val b = ts[i + 1]
                if (focal >= a.focal && focal <= b.focal) {
                    val range = b.focal - a.focal
                    if (abs(range) < 1e-5f) return TcaParams(a.vr, a.vb)
                    val t = (focal - a.focal) / range
                    return TcaParams(a.vr + t * (b.vr - a.vr), a.vb + t * (b.vb - a.vb))
                }
            }
            return TcaParams(last.vr, last.vb)
        }

        /** 暗角参数插值（焦距插值 + 光圈/距离最佳采样点选择）。无数据返回 null。 */
        fun computeVignettingParams(
            lens: LensEntry,
            focal: Float,
            aperture: Float,
            distance: Float,
        ): VignettingParams? {
            val vs = lens.vignettings.sortedBy { it.focal }
            if (vs.isEmpty()) return null
            val targetAp = aperture
            val targetDist = distance

            fun bestInGroup(group: List<VignettingCalib>): VignettingCalib? {
                if (group.isEmpty()) return null
                val bestAp = group.minByOrNull { abs(it.aperture - targetAp) } ?: return null
                val apCandidates = group.filter { abs(it.aperture - bestAp.aperture) < 0.01f }
                return apCandidates.minByOrNull { abs(it.distance - targetDist) } ?: bestAp
            }

            // 焦距去重（0.01 精度）
            val uniqueFocals = vs.map { it.focal }
                .distinctBy { (it * 100f).toInt() }
                .sorted()
            val firstFocal = uniqueFocals.first()
            val lastFocal = uniqueFocals.last()

            fun groupFor(f: Float) = vs.filter { abs(it.focal - f) < 0.01f }

            fun interp(g1: List<VignettingCalib>, g2: List<VignettingCalib>,
                       f1: Float, f2: Float, f: Float): VignettingParams? {
                val p1 = bestInGroup(g1) ?: return null
                val p2 = bestInGroup(g2) ?: return VignettingParams(p1.k1, p1.k2, p1.k3)
                val range = f2 - f1
                if (abs(range) < 0.01f) return VignettingParams(p1.k1, p1.k2, p1.k3)
                val t = (f - f1) / range
                return VignettingParams(
                    p1.k1 + t * (p2.k1 - p1.k1),
                    p1.k2 + t * (p2.k2 - p1.k2),
                    p1.k3 + t * (p2.k3 - p1.k3),
                )
            }

            // 低于最小焦距：用前两点外推
            if (focal < firstFocal) {
                if (uniqueFocals.size >= 2) {
                    interp(groupFor(uniqueFocals[0]), groupFor(uniqueFocals[1]),
                        uniqueFocals[0], uniqueFocals[1], focal)?.let { return it }
                }
                return bestInGroup(groupFor(firstFocal))
                    ?.let { VignettingParams(it.k1, it.k2, it.k3) }
            }
            // 高于最大焦距：用后两点外推
            if (focal > lastFocal) {
                if (uniqueFocals.size >= 2) {
                    val fn1 = uniqueFocals[uniqueFocals.size - 2]
                    val fn = uniqueFocals.last()
                    interp(groupFor(fn1), groupFor(fn), fn1, fn, focal)?.let { return it }
                }
                return bestInGroup(groupFor(lastFocal))
                    ?.let { VignettingParams(it.k1, it.k2, it.k3) }
            }
            // 区间内：查找包围对
            for (i in 0 until uniqueFocals.size - 1) {
                val f1 = uniqueFocals[i]; val f2 = uniqueFocals[i + 1]
                if (focal >= f1 && focal <= f2) {
                    interp(groupFor(f1), groupFor(f2), f1, f2, focal)?.let { return it }
                }
            }
            return bestInGroup(vs)?.let { VignettingParams(it.k1, it.k2, it.k3) }
        }
    }
}
