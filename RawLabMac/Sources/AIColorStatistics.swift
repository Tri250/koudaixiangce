import AppKit

enum AIColorMethod: String, CaseIterable, Identifiable, Codable {
    case ai, chromaOT, reinhard
    var id: String { rawValue }
    var title: String {
        switch self { case .ai: return "AI 仿色"; case .chromaOT: return "色度 OT"; case .reinhard: return "Reinhard" }
    }
}

enum AIColorTarget: String, CaseIterable, Identifiable, Codable {
    case sourceMatch, referenceStyle
    var id: String { rawValue }
    var title: String { self == .sourceMatch ? "当前素材匹配" : "仅参考风格" }
}

struct AIColorControls: Codable, Equatable {
    var color = 1.0
    var tone = 1.0
    static let full = Self()
    static let preserveTone = Self(color: 1, tone: 0)

    func validated() throws -> Self {
        guard [color, tone].allSatisfy({ $0.isFinite && (0...1).contains($0) }) else {
            throw AIError.message("颜色与影调强度必须在 0-100% 之间。")
        }
        return self
    }
    func resolve(_ recipe: AIColorRecipe) throws -> AIColorRecipe {
        _ = try validated(); var value = try recipe.validated()
        let c = Float(color), t = Float(tone)
        value.tone = value.tone.enumerated().map { Float($0.offset)/8 + ($0.element-Float($0.offset)/8)*t }
        value.chroma = 1 + (value.chroma-1)*c
        value.hueBands = value.hueBands.map { .init(hue: $0.hue*c, chroma: 1+($0.chroma-1)*c, lightness: $0.lightness*t) }
        value.toning = value.toning.map { .init(a: $0.a*c, b: $0.b*c) }
        return try value.validated()
    }
}

struct AIChromaMatrix: Codable, Equatable {
    var a: Double; var b: Double; var c: Double; var d: Double
    init(_ a: Double, _ b: Double, _ c: Double, _ d: Double) { self.a = a; self.b = b; self.c = c; self.d = d }
    static let identity = Self(1,0,0,1)
    var transposed: Self { Self(a,c,b,d) }
    var regularized: Self {
        let mean = (a+d)/2
        return Self(0.95*a+0.05*mean+1e-6, 0.95*b, 0.95*c, 0.95*d+0.05*mean+1e-6)
    }
    static func + (lhs: Self, rhs: Self) -> Self { Self(lhs.a+rhs.a,lhs.b+rhs.b,lhs.c+rhs.c,lhs.d+rhs.d) }
    static func * (lhs: Self, rhs: Double) -> Self { Self(lhs.a*rhs,lhs.b*rhs,lhs.c*rhs,lhs.d*rhs) }
    static func * (lhs: Self, rhs: Self) -> Self {
        Self(lhs.a*rhs.a+lhs.b*rhs.c,lhs.a*rhs.b+lhs.b*rhs.d,lhs.c*rhs.a+lhs.d*rhs.c,lhs.c*rhs.b+lhs.d*rhs.d)
    }
    func applied(_ p: SIMD2<Double>) -> SIMD2<Double> { SIMD2(a*p.x+b*p.y, c*p.x+d*p.y) }
    func distance(_ other: Self) -> Double { max(abs(a-other.a),abs(b-other.b),abs(c-other.c),abs(d-other.d)) }
    func inverse() throws -> Self {
        let det = a*d-b*c
        guard det.isFinite, det > 0 else { throw AIError.message("色度协方差无法稳定求逆。") }
        return Self(d,-b,-c,a)*(1/det)
    }
    func squareRoot() throws -> Self {
        // 二维正定对称矩阵的闭式主平方根；不使用白化染色替代 Gaussian OT。
        let off = (b+c)/2, det = a*d-off*off
        guard det.isFinite, det > 0, a+d > 0 else { throw AIError.message("色度协方差不是正定矩阵。") }
        let root = sqrt(det), denominator = sqrt(a+d+2*root)
        return Self(a+root,off,off,d+root)*(1/denominator)
    }
    func limitedGain() -> Self {
        if b == 0 && c == 0 { return Self(min(4,max(0.25,a)),0,0,min(4,max(0.25,d))) }
        let off = (b+c)/2, angle = 0.5*atan2(2*off,a-d)
        let co = cos(angle), si = sin(angle), radius = hypot(a-d,2*off)/2
        let hi = min(4,max(0.25,(a+d)/2+radius)), lo = min(4,max(0.25,(a+d)/2-radius))
        return Self(hi*co*co+lo*si*si,(hi-lo)*co*si,(hi-lo)*co*si,hi*si*si+lo*co*co)
    }
}

struct AIChromaMapping: Codable, Equatable {
    var matrix: AIChromaMatrix
    var biasA: Double
    var biasB: Double
    var bias: SIMD2<Double> { SIMD2(biasA,biasB) }
    static let identity = Self(matrix: .identity, biasA: 0, biasB: 0)
    var parameters: [Float] { [matrix.a,matrix.b,matrix.c,matrix.d,biasA,biasB].map(Float.init) }
    func scaled(by strength: Double) -> Self {
        Self(matrix: .identity*(1-strength)+matrix*strength, biasA: biasA*strength, biasB: biasB*strength)
    }
    func validated() throws -> Self {
        guard parameters.allSatisfy(\.isFinite), [matrix.a,matrix.b,matrix.c,matrix.d].allSatisfy({ abs($0) <= 4 }),
              abs(biasA) <= 0.08, abs(biasB) <= 0.08 else { throw AIError.message("冻结色度变换超出范围。") }
        return self
    }
}

enum AIColorStatistics {
    struct Statistics {
        var mean: SIMD3<Double>
        var covariance: AIChromaMatrix
        var lightnessVariance: Double
        var count: Int
    }
    struct Fit {
        let recipe: AIColorRecipe
        let mapping: AIChromaMapping
        let diagnostics: [String]
    }
    static func oklab(_ rgb: SIMD3<Double>) -> SIMD3<Double> {
        func linear(_ v: Double) -> Double { v <= 0.04045 ? v/12.92 : pow((v+0.055)/1.055,2.4) }
        let r = linear(rgb.x), g = linear(rgb.y), b = linear(rgb.z)
        let l = cbrt(0.4122214708*r+0.5363325363*g+0.0514459929*b)
        let m = cbrt(0.2119034982*r+0.6806995451*g+0.1073969566*b)
        let s = cbrt(0.0883024619*r+0.2817188376*g+0.6299787005*b)
        return SIMD3(0.2104542553*l+0.7936177850*m-0.0040720468*s,
                     1.9779984951*l-2.4285922050*m+0.4505937099*s,
                     0.0259040371*l+0.7827717662*m-0.8086757660*s)
    }
    static func analyze(_ image: CGImage) throws -> Statistics {
        let scale = min(1,256/Double(max(image.width,image.height)))
        let width = max(1,Int(Double(image.width)*scale)), height = max(1,Int(Double(image.height)*scale))
        var bytes = [UInt8](repeating: 0, count: width*height*4)
        let result = bytes.withUnsafeMutableBytes { buffer -> Statistics? in
            guard let space = CGColorSpace(name: CGColorSpace.sRGB),
                  let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                    bytesPerRow: width*4, space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue) else { return nil }
            context.interpolationQuality = .high
            context.draw(image, in: CGRect(x: 0,y: 0,width: width,height: height))
            let pixels = buffer.bindMemory(to: UInt8.self)
            var mean = SIMD3<Double>.zero, sum = SIMD3<Double>.zero, cross = 0.0, count = 0
            for i in stride(from: 0, to: pixels.count, by: 4) {
                let lab = oklab(SIMD3(Double(pixels[i])/255,Double(pixels[i+1])/255,Double(pixels[i+2])/255))
                if lab.x <= 0.02 || lab.x >= 0.98 { continue }
                count += 1
                let delta = lab-mean; mean += delta/Double(count)
                let after = lab-mean; sum += delta*after; cross += delta.y*after.z
            }
            guard count >= 16 else { return nil }
            let denominator = Double(count)
            return Statistics(mean: mean, covariance: AIChromaMatrix(sum.y/denominator,cross/denominator,cross/denominator,sum.z/denominator),
                              lightnessVariance: sum.x/denominator, count: count)
        }
        guard let result else { throw AIError.message("图像有效颜色样本不足，请使用未严重裁切的参考图。") }
        return result
    }
    static func fit(source: Statistics, references: [(Statistics, Double)], method: AIColorMethod) throws -> Fit {
        guard method != .ai, references.allSatisfy({ $0.1.isFinite && (0...1).contains($0.1) }) else {
            throw AIError.message("本地方法或参考权重无效。")
        }
        let active = references.filter { $0.1 > 0 }, total = active.reduce(0) { $0+$1.1 }
        guard total > 0 else { throw AIError.message("至少启用一张参考图。") }
        var mean = SIMD3<Double>.zero, covariance = AIChromaMatrix(0,0,0,0), variance = 0.0
        for (stats, weight) in active {
            let w = weight/total; mean += stats.mean*w
            covariance = covariance+stats.covariance*w; variance += stats.lightnessVariance*w
        }
        var diagnostics: [String] = []
        var mapping = AIChromaMapping.identity
        if source.covariance.a+source.covariance.d < 1e-7 || covariance.a+covariance.d < 1e-7 {
            diagnostics.append("源图或参考组色彩变化过少，色度回退为恒等变换。")
        } else {
            let s = source.covariance.regularized, r = covariance.regularized
            let raw: AIChromaMatrix
            if method == .chromaOT {
                let half = try s.squareRoot(), inverse = try half.inverse()
                raw = inverse * (try (half*r*half).squareRoot()) * inverse
            } else { raw = AIChromaMatrix(sqrt(r.a/s.a),0,0,sqrt(r.d/s.d)) }
            let matrix = raw.limitedGain()
            let bias = SIMD2(mean.y,mean.z)-matrix.applied(SIMD2(source.mean.y,source.mean.z))
            let a = min(0.08,max(-0.08,bias.x)), b = min(0.08,max(-0.08,bias.y))
            if matrix.distance(raw) > 1e-8 || a != bias.x || b != bias.y { diagnostics.append("已限制过大的色度增益或偏移，结果不再严格匹配参考统计。") }
            mapping = try AIChromaMapping(matrix: matrix, biasA: a, biasB: b).validated()
        }
        let spread = active.reduce(0.0) { result, entry in
            let delta = entry.0.mean-mean
            return result+entry.1/total*(delta.y*delta.y+delta.z*delta.z)
        }
        if spread > 0.0025 { diagnostics.append("参考图色彩分布差异较大；不等于已确认风格冲突。") }
        var recipe = AIColorRecipe.identity(name: method == .chromaOT ? "色度 OT 匹配" : "Reinhard 匹配",
            summary: "Oklab 统计基线，按每图权重聚合图内统计；针对当前素材，不保证通用风格。")
        let gain = source.lightnessVariance > 1e-7 ? min(1.25,max(0.75,sqrt(variance/source.lightnessVariance))) : 1
        let shift = min(0.05,max(-0.05,mean.x-source.mean.x))
        recipe.tone = (0...8).map { i in
            let l = Double(i)/8
            return Float(l+4*l*(1-l)*((gain-1)*(l-0.5)+shift))
        }
        return Fit(recipe: try recipe.validated(), mapping: mapping, diagnostics: diagnostics)
    }
}
