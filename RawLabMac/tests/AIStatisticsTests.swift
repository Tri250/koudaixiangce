import AppKit

private func check(_ value: @autoclosure () -> Bool, _ name: String) {
    guard value() else { fatalError("FAIL: \(name)") }
    print("PASS: \(name)")
}

@main struct AIStatisticsTests {
    static func main() throws {
        setbuf(stdout, nil)
        let s = AIColorStatistics.Statistics(mean: SIMD3(0.5, 0.01, -0.02),
            covariance: AIChromaMatrix(0.004, 0.001, 0.001, 0.002), lightnessVariance: 0.02, count: 100)
        let r = AIColorStatistics.Statistics(mean: SIMD3(0.55, -0.005, -0.01),
            covariance: AIChromaMatrix(0.002, -0.0005, -0.0005, 0.003), lightnessVariance: 0.03, count: 100)
        let ot = try AIColorStatistics.fit(source: s, references: [(r, 1)], method: .chromaOT)
        let a = ot.mapping.matrix
        let transported = a * s.covariance.regularized * a.transposed
        check(transported.distance(r.covariance.regularized) < 1e-10,
              "Noncommuting Gaussian OT maps the regularized covariance")
        check(abs(a.b-a.c) < 1e-10, "Gaussian OT matrix is symmetric, unlike generic whitening-coloring")
        let target = a.applied(SIMD2(s.mean.y, s.mean.z)) + ot.mapping.bias
        check(simdDistance(target, SIMD2(r.mean.y, r.mean.z)) < 1e-10, "Gaussian OT maps the chroma mean")
        let identity = try AIColorStatistics.fit(source: s, references: [(s, 1)], method: .chromaOT)
        check(identity.mapping.matrix.distance(.identity) < 1e-10 && simdDistance(identity.mapping.bias, .zero) < 1e-10,
              "Same source and reference fit identity")
        let diagonalS = AIColorStatistics.Statistics(mean: s.mean, covariance: AIChromaMatrix(0.004,0,0,0.002), lightnessVariance: 0.02, count: 100)
        let diagonalR = AIColorStatistics.Statistics(mean: s.mean, covariance: AIChromaMatrix(0.002,0,0,0.003), lightnessVariance: 0.02, count: 100)
        let diagonalOT = try AIColorStatistics.fit(source: diagonalS, references: [(diagonalR,1)], method: .chromaOT)
        check(abs(diagonalOT.mapping.matrix.b) < 1e-10 && abs(diagonalOT.mapping.matrix.a - sqrt(diagonalR.covariance.regularized.a/diagonalS.covariance.regularized.a)) < 1e-10,
              "Diagonal OT reduces to channel standard-deviation ratios")
        let reinhard = try AIColorStatistics.fit(source: s, references: [(r,1)], method: .reinhard)
        check(reinhard.mapping.matrix.b == 0 && reinhard.mapping.matrix.c == 0, "Reinhard remains a diagonal engineering baseline")
        check(abs(reinhard.mapping.matrix.a-sqrt(r.covariance.regularized.a/s.covariance.regularized.a)) < 1e-10 &&
              abs(reinhard.mapping.matrix.d-sqrt(r.covariance.regularized.d/s.covariance.regularized.d)) < 1e-10,
              "Reinhard matches regularized per-channel standard deviations")
        let root = try s.covariance.squareRoot()
        let inverseRoot = try root.inverse()
        check((root*root).distance(s.covariance) < 1e-12 && (root*inverseRoot).distance(.identity) < 1e-12,
              "SPD square root and inverse satisfy their defining identities")
        let limited = AIChromaMatrix(5,4.99,4.99,5).limitedGain()
        check(abs(limited.a+limited.b-4) < 1e-12 && abs(limited.a-limited.b-0.25) < 1e-12,
              "Principal gains, not just diagonal entries, are limited")
        var referenceA = s, referenceB = s
        referenceA.mean += SIMD3(0,0.04,-0.04); referenceB.mean -= SIMD3(0,0.04,-0.04)
        let within = try AIColorStatistics.fit(source: s,references: [(referenceA,1),(referenceB,1)],method: .chromaOT)
        check(within.mapping.matrix.distance(.identity) < 1e-10,
              "Reference mean differences do not inflate within-image covariance")
        var huge = r; huge.count = 1_000_000
        let first = try AIColorStatistics.fit(source: s, references: [(r,0.25),(s,0.75)], method: .chromaOT)
        let second = try AIColorStatistics.fit(source: s, references: [(s,0.75),(huge,0.25)], method: .chromaOT)
        check(first.mapping.matrix.distance(second.mapping.matrix) < 1e-10 && simdDistance(first.mapping.bias, second.mapping.bias) < 1e-10,
              "Per-image weights are independent of order and pixel count")
        let ignored = try AIColorStatistics.fit(source: s, references: [(r,0),(s,1)], method: .chromaOT)
        check(ignored.mapping.matrix.distance(.identity) < 1e-10, "Zero-weight references are excluded")
        var flat = s; flat.covariance = AIChromaMatrix(0,0,0,0)
        let fallback = try AIColorStatistics.fit(source: flat, references: [(r,1)], method: .chromaOT)
        check(fallback.mapping == .identity && !fallback.diagnostics.isEmpty, "Low-information source has an explicit finite fallback")
        let flatTarget = try AIColorStatistics.fit(source: s,references: [(flat,1)],method: .reinhard)
        check(flatTarget.mapping == .identity && !flatTarget.diagnostics.isEmpty, "Low-information target also has an explicit fallback")
        var narrow = s; narrow.covariance = AIChromaMatrix(0.002,0.002,0.002,0.002)
        let singular = try AIColorStatistics.fit(source: narrow,references: [(r,1)],method: .chromaOT)
        check(singular.mapping.parameters.allSatisfy(\.isFinite) && !singular.diagnostics.isEmpty,
              "Rank-one covariance is regularized and reports limited gains")
        var recipe = ot.recipe; recipe.chroma = 0.8; recipe.toning[0].a = 0.01
        let off = try AIColorControls(color: 0, tone: 0).resolve(recipe)
        check(off.parameters == AIColorRecipe.identity(name: recipe.name, summary: recipe.summary).parameters,
              "Zero color and tone controls resolve to identity style")
        check(ot.mapping.scaled(by: 0) == .identity, "Zero color control disables the frozen matrix and bias")
        let preserved = try AIColorControls(color: 1, tone: 0).resolve(recipe)
        check(preserved.tone == (0...8).map { Float($0)/8 } && preserved.hueBands.allSatisfy { $0.lightness == 0 },
              "Tone zero disables both tone curve and hue-band lightness")
        let white = AIColorStatistics.oklab(SIMD3(1,1,1))
        check(abs(white.x-1) < 1e-6 && abs(white.y) < 1e-6 && abs(white.z) < 1e-6, "sRGB Oklab conversion has a D65 white anchor")
        let context = CGContext(data: nil,width: 64,height: 32,bitsPerComponent: 8,bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!,bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        context.setFillColor(red: 0.2,green: 0.4,blue: 0.6,alpha: 1)
        context.fill(CGRect(x: 0,y: 0,width: 64,height: 32))
        let pixelStats = try AIColorStatistics.analyze(context.makeImage()!)
        let anchor = AIColorStatistics.oklab(SIMD3(0.2,0.4,0.6))
        check(abs(pixelStats.mean.x-anchor.x) < 1e-6 && abs(pixelStats.mean.y-anchor.y) < 1e-6 && abs(pixelStats.mean.z-anchor.z) < 1e-6,
              "Image statistics use declared sRGB RGB byte order")
        check(pixelStats.covariance.distance(AIChromaMatrix(0,0,0,0)) < 1e-12 && pixelStats.count == 2048,
              "Flat colored image has finite zero covariance")
        context.setFillColor(red: 1,green: 0,blue: 0,alpha: 1)
        context.fill(CGRect(x: 0,y: 0,width: 32,height: 32))
        context.setFillColor(red: 0,green: 0,blue: 1,alpha: 1)
        context.fill(CGRect(x: 32,y: 0,width: 32,height: 32))
        let mixed = try AIColorStatistics.analyze(context.makeImage()!)
        let red = AIColorStatistics.oklab(SIMD3(1,0,0)), blue = AIColorStatistics.oklab(SIMD3(0,0,1))
        let delta = (red-blue)/2, expectedMean = (red+blue)/2
        check(abs(mixed.mean.x-expectedMean.x) < 1e-12 && abs(mixed.mean.y-expectedMean.y) < 1e-12 &&
              mixed.covariance.distance(AIChromaMatrix(delta.y*delta.y,delta.y*delta.z,delta.y*delta.z,delta.z*delta.z)) < 1e-12 &&
              abs(mixed.lightnessVariance-delta.x*delta.x) < 1e-12,
              "Welford mean, variances and cross covariance agree with known nonzero samples")
        context.setFillColor(red: 0,green: 0,blue: 0,alpha: 1)
        context.fill(CGRect(x: 0,y: 0,width: 64,height: 32))
        do { _ = try AIColorStatistics.analyze(context.makeImage()!); fatalError("Clipped-only image accepted") }
        catch is AIError { print("PASS: Clipped-only input has an explicit insufficient-sample error") }
        for bad in [Double.nan, -1, 1.1] {
            do { _ = try AIColorControls(color: bad, tone: 0).resolve(recipe); fatalError("Invalid control accepted") }
            catch is AIError { print("PASS: Invalid local control rejected") }
        }
        do { _ = try AIColorStatistics.fit(source: s, references: [(r,0)], method: .chromaOT); fatalError("No active reference accepted") }
        catch is AIError { print("PASS: No active reference rejected") }
    }

    static func simdDistance(_ a: SIMD2<Double>, _ b: SIMD2<Double>) -> Double { hypot(a.x-b.x, a.y-b.y) }
}
