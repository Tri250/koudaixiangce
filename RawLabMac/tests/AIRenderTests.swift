import AppKit
import ImageIO

@main struct AIRenderTests {
    static func main() throws {
        setbuf(stdout, nil)
        guard CommandLine.arguments.count == 3 else { throw RenderError.failed("Pass RAW and display CUBE fixtures") }
        let raw = URL(fileURLWithPath: CommandLine.arguments[1])
        var cube = URL(fileURLWithPath: CommandLine.arguments[2])
        let original = try OriginalFileIdentity(raw)
        let directory = try AITemporaryDirectory()
        let cpu = try RenderEngine(gpuMode: SONY2FUJI_GPU_OFF)
        let gpu = try RenderEngine(gpuMode: SONY2FUJI_GPU_FORCE)
        var settings = Adjustments(); settings.exposure = 0.35
        var localLook: AIColorLook?
        if CommandLine.arguments[2] == "--local-ot" {
            var teacherRecipe = AIColorRecipe.identity(name: "Controlled color teacher",summary: "Known global fixture, not inferred style")
            teacherRecipe.chroma = 0.9; teacherRecipe.toning[0].a = -0.002; teacherRecipe.toning[2].b = 0.003
            let teacher = try AIColorLook.compile(recipe: teacherRecipe,model: "test-only")
            guard let sourceFrame = try cpu.render(raw,settings: settings,lut: nil,edge: 500),
                  let targetFrame = try cpu.render(raw,settings: settings,lut: teacher.url,edge: 500) else {
                throw RenderError.failed("Missing statistics fixture")
            }
            let fit = try AIColorStatistics.fit(source: AIColorStatistics.analyze(sourceFrame.image),
                references: [(AIColorStatistics.analyze(targetFrame.image),1)],method: .chromaOT)
            let look = try AIColorLook.compile(recipe: fit.recipe,model: "local-regression",mapping: fit.mapping,
                controls: AIColorControls(color: 0.7,tone: 0.4),method: .chromaOT,diagnostics: fit.diagnostics)
            localLook = look; cube = look.url
            print("PASS: real preview statistics frozen and baked; grid=\(look.report.gridSize), max RGB error=\(look.report.maxError)")
        }
        for strength in [0.0, 1.0, 2.0] {
            settings.strength = strength
            guard let a = try cpu.render(raw, settings: settings, lut: cube, edge: 500),
                  let b = try gpu.render(raw, settings: settings, lut: cube, edge: 500),
                  let first = a.image.dataProvider?.data, let second = b.image.dataProvider?.data else {
                throw RenderError.failed("Missing comparison pixels")
            }
            let x = first as Data, y = second as Data
            let maximum = zip(x,y).map { abs(Int($0)-Int($1)) }.max() ?? 999
            guard x.count == y.count, maximum <= 2, gpu.lastBackend == SONY2FUJI_BACKEND_METAL else {
                throw RenderError.failed("Display LUT CPU/Metal mismatch")
            }
            print("PASS: real RAW display LUT strength \(strength), CPU/Metal max DN=\(maximum)")
        }
        settings.strength = 0.75; settings.contrast = 12
        let journal = BatchJournal(directory: directory.url.appendingPathComponent("journal"))
        for png in [false, true] {
            try autoreleasepool {
                let single = directory.url.appendingPathComponent(png ? "single.png" : "single.jpg")
                _ = try gpu.render(raw, settings: settings, lut: cube, edge: nil, output: single)
                guard gpu.lastBackend == SONY2FUJI_BACKEND_METAL,
                      let source = CGImageSourceCreateWithURL(single as CFURL, nil),
                      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
                    throw RenderError.failed("Missing full-resolution export")
                }
                if png {
                    let handle = try FileHandle(forReadingFrom: single); defer { try? handle.close() }
                    let header = try handle.read(upToCount: 26) ?? Data()
                    guard header.count == 26, header[24] == 16 else { throw RenderError.failed("PNG is not 16-bit") }
                }
                var job = BatchExportJob(source: raw, settings: settings, look: nil, lookName: "AI test")
                job.look = try journal.snapshotLook(cube, for: job.id)
                job.outputDirectory = directory.url; job.png = png
                var item = try BatchExportItem(url: raw); item.selected = true; job.items = [item]
                let batch = BatchExportModel(job: job, journal: journal)
                batch.start()
                let deadline = Date().addingTimeInterval(120)
                while batch.running && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.05)) }
                guard !batch.running, batch.job.succeededCount == 1, let output = batch.job.items[0].output,
                      let batchSource = CGImageSourceCreateWithURL(output as CFURL, nil),
                      let batchImage = CGImageSourceCreateImageAtIndex(batchSource, 0, nil),
                      image.width == batchImage.width, image.height == batchImage.height,
                      image.width > 2000, image.height > 2000,
                      image.dataProvider!.data! as Data == batchImage.dataProvider!.data! as Data else {
                    throw RenderError.failed(batch.error ?? "Single and batch AI exports differ")
                }
                print("PASS: \(png ? "16-bit PNG" : "JPEG") full-resolution AI look \(image.width)x\(image.height), single/batch pixels identical")
            }
        }
        guard try OriginalFileIdentity(raw) == original else { throw RenderError.failed("Original RAW was modified") }
        withExtendedLifetime(localLook) {}
        print("PASS: original RAW unchanged")
    }
}
