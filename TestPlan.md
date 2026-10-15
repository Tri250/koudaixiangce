# RawLab 功能性测试用例与验收标准

> **文档版本**: v1.0 · **日期**: 2026-10-15 · **覆盖范围**: 全部 8 大功能模块 / 约 50+ 子功能模块

## 目录

- [0. 用例字段约定与优先级定义](#0-用例字段约定与优先级定义)
- [1. 核心 C++ 引擎（lutools）](#1-核心-c-引擎lutools)
  - [1.1 RAW 文件解码](#11-raw-文件解码)
  - [1.2 色彩空间转换](#12-色彩空间转换)
  - [1.3 3D LUT 引擎](#13-3d-lut-引擎)
  - [1.4 图像处理管线（白平衡/曝光/色调）](#14-图像处理管线白平衡曝光色调)
  - [1.5 GPU 加速渲染（三后端）](#15-gpu-加速渲染三后端)
  - [1.6 GPU 策略与 Fallback](#16-gpu-策略与-fallback)
  - [1.7 图像编码器（JPEG/PNG）](#17-图像编码器jpegpng)
  - [1.8 CLI 工具](#18-cli-工具)
  - [1.9 C ABI 稳定性](#19-c-abi-稳定性)
- [2. 跨平台共享层 ExportMetadata](#2-跨平台共享层-exportmetadata)
- [3. macOS 客户端](#3-macos-客户端)
- [4. Windows 客户端](#4-windows-客户端)
- [5. iOS 客户端](#5-ios-客户端)
- [6. Android 客户端](#6-android-客户端)
- [7. 跨平台一致性验证](#7-跨平台一致性验证)
- [8. 性能与资源](#8-性能与资源)
- [9. 验收标准矩阵](#9-验收标准矩阵)
- [10. Bug 记录模板](#10-bug-记录模板)

---

## 0. 用例字段约定与优先级定义

| 字段 | 含义 |
|---|---|
| 用例编号 | `TC-{模块缩写}-{三位序号}` |
| 所属模块 | 8 大模块之一 |
| 子功能模块 | 对应前述清单的子模块 |
| 测试类别 | 功能 / 边界 / 异常 / 跨模块 / 回归 |
| 前置条件 | 执行前提（文件、环境、GPU 可用） |
| 操作步骤 | 可复现的具体步骤 |
| 预期结果 | 可量化的验收标准 |
| 关联已有测试 | 仓库中已有的覆盖此点的测试文件（无则标"新增"） |
| 优先级 | P0 = 冒烟阻塞 / P1 = 核心功能 / P2 = 边缘场景 |

**优先级定义**：
- **P0**：阻塞发布的功能缺失或崩溃，CI 必须通过
- **P1**：核心用户路径、主要回归风险
- **P2**：边界/异常/性能优化场景

---

## 1. 核心 C++ 引擎（lutools）

### 1.1 RAW 文件解码

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-RAW-001 | Sony ARW 解码 | 功能 | 存在 `.ARW` 测试夹具 | 调用 `RAWProcessor::loadFile + process` | `ErrorCode::Success`；输出 ImageData 宽高与 EXIF 报告一致；pixels 非空 | 新增（smoke.sh 隐式覆盖但未显式校验宽高） | P0 |
| TC-RAW-002 | Sony SRF 解码 | 功能 | 存在 `.SRF` 夹具 | 同上 | 同 TC-RAW-001 | 新增 | P1 |
| TC-RAW-003 | DNG 解码 | 功能 | 存在 `.DNG`（DJI / Fuji X100VI）夹具 | 同上 | 同 TC-RAW-001 | smoke.sh（DJI 夹具） | P0 |
| TC-RAW-004 | CR2 / CR3 / NEF / RAF / RW2 / ORF | 功能 | 各扩展名夹具各一个 | 各调一次 loadFile + process | 全部 Success；各输出正确宽高 | 新增（adjustments.sh 枚举扩展名但未逐一断言） | P1 |
| TC-RAW-005 | 半分辨率预览模式 | 边界 | 任意 RAW 夹具 | `options.halfSize=true` 调 process | 输出宽高约为全分辨率 1/2（向下取整） | core_tests 隐式验证 worker 栈 | P1 |
| TC-RAW-006 | 内嵌预览提取 | 功能 | RAW 带 JPEG 内嵌预览 | 调 `raw_smoke.cpp` 中 `embeddedPreview()` | 返回非空 ImageData；方向码 flip 与 EXIF 一致 | raw_smoke.cpp（验证 7008×4672 + flip=5） | P0 |
| TC-RAW-007 | 不存在文件 | 异常 | 无 | loadFile("/tmp/nonexistent.raw") | 返回 `ErrorCode::NotFound` 或 `IOError`；进程不崩溃 | smoke.sh（--smoke missing.raw 分支） | P0 |
| TC-RAW-008 | 损坏 RAW | 异常 | 任意 1KB 随机 bytes 文件伪装成 .ARW | loadFile(corrupt.arw) | 返回 `Unsupported` / `ParseError`；不产生部分写入 | 新增 | P0 |
| TC-RAW-009 | 非 RAW 格式文件 | 异常 | 一个 .txt 文件 | loadFile(a.txt) | 返回 `Unsupported` | 新增 | P1 |
| TC-RAW-010 | 空文件 | 异常 | 0 字节文件 | loadFile(empty.arw) | 返回错误；不崩溃 | 新增 | P1 |
| TC-RAW-011 | 超大 RAW（≥ 60MP） | 边界 | 存在 60MP+ 全画幅夹具 | 全分辨率 process | 内存使用 ≤ width×height×3×4×2 bytes；512KB 栈线程能跑完 | core_tests（worker 栈 512KB 用缩略图间接测） | P1 |
| TC-RAW-012 | 旋转方向（90/180/270） | 边界 | 带 EXIF orientation ≠ 1 的 RAW | 调 process 后读 ImageData 宽高 | 宽高按 orientation 交换；输出像素方向正确 | highlight_regression.cpp（验证 flip=5） | P1 |
| TC-RAW-013 | 私有相机色彩矩阵读取 | 功能 | Sony ARW + DNG 夹具 | 调 `getCameraColorMatrix(matrix)` | 返回 true；3×3 矩阵元素非零、有意义（对角线为主对角占优） | 新增 | P1 |

---

### 1.2 色彩空间转换

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-CS-001 | Sony Native → F-Gamut 矩阵 | 功能 | ColorConverter 就绪 | `getConversionMatrix(SonyNative, FujiFilm_FGamut)` | 返回 3×3 非奇异矩阵；行列式 > 0；非对角元素反映红移 | 新增 | P0 |
| TC-CS-002 | sRGB ↔ XYZ ↔ sRGB 往返 | 边界 | 同上 | 白 + 黑 + 红 + 绿 + 蓝 + 黄各点做 forward + inverse | 每点 round-trip 误差 < 1e-4（浮点精度） | 新增 | P0 |
| TC-CS-003 | F-Gamut ↔ XYZ ↔ F-Gamut 往返 | 边界 | 同上 | 同上 | 同上 | 新增 | P0 |
| TC-CS-004 | 8 种色彩空间任意互转 | 功能 | ColorConverter 就绪 | 对 8 种 ColorSpace 两两调用 `getConversionMatrix` | 全部成功；无 NaN/Inf；复合链 A→B→C→A 误差 < 1e-3 | 新增 | P1 |
| TC-CS-005 | 相机矩阵转换 + XYZ 色适应 | 功能 | RAW 已加载，相机色彩矩阵可用 | `convertImage(img, cameraMatrix, cameraWP, FujiFilm_FGamut)` | 输出像素数值稳定、非 NaN；色适应矩阵非奇异 | 新增 | P0 |
| TC-CS-006 | 奇异矩阵保护 | 异常 | 构造秩亏 3×3 矩阵 | `getConversionMatrix(singularMatrix, ...)` | 返回保护后的矩阵或报错；不产生 NaN | 新增 | P1 |
| TC-CS-007 | GammaConverter sRGB 曲线 round-trip | 边界 | 同上 | 在 [0,1] 均匀采样 1000 点做 applySRGBGamma → removeSRGBGamma | 每点误差 < 1e-4 | 新增 | P1 |
| TC-CS-008 | GammaConverter F-Log2 编码输出范围 | 边界 | 同上 | 对 linear ∈ [-0.5, 16.0] 扫 `applyFLog2` | 线性 → F-Log2 单调递增；正值区间；overflow 截断到可表示最大值 | 新增 | P0 |
| TC-CS-009 | F-Log2 编码全图（含诊断） | 功能 | ImageData 有 0 以下和 >1 的溢出像素 | `applyFLog2ToImage(img, {enableDiagnostics=true})` | 返回 struct 中 `negative_pixels` 和 `over_pixels` 计数准确；图像未污染 | 新增 | P1 |
| TC-CS-010 | 极端色温 D55 / D65 / 50000K | 边界 | 色温-色调 round-trip | 各调一次 `temperatureWhitePoint` → `whitePointTemperature` | 色温误差 < 0.5%；色调误差 < 0.5 | white_balance_tests.cpp（已覆盖 2000–50000） | P0 |

---

### 1.3 3D LUT 引擎

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-LUT-001 | 17-grid .cube 加载 | 功能 | 存在 17-grid .cube | `LUTParser::loadLUT` | `size_=17`；`isValid()=true`；domain=[0,0,0]-[1,1,1] | 新增 | P1 |
| TC-LUT-002 | 33-grid .cube 加载（X100VI 内置） | 功能 | `lutools/F-Log2/X100VI_FLog2_FGamut_to_ETERNA_BT.709_33grid_V.1.00.cube` | 同上 | `size_=33`；title / domain 正确 | 新增 | P0 |
| TC-LUT-003 | 65-grid .cube 加载（完整胶片集） | 功能 | `lutools/flog-2-new/` 下 11 个 .cube | 同上 | 全部 size_=65；isValid=true | 新增 | P0 |
| TC-LUT-004 | 缓存加载（同一文件两次） | 功能 | 同上 | `loadLUT` + `loadLUTCached` 比较 raw pointer | 第二次返回的 `shared_ptr` 与第一次共享引用计数 | 新增 | P1 |
| TC-LUT-005 | 三线性插值单像素端点 | 边界 | 65-grid LUT 加载 | 分别 apply(0,0,0) / (1,1,1) / (0.5,0.5,0.5) | 输出精确等于 cube table 对应 corner 值；中点是 8 邻域精确平均 | 新增 | P0 |
| TC-LUT-006 | 三线性插值全图应用（in-place） | 功能 | 同上 + 非空 ImageData | `LUTApplicator::applyToImage(image)` | 输出像素全部 ∈ [domainMin, domainMax]；无 NaN | 新增 | P0 |
| TC-LUT-007 | LUT 强度混合 0% / 50% / 100% / 200% | 功能 | 同上 | 依次用 strength 参数混合 neutral 与 LUT 结果 | 0% = neutral；100% = 纯 LUT；200% = 1×LUT + 1×(LUT-neutral)；单调性成立 | 新增 | P0 |
| TC-LUT-008 | 损坏 .cube（截断/二进制噪声） | 异常 | 损坏的 .cube 拷贝 | loadLUT(corrupt.cube) | ErrorCode 非 Success；LUT3D::isValid()=false；不崩溃 | 新增 | P0 |
| TC-LUT-009 | 空 .cube（仅 header 无 data） | 异常 | header-only .cube | 同上 | ErrorCode 非 Success | 新增 | P1 |
| TC-LUT-010 | 非立方 grid（如 5 或 13） | 边界 | 构造 5-grid .cube | 同上 | 若代码假设 3 的幂，应返回 ErrorCode；否则正常加载 | 新增 | P1 |
| TC-LUT-011 | 输出域越界（domain 非 [0,1]） | 边界 | 构造 domainMin=[-1,0,0] / domainMax=[2,1,1] .cube | 调 LUTApplicator | 应做 domain clamp 并产生 warning（或报错） | 新增 | P1 |
| TC-LUT-012 | detectFormat 识别 .cube | 功能 | 同上 | `LUTParser::detectFormat(path)` | 返回 "cube" | 新增 | P2 |

---

### 1.4 图像处理管线（白平衡/曝光/色调）

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-WB-001 | 拍摄时白平衡恢复 | 功能 | 带相机色彩矩阵的 RAW | 加载 → `getAsShotWhiteBalance(T,Tint)` → 以 Camera WB 模式 process → 取 R/G/B 平均 | R/G ≈ 1、B/G ≈ 1（灰场中性）；色温误差 < 50K | white_balance_tests.cpp | P0 |
| TC-WB-002 | DNG 双光源 AnalogBalance + ColorMatrix 插值 | 功能 | 合成 Dual-illuminant DNG 夹具 | synthetic.CameraWhiteBalance.multipliers(5000,0,gains) | gains 与 AnalogyBalance × Calibration × ColorMatrix 精确匹配（误差 < 1e-5） | white_balance_tests.cpp（line 42-48） | P0 |
| TC-WB-003 | 色温滑块端点 2000K / 50000K | 边界 | 任意 RAW | process 设 K=2000 / 50000 | 输出稳定、非 NaN；2000K 整体偏暖、50000K 整体偏冷 | white_balance_tests.cpp（line 55-58） | P0 |
| TC-WB-004 | 色调滑块端点 -150 / +150 | 边界 | 同上 | tint=-150 / +150 | 输出稳定、非 NaN；负色调偏绿、正色调偏品红 | white_balance_tests.cpp（line 55-58） | P0 |
| TC-WB-005 | 白平衡必须在 demosaic 之前应用 | 跨模块 | 内部插桩可用 | 捕获 `process()` 调用中 WB gain 应用时机 | 时序：WB → demosaic → 色彩矩阵 → F-Log2 → LUT | lutools/AGENTS.md 约束 | P0 |
| TC-EXP-001 | 三种曝光基准 Scene / Preview / Sensor | 功能 | 同上 | 依次设 `ExposureMode::scene / preview / sensor` 各自 render | Scene = BaselineExposure + 场景默认；Preview ≈ 内嵌 JPEG 亮度；Sensor 不做额外偏移 | Engine.swift 三种 mode 映射 | P0 |
| TC-EXP-002 | 负曝光 -4EV 不过度压黑 | 边界 | 高光丰富 RAW | exposure=-4.0 render | 最暗像素仍可辨别层次；整体平均亮度下降 ≈ 4 stop | 新增 | P1 |
| TC-EXP-003 | 正曝光 +4EV 不过度剪切 | 边界 | 同上 | exposure=+4.0 render | 高光压缩生效；白场不产生品红/绿偏移（TC-HL-001） | highlight_regression.cpp | P0 |
| TC-HL-001 | 饱和高光不渲染品红（回归） | 回归 | highlight_regression 夹具 RAW + LUT | 以 gain=0.5 / 1 / 2 扫描车身高光 ROI | filmPink ≤ 1%、neutralPink ≤ 1%、meanMagenta < 0.02 | highlight_regression.cpp | P0 |
| TC-TONE-001 | 对比度 ±100% | 边界 | 任意 RAW + LUT | contrast=-1.0 → output 低对比；contrast=+1.0 → 硬对比 | ±1.0 时仍无 NaN；直方图极值变化可观测 | 新增 | P1 |
| TC-TONE-002 | 高光 +100% 实际变暗（UI→Core 符号反转） | 跨模块 | 同上 | UI 传 highlights=+100% → Core 实际收到 highlights=-1.0 | 渲染高光被压缩变暗；UI 滑块↔Core 符号映射正确 | Adjustments.swift line 39 | P0 |
| TC-TONE-003 | 阴影 +100% 提升但不引入 banding | 边界 | 暗部丰富 RAW | shadows=+100% render | 暗部细节可见；无明显 banding（人工或 PSNR 判定） | 新增 | P2 |
| TC-TONE-004 | S 曲线 ±100% | 边界 | 同上 | toneCurve=-1 → soft；toneCurve=+1 → steep | 单调曲线；无过冲 | 新增 | P1 |
| TC-TONE-005 | 饱和度 ±100% | 边界 | 同上 | saturation=-1 → 黑白；saturation=+1 → 全饱和 | -1 时输出近似灰度；+1 时非负值不溢出 | 新增 | P1 |
| TC-TONE-006 | 锐化 0–200% | 边界 | 同上 | sharpening=0 → 无锐化；200 → 强锐化 | 200% 仍稳定；无 ring artifact 外溢到邻域 | 新增 | P2 |

---

### 1.5 GPU 加速渲染（三后端）

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-GPU-001 | Metal LUT 应用器 | 功能 | macOS + Metal 可用 + 65-grid LUT | 设 `SONY2FUJI_GPU_FORCE` + Metal 后端 render | 成功；输出非空；无崩溃 | gpu_photo_tests.cpp | P0 |
| TC-GPU-002 | Metal 直方图 + 裁切遮罩 | 功能 | 同上 | `sony2fuji_analyze_image` | 768 bins 非全零；clipping mask 非空；直方图与 CPU 同输入一致（bin 差 ≤ 5%） | 新增 | P0 |
| TC-GPU-003 | D3D11 LUT + Photo | 功能 | Windows + D3D11 | 设 `SONY2FUJI_GPU_FORCE` + D3D11 后端 render | 成功；非空；帧时间 < CPU × 0.5 | 新增 | P0 |
| TC-GPU-004 | GLES LUT + Photo | 功能 | Android + GLES 3.x | 同上 | 同上 | 新增 | P0 |
| TC-GPU-005 | 三后端数值一致性 | 跨模块 | 同一 RAW + 同一 LUT + 同一调整 | 依次 Metal / D3D11 / GLES 渲染同一输入，取同一 ROI 的 RGB 均值 | 任意两后端 ROI 均值差 < 0.01（float 误差容忍） | 新增 | P0 |
| TC-GPU-006 | Metal vs CPU 输出差异 | 边界 | 同上 | 同输入 CPU render vs GPU render | 全图 RMS 差 < 0.005；单像素最大差 < 0.02 | 新增 | P0 |
| TC-GPU-007 | GPU 并发提交 100 个独立 render | 边界 | 同上 | 100 线程同时 `sony2fuji_process` 不同 RAW | 全部成功；GPU 队列不阻塞；CPU fallback 不触发 | 新增 | P1 |

---

### 1.6 GPU 策略与 Fallback

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-GPU-010 | GPU AUTO 检测 Metal/D3D11/GLES 可用 | 功能 | 对应平台 GPU 正常 | `SONY2FUJI_GPU_AUTO` 调用 `session_create` + `session_set_gpu_config` | 成功返回；`gpuMode_effective` 非 CPU | 新增 | P0 |
| TC-GPU-011 | GPU Force 但硬件不可用 | 异常 | macOS 禁用 Metal / Windows 无 D3D11 | `SONY2FUJI_GPU_FORCE` + 硬件禁用 | 返回 ErrorCode + 明确错误消息；不崩溃；不"伪装成功" | lutools/AGENTS.md 约束 | P0 |
| TC-GPU-012 | GPU 运行时失败 → CPU fallback | 异常 | 启动后拔 GPU / 驱动崩溃模拟 | 提交一次 render，在 GPU 路径抛异常 | 自动切 CPU 并成功返回（或第一次报错后第二次走 CPU） | lutools/AGENTS.md："Auto must retain CPU fallback" | P0 |

---

### 1.7 图像编码器（JPEG/PNG）

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-ENC-001 | JPEG 保存 quality=95 | 功能 | 非空 ImageData | `ImageEncoder::saveJPEG(img, out.jpg, 95)` | 输出文件非空；用 stbi/jpeg 读回宽高一致 | 新增 | P0 |
| TC-ENC-002 | JPEG quality=1 / 100 边界 | 边界 | 同上 | quality=1 和 100 | 都成功；100 文件明显大于 1 | 新增 | P1 |
| TC-ENC-003 | PNG 保存 8-bit | 功能 | 同上 | savePNG(img, out.png) | 无损；读回像素与原 ImageData 逐像素相等 | 新增 | P0 |
| TC-ENC-004 | 空 ImageData 保存 | 异常 | width=0 或 pixels 空 | saveJPEG / savePNG | 返回 ErrorCode::InvalidArgument；不产生 0 字节文件 | 新增 | P1 |
| TC-ENC-005 | 只读目录 | 异常 | 只读目录 | saveJPEG 到只读目录 | 返回 IOError；不崩溃 | 新增 | P1 |

---

### 1.8 CLI 工具

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-CLI-001 | 基本管线 RAW → LUT → JPEG | 功能 | 构建好的 `lutools/build/cli/sony2fuji` + RAW + 65-grid LUT | `./sony2fuji -i in.ARW -l eterna.cube -o out.jpg` | exit code=0；out.jpg 非空；分辨率与 RAW 一致 | 新增 | P0 |
| TC-CLI-002 | 无 LUT（中性渲染） | 功能 | 同上，去掉 `-l` 参数 | 同上 | 成功；输出中性无胶片色调 | 新增 | P0 |
| TC-CLI-003 | 调整 exposure / temperature / tint / contrast | 功能 | 同上 | 传 `-e +1.3 -t 5600 -T -30 -c 0.2` | exit=0；与 Core C++ API 调用相同输入输出一致 | 新增 | P1 |
| TC-CLI-004 | 输出 PNG | 功能 | 同上 | `-o out.png` | exit=0；PNG 无损读回一致 | 新增 | P0 |
| TC-CLI-005 | 输入/输出同文件 | 异常 | 同上 | `-i file.jpg -o file.jpg` | exit != 0；不覆盖原文件 | 新增 | P0 |
| TC-CLI-006 | 缺 RAW | 异常 | 同上 | 不带 `-i` | exit != 0；打印 usage | 新增 | P1 |

---

### 1.9 C ABI 稳定性

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-ABI-001 | session_create / destroy 生命周期 | 功能 | libsony2fuji_core.so/dylib/dll 可用 | C 代码 create / process / destroy 各一次 | 无泄漏（ASan clean）；destroy 后再调 process 返回 INVALID_ARGUMENT | core_tests.cpp | P0 |
| TC-ABI-002 | request struct size 版本兼容 | 功能 | 同上 | 用 sizeof(request) ≠ struct_size 的旧版本二进制调用 | 返回 INVALID_ARGUMENT 不崩溃 | 新增 | P0 |
| TC-ABI-003 | 空 buffer 安全释放 | 异常 | 同上 | `sony2fuji_release_buffer` 对零初始化 buffer | 安全 no-op；不崩溃 | 新增 | P1 |
| TC-ABI-004 | status_message 覆盖所有 status_code | 功能 | 同上 | 对 0..N 每个 status_code 调 status_message | 返回非空字符串；不含未初始化内存 | 新增 | P1 |
| TC-ABI-005 | FFI 头文件无未定义宏交叉包含 | 功能 | 新增 .c 编译单元只 include sony2fuji_c.h | gcc/clang -Werror 编译 | 无 warning / 无宏冲突 | 新增 | P1 |

---

## 2. 跨平台共享层 ExportMetadata

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-EM-001 | 复制 EXIF / TIFF / GPS / 保留原始 MakerNote 排除 | 功能 | macOS + iOS 共享层 | `ExportMetadata.properties(from: width: height: orientation:)` | 返回字典中 EXIF/TIFF/GPS 存在；MakerNote / CFA 等已剔除；PixelXDim/PixelYDim 已改 | ExportMetadataTests.swift | P0 |
| TC-EM-002 | preserve() 另存为不覆盖原 RAW | 功能 | macOS 端 | `preserve(from: original.ARW, in: rendered.jpg)` | original.ARW 的 inode / mtime 不变 | 新增 | P0 |
| TC-EM-003 | 同文件保护 | 异常 | 同上 | `preserve(from: url, in: url)` | 抛出 `Failure.sameFile` | ExportMetadata.swift line 64 | P0 |
| TC-EM-004 | PNG 输出 EXIF 显式编码 | 功能 | PNG 输出路径 | preserve 到 out.png | PNG 文件中有 EXIF chunk（通过 PNG chunk 解析确认） | ExportMetadata.swift line 79-83 | P1 |
| TC-EM-005 | JPEG 输出通过 CopyImageSource + typed tag 保留 EXIF rational 精度 | 功能 | JPEG 输出路径 | preserve 到 out.jpg | EXIF rational 字段（曝光时间、光圈等）保持 rational 类型，不被转成 int 字符串 | ExportMetadata.swift line 97-100 | P1 |
| TC-EM-006 | 输出 Software tag = "RawLab" | 功能 | 同上 | 保存后读 TIFF:Software | 值为 "RawLab" | 新增 | P1 |
| TC-EM-007 | 不可读源文件 | 异常 | 权限剥夺后源文件 | properties(from:) | 抛出 unreadableSource | 新增 | P1 |
| TC-EM-008 | 跨平台契约一致（Swift vs C# vs Kotlin） | 跨模块 | 三端各自 `ExportMetadata` | 同一张 RAW 各端各导出 JPEG → 解析 EXIF 字段 | 关键字段一致：Make / Model / DateTime / Lens / Orientation / GPS | 新增 | P0 |

---

## 3. macOS 客户端

### 3.1 文件浏览与图库

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-MAC-001 | 文件树按需加载缩略图 | 功能 | 含多张 RAW 的文件夹 | 打开文件夹 → 滚动 → 观察缩略图加载 | 仅可见 RAW 解码缩略图；滚动到新区域才触发新解码 | FileBrowserTests.swift | P0 |
| TC-MAC-002 | 缩略图用内嵌预览，不解码全 RAW | 功能 | 同上 | 打开 Activity Monitor 观察 CPU/内存 | 仅调 LibRaw unpack_thumb，不调 dcraw_process（可用 sample 进程验证） | 新增 | P0 |
| TC-MAC-003 | 200–300pt 可折叠左侧文件树 | 功能 | 窗口 ≥ 950×620 | 拖动分隔条 + 点击折叠按钮 | 宽度在区间内；折叠后缩略图区自动填满；展开后位置保持 | 新增 | P1 |
| TC-MAC-004 | RAW 扩展名识别（9 种） | 功能 | 文件夹包含 ARW/SRF/DNG/NEF/CR2/CR3/RAF/RW2/ORF | 扫文件树 | 9 种全部出现；非 RAW 隐藏 | 新增 | P1 |
| TC-MAC-005 | 空文件夹 | 边界 | 空文件夹 | 打开 | 文件树显示空状态；程序不崩溃 | 新增 | P2 |
| TC-MAC-006 | 海量 RAW（≥ 500 张） | 边界 | 500+ RAW 目录 | 打开并滚动到底部 | 首次加载 < 2s；滚动不丢缩略图；内存 ≤ 200MB | 新增 | P1 |

### 3.2 照片画布

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-MAC-100 | 中性 vs LUT 结果并排对比 | 功能 | 已打开 RAW + 已选胶片 | 点击 toolbar 对比切换 | 左 neutral / 右 result；共享 zoom/pan；左下角 overlay 标签 | 新增 | P0 |
| TC-MAC-101 | Fit 模式 | 功能 | 同上 | zoom 菜单 → Fit | 图像在画布内完整显示；缩放比例自动计算 | 新增 | P0 |
| TC-MAC-102 | 实际像素模式 | 边界 | 同上 | zoom 菜单 → 实际像素 | 显示 100% 原始像素（考虑 display scale） | 新增 | P0 |
| TC-MAC-103 | 缩放 0.1× – 16× 边界 | 边界 | 同上 | 多次 zoom in / out | magnification 被 clamp 在 [0.1, 16] 区间；pan 始终有效 | 新增 | P1 |
| TC-MAC-104 | 照片调整不覆盖原始 RAW | 功能 | 导出路径与 RAW 不同 | 导出 JPEG | 原 RAW inode/mtime 不变 | smoke.sh | P0 |

### 3.3 调整 Dock

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-MAC-200 | 9 个圆形工具 + 胶片工具行 | 功能 | 已打开 RAW | 观察底部 Dock | 10 个工具（film / exposure / highlights / shadows / contrast / toneCurve / saturation / temperature / tint / sharpening） | 新增 | P0 |
| TC-MAC-201 | 圆环进度指示器 | 功能 | 同上 | 拖动 exposure slider 远离默认值 | 圆环正向顺时针 / 反向逆时针；回到默认值圆环消失 | 新增 | P1 |
| TC-MAC-202 | 数值输入框 + 滑块双向同步 | 功能 | 同上 | 在数值框输入 +2.3 EV → 观察滑块位置 | 滑块跳到对应位置；反之亦然 | 新增 | P0 |
| TC-MAC-203 | reset 按钮 → 该参数回到默认 | 功能 | 同上 | 改某个值 → 点 reset | 该参数回到 Camera WB 或 scene 默认值；其他参数不变 | 新增 | P0 |
| TC-MAC-204 | 色温倒数 Kelvin 刻度 | 边界 | 同上 | 拖动色温 slider | UI 位置与 1/K 倒数关系对应；数值输入仍显示 K 值 | Adjustments.swift line 169 / reciprocalScale | P0 |
| TC-MAC-205 | 色调滑块 -150 / +150 边界 | 边界 | 同上 | 拖到端点并越界松手 | 被 clamp 到 [-150, 150] | 新增 | P1 |
| TC-MAC-206 | 胶片选择切换 → 下方编辑器变为横向标签选择 | 功能 | 点击 film 工具 | 编辑器区域替换为胶片标签横向滚动；选择后数值编辑器消失 | 新增 | P0 |
| TC-MAC-207 | Dock 可折叠 + 跨照片保持高度 | 功能 | 折叠后切换照片 → 再展开 | 高度恢复到折叠前；底部 tool 选中状态保持 | 新增 | P1 |
| TC-MAC-208 | 参数分组 reset 菜单（Film / Input / Tone / Color / Detail） | 功能 | 点击 Dock header reset 菜单 | 选 "Tone" | highlights/shadows/contrast/toneCurve 四个参数同时回到默认 | AdjustmentGroup enum | P1 |

### 3.4 胶片模拟

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-MAC-300 | 11 种 65-grid 胶片 LUT 全部可加载 | 功能 | flog-2-new/ 资源齐全 | 依次点击 Acros / Astia / Classic-Chrome / Classic-Neg / Eterna / Eterna-BB / Pro-Neg.Std / Provia / Reala-Ace / Velvia | 每次切换 1s 内完成；渲染成功；无错误提示 | 新增 | P0 |
| TC-MAC-301 | Acros（黑白）输出确实为灰度 | 功能 | 同上 | 选 Acros → 观察 result | result 对比度高、饱和度极低；直方图三通道高度近似 | 新增 | P0 |
| TC-MAC-302 | Eterna vs Velvia 风格差异 | 功能 | 同上 | 同场景分别选 Eterna 和 Velvia | Velvia 饱和度明显高于 Eterna；暗部更硬 | 新增 | P0 |
| TC-MAC-303 | 自定义 .cube 导入 | 功能 | 存在外部 .cube 文件 | File → 导入 LUT → 选外部文件 | Dock 追加该胶片标签；选中后正常渲染 | EditorModel.importLUT() | P1 |
| TC-MAC-304 | 损坏 / 非 F-Log2 LUT 导入 | 异常 | 非 F-Log2 .cube | 同上 | 导入后渲染报错 "不支持此 LUT…需声明 F-Gamut/F-Log2 输入" | Engine.swift 错误文案 | P0 |

### 3.5 直方图

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-MAC-400 | RGB 三通道 + 裁切遮罩 | 功能 | 已渲染 result | 观察 histogram | 红/绿/蓝三栈；右上角裁切区域用诊断色标记 | HistogramTests.swift | P0 |
| TC-MAC-401 | 跨照片保持折叠状态 | 功能 | 折叠后切换到另一张 RAW | 直方图仍折叠 | 新增 | P1 |
| TC-MAC-402 | 直方图基于当前 sRGB 输出 | 边界 | 同上 | 导出后外部工具读直方图分布 | 两者近似一致（±5%） | DESIGN.md 约束 | P2 |

### 3.6 渲染调度

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-MAC-500 | 拖动 slider → 立即 1000px 预览 | 功能 | 已打开 RAW | 快速拖动 exposure slider 10 次 | 每次松开 50ms 内出现交互预览（1000px edge） | RenderSchedulingTests.swift | P0 |
| TC-MAC-501 | 停止拖动 150ms → 精确 2000px → 可选原尺寸 | 功能 | 同上 | 停止拖动 → 等待 | 150ms debounce 后换出精确 2000px；fullResolution=true 时换成原尺寸 | EditorModel.schedule() | P0 |
| TC-MAC-502 | 旧任务自动丢弃 | 边界 | 同上 | 1s 内改 20 次参数 | 仅最新一次结果被 publish；队列不累积；最终发布的参数对得上 | RenderSchedulingTests.swift | P0 |
| TC-MAC-503 | 原尺寸 GPU 渲染不阻塞交互 | 功能 | 同上 | fullResolution=true → GPU FORCE | 渲染期间 UI 仍可拖动 slider、切换文件 | 新增 | P1 |

### 3.7 导出与打包

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-MAC-600 | JPEG 导出 | 功能 | 已渲染 result | toolbar → 导出 → 选路径 | 文件生成；文件名含 "-FILMNAME.jpg"；EXIF 完整 | 新增 | P0 |
| TC-MAC-601 | PNG 导出 | 功能 | 同上 | 同上选 PNG | 无损；EXIF 通过 eXIf chunk 保存 | 新增 | P0 |
| TC-MAC-602 | 不覆盖原 RAW | 功能 | 导出路径 = RAW 同目录但文件名不同 | 导出 | 原 RAW mtime 不变 | smoke.sh | P0 |
| TC-MAC-603 | bundle.sh 完整性：架构 + rpath + 签名 | 功能 | 构建好的 .app | bash RawLabMac/tests/bundle.sh | 全部 PASS：Mach-O 架构匹配；所有 @rpath 依赖存在；codesign --verify 通过 | bundle.sh | P0 |
| TC-MAC-604 | macOS 最低版本约束 | 边界 | 同上 | MACOSX_DEPLOYMENT_TARGET=14.0 bundle.sh | PASS；Info.plist LSMinimumSystemVersion = 14.0 | bundle.sh | P1 |

---

## 4. Windows 客户端

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-WIN-001 | MainWindow 启动 → WPF 布局完整 | 功能 | Windows 11 + D3D11 | 双击 exe | 左文件树 + 中画布 + 右/下调整 + 胶片选择全部可见 | 新增 | P0 |
| TC-WIN-002 | Native.cs P/Invoke 桥接 | 功能 | 同上 | 从 UI 触发一次 render | 成功；Native 指针生命周期正确；无 AccessViolationException | 新增 | P0 |
| TC-WIN-003 | 缩略图原生 C++ 加速（thumbnail.cpp） | 功能 | 同上 | 打开含 50 张 RAW 的目录 | 首次扫描 < 2s；全部缩略图正确 | 新增 | P1 |
| TC-WIN-004 | D3D11 GPU 渲染（Force 模式） | 功能 | 同上 | 调整 slider → 渲染 | 帧时间显著快于 CPU（同输入） | 新增 | P0 |
| TC-WIN-005 | ComparisonChecks 回归：对比度/饱和度/高光/阴影 | 回归 | RawLabWindows.Tests.csproj | `dotnet test` | 全部通过 | ComparisonChecks.cs | P0 |
| TC-WIN-006 | ExportMetadataChecks 回归 | 回归 | 同上 | 同上 | 全部通过 | ExportMetadataChecks.cs | P0 |
| TC-WIN-007 | 胶片模拟 11 种全部可见 | 功能 | 启动后 FilmDock | 依次点击 | 渲染成功；风格差异可观测 | 新增 | P0 |
| TC-WIN-008 | 导出 JPEG / PNG + EXIF 保留 | 功能 | 同上 | 导出 → exiftool 读 | 关键字段 Make/Model/Lens/DateTime/GPS 存在；RAW 不被覆盖 | 新增 | P0 |
| TC-WIN-009 | 不覆盖原 RAW | 功能 | 导出路径故意设为 RAW 目录下同名 | 保存 | 拒绝覆盖或自动改名；原 RAW 不变 | 新增 | P0 |
| TC-WIN-010 | 深色主题 | 功能 | Windows 深色系统主题 | 启动 | 窗口背景深灰；黄色选中色在深色背景上清晰；无对比度不足 | 新增 | P1 |

---

## 5. iOS 客户端

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-IOS-001 | PhotoKit 相册访问授权流程 | 功能 | iOS Simulator 或真机 | 首次启动 → 授权 | 正确弹 Photos Usage Description；授权后可扫到相册 | iOS Info.plist 声明 | P0 |
| TC-IOS-002 | ContentView 主布局 | 功能 | 同上 | 打开 RAW | 顶部导航 + 中部照片 + 底部调整 + 胶片 Dock | 新增 | P0 |
| TC-IOS-003 | Sony2FujiProcessor Swift → ObjC++ → LibRaw 桥 | 功能 | 同上 | 选一张 ARW | 成功渲染；metadata 显示相机型号 | ProcessorExportTests.swift | P0 |
| TC-IOS-004 | Metal GPU 渲染（iOS） | 功能 | iOS 16+ + Metal | 调整 | 帧时间稳定 30fps+ | 新增 | P0 |
| TC-IOS-005 | EditorPresentationTests | 回归 | XCTest | `xcodebuild test -scheme RawLab` | 全部通过 | EditorPresentationTests.swift | P0 |
| TC-IOS-006 | ProcessorExportTests | 回归 | 同上 | 同上 | 全部通过 | ProcessorExportTests.swift | P0 |
| TC-IOS-007 | BundledLUTTests | 回归 | 同上 | 同上 | 全部通过 | BundledLUTTests.c / presentation.sh | P0 |
| TC-IOS-008 | F-Log2 33-grid 内置 LUT 资源存在 | 功能 | App Bundle | LUTParser::loadLUT 读 Bundle 中 .cube | 4 个 ETERNA 系列加载成功 | x100vi-3d-lut-v100/ 资源 | P0 |
| TC-IOS-009 | 横向 / 纵向方向 | 边界 | 横屏 RAW | 旋转设备 | 画布宽高自适应；调整 Dock 位置不重叠 | 新增 | P1 |
| TC-IOS-010 | 内存压力警告下释放非必要资源 | 边界 | 连续切换 20 张 RAW | 不崩溃；收到 memory warning 后缩略图缓存清空 | 新增 | P1 |

---

## 6. Android 客户端

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-AND-001 | AlbumScreen 缩略图网格 | 功能 | Android 12+ 设备 | 授予相册权限 | 网格正确显示所有 RAW；点击进入 EditorScreen | AlbumScreenTest.kt | P0 |
| TC-AND-002 | EditorScreen 渲染管线 | 功能 | 同上 | 选 RAW → 调节胶片 | 成功渲染 GLES 加速；交互流畅 | EditorScreenTest.kt | P0 |
| TC-AND-003 | NativeProcessor JNI 桥接 | 功能 | 同上 | 触发一次 native 调用 | 不抛 UnsatisfiedLinkError；返回非空 buffer | NativeProcessorTest.kt | P0 |
| TC-AND-004 | GLES 3.x GPU 渲染 | 功能 | 同上 | 调整参数 → 渲染 | 帧率 ≥ 24fps；同输入与 CPU 输出 RMS 差 < 0.01 | 新增 | P0 |
| TC-AND-005 | RenderQueue 异步结果分发 | 功能 | 同上 | 1s 内改 5 次参数 | 仅最新结果 publish；无结果乱序 | 新增 | P1 |
| TC-AND-006 | WorkingCopy 临时文件清理 | 功能 | 导出一张后 | 退出 app | 临时文件目录空（或 ≤ 1 个） | WorkingCopyTest.kt | P1 |
| TC-AND-007 | PhotoStorage 沙盒隔离 | 功能 | 同上 | 卸载 app 后重装 | 前次的临时副本不存在 | PhotoStorageTest.kt | P1 |
| TC-AND-008 | 导出 EXIF 保留 | 功能 | 导出后用 EXIF 查看 App | Make/Model/Lens/DateTime/GPS 存在 | ExportMetadataInstrumentedTest.kt | P0 |
| TC-AND-009 | PhotoStorageTest / WorkingCopyTest 单元测试 | 回归 | `./gradlew test` | 执行 | 全部通过 | 对应 Test.kt | P0 |
| TC-AND-010 | gpu_smoke.cpp 冒烟 | 回归 | NDK + adb | `adb push` + 运行 | GPU 功能 OK | tests/gpu_smoke.cpp | P0 |
| TC-AND-011 | Android 相册权限被拒 | 异常 | 首次启动拒绝权限 | AlbumScreen 显示说明页；用户可稍后在 Settings 重授权 | 新增 | P1 |

---

## 7. 跨平台一致性验证

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-CROSS-001 | 同一 RAW + 同一 LUT + 同调整 → 四端输出数值一致 | 跨模块 | 构建 iOS/macOS/Android/Windows 四端 | 各端各自 render，同一 ROI RGB 均值 | 任意两端差 < 0.01 float | 新增 | P0 |
| TC-CROSS-002 | Adjustment 参数模型语义对齐（Swift Adjustments.swift ↔ C# Adjustments.cs ↔ Kotlin EditSettings.kt） | 跨模块 | 三端文件 | 核对所有字段名、默认值、slider 范围、符号约定 | 完全一致；"高光 UI +100% → Core -1" 约定三端都体现 | 新增 | P0 |
| TC-CROSS-003 | 色温 reciprocalScale 约定 | 跨模块 | 三端同上 | 核对色温 slider 刻度 | 三端都用倒数 K 刻度 | Adjustments.swift line 169 | P1 |
| TC-CROSS-004 | 胶片名显示映射一致 | 跨模块 | 三端 Bundle LUT 资源 | 选 Eterna / Eterna-BB 等 | 三端显示名称相同；LUT 文件路径解析规则相同 | Engine.swift Film.bundled() | P1 |

---

## 8. 性能与资源

| 用例编号 | 子功能模块 | 测试类别 | 前置条件 | 操作步骤 | 预期结果 | 关联已有测试 | 优先级 |
|---|---|---|---|---|---|---|---|
| TC-PERF-001 | 交互预览帧时间 | 功能 | macOS + RAW 中 40MP | 拖动 slider 期间 | ≥ 15fps（即每帧 < 67ms） | 新增 | P0 |
| TC-PERF-002 | 原尺寸全分辨率 GPU 渲染时间 | 功能 | 同上 | 停止拖动触发 exact | ≥ 5fps；不超过 CPU 同输入时间 | render_benchmark.cpp | P0 |
| TC-PERF-003 | 启动时间（cold） | 功能 | macOS Release build | 冷启动到渲染就绪 | < 3s | 新增 | P1 |
| TC-PERF-004 | 512KB 栈线程缩略图解码（不栈溢出） | 回归 | core_tests 夹具 | loadPreviewLuminance 在 512KB 栈 pthread | PASS | core_tests.cpp line 60-78 | P0 |
| TC-PERF-005 | 长时间交互（10min）无内存泄漏 | 边界 | Instruments/ASan 可用 | 持续拖动 slider + 切换照片 | 堆内存增量 ≤ 50MB；泄漏工具报告 0 new / 10 秒 | 新增 | P1 |
| TC-PERF-006 | RAW LUT 重复打开 → LUTParser::loadLUTCached 命中 | 功能 | 同 LUT 连续 load 3 次 | 观察时间 | 第二次之后耗时 < 第一次 × 0.1 | 新增 | P1 |
| TC-PERF-007 | 全分辨率 60MP RAW 内存占用 | 边界 | 60MP+ RAW | 全分辨率 render | 峰值内存 ≤ width×height×3×4×1.5 bytes（约 1.1GB） | 新增 | P1 |

---

## 9. 验收标准矩阵

### P0 阻塞清单（CI 必须全部通过）

| 用例编号 | 验收准则 |
|---|---|
| TC-RAW-001 | Sony ARW → Success；宽高正确；pixels 非空 |
| TC-RAW-003 | DJI DNG → Success |
| TC-RAW-006 | 内嵌预览宽高正确；flip 正确 |
| TC-RAW-007 | 不存在文件 → 返回错误码；进程不崩 |
| TC-RAW-008 | 损坏 RAW → 返回错误码；不产生部分输出 |
| TC-CS-005 | 相机矩阵 → F-Gamut 转换数值稳定非 NaN |
| TC-CS-008 | F-Log2 输出单调、正值区间正确 |
| TC-LUT-002 | 33-grid ETERNA LUT 加载 size_=33 |
| TC-LUT-003 | 11 种 65-grid 胶片 LUT 全部 size_=65 |
| TC-LUT-005 | 三线性插值端点精确等于 cube corner |
| TC-LUT-006 | 整图 LUT 应用无 NaN、输出域内 |
| TC-LUT-007 | 强度 0%/100% 语义正确 |
| TC-LUT-008 | 损坏 .cube 返回错误码 |
| TC-WB-001 | 拍摄时白平衡恢复灰场中性 |
| TC-WB-002 | DNG 双光源插值精确 |
| TC-WB-003/004 | 色温/色调端点稳定非 NaN |
| TC-WB-005 | WB 必须在 demosaic 前应用 |
| TC-EXP-001 | 三种曝光基准输出语义正确可区分 |
| TC-EXP-003 | +4EV 高光不品红 |
| TC-HL-001 | 饱和高光品红回归零 |
| TC-TONE-002 | 高光 UI +100% → Core -1 映射正确 |
| TC-GPU-001/003/004 | Metal / D3D11 / GLES 三后端各自 render 成功 |
| TC-GPU-005 | 三后端同一 ROI 均值差 < 0.01 |
| TC-GPU-006 | GPU vs CPU RMS 差 < 0.005 |
| TC-GPU-011 | GPU FORCE 但硬件不可用 → 返回明确错误不伪装成功 |
| TC-GPU-012 | GPU 运行时失败 → 自动切 CPU 成功返回 |
| TC-EM-001 | EXIF/TIFF/GPS 存在；MakerNote 排除 |
| TC-EM-002 | preserve() 不覆盖原 RAW |
| TC-EM-003 | 同文件保护生效 |
| TC-ABI-001 | session create/destroy 无泄漏 |
| TC-ABI-002 | struct_size 不匹配返回 INVALID_ARGUMENT |
| TC-MAC-603 | bundle.sh 全部 PASS |
| TC-MAC-600/601 | JPEG / PNG 导出成功 + EXIF 完整 |
| TC-WIN-005 | ComparisonChecks 全部通过 |
| TC-WIN-006 | ExportMetadataChecks 全部通过 |
| TC-IOS-003 | iOS 桥接渲染成功 |
| TC-IOS-005/006/007 | 三份 XCTest 套件全通过 |
| TC-AND-001/002 | AlbumScreen + EditorScreen 流程通 |
| TC-AND-004 | GLES 渲染帧率 ≥ 24fps |
| TC-AND-008 | EXIF 保留 |
| TC-AND-010 | gpu_smoke.cpp 冒烟 OK |
| TC-CROSS-001 | 四端同输入同 ROI 均值差 < 0.01 |
| TC-CROSS-002 | 三端 Adjustment 模型对齐 |
| TC-PERF-001 | 交互预览 ≥ 15fps |
| TC-PERF-004 | 512KB 栈线程缩略图解码 PASS |

### 完成度标准

- **P0 全部通过** → 可进入 Beta
- **P0 通过 + P1 通过 ≥ 80%** → 可发布 RC
- **P0 + P1 全部通过 + P2 通过 ≥ 50%** → 正式 Release

---

## 10. Bug 记录模板

> （执行后填写；禁止预填"通过率 100%"或"发现 Bug=0"这类无证据结论）

| Bug ID | 发现用例 | 严重度 | 描述 | 复现步骤 | 预期行为 | 实际行为 | 根因 | 状态 | 修复 commit |
|---|---|---|---|---|---|---|---|---|---|
| BUG-001 | TC-XXX-YYY | P0/P1/P2 | 简述 | 具体步骤 | 预期结果 | 实际现象 | 初步分析 | NEW / IN_PROGRESS / FIXED / WONTFIX | - |

---

## 附录：测试执行环境清单

| 环境 | 必需组件 | 说明 |
|---|---|---|
| lutools C++ core | CMake 3.20+、libraw 开发库、libjpeg-turbo、Little-CMS、JasPer、OpenMP、stb | `bash lutools/test.sh` 或 `ctest --test-dir build-macos` |
| macOS | Xcode 16+、Metal framework、AppKit/SwiftUI SDK ≥ 26.0 | `bash RawLabMac/build.sh && bash RawLabMac/tests/*.sh` |
| iOS | Xcode + iOS Simulator（iPhone 15+） | `xcodebuild test -scheme RawLab` |
| Android | Android Studio + NDK r27 + GLES 3.x 设备 / Emulator | `./gradlew connectedAndroidTest` + `adb push gpu_smoke` |
| Windows | Visual Studio 2022 + D3D11 + .NET 8 | `dotnet test RawLabWindows.Tests/RawLabWindows.Tests.csproj` |
| RAW 夹具 | Sony ARW、DJI DNG、Canon CR2/CR3、Nikon NEF、Fujifilm RAF、Panasonic RW2、Olympus ORF | 环境变量 `RAWLAB_TEST_RAW` 指向本地夹具路径 |
| LUT 资源 | `lutools/F-Log2/`（33-grid × 4）、`lutools/flog-2-new/`（65-grid × 11） | 构建时自动 Bundle |

---

*文档完。所有用例可直接在对应平台执行；Bug 记录表留空，待执行后填写。*
