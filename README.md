# RapidRAW - 专业RAW照片编辑器

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![MinSDK](https://img.shields.io/badge/MinSDK-26-blue.svg)](https://developer.android.com/about/versions/marshmallow)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-36-blue.svg)](https://developer.android.com/about/versions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-2026.01-orange.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Commercial-red.svg)](#license)

**RapidRAW** 是一款专为Android平台打造的专业RAW照片编辑应用，提供完整的59项摄影后期处理功能，媲美桌面级专业软件（Lightroom/Darktable）。

---

## ✨ 核心特性

### 📸 RAW解码引擎
- 支持 **11+ RAW格式**：DNG/CR2/CR3/NEF/ARW/RAF/ORF/RW2/PEF/SRW
- 基于 **LibRaw 0.21.2** 专业解码库
- **100MP超大文件支持**：分块处理 + 内存保护
- **GPU加速管线**：Vulkan Compute Shader实时预览

### 🎨 专业调色系统
| 类别 | 功能项 |
|------|--------|
| **基础调色** | 曝光/对比度/高光/阴影/白色/黑色/亮度 (7项) |
| **颜色调整** | 色温/色调/饱和度/自然饱和度/HSL八色混合器 (13项) |
| **曲线系统** | Luma/R/G/B四通道曲线 + Cubic Hermite插值 |
| **色彩分级** | 阴影/中间调/高光三区色彩轮 + 混合平衡控制 |
| **色调映射** | AgX胶片式色调映射 / Basic线性映射 |
| **色彩校准** | RGB主色校正 +阴影色调微调 |

### 🔧 细节与效果
- **锐化/清晰度/结构/中心**：Unsharp Mask + 局部对比度
- **降噪**：亮度降噪/色彩降噪/BM3D高级降噪
- **去雾/色差校正/晕影**：专业级算法
- **胶片颗粒/LUT**：内置 Kodak Portra/Fuji Velvia 等5款经典胶片预设
- **发光/光晕/镜头光斑**：Flare.wgsl物理模拟

### 🖼️ 几何变换
- **裁剪**：自由裁剪 + 预设比例（1:1/4:3/16:9等）
- **旋转/翻转**：90°旋转 + 水平/垂直翻转
- **拉直工具**：Hough变换自动检测水平线
- **透视校正**：四点网格畸变校正
- **镜头校正**：内置Lensfun数据库（Canon/Nikon/Sony/Fuji/Sigma）

### 🎭 蒙版系统
- **画笔蒙版**：手势绘制 + 流量/硬度控制
- **线性/径向渐变蒙版**：参数化生成
- **AI智能蒙版**：主体/天空/前景自动识别（基于ONNX Runtime）
- **蒙版叠加**：多蒙版混合/反转/透明度控制

### 📚 图库管理
- **文件夹树浏览**：Android SAF + MediaStore
- **缩略图生成**：并行解码 + LRU缓存
- **评分/颜色标签**：1-5星 + 5色标签系统
- **智能筛选**：仅RAW/收藏/按日期/按相机
- **虚拟副本**：非破坏性多版本编辑
- **废片筛选**：重复/模糊检测

### 📤 导出系统
- **格式支持**：JPEG/PNG/TIFF/HEIF/AVIF/EXR/HDR
- **批量导出**：WorkManager后台队列
- **水印系统**：文字/图像水印 + 位置/透明度控制
- **元数据写入**：完整EXIF保留 + IPTC编辑
- **文件名模板**：{日期}_{相机}_{序号} 等变量

### 🤖 AI能力
- **智能优化**：一键分析场景并推荐调色参数
- **AI消除**：Inpainting修复画面瑕疵
- **AI去噪**：深度学习降噪（NIND模型）
- **AI遮罩生成**：SAM模型主体分割
- **场景识别**：人像/风景/夜景/微距等8类自动分类

### 🔄 云端与协作
- **配方分享**：导出调整参数为 .rrrecipe 文件
- **LUT市场**：社区LUT预设浏览与下载
- **云端同步**：Firebase/Firestore配方同步（可选）

---

## 🚀 快速开始

### 系统要求
- **Android版本**：6.0+ (API 26+)
- **内存建议**：4GB RAM（处理24MP RAW）
- **存储空间**：100MB应用 + 工作缓存
- **推荐设备**：Snapdragon 8 Gen 2+ / GPU Vulkan支持

### 安装方式

#### 1. Google Play Store（推荐）
```bash
# 搜索 "RapidRAW" 或扫描二维码
https://play.google.com/store/apps/details?id=com.rapidraw
```

#### 2. GitHub Release APK
```bash
# 下载最新release版本
https://github.com/your-org/rapidraw-android/releases

# 安装命令
adb install RapidRAW-v1.7.0-prod-release.apk
```

#### 3. 本地构建
```bash
# 克隆仓库
git clone https://github.com/your-org/rapidraw-android.git
cd rapidraw-android

# 构建生产版本
./gradlew assembleProdRelease

# 输出位置
app/build/outputs/apk/prod/release/RapidRAW-prod-release.apk
```

---

## 🏗️ 开发环境配置

### 前置依赖
| 工具 | 版本要求 | 安装命令 |
|------|----------|----------|
| Android Studio | 2024.1+ | [官网下载](https://developer.android.com/studio) |
| JDK | 17+ | `brew install openjdk@17` |
| Gradle | 8.6+ | `brew install gradle` |
| NDK | 26.3.11579264 | Android Studio SDK Manager |
| CMake | 3.22.1+ | Android Studio SDK Manager |

### 项目结构
```
RapidRAW-Android/
├── app/                     # 主应用模块
│   ├── src/main/
│   │   ├── java/            # Kotlin源码（62 @Composable）
│   │   ├── cpp/             # C++ Native层（LibRaw/Vulkan）
│   │   ├── res/             # 资源文件（中/英/日/韩）
│   │   └── assets/          # 内置资源（LUT/Lensfun数据库）
│   ├── build.gradle.kts     # 构建配置
│   └── proguard-rules.pro   # ProGuard规则
├── gradle/
│   ├── libs.versions.toml   # 版本目录
│   └── wrapper/             # Gradle Wrapper
├── build.gradle.kts         # 根构建脚本
├── settings.gradle.kts      # 项目设置
├── detekt-config.yml        # Detekt代码规范
└── .github/workflows/       # CI/CD流程
```

### 构建变体
| 变体 | ApplicationId | 用途 | 构建命令 |
|------|---------------|------|----------|
| **dev** | com.rapidraw.dev | 开发调试 | `./gradlew assembleDevDebug` |
| **staging** | com.rapidraw.staging | 预发布测试 | `./gradlew assembleStagingRelease` |
| **prod** | com.rapidraw | 生产发布 | `./gradlew assembleProdRelease` |

### 关键Gradle任务
```bash
# Lint检查
./gradlew lintProdRelease

# 单元测试
./gradlew testProdReleaseUnitTest

# 代码覆盖率报告
./gradlew koverHtmlReportProdRelease

# Detekt静态分析
./gradlew detekt

# Benchmark性能测试
./gradlew assembleProdRelease -PincludeX86_64=true
adb install app/build/outputs/apk/prod/release/*.apk
adb shell am instrument -w com.rapidraw/androidx.benchmark.junit4.AndroidBenchmarkRunner

# 清理构建
./gradlew clean
```

---

## 🧪 测试策略

### 测试覆盖率目标
- **单元测试**：60%+（核心业务逻辑）
- **UI测试**：关键Composable覆盖（EditorScreen/LibraryScreen）
- **集成测试**：完整编辑流程E2E验证
- **Instrumentation测试**：真实设备功能验证

### 运行测试
```bash
# 所有单元测试
./gradlew test

# 特定测试类
./gradlew test --tests "com.rapidraw.core.ImageProcessorTest"

# Android Instrumentation测试（需要设备）
./gradlew connectedAndroidTest

# Compose UI测试
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rapidraw.ui.ComposeSmokeTest
```

---

## 📊 性能指标

### 启动性能
| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| 冷启动时间 | < 2s | `adb shell am start -W com.rapidraw/.MainActivity` |
| 热启动时间 | < 500ms | Activity已驻留内存 |
| 首帧渲染 | < 300ms | `onCreate` → `setContent`完成 |

### 处理性能（Snapdragon 8 Gen 3）
| 操作 | 24MP RAW | 测试方法 |
|------|-----------|----------|
| RAW解码 | ~500ms | `ImageProcessor.decodeRaw()` |
| 预览GPU处理 (1080p) | ~15ms | `GpuPipeline.processPreview()` |
| 全分辨率导出 | ~200ms | `ImageProcessor.processFullResolution()` |
| 缩略图生成 | ~80ms/张 | `ThumbnailGenerator.generate()` |

### 内存占用
| 场景 | 内存峰值 | 监控方法 |
|------|-----------|----------|
| 图库浏览 | ~150MB | Android Studio Memory Profiler |
| 编辑器 (24MP) | ~400MB | Bitmap + GPU纹理 |
| 批量导出 (10张) | ~600MB | WorkManager后台任务 |

---

## 🛠️ 技术架构

### 核心技术栈
- **UI层**：Jetpack Compose + Material3 + OPPO摄影交互规范
- **业务层**：MVVM + ViewModel + StateFlow + Repository
- **数据层**：Room数据库 + ContentResolver + SAF
- **Native层**：LibRaw (C++) + Vulkan Compute Shader
- **AI推理**：TensorFlow Lite + ONNX Runtime + NNAPI Delegate
- **依赖注入**：Hilt（计划引入）
- **异步处理**：Kotlin Coroutines + Flow + WorkManager

### GPU管线流程
```
Android Bitmap (ARGB_8888)
    ↓
[Rust JNI] processPreview()
    ↓
解析 Adjustments JSON → AllAdjustments struct
    ↓
缩放原始图像到预览尺寸 (downscale_f32_image)
    ↓
上传 f32 像素到 GPU 纹理 (Rgba16Float)
    ↓
生成模糊纹理 (sharpness/clarity/structure)
    ↓
生成光斑纹理 (flare.wgsl)
    ↓
生成蒙版图集 (mask_textures[0..8])
    ↓
运行主 Compute Pipeline (shader.wgsl::main_compute())
    ├─ 线性曝光 → 白平衡 → 色调调整
    ├─ HSL → 色彩分级 → 色彩校准
    ├─ 局部对比度 → 锐化 → 降噪
    ├─ 色差 → 去雾 → 发光/光晕
    ├─ 晕影 → 胶片颗粒 → LUT
    ├─ 曲线 → 色调映射 → sRGB转换
    └─ 蒙版叠加 → 裁剪警告
    ↓
读取输出纹理 → 填充 Android Bitmap (零拷贝)
```

---

## 🔐 安全与隐私

### 数据安全
- **本地存储**：所有编辑数据仅存储在用户设备
- **无云端强制同步**：云端功能完全可选
- **权限最小化**：仅请求必要存储/相机权限
- **ProGuard加固**：Release构建移除调试日志 + 代码混淆

### 应用完整性
- **APK签名校验**：运行时验证签名指纹
- **防二次打包**：SecurityProvider签名验证
- **SSL证书锁定**：OkHttp CertificatePinner（计划添加）
- **调试检测**：isDebuggerConnected() + Root检测

---

## 🌍 国际化支持

### 已支持语言
- 🇨🇳 **中文（简体）**：完整翻译
- 🇺🇸 **English**：完整翻译
- 🇯🇵 **日本語**：完整翻译
- 🇰🇷 **한국어**：完整翻译

### 贡献翻译
```bash
# 翻译文件位置
app/src/main/res/values-{lang}/strings.xml

# 新增语言步骤
1. 复制 values/strings.xml 到 values-{lang}/strings.xml
2. 翻译所有 <string> 标签内容
3. 更新 locale_config.xml 添加新语言
4. 提交 PR 并标注 "Translation: {lang}"
```

---

## 🤝 贡献指南

详见 [CONTRIBUTING.md](CONTRIBUTING.md)

### 快速贡献流程
1. Fork → Clone → 创建功能分支
2. 编写代码 + 补充单元测试
3. 运行 `./gradlew lint detekt test` 确保质量
4. 提交 PR → 等待Code Review
5. 合入主分支 → 自动触发CI构建

---

## 📝 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)

---

## 📄 许可协议

**RapidRAW** 为商业软件，源代码仅供学习参考。

- ✅ 个人学习与研究使用
- ✅ 学术教学与演示
- ❌ 商业分发与二次开发
- ❌ 去除版权声明与签名

详见 [LICENSE](LICENSE) 文件。

---

## 🆘 技术支持

### 常见问题
| 问题 | 解决方案 |
|------|----------|
| RAW解码失败 | 检查NDK版本是否正确安装 |
| GPU处理崩溃 | 确认设备支持Vulkan (Adreno 630+) |
| 导出队列卡住 | 检查WorkManager权限 + 电池优化豁免 |
| 内存溢出OOM | 降低预览分辨率或启用分块处理 |

### 反馈渠道
- 📧 **Email**: support@rapidraw.app
- 🐛 **Bug报告**: [GitHub Issues](https://github.com/your-org/rapidraw-android/issues)
- 💬 **社区讨论**: [GitHub Discussions](https://github.com/your-org/rapidraw-android/discussions)
- 📚 **帮助中心**: 应用内"帮助与反馈"页面

---

## 🎯 未来规划

### v1.8.0（2026 Q3）
- ✅ 多图层编辑系统（LayerStack完整实现）
- ✅ 全景拼接（PanoramaStitcher）
- ✅ HDR合并（HdrMergeProcessor）
- ✅ 拼贴画模板（CollageMaker）

### v2.0.0（2026 Q4）
- ✅ 视频编辑支持（基础调色）
- ✅ 云端资产管理（DAM完整实现）
- ✅ 协作编辑（配方共享与版本控制）
- ✅ AI模型商店（自定义模型下载）

---

## 📊 项目统计

- **总代码行数**：~15,000 行 Kotlin + ~8,000 行 C++
- **Composable函数**：62 个
- **ViewModel数量**：12 个
- **单元测试**：45 个测试文件
- **构建时间**：~3分钟（首次）/ ~30秒（增量）
- **APK体积**：~25MB (prod release)

---

**Made with ❤️ by RapidRAW Team**

*致敬专业摄影师与开源社区的贡献者*