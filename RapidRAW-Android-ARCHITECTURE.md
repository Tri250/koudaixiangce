# RapidRAW → Android 纯原生移植架构设计文档

> 基于 RapidRAW v1.4.12（Rust + Tauri + React + WGPU/WGSL）完整源码深度分析
> 目标平台：Android（API 26+），纯原生 Kotlin/Jetpack Compose + Rust NDK + WGPU/Vulkan
> 设计理念：OPPO 摄影用户交互体验 + 哈苏橙主题色 + 功能完整 + 无模拟/空实现/简化逻辑

---

## 一、项目概述与特性映射

### 1.1 原项目架构

| 层级 | 原始技术栈 | 核心职责 |
|------|-----------|---------|
| 前端 UI | React 19 + TypeScript + TailwindCSS + Framer Motion + Lucide Icons | 编辑器面板、图库浏览、滑块控件、模态框、预设面板 |
| IPC 通信 | Tauri 2.x invoke/listen | 前端 ↔ 后端命令式通信（86 个 `#[tauri::command]`） |
| 后端核心 | Rust（rawler, wgpu, rayon, image, ort, lensfun_db） | RAW 解码、GPU 处理、AI 推理、文件管理、EXIF、镜头校正 |
| GPU 管线 | WGPU + WGSL Compute Shader | 32-bit 线性空间全管线：曝光→色调映射→HSL→曲线→蒙版→输出 |
| 数据存储 | `.rrdata` JSON sidecar 文件 | 非破坏性编辑元数据 |

### 1.2 完整特性清单与 Android 移植映射

| # | 特性 | 原始实现 | Android 移植方案 | 优先级 |
|---|------|---------|-----------------|--------|
| **核心引擎** | | | | |
| 1 | RAW 解码 | rawler（Rust crate） | 保留 rawler，编译为 `cdylib` 供 JNI 调用 | P0 |
| 2 | GPU 加速处理 | WGPU + WGSL Compute | WGPU Android 后端（Vulkan），复用同一套 WGSL 着色器 | P0 |
| 3 | 非破坏性编辑 | `.rrdata` JSON sidecar | 改用 Room 数据库存储 adjustments JSON，保留 sidecar 兼容 | P0 |
| 4 | JPEG/PNG/TIFF/EXR/QOI 加载 | image crate + exr + qoi | 保留 Rust 实现，Android Bitmap 互转 | P0 |
| **调色系统** | | | | |
| 5 | 曝光（线性 + 胶片式） | WGSL `apply_linear_exposure` / `apply_filmic_exposure` | 复用 WGSL | P0 |
| 6 | 对比度 | WGSL `apply_tonal_adjustments` | 复用 WGSL | P0 |
| 7 | 高光/阴影/白/黑 | WGSL `apply_highlights_adjustment` 等 | 复用 WGSL | P0 |
| 8 | 亮度 | WGSL `apply_filmic_exposure` | 复用 WGSL | P0 |
| 9 | 色温/色调 | WGSL `apply_white_balance` | 复用 WGSL | P0 |
| 10 | 饱和度/自然饱和度 | WGSL `apply_creative_color` | 复用 WGSL | P0 |
| 11 | HSL 8色混合器 | WGSL `apply_hsl_panel` | 复用 WGSL | P1 |
| 12 | 色调曲线（Luma/R/G/B） | WGSL `apply_curve`（Cubic Hermite） | 复用 WGSL | P1 |
| 13 | 色彩分级（阴影/中间调/高光） | WGSL `apply_color_grading` + Color Wheels | 复用 WGSL + Compose ColorWheel | P1 |
| 14 | 色彩校准（RGB 主色） | WGSL `apply_color_calibration` | 复用 WGSL | P2 |
| 15 | 色调映射（AgX/Basic） | WGSL AgX 矩阵变换 | 复用 WGSL | P1 |
| **细节与效果** | | | | |
| 16 | 锐化 | WGSL sharpness_blur 差值 | 复用 WGSL | P1 |
| 17 | 清晰度/结构/中心 | WGSL `apply_local_contrast` | 复用 WGSL | P1 |
| 18 | 去雾 | WGSL dehaze | 复用 WGSL | P1 |
| 19 | 降噪（亮度/色彩） | Rust CPU + WGSL | 复用 Rust + WGSL | P1 |
| 20 | BM3D 降噪 | Rust `denoising.rs` | 保留 Rust 实现 | P2 |
| 21 | 色差校正 | WGSL chromatic_aberration | 复用 WGSL | P1 |
| 22 | 晕影 | WGSL vignette | 复用 WGSL | P1 |
| 23 | 胶片颗粒 | WGSL gradient_noise | 复用 WGSL | P2 |
| 24 | LUT（.cube/.3dl/.png） | Rust `lut_processing.rs` + WGSL 3D texture | 保留 Rust + WGSL | P2 |
| 25 | 发光/光晕/镜头光晕 | WGSL glow/halation + flare.wgsl | 复用 WGSL | P2 |
| **几何变换** | | | | |
| 26 | 裁剪 | Rust `apply_crop` | 保留 Rust | P0 |
| 27 | 旋转/翻转 | Rust `apply_rotation/apply_flip` | 保留 Rust | P0 |
| 28 | 拉直 | Rust Hough 变换 + 检测 | 保留 Rust | P1 |
| 29 | 透视校正 | Rust `warp_image_geometry` | 保留 Rust | P2 |
| 30 | 镜头校正 | Rust lensfun_db XML 解析 | 保留 Rust，嵌入 lensfun_db 到 assets | P2 |
| **蒙版系统** | | | | |
| 31 | 画笔蒙版 | Rust `generate_mask_bitmap` Brush | 保留 Rust + Compose Canvas 手势 | P1 |
| 32 | 线性渐变蒙版 | Rust Linear gradient | 保留 Rust | P1 |
| 33 | 径向蒙版 | Rust Radial gradient | 保留 Rust | P1 |
| 34 | AI 主体/天空/前景蒙版 | Rust ONNX Runtime（u2netp/sam） | 保留 ort，Android NNAPI delegate | P2 |
| 35 | 蒙版叠加/合并/反转 | Rust + WGSL mask_atlas | 复用 WGSL | P1 |
| **图库管理** | | | | |
| 36 | 文件夹树浏览 | Rust `file_management.rs` | Android MediaStore + SAF | P0 |
| 37 | 缩略图生成 | Rust rayon 并行 | 保留 Rust，异步生成 | P0 |
| 38 | 评分/颜色标签 | `.rrdata` JSON | Room 数据库 | P1 |
| 39 | 标签系统 | Rust tagging（CLIP 模型） | 保留 ort，Android 端简化 | P2 |
| 40 | 筛选与排序 | Rust | Room SQL 查询 | P1 |
| 41 | 虚拟副本 | `.rrdata` | Room 数据库 | P1 |
| 42 | 废片筛选（重复/模糊检测） | Rust `culling.rs` | 保留 Rust | P2 |
| **导入导出** | | | | |
| 43 | JPEG/PNG/TIFF 导出 | Rust image crate | 保留 Rust | P0 |
| 44 | 批量导出 | Rust tokio 异步 | 保留 Rust | P1 |
| 45 | 水印 | Rust `WatermarkSettings` | 保留 Rust | P2 |
| 46 | 元数据写入 | Rust little_exif | 保留 Rust | P2 |
| 47 | 文件名模板 | Rust | 保留 Rust | P2 |
| **预设系统** | | | | |
| 48 | 创建/保存/覆盖预设 | JSON 文件 | Room + JSON 导出 | P1 |
| 49 | 导入预设（.xmp/.lrtemplate） | Rust `preset_converter.rs` | 保留 Rust | P2 |
| 50 | 复制/粘贴设置 | Frontend state | Room + ViewModel | P1 |
| **高级功能** | | | | |
| 51 | 撤销/重做 | React useHistoryState | ViewModel + StateFlow 列表 | P0 |
| 52 | 全景拼接 | Rust `panorama_stitching.rs` | 保留 Rust | P2 |
| 53 | 拼贴画 | React Konva | Compose Canvas | P2 |
| 54 | 负片转换 | Rust `negative_conversion.rs` | 保留 Rust | P2 |
| 55 | AI 修复（Inpainting） | Rust `inpainting.rs` + ONNX | 保留 ort | P2 |
| 56 | ComfyUI 连接 | Rust WebSocket | 保留 Rust，Android 端配置 | P2 |
| 57 | EXIF 查看 | Rust `exif_processing.rs` | 保留 Rust | P1 |
| 58 | 直方图/波形 | Frontend Canvas | Compose Canvas | P1 |
| 59 | 裁剪警告（高光/阴影溢出） | WGSL `show_clipping` | 复用 WGSL | P1 |

---

## 二、Android 技术选型

### 2.1 技术栈总览

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android Application                          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              UI Layer (Jetpack Compose)                    │ │
│  │  Material3 + 自定义 OPPO 摄影主题 + 哈苏橙色彩体系          │ │
│  └──────────────────────────┬─────────────────────────────────┘ │
│  ┌──────────────────────────▼─────────────────────────────────┐ │
│  │           ViewModel Layer (MVVM + StateFlow)               │ │
│  │  EditorViewModel / LibraryViewModel / ExportViewModel      │ │
│  └──────────────────────────┬─────────────────────────────────┘ │
│  ┌──────────────────────────▼─────────────────────────────────┐ │
│  │            Repository Layer (Room + ContentResolver)        │ │
│  │  ImageRepository / PresetRepository / SettingsRepository   │ │
│  └──────────────────────────┬─────────────────────────────────┘ │
│  ┌──────────────────────────▼─────────────────────────────────┐ │
│  │            JNI Bridge Layer (rust-core cdylib)             │ │
│  │  RapidRawNative: loadRaw / processPreview / exportImage    │ │
│  └──────────────────────────┬─────────────────────────────────┘ │
│  ┌──────────────────────────▼─────────────────────────────────┐ │
│  │        Rust Core Library (librapidraw_core.so)             │ │
│  │  rawler | wgpu(Vulkan) | rayon | image | ort | lensfun_db │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心依赖

| 组件 | 选型 | 理由 |
|------|------|------|
| UI 框架 | Jetpack Compose (BOM 2024+) | 原生声明式 UI，动画流畅，适合摄影编辑器 |
| 主题 | Material3 + 自定义 ColorScheme | 哈苏橙主题，深色模式优先 |
| 导航 | Compose Navigation | 单 Activity + 多 Composable |
| 异步 | Kotlin Coroutines + Flow | 与 Rust JNI 回调配合 |
| 图片加载 | 自研（Rust 解码 → Bitmap） | RAW 文件无现成库，必须走 Rust |
| 数据库 | Room | 替代 JSON sidecar，提供查询/排序/筛选 |
| DI | Hilt | 标准依赖注入 |
| 文件访问 | SAF (Storage Access Framework) | Android 11+ 分区存储 |
| 缩略图 | Rust rayon 并行生成 | 复用原始缩略图管线 |
| GPU | WGPU Android (Vulkan 后端) | 与桌面端共享同一套 WGSL 着色器 |
| AI 推理 | ONNX Runtime (Android) | 复用原始 ONNX 模型 |
| JNI | JNI (手动绑定) + jni-rs crate | 零拷贝 Bitmap 传输 |

### 2.3 为什么选择 WGPU 而非 RenderScript/Vulkan 直接调用

1. **代码复用**：WGSL 着色器代码 100% 复用（shader.wgsl / blur.wgsl / flare.wgsl），无需重写为 GLSL/SPIR-V
2. **跨平台一致性**：WGPU 在 Android 上走 Vulkan 后端，处理结果与桌面端一致
3. **管线完整**：WGPU 的 Compute Pipeline 完美映射原始 Rust `gpu_processing.rs` 的管线结构
4. **维护成本**：WGSL → 多后端编译由 WGPU 自动完成，无需维护多套着色器

---

## 三、Rust 核心库设计（librapidraw_core.so）

### 3.1 Cargo.toml 配置

```toml
[package]
name = "rapidraw-core"
version = "1.4.12"
edition = "2024"

[lib]
name = "rapidraw_core"
crate-type = ["cdylib"]  # Android 动态库

[dependencies]
rawler = { path = "rawler/rawler" }
wgpu = "28"
pollster = "0.4"
bytemuck = { version = "1.24", features = ["derive"] }
image = "0.25"
rayon = "1.11"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
half = { version = "2.7", features = ["bytemuck"] }
anyhow = "1"
log = "0.4"
nalgebra = "0.34"
glam = "0.30"
quick-xml = { version = "0.36", features = ["serialize"] }
fuzzy-matcher = "0.3"
encase = "0.12"

# Android JNI
jni = "0.21"
android_logger = "0.14"

# 可选：AI 推理（P2 阶段）
ort = { version = "2.0.0-rc.10", features = ["ndarray", "load-dynamic"], optional = true }
ndarray = { version = "0.16", optional = true }

[features]
default = []
ai = ["ort", "ndarray"]

[target.'cfg(target_os = "android")'.dependencies]
wgpu = { version = "28", features = ["vulkan"] }
```

### 3.2 JNI 接口设计

```rust
// src/android.rs — JNI 导出函数

use jni::objects::{JBitmap, JClass, JString, JNIEnv};
use jni::sys::{jint, jlong, jfloat, jboolean, jbyteArray, jobject};

// ========== 图像加载 ==========

/// 从文件路径加载 RAW/JPEG 图像，返回线性 f32 像素数据
/// 返回值：long handle（指向 NativeImage 的指针）
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_loadImage(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    fast_demosaic: jboolean,
    highlight_compression: jfloat,
) -> jlong {
    let path_str = env.get_string(&path).unwrap().into();
    // 1. 读取文件字节
    // 2. 调用 load_base_image_from_bytes（复用 image_loader.rs）
    // 3. 应用几何变换（复用 apply_all_transformations）
    // 4. 返回 Box::into_raw(Box::new(NativeImage { ... })) as jlong
    todo!("实现")
}

/// 释放 NativeImage 资源
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_freeImage(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    // let _ = Box::from_raw(handle as *mut NativeImage);
}

/// 获取图像尺寸
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_getImageDimensions(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jintArray {
    // 返回 [width, height]
}

// ========== GPU 处理 ==========

/// 初始化 GPU 上下文（WGPU Vulkan 后端）
/// 需要在主线程调用，传入 Android 的 NativeWindow
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_initGpuContext(
    mut env: JNIEnv,
    _class: JClass,
    native_window: jobject, // Android Surface 的 native window
) -> jboolean {
    // 1. 从 ANativeWindow 创建 WGPU Surface
    // 2. 请求 Vulkan 后端适配器
    // 3. 创建 GpuProcessor（复用 gpu_processing.rs）
}

/// 处理预览：应用所有调整参数，输出到 Android Bitmap
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_processPreview(
    mut env: JNIEnv,
    _class: JClass,
    image_handle: jlong,
    adjustments_json: JString,  // 完整的 Adjustments JSON
    output_bitmap: JBitmap,     // Android Bitmap 对象
    preview_width: jint,
    preview_height: jint,
) -> jboolean {
    // 1. 解析 adjustments_json → AllAdjustments（复用 get_all_adjustments_from_json）
    // 2. 缩放图像到 preview 尺寸（复用 downscale_f32_image）
    // 3. 上传到 GPU 纹理
    // 4. 运行 Compute Pipeline（复用 GpuProcessor::process）
    // 5. 读取输出纹理 → 填充 Android Bitmap（零拷贝）
}

/// 处理全分辨率图像用于导出
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_processFullResolution(
    mut env: JNIEnv,
    _class: JClass,
    image_handle: jlong,
    adjustments_json: JString,
) -> jbyteArray {
    // 全分辨率处理，返回 JPEG/PNG 字节
}

// ========== 蒙版 ==========

/// 生成蒙版位图
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_generateMask(
    mut env: JNIEnv,
    _class: JClass,
    mask_json: JString,    // MaskDefinition JSON
    image_width: jint,
    image_height: jint,
) -> jbyteArray {
    // 复用 mask_generation::generate_mask_bitmap
}

// ========== EXIF ==========

/// 读取 EXIF 数据
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_readExif(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    // 复用 exif_processing::read_exif_data → JSON string
}

// ========== 镜头校正 ==========

/// 查找镜头校正参数
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_findLensCorrection(
    mut env: JNIEnv,
    _class: JClass,
    maker: JString,
    model: JString,
    focal_length: jfloat,
    aperture: jfloat,
) -> jstring {
    // 复用 lens_correction.rs → JSON
}

// ========== 缩略图 ==========

/// 生成缩略图字节
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_generateThumbnail(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    target_size: jint,
) -> jbyteArray {
    // JPEG 缩略图字节，供 Android BitmapFactory 解码
}

// ========== 导出 ==========

/// 导出图像
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_exportImage(
    mut env: JNIEnv,
    _class: JClass,
    image_handle: jlong,
    adjustments_json: JString,
    export_settings_json: JString,  // ExportSettings JSON
) -> jbyteArray {
    // 复用 image_processing + JpegEncoder → 字节
}

// ========== LUT ==========

/// 解析 LUT 文件
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_parseLut(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jlong {
    // 复用 lut_processing → 返回 Lut handle
}
```

### 3.3 WGPU Android 初始化

```rust
// src/gpu_android.rs

use wgpu::{Instance, Surface, Backends};

pub fn create_android_gpu_surface(
    native_window: *mut std::ffi::c_void, // ANativeWindow*
) -> (Instance, Surface, wgpu::Adapter, wgpu::Device, wgpu::Queue) {
    let instance = Instance::new(&wgpu::InstanceDescriptor {
        backends: Backends::VULKAN,  // Android 强制 Vulkan
        ..Default::default()
    });

    // 从 ANativeWindow 创建 Surface
    let surface = unsafe {
        instance.create_surface_from_raw_surface(
            native_window,
            wgpu::SurfaceTargetUnsafe::RawWindow {
                raw_window_handle: ...,
                raw_display_handle: ...,
            }
        ).unwrap()
    };

    let adapter = pollster::block_on(instance.request_adapter(&
        wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            compatible_surface: Some(&surface),
            ..Default::default()
        }
    )).unwrap();

    let (device, queue) = pollster::block_on(adapter.request_device(&
        wgpu::DeviceDescriptor { ..Default::default() }, None
    )).unwrap();

    (instance, surface, adapter, device, queue)
}
```

### 3.4 零拷贝 Bitmap 传输

```rust
// 关键优化：直接写入 Android Bitmap 像素缓冲区
fn fill_android_bitmap(env: &mut JNIEnv, bitmap: &JBitmap, rgba_data: &[u8]) {
    let mut pixels = env.lock_bitmap_pixels(bitmap).unwrap();
    let pixel_slice = pixels.as_mut_slice();
    pixel_slice.copy_from_slice(rgba_data);
    // unlock 自动完成
}
```

---

## 四、Android 应用架构

### 4.1 项目结构

```
RapidRAW-Android/
├── app/
│   ├── src/main/
│   │   ├── java/com/rapidraw/
│   │   │   ├── core/                          # JNI 桥接层
│   │   │   │   ├── RapidRawNative.kt          # JNI 声明 + 生命周期管理
│   │   │   │   ├── NativeImage.kt             # 图像句柄封装
│   │   │   │   └── GpuContext.kt              # GPU 上下文管理
│   │   │   │
│   │   │   ├── data/                           # 数据层
│   │   │   │   ├── db/
│   │   │   │   │   ├── AppDatabase.kt         # Room 数据库
│   │   │   │   │   ├── ImageEntity.kt         # 图像实体（路径/评分/标签/调整参数）
│   │   │   │   │   ├── PresetEntity.kt        # 预设实体
│   │   │   │   │   └── FolderEntity.kt        # 文件夹实体
│   │   │   │   ├── repository/
│   │   │   │   │   ├── ImageRepository.kt     # 图像数据仓库
│   │   │   │   │   ├── PresetRepository.kt    # 预设仓库
│   │   │   │   │   └── SettingsRepository.kt  # 设置仓库
│   │   │   │   └── model/
│   │   │   │       ├── Adjustments.kt         # 调整参数数据类（1:1 映射原始 Adjustments 接口）
│   │   │   │       ├── ExportSettings.kt      # 导出设置
│   │   │   │       ├── MaskDefinition.kt      # 蒙版定义
│   │   │   │       ├── ExifData.kt           # EXIF 数据
│   │   │   │       └── ImageFile.kt          # 图像文件信息
│   │   │   │
│   │   │   ├── engine/                         # 处理引擎封装
│   │   │   │   ├── ImageProcessor.kt          # 图像处理调度器
│   │   │   │   ├── PreviewPipeline.kt         # 预览管线（防抖 + 增量更新）
│   │   │   │   ├── ThumbnailGenerator.kt      # 缩略图生成器
│   │   │   │   └── ExportPipeline.kt          # 导出管线
│   │   │   │
│   │   │   ├── ui/                             # UI 层
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt              # OPPO 摄影主题定义
│   │   │   │   │   ├── Color.kt              # 哈苏橙色彩体系
│   │   │   │   │   ├── Type.kt               # 字体排版
│   │   │   │   │   └── Shape.kt              # 形状圆角
│   │   │   │   ├── navigation/
│   │   │   │   │   └── RapidNavHost.kt       # 导航图
│   │   │   │   ├── library/                   # 图库模块
│   │   │   │   │   ├── LibraryScreen.kt       # 图库主屏
│   │   │   │   │   ├── LibraryViewModel.kt    # 图库 ViewModel
│   │   │   │   │   ├── FolderTree.kt         # 文件夹树组件
│   │   │   │   │   ├── ImageGrid.kt          # 图片网格
│   │   │   │   │   └── FilterBar.kt          # 筛选栏
│   │   │   │   ├── editor/                    # 编辑器模块
│   │   │   │   │   ├── EditorScreen.kt       # 编辑器主屏
│   │   │   │   │   ├── EditorViewModel.kt    # 编辑器 ViewModel
│   │   │   │   │   ├── ImagePreview.kt       # 图像预览（支持缩放/拖拽）
│   │   │   │   │   ├── HistogramOverlay.kt   # 直方图叠加层
│   │   │   │   │   ├── CropOverlay.kt        # 裁剪叠加层
│   │   │   │   │   ├── MaskCanvas.kt         # 蒙版绘制 Canvas
│   │   │   │   │   └── WaveformOverlay.kt    # 波形叠加层
│   │   │   │   ├── adjustments/              # 调整面板组件
│   │   │   │   │   ├── BasicPanel.kt         # 基础调整（曝光/对比/高光/阴影/白/黑/亮度）
│   │   │   │   │   ├── ColorPanel.kt         # 颜色调整（色温/色调/饱和度/自然饱和度/HSL）
│   │   │   │   │   ├── CurvesPanel.kt        # 曲线调整
│   │   │   │   │   ├── DetailsPanel.kt       # 细节调整（锐化/清晰度/降噪/去雾/结构）
│   │   │   │   │   ├── EffectsPanel.kt       # 效果（晕影/颗粒/LUT/发光/光晕/光斑）
│   │   │   │   │   ├── TransformPanel.kt     # 几何变换
│   │   │   │   │   ├── ColorGradingPanel.kt  # 色彩分级
│   │   │   │   │   └── ColorCalibrationPanel.kt # 色彩校准
│   │   │   │   ├── panels/                   # 右侧/底部面板
│   │   │   │   │   ├── AdjustmentsSheet.kt   # 调整面板（底部弹出 Sheet）
│   │   │   │   │   ├── PresetsSheet.kt       # 预设面板
│   │   │   │   │   ├── MasksSheet.kt         # 蒙版面板
│   │   │   │   │   ├── CropSheet.kt          # 裁剪面板
│   │   │   │   │   ├── ExportSheet.kt        # 导出面板
│   │   │   │   │   ├── MetadataSheet.kt      # 元数据面板
│   │   │   │   │   └── AiSheet.kt            # AI 面板
│   │   │   │   ├── components/               # 通用 UI 组件
│   │   │   │   │   ├── HasselSlider.kt       # 哈苏橙自定义滑块
│   │   │   │   │   ├── ColorWheel.kt         # 色彩轮
│   │   │   │   │   ├── CurveEditor.kt        # 曲线编辑器
│   │   │   │   │   ├── BeforeAfterToggle.kt  # 修改前后对比
│   │   │   │   │   ├── Filmstrip.kt          # 底部胶片条
│   │   │   │   │   ├── RatingBar.kt          # 评分条
│   │   │   │   │   ├── CollapsibleSection.kt # 可折叠区域
│   │   │   │   │   └── ConfirmDialog.kt      # 确认对话框
│   │   │   │   └── MainActivity.kt           # 主 Activity
│   │   │   │
│   │   │   └── RapidRawApp.kt                # Application 类
│   │   │
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── colors.xml               # 哈苏橙色彩定义
│   │   │   │   └── themes.xml               # 主题
│   │   │   └── drawable/                    # 图标资源
│   │   │
│   │   └── assets/
│   │       └── lensfun_db/                  # 镜头校正数据库（从原始项目复制）
│   │
│   └── build.gradle.kts
│
├── rust-core/                                 # Rust 核心库
│   ├── Cargo.toml
│   ├── src/
│   │   ├── lib.rs                            # 库入口
│   │   ├── android.rs                        # JNI 导出
│   │   ├── raw_processing.rs                 # 复用自原始项目
│   │   ├── image_loader.rs                   # 复用
│   │   ├── image_processing.rs               # 复用
│   │   ├── gpu_processing.rs                 # 复用
│   │   ├── gpu_android.rs                    # Android WGPU 初始化
│   │   ├── mask_generation.rs                # 复用
│   │   ├── exif_processing.rs                # 复用
│   │   ├── lens_correction.rs                # 复用
│   │   ├── lut_processing.rs                 # 复用
│   │   ├── formats.rs                        # 复用
│   │   ├── denoising.rs                      # 复用
│   │   ├── file_management.rs                # 适配 Android 路径
│   │   ├── inpainting.rs                     # 复用
│   │   ├── negative_conversion.rs             # 复用
│   │   ├── panorama_stitching.rs             # 复用
│   │   ├── panorama_utils/                   # 复用
│   │   ├── tagging.rs                        # 复用
│   │   ├── tagging_utils/                    # 复用
│   │   ├── culling.rs                        # 复用
│   │   ├── ai_processing.rs                  # 复用（可选）
│   │   ├── ai_connector.rs                   # 复用（可选）
│   │   ├── preset_converter.rs               # 复用
│   │   └── shaders/
│   │       ├── shader.wgsl                   # 100% 复用
│   │       ├── blur.wgsl                     # 100% 复用
│   │       └── flare.wgsl                    # 100% 复用
│   └── rawler/                               # git submodule
│
└── build.gradle.kts                           # 根构建脚本
```

### 4.2 核心数据模型

```kotlin
// data/model/Adjustments.kt
// 1:1 映射原始 src/utils/adjustments.tsx 的 Adjustments 接口

@Serializable
data class Adjustments(
    // --- 基础调色 ---
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,

    // --- 颜色 ---
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val hsl: HslAdjustments = HslAdjustments(),
    val colorGrading: ColorGradingProps = ColorGradingProps(),
    val colorCalibration: ColorCalibration = ColorCalibration(),

    // --- 细节 ---
    val sharpness: Float = 0f,
    val lumaNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val structure: Float = 0f,
    val centre: Float = 0f,
    val chromaticAberrationRedCyan: Float = 0f,
    val chromaticAberrationBlueYellow: Float = 0f,

    // --- 效果 ---
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 0f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 0f,
    val grainAmount: Float = 0f,
    val grainSize: Float = 0f,
    val grainRoughness: Float = 0f,
    val lutIntensity: Float = 0f,
    val glowAmount: Float = 0f,
    val halationAmount: Float = 0f,
    val flareAmount: Float = 0f,

    // --- 几何 ---
    val rotation: Float = 0f,
    val orientationSteps: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropData? = null,
    val transformDistortion: Float = 0f,
    val transformVertical: Float = 0f,
    val transformHorizontal: Float = 0f,
    val transformRotate: Float = 0f,
    val transformAspect: Float = 0f,
    val transformScale: Float = 100f,
    val transformXOffset: Float = 0f,
    val transformYOffset: Float = 0f,

    // --- 镜头校正 ---
    val lensMaker: String? = null,
    val lensModel: String? = null,
    val lensDistortionAmount: Float = 100f,
    val lensVignetteAmount: Float = 100f,
    val lensTcaAmount: Float = 100f,
    val lensDistortionEnabled: Boolean = true,
    val lensTcaEnabled: Boolean = true,
    val lensVignetteEnabled: Boolean = true,
    val lensDistortionParams: LensDistortionParams? = null,

    // --- 曲线 ---
    val curves: CurvesData = CurvesData(),

    // --- 蒙版 ---
    val masks: List<MaskContainer> = emptyList(),

    // --- AI 修复 ---
    val aiPatches: List<AiPatch> = emptyList(),

    // --- 其他 ---
    val toneMapper: String = "basic",  // "agx" | "basic"
    val showClipping: Boolean = false,
    val rating: Int = 0,
    val aspectRatio: Float? = null,
    val sectionVisibility: SectionVisibility = SectionVisibility(),
)

@Serializable
data class HslAdjustments(
    val reds: HueSatLum = HueSatLum(),
    val oranges: HueSatLum = HueSatLum(),
    val yellows: HueSatLum = HueSatLum(),
    val greens: HueSatLum = HueSatLum(),
    val aquas: HueSatLum = HueSatLum(),
    val blues: HueSatLum = HueSatLum(),
    val purples: HueSatLum = HueSatLum(),
    val magentas: HueSatLum = HueSatLum(),
)

@Serializable
data class HueSatLum(val hue: Float = 0f, val saturation: Float = 0f, val luminance: Float = 0f)

@Serializable
data class CurvesData(
    val luma: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val red: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val green: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val blue: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
)

@Serializable
data class Coord(val x: Float, val y: Float)

@Serializable
data class ColorGradingProps(
    val shadows: HueSatLum = HueSatLum(),
    val midtones: HueSatLum = HueSatLum(),
    val highlights: HueSatLum = HueSatLum(),
    val blending: Float = 0f,
    val balance: Float = 0f,
)

@Serializable
data class ColorCalibration(
    val shadowsTint: Float = 0f,
    val redHue: Float = 0f,
    val redSaturation: Float = 0f,
    val greenHue: Float = 0f,
    val greenSaturation: Float = 0f,
    val blueHue: Float = 0f,
    val blueSaturation: Float = 0f,
)

@Serializable
data class CropData(
    val x: Double, val y: Double,
    val width: Double, val height: Double,
)

@Serializable
data class LensDistortionParams(
    val k1: Float = 0f, val k2: Float = 0f, val k3: Float = 0f,
    val model: Int = 0,
    val tca_vr: Float = 1f, val tca_vb: Float = 1f,
    val vig_k1: Float = 0f, val vig_k2: Float = 0f, val vig_k3: Float = 0f,
)

@Serializable
data class MaskContainer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 100f,
    val adjustments: Adjustments? = null,
    val subMasks: List<SubMaskData> = emptyList(),
)

@Serializable
data class SubMaskData(
    val id: String,
    val type: String,  // "brush" | "linear" | "radial" | "ai_subject" | "ai_sky" | "ai_foreground"
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 100f,
    val mode: String = "additive",
    val parameters: String = "{}", // JSON
)
```

### 4.3 EditorViewModel 核心逻辑

```kotlin
class EditorViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val imageProcessor: ImageProcessor,
) : ViewModel() {

    // 当前编辑状态
    private val _adjustments = MutableStateFlow(Adjustments())
    val adjustments: StateFlow<Adjustments> = _adjustments.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 撤销/重做栈
    private val undoStack = mutableListOf<Adjustments>()
    private val redoStack = mutableListOf<Adjustments>()
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // 当前图像
    private var currentImageHandle: Long = 0L
    private var currentImagePath: String = ""

    // 预览防抖
    private val previewJob = MutableStateFlow<Job?>(null)

    fun loadImage(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            currentImagePath = path
            currentImageHandle = RapidRawNative.loadImage(path, false, 1.5f)
            _isLoading.value = false
            updatePreview()
        }
    }

    fun updateAdjustments(block: (Adjustments) -> Adjustments) {
        val old = _adjustments.value
        pushUndo(old)
        _adjustments.value = block(old)
        schedulePreviewUpdate()
    }

    fun updateAdjustment(key: String, value: Float) {
        updateAdjustments { adj ->
            // 通过反射或 when 表达式更新对应字段
            adj.copyByField(key, value)
        }
    }

    private fun schedulePreviewUpdate() {
        previewJob.value?.cancel()
        previewJob.value = viewModelScope.launch(Dispatchers.IO) {
            delay(50) // 50ms 防抖
            updatePreview()
        }
    }

    private fun updatePreview() {
        if (currentImageHandle == 0L) return
        val bitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val json = Json.encodeToString(_adjustments.value)
        RapidRawNative.processPreview(currentImageHandle, json, bitmap, previewWidth, previewHeight)
        _previewBitmap.value = bitmap
    }

    // 撤销/重做实现
    fun undo() { /* ... */ }
    fun redo() { /* ... */ }

    private fun pushUndo(adj: Adjustments) {
        undoStack.add(adj)
        redoStack.clear()
        updateUndoRedoState()
    }

    override fun onCleared() {
        super.onCleared()
        if (currentImageHandle != 0L) {
            RapidRawNative.freeImage(currentImageHandle)
        }
    }
}
```

### 4.4 Room 数据库设计

```kotlin
@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val path: String,
    val fileName: String,
    val folderPath: String,
    val isRaw: Boolean,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val dateModified: Long,
    val rating: Int = 0,
    val colorLabel: String? = null,   // "red" | "yellow" | "green" | "blue" | "purple"
    val tags: String? = null,         // JSON array
    val adjustmentsJson: String? = null,  // 完整 Adjustments JSON
    val thumbnailPath: String? = null,
    val virtualCopyOf: String? = null,    // 虚拟副本来源路径
    val lastEdited: Long? = null,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long? = null,
    val adjustmentsJson: String,     // 完整 Adjustments JSON
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "preset_folders")
data class PresetFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val path: String,
    val displayName: String,
    val parentId: String? = null,
    val isPinned: Boolean = false,
    val imageCount: Int = 0,
)
```

---

## 五、OPPO 摄影交互 + 哈苏橙主题设计

### 5.1 色彩体系

```kotlin
// ui/theme/Color.kt

// 哈苏橙主色系 (#E8600C 为核心色)
val HasselbladOrange = Color(0xFFE8600C)
val HasselbladOrangeLight = Color(0xFFF2803F)
val HasselbladOrangeDark = Color(0xFFB84D0A)
val HasselbladOrangeMuted = Color(0x80E8600C)     // 50% 透明度

// 暗色系（摄影编辑器背景）
val EditorBackground = Color(0xFF1A1A1A)           // 主背景
val EditorSurface = Color(0xFF242424)              // 卡片/面板
val EditorSurfaceVariant = Color(0xFF2E2E2E)       // 次级表面
val EditorBorder = Color(0xFF3A3A3A)               // 边框分割线

// 文字色
val TextPrimary = Color(0xFFF0F0F0)
val TextSecondary = Color(0xFF999999)
val TextTertiary = Color(0xFF666666)

// 功能色
val ClippingRed = Color(0xFFFF4444)                // 高光溢出
val ClippingBlue = Color(0xFF4488FF)               // 阴影溢出
val SuccessGreen = Color(0xFF4CAF50)
val WarningYellow = Color(0xFFFFC107)

// 渐变（哈苏橙渐变，用于滑块轨道、按钮高亮）
val HasselbladGradient = Brush.horizontalGradient(
    colors = listOf(HasselbladOrangeDark, HasselbladOrange, HasselbladOrangeLight)
)
```

### 5.2 主题定义

```kotlin
// ui/theme/Theme.kt

private val RapidRawColorScheme = darkColorScheme(
    primary = HasselbladOrange,
    onPrimary = Color.White,
    primaryContainer = HasselbladOrangeDark,
    secondary = Color(0xFF444444),
    background = EditorBackground,
    surface = EditorSurface,
    surfaceVariant = EditorSurfaceVariant,
    outline = EditorBorder,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun RapidRawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RapidRawColorScheme,
        typography = RapidRawTypography,  // 思源黑体 / OPPO Sans
        shapes = RapidRawShapes,          // 小圆角 (4dp)
        content = content,
    )
}
```

### 5.3 OPPO 摄影交互规范

#### 5.3.1 编辑器交互模式

```
┌─────────────────────────────────────────┐
│  ← 图库    [EXIF] [撤销] [重做] [···]    │  ← 顶部工具栏（半透明）
│─────────────────────────────────────────│
│                                         │
│                                         │
│          图 像 预 览 区 域               │  ← 支持双指缩放、单指拖拽
│          （触摸交互：                    │
│           双击→100%，                    │
│           双指缩放→自由缩放,             │
│           长按→查看原图）                │
│                                         │
│                                         │
│─────────────────────────────────────────│
│  ◄ 胶片条 ►  （水平滚动缩略图列表）      │  ← 底部胶片条
│─────────────────────────────────────────│
│  [基础] [颜色] [曲线] [细节] [效果] [几何]│  ← 面板切换标签栏
│─────────────────────────────────────────│
│  ┌─────────────────────────────────────┐│
│  │  曝光    ──────●──────  +0.5        ││  ← 哈苏橙滑块区域
│  │  对比    ──●──────────  -15         ││
│  │  高光    ─────────●───  +30         ││
│  │  阴影    ───●─────────  -20         ││
│  │  白色    ●────────────  -5          ││
│  │  黑色    ───────────●─  +10         ││
│  └─────────────────────────────────────┘│
│                                         │
│        ▲ 上滑关闭 / 下拉展开             │  ← 底部调整面板（可拖拽）
└─────────────────────────────────────────┘
```

#### 5.3.2 手势交互定义

| 手势 | 区域 | 行为 |
|------|------|------|
| 单指拖拽 | 图像预览 | 平移图像 |
| 双指缩放 | 图像预览 | 缩放图像 |
| 双击 | 图像预览 | 切换 100% / 适应 |
| 长按 | 图像预览 | 显示原图（无调整） |
| 上下滑动 | 调整面板标签 | 切换调整面板展开/收起 |
| 左右滑动 | 滑块 | 调整参数值 |
| 双击滑块 | 滑块 | 重置为默认值 |
| 点击数值 | 滑块数值 | 弹出精确输入键盘 |
| 左右滑动 | 胶片条 | 切换图片 |
| 双指捏合 | 图库网格 | 切换网格大小 |

#### 5.3.3 核心自定义组件

**HasselSlider** — 哈苏橙风格参数滑块：

```kotlin
@Composable
fun HasselSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    format: (Float) -> String = { it.toInt().toString() },
) {
    var isDragging by remember { mutableStateOf(false) }
    val animatedValue by animateFloatAsState(value, label = "slider")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(48.dp),
        )

        // 滑块轨道
        Box(modifier = Modifier.weight(1f).height(24.dp)) {
            // 灰色底线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center)
                    .background(EditorBorder)
            )
            // 哈苏橙已填充区域
            val fraction = (value - range.start) / (range.endInclusive - range.start)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(if (isDragging) 3.dp else 2.dp)
                    .align(Alignment.CenterStart)
                    .background(HasselbladOrange, CircleShape)
            )
            // 滑块把手
            Box(
                modifier = Modifier
                    .offset { IntOffset((fraction * maxWidth).roundToPx() - 8.dp.roundToPx(), 0) }
                    .size(if (isDragging) 18.dp else 14.dp)
                    .background(HasselbladOrange, CircleShape)
                    .pointerInput(Unit) { /* drag gesture */ }
            )
        }

        // 数值显示
        Text(
            text = format(value),
            style = MaterialTheme.typography.bodySmall,
            color = if (value != 0f) HasselbladOrange else TextTertiary,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}
```

**CurveEditor** — 曲线编辑器：

```kotlin
@Composable
fun CurveEditor(
    points: List<Coord>,
    onPointsChanged: (List<Coord>) -> Unit,
    activeChannel: ChannelConfig,  // LUMA / RED / GREEN / BLUE
) {
    Canvas(modifier = Modifier.fillMaxWidth().aspectOf(1f)) {
        // 1. 绘制网格（25% 间隔灰色虚线）
        // 2. 绘制对角线（中性参考线）
        // 3. 绘制曲线（Cubic Hermite 插值，对应 WGSL 的 apply_curve 算法）
        // 4. 绘制控制点（哈苏橙圆点，可拖拽）
        // 5. 支持添加/删除控制点
    }
}
```

**ColorWheel** — 色彩轮：

```kotlin
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    onHueChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
) {
    // 圆形色相环 + 中心亮度的径向渐变
    // 拖拽控制点定位（复用原始 @uiw/react-color-wheel 的逻辑）
    Canvas(modifier = Modifier.size(120.dp)) {
        // 绘制色相环
        // 绘制控制点
    }
}
```

### 5.4 OPPO 摄影风格 UI 特色

1. **底部面板优先**：编辑面板从底部弹出（BottomSheetScaffold），符合单手操作习惯
2. **大触控区域**：所有可交互元素最小 44dp 触控区域
3. **即时预览反馈**：滑块拖拽时 50ms 内更新预览（WGPU Compute Pipeline 保证性能）
4. **暗色沉浸式**：全屏编辑时状态栏/导航栏隐藏，深色背景突出图像
5. **手势直觉化**：长按看原图、双击切换缩放、双击滑块归零
6. **哈苏橙焦点**：所有激活状态、选中标记、进度指示均使用哈苏橙
7. **OPPO 字体**：使用 OPPO Sans / 思源黑体，字重 400/500/700
8. **圆角卡片**：面板使用 16dp 圆角，与 ColorOS 设计语言一致
9. **动画过渡**：面板展开/收起使用 spring 动画，duration 300ms

---

## 六、GPU 处理管线详解

### 6.1 Android 上的 WGPU 处理流程

```
Android Bitmap (ARGB_8888)
        │
        ▼
[Rust JNI] processPreview()
        │
        ├── 1. 解析 Adjustments JSON → AllAdjustments struct
        │      （复用 image_processing.rs::get_all_adjustments_from_json）
        │
        ├── 2. 缩放原始图像到预览尺寸
        │      （复用 downscale_f32_image）
        │
        ├── 3. 上传 f32 像素到 GPU 纹理（Rgba16Float）
        │      wgpu::Texture → input_texture
        │
        ├── 4. 生成模糊纹理（sharpness/clarity/structure）
        │      GpuProcessor::process_blurs()
        │      ├── horizontal_blur → ping_pong
        │      └── vertical_blur → blur_texture
        │
        ├── 5. 生成光斑纹理（flare）
        │      GpuProcessor::process_flares()
        │      ├── flare_threshold_pipeline
        │      ├── flare_ghosts_pipeline
        │      └── composite → flare_texture
        │
        ├── 6. 生成蒙版图集
        │      mask_bitmaps → mask_textures[0..8]
        │
        ├── 7. 运行主 Compute Pipeline
        │      shader.wgsl::main_compute()
        │      ├── 读取 input_texture + adjustments uniform
        │      ├── 逐像素处理：
        │      │   a. 线性曝光 (apply_linear_exposure)
        │      │   b. 胶片亮度 (apply_filmic_exposure)
        │      │   c. 白平衡 (apply_white_balance)
        │      │   d. 高光 (apply_highlights_adjustment)
        │      │   e. 色调调整 (apply_tonal_adjustments: con/sh/wh/bl)
        │      │   f. 创意色彩 (apply_creative_color: sat/vib)
        │      │   g. HSL (apply_hsl_panel)
        │      │   h. 色彩分级 (apply_color_grading)
        │      │   i. 色彩校准 (apply_color_calibration)
        │      │   j. 局部对比度 (apply_local_contrast: clarity/structure)
        │      │   k. 锐化 (unsharp_mask with blur_texture)
        │      │   l. 降噪 (color_noise_reduction)
        │      │   m. 色差 (chromatic_aberration)
        │      │   n. 去雾 (dehaze)
        │      │   o. 发光/光晕/光斑
        │      │   p. 晕影
        │      │   q. 胶片颗粒 (gradient_noise)
        │      │   r. LUT (3D texture lookup)
        │      │   s. 曲线 (apply_curve × 4通道)
        │      │   t. 色调映射 (AgX / basic)
        │      │   u. sRGB 转换 (linear_to_srgb)
        │      │   v. 蒙版叠加 (9 个蒙版纹理混合)
        │      │   w. 裁剪警告 (show_clipping)
        │      └── 输出到 output_texture (Rgba8Unorm)
        │
        └── 8. 读取输出纹理 → 填充 Android Bitmap
               read_texture_data → fill_android_bitmap
```

### 6.2 性能预估

| 操作 | 桌面 (RTX 3060) | Android 预估 (Snapdragon 8 Gen 3) |
|------|-----------------|-------------------------------------|
| RAW 解码 (24MP) | ~200ms | ~500ms |
| 预览 GPU 处理 (1080p) | ~5ms | ~15ms |
| 全分辨率导出 (24MP) | ~50ms | ~200ms |
| 缩略图生成 | ~30ms/张 | ~80ms/张 |

关键优化策略：
- 预览使用 1080p 缩放图，全分辨率仅导出时使用
- GPU 纹理缓存：图像不变时复用 input_texture
- 防抖：滑块拖拽 50ms 延迟后触发预览更新
- 异步：RAW 解码和缩略图生成在 IO 线程池

---

## 七、分阶段交付计划

### Phase 1：基础 RAW 预览管线（P0）

**目标**：可加载 RAW/JPEG 图像，实时预览基础调色参数

| 任务 | 内容 | 对应原始模块 |
|------|------|-------------|
| 1.1 | Android 项目骨架 + Gradle NDK 配置 + Rust cdylib 构建 | — |
| 1.2 | Rust 核心库：raw_processing + image_loader + formats | raw_processing.rs, image_loader.rs, formats.rs |
| 1.3 | Rust 核心库：image_processing（几何变换 + 调整参数解析） | image_processing.rs |
| 1.4 | Rust 核心库：WGPU Android 初始化 + shader.wgsl 集成 | gpu_processing.rs, shaders/ |
| 1.5 | JNI 桥接：loadImage / processPreview / freeImage | — |
| 1.6 | EditorScreen：图像预览 + 手势缩放/拖拽 | Editor.tsx, ImageCanvas.tsx |
| 1.7 | BasicPanel：曝光/对比/高光/阴影/白/黑/亮度 | Basic.tsx |
| 1.8 | 哈苏橙主题 + OPPO 交互规范 | themes.tsx |
| 1.9 | 撤销/重做 | useHistoryState.tsx |
| 1.10 | 简易图库：文件选择 + 缩略图 | MainLibrary.tsx |

**交付物**：可安装 APK，选择图片 → 实时调整 7 个基础参数 → 预览

### Phase 2：完整调色 + 图库管理（P0-P1）

| 任务 | 内容 | 对应原始模块 |
|------|------|-------------|
| 2.1 | ColorPanel：色温/色调/饱和度/自然饱和度 | Color.tsx |
| 2.2 | HSL 8色混合器 | Color.tsx (HSL 部分) |
| 2.3 | 色调曲线编辑器（Luma/R/G/B） | Curves.tsx |
| 2.4 | 色彩分级（色彩轮 + 阴影/中间调/高光） | Color.tsx (Color Grading) |
| 2.5 | 色彩校准 | Color.tsx (Calibration) |
| 2.6 | DetailsPanel：锐化/清晰度/降噪/去雾/结构 | Details.tsx |
| 2.7 | EffectsPanel：晕影/颗粒/LUT | Effects.tsx |
| 2.8 | 色差校正 | Details.tsx (CA) |
| 2.9 | 色调映射（AgX） | shader.wgsl (AgX) |
| 2.10 | 完整图库：文件夹树 + 网格 + 筛选/排序 | MainLibrary.tsx, FolderTree.tsx |
| 2.11 | Room 数据库：图像元数据 + adjustments 持久化 | file_management.rs |
| 2.12 | 评分/颜色标签 | AppProperties.tsx |
| 2.13 | 底部胶片条 | Filmstrip.tsx |
| 2.14 | 直方图叠加 | — |
| 2.15 | 裁剪警告 | shader.wgsl (show_clipping) |

### Phase 3：裁剪/几何 + 导出 + 预设（P0-P1）

| 任务 | 内容 | 对应原始模块 |
|------|------|-------------|
| 3.1 | 裁剪工具（交互 + 应用） | CropPanel.tsx |
| 3.2 | 旋转/翻转 | EditorToolbar.tsx |
| 3.3 | 拉直工具 | EditorToolbar.tsx |
| 3.4 | 透视校正 | TransformModal.tsx |
| 3.5 | JPEG/PNG/TIFF 导出 | ExportPanel.tsx |
| 3.6 | 批量导出 | ExportPanel.tsx |
| 3.7 | 导出设置（质量/尺寸/元数据/水印） | ExportPanel.tsx |
| 3.8 | 预设创建/保存/应用 | PresetsPanel.tsx |
| 3.9 | 复制/粘贴设置 | CopyPasteSettingsModal.tsx |
| 3.10 | EXIF 查看面板 | MetadataPanel.tsx |

### Phase 4：蒙版系统 + 镜头校正（P1-P2）

| 任务 | 内容 | 对应原始模块 |
|------|------|-------------|
| 4.1 | 画笔蒙版（Compose Canvas 手势 + Rust 位图生成） | Masks.tsx, mask_generation.rs |
| 4.2 | 线性渐变蒙版 | mask_generation.rs |
| 4.3 | 径向蒙版 | mask_generation.rs |
| 4.4 | 蒙版叠加/合并/反转/透明度 | Masks.tsx, shader.wgsl (mask_atlas) |
| 4.5 | 蒙版独立调整参数 | MaskAdjustments in WGSL |
| 4.6 | 镜头校正（lensfun_db 解析 + 应用） | LensCorrectionModal.tsx, lens_correction.rs |
| 4.7 | 虚拟副本 | file_management.rs |
| 4.8 | 发光/光晕/镜头光斑效果 | Effects.tsx, flare.wgsl |

### Phase 5：高级功能（P2）

| 任务 | 内容 | 对应原始模块 |
|------|------|-------------|
| 5.1 | AI 主体/天空/前景蒙版（ONNX Runtime） | ai_processing.rs, AIPanel.tsx |
| 5.2 | AI 修复（Inpainting） | inpainting.rs |
| 5.3 | 自动标签（CLIP 模型） | tagging.rs |
| 5.4 | 废片筛选 | CullingModal.tsx, culling.rs |
| 5.5 | BM3D 降噪 | DenoiseModal.tsx, denoising.rs |
| 5.6 | 全景拼接 | PanoramaModal.tsx, panorama_stitching.rs |
| 5.7 | 拼贴画 | CollageModal.tsx |
| 5.8 | 负片转换 | NegativeConversionModal.tsx |
| 5.9 | LUT 导入（.cube/.3dl/.png） | lut_processing.rs |
| 5.10 | 预设导入（.xmp/.lrtemplate） | preset_converter.rs |
| 5.11 | ComfyUI 连接 | ai_connector.rs |
| 5.12 | 水印 | ExportPanel.tsx |
| 5.13 | 波形监视器 | Waveform.tsx |
| 5.14 | 文件导入/重命名/删除 | file_management.rs |
| 5.15 | 批量文件名模板 | ExportPanel.tsx |

---

## 八、构建配置

### 8.1 Rust 交叉编译

```toml
# .cargo/config.toml
[target.aarch64-linux-android]
linker = "aarch64-linux-android21-clang"

[target.armv7-linux-androideabi]
linker = "armv7-linux-androideabi21-clang"

[target.x86_64-linux-android]
linker = "x86_64-linux-android21-clang"
```

### 8.2 Gradle NDK 集成

```kotlin
// app/build.gradle.kts
android {
    namespace = "com.rapidraw"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    externalNativeBuild {
        cmake { path = file("rust-core/CMakeLists.txt") }
    }
}

// Rust 构建脚本
tasks.register("buildRust") {
    exec {
        workingDir = file("rust-core")
        commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a",
                     "-t", "x86_64", "-o", "../app/src/main/jniLibs", "build", "--release")
    }
}
```

---

## 九、关键风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| WGPU Android Vulkan 兼容性 | 部分旧设备 Vulkan 支持不完整 | 降级到 CPU 处理路径（rayon 并行） |
| rawler 大文件内存占用 | 24MP RAW 解码需 ~500MB | 分块处理 + 预览缩放 + 大堆内存配置 |
| JNI 调用延迟 | 频繁小调用累积延迟 | 批量传输 + 零拷贝 Bitmap |
| ONNX Runtime Android 包体积 | AI 模型 ~50MB | 可选下载，不内置 |
| Android 11+ 分区存储限制 | 无法直接访问任意目录 | SAF + MediaStore + 原始路径 fallback |
| 镜头校正数据库大小 | lensfun_db XML ~5MB | 压缩存储到 assets，按需解析 |

---

## 十、总结

本架构设计的核心原则是**最大化复用 RapidRAW 原始 Rust 核心代码**：

1. **WGSL 着色器 100% 复用** — shader.wgsl / blur.wgsl / flare.wgsl 无需任何修改
2. **Rust 处理模块 95% 复用** — raw_processing / image_processing / mask_generation / exif / lut / lens 等模块仅去除 Tauri 依赖
3. **数据模型 1:1 映射** — Kotlin Adjustments 数据类完全对应原始 TypeScript Adjustments 接口
4. **GPU 管线架构不变** — WGPU Compute Pipeline 在 Android Vulkan 后端运行同一套着色器

仅有的改动集中在：
- Tauri IPC → JNI 桥接
- React UI → Jetpack Compose
- JSON sidecar → Room 数据库
- 桌面窗口系统 → Android Activity + Surface
