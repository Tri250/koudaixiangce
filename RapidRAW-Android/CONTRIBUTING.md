# Contributing to RapidRAW

感谢您有兴趣为RapidRAW做出贡献！本文档将帮助您了解贡献流程和开发规范。

---

## 📋 贡献方式

### 1. 报告问题 (Bug Reports)
如果您发现了bug或问题，请通过 [GitHub Issues](https://github.com/your-org/rapidraw-android/issues/new) 提交。

**问题报告模板：**
```markdown
**问题描述**
简洁清晰地描述问题。

**复现步骤**
1. 打开应用
2. 点击 '...'
3. 滑动到 '...'
4. 出现错误

**预期行为**
描述您期望发生的情况。

**实际行为**
描述实际发生的情况。

**环境信息**
- 设备型号: [例如: Pixel 8 Pro]
- Android版本: [例如: Android 16]
- RapidRAW版本: [例如: v1.7.0]
- 内存: [例如: 8GB]

**截图/日志**
如果适用，添加截图或崩溃日志帮助解释问题。
```

### 2. 功能建议 (Feature Requests)
我们欢迎新功能建议！请在 Issues 中标注 `enhancement` 标签。

**功能建议模板：**
```markdown
**功能描述**
清晰描述您希望添加的功能。

**使用场景**
描述该功能解决了什么问题或在什么场景下使用。

**实现思路**
如果您有技术实现建议，请简要描述。

**优先级**
- [ ] 高优先级（核心功能缺失）
- [ ] 中优先级（增强用户体验）
- [ ] 低优先级（锦上添花）
```

### 3. 提交代码 (Pull Requests)
详见下方完整流程。

---

## 🛠️ 开发环境配置

### 必备工具
| 工具 | 版本 | 安装方式 |
|------|------|----------|
| Android Studio | 2024.1+ | [官网下载](https://developer.android.com/studio) |
| JDK | 17+ | `brew install openjdk@17` (macOS) |
| Gradle | 8.6+ | 项目自带wrapper |
| NDK | 26.3.11579264 | Android Studio SDK Manager |
| CMake | 3.22.1+ | Android Studio SDK Manager |
| Git | 2.30+ | `brew install git` (macOS) |

### 项目设置
```bash
# 1. Fork仓库到您的GitHub账号
# 2. Clone您的fork到本地
git clone https://github.com/YOUR_USERNAME/rapidraw-android.git
cd rapidraw-android

# 3. 添加上游仓库作为remote
git remote add upstream https://github.com/original-org/rapidraw-android.git

# 4. 验证remote配置
git remote -v
# 输出应包含:
# origin    https://github.com/YOUR_USERNAME/rapidraw-android.git (fetch)
# origin    https://github.com/YOUR_USERNAME/rapidraw-android.git (push)
# upstream  https://github.com/original-org/rapidraw-android.git (fetch)
# upstream  https://github.com/original-org/rapidraw-android.git (push)

# 5. 同步最新代码
git fetch upstream
git checkout main
git merge upstream/main

# 6. 构建项目验证环境
./gradlew assembleDevDebug
```

---

## 🔄 Pull Request流程

### Step 1: 创建功能分支
```bash
# 从main创建新分支
git checkout main
git pull upstream main
git checkout -b feature/your-feature-name

# 分支命名规范:
# feature/xxx  - 新功能
# fix/xxx      - bug修复
# refactor/xxx - 代码重构
# docs/xxx     - 文档更新
# test/xxx     - 测试补充
```

### Step 2: 编写代码 + 测试
```bash
# 开发过程中定期提交
git add .
git commit -m "feat: add new feature X"

# 提交信息规范 (Conventional Commits):
# feat:     新功能
# fix:      bug修复
# refactor: 重构（不改变功能）
# docs:     文档更新
# test:     测试相关
# chore:    构建/工具变更
# perf:     性能优化
# style:    代码格式调整

# 示例:
# feat: add HDR export support
# fix: resolve OOM crash when loading 100MP RAW
# refactor: migrate DiContainer to Hilt
# docs: update README with performance benchmarks
# test: add EditorViewModel unit tests
```

### Step 3: 代码质量检查
```bash
# 运行Lint检查
./gradlew lintDevDebug

# 运行Detekt静态分析
./gradlew detekt

# 运行单元测试
./gradlew testDevDebugUnitTest

# 运行代码覆盖率
./gradlew koverHtmlReportDevDebug

# 查看覆盖率报告
open app/build/reports/kover/html/devDebug/index.html

# 覆盖率目标: 60%+ (新增代码)
```

### Step 4: 提交Pull Request
```bash
# 推送到您的fork
git push origin feature/your-feature-name

# 在GitHub网页创建PR:
# 1. 打开 https://github.com/YOUR_USERNAME/rapidraw-android
# 2. 点击 "Compare & pull request"
# 3. 填写PR标题和描述 (参考下方模板)
# 4. 等待CI检查通过 + Code Review
```

**PR描述模板：**
```markdown
## 变更类型
- [ ] 新功能 (feat)
- [ ] Bug修复 (fix)
- [ ] 重构 (refactor)
- [ ] 文档 (docs)
- [ ] 测试 (test)

## 变更描述
清晰描述本次PR解决的问题或添加的功能。

## 相关Issue
关闭 #XXX (如有相关Issue)

## 测试计划
- [ ] 单元测试已添加/更新
- [ ] 手动测试已完成
- [ ] 性能影响已评估

## 代码质量
- [ ] Lint检查通过 (`./gradlew lint`)
- [ ] Detekt检查通过 (`./gradlew detekt`)
- [ ] 单元测试通过 (`./gradlew test`)
- [ ] 覆盖率达标 (60%+)

## 截图/演示
（如适用）添加截图或GIF演示变更效果。

## 破坏性变更
- [ ] 无破坏性变更
- [ ] 有破坏性变更 (请详细说明)

## Checklist
- [ ] 代码遵循项目风格指南
- [ ] 已更新相关文档
- [ ] 已更新CHANGELOG.md
- [ ] 提交信息符合Conventional Commits规范
```

### Step 5: Code Review + 合并
```bash
# 根据Review反馈修改代码
git add .
git commit -m "refactor: address review feedback"
git push origin feature/your-feature-name

# 合并后清理分支
git checkout main
git pull upstream main
git branch -d feature/your-feature-name
git push origin --delete feature/your-feature-name
```

---

## 📐 代码规范

### Kotlin代码风格
```kotlin
// 1. 类命名: PascalCase
class ImageProcessor { }

// 2. 函数命名: camelCase，动词开头
fun processImage() { }
fun calculateExposure() { }

// 3. 变量命名: camelCase
val imageProcessor = ImageProcessor()
var adjustments: Adjustments? = null

// 4. 常量命名: UPPER_SNAKE_CASE
const val MAX_IMAGE_SIZE = 8192
private const val TAG = "ImageProcessor"

// 5. 文件组织顺序
// - File header (license comment)
// - Package statement
// - Imports (alphabetical order)
// - Class/Object declaration

// 6. 函数组织顺序
// - Public functions
// - Internal functions
// - Private functions
// - Companion object
```

### Compose UI规范
```kotlin
// 1. Composable函数命名: PascalCase
@Composable
fun EditorScreen() { }

// 2. 状态声明在最前
@Composable
fun EditorScreen() {
    var adjustments by remember { mutableStateOf(Adjustments()) }
    var isLoading by remember { mutableStateOf(false) }
    
    // UI实现
}

// 3. Modifier作为最后一个参数
@Composable
fun CustomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) { }

// 4. 事件回调命名: onXxx
@Composable
fun ImagePreview(
    onImageClick: (Uri) -> Unit,
    onZoomChange: (Float) -> Unit
) { }
```

### Native C++规范
```cpp
// 1. 类命名: PascalCase
class RawDecoder { };

// 2. 函数命名: camelCase
void processRawImage() { }

// 3. 变量命名: camelCase，成员变量前缀m_
int mImageWidth;
std::string mFilePath;

// 4. 常量命名: UPPER_SNAKE_CASE
static const int MAX_PIXELS = 8192;

// 5. JNI函数命名: Java_包名_类名_方法名
JNIEXPORT jlong JNICALL
Java_com_rapidraw_core_RawDecoder_loadRawImage(JNIEnv* env, jobject obj, jstring path);
```

---

## 🧪 测试规范

### 单元测试规范
```kotlin
// 1. 测试类命名: XxxTest
class ImageProcessorTest { }

// 2. 测试函数命名: `功能描述_测试场景_预期结果`
@Test
fun `processImage with valid bitmap returns processed bitmap`() { }

// 3. 测试结构: Given-When-Then
@Test
fun `calculateExposure with positive value increases brightness`() {
    // Given: 准备测试数据
    val processor = ImageProcessor()
    val bitmap = createTestBitmap()
    
    // When: 执行测试操作
    val result = processor.adjustExposure(bitmap, 1.5f)
    
    // Then: 验证结果
    assertTrue(result.isBrighterThan(bitmap))
}

// 4. 边界测试
@Test
fun `adjustExposure with extreme value does not crash`() {
    val processor = ImageProcessor()
    val bitmap = createTestBitmap()
    
    // 测试极端值
    assertDoesNotThrow { processor.adjustExposure(bitmap, 100.0f) }
    assertDoesNotThrow { processor.adjustExposure(bitmap, -100.0f) }
}
```

### UI测试规范
```kotlin
// 1. 使用Compose Testing API
@Test
fun editorScreen_displaysAdjustmentPanels() {
    composeRule.setContent {
        RapidRawTheme {
            EditorScreen()
        }
    }
    
    // 验证关键UI元素存在
    composeRule.onNodeWithText("曝光").assertIsDisplayed()
    composeRule.onNodeWithText("对比度").assertIsDisplayed()
}

// 2. 测试用户交互
@Test
fun slider_adjustsExposureCorrectly() {
    composeRule.setContent {
        RapidRawTheme {
            EditorScreen()
        }
    }
    
    // 模拟滑块拖动
    composeRule.onNodeWithText("曝光")
        .performTouchInput { swipeRight() }
    
    // 验证状态更新
    composeRule.onNodeWithText("+1.5").assertIsDisplayed()
}
```

---

## 📝 文档规范

### README更新
新增功能必须在README.md中更新：
1. 功能列表（如适用）
2. 使用示例（如适用）
3. 性能影响（如有显著影响）

### CHANGELOG更新
所有变更必须在CHANGELOG.md中记录：
```markdown
## [Unreleased]

### Added
- 新功能描述

### Fixed
- Bug修复描述

### Changed
- 变更描述
```

### 代码注释
```kotlin
// 1. 公共API必须有KDoc文档
/**
 * 调整图像曝光度。
 *
 * @param bitmap 输入Bitmap图像
 * @param exposure 曝光调整值，范围[-5.0, 5.0]
 * @return 调整后的Bitmap图像
 * @throws IllegalArgumentException 如果bitmap已回收
 */
fun adjustExposure(bitmap: Bitmap, exposure: Float): Bitmap { }

// 2. 复杂逻辑必须有行内注释
// 使用Cubic Hermite插值计算曲线值
val interpolatedValue = hermiteInterpolate(p0, p1, m0, m1, t)

// 3. TODO注释格式
// TODO: v1.8.0 - 实现HDR合并功能 (优先级: 高)
```

---

## 🏆 贡献者认可

### 贡献类型与认可方式
| 贡献类型 | 认可方式 |
|---------|----------|
| **代码贡献** | CHANGELOG.md署名 + GitHub Contributors |
| **Bug报告** | CHANGELOG.md "Thanks to" 章节 |
| **文档贡献** | README.md贡献者列表 |
| **翻译贡献** | README.md国际化章节署名 |

### 贡献者等级
- **Bronze**: 1-5次有效贡献
- **Silver**: 6-15次有效贡献
- **Gold**: 16+次有效贡献 + Reviewer权限
- **Platinum**: 核心贡献者 + Maintainer权限

---

## 🆘 获取帮助

### 技术问题
- **Email**: dev@rapidraw.app
- **GitHub Discussions**: [技术讨论区](https://github.com/your-org/rapidraw-android/discussions)

### Code Review帮助
- 标注PR为 "Help Wanted" 或 "Review Needed"
- 在Discussions中提问具体技术问题

---

## 📄 许可协议

**重要提示**: RapidRAW为商业软件，贡献者需签署CLA (Contributor License Agreement)。

- ✅ 贡献代码授予项目非独家使用权
- ✅ 贡献者保留代码版权
- ❌ 禁止贡献包含第三方版权代码（未经授权）
- ❌ 禁止贡献违反专利/许可协议的代码

首次提交PR时，系统会自动提示签署CLA。

---

**感谢您的贡献！让我们一起打造最好的Android RAW编辑器。**

*Maintained by RapidRAW Team*