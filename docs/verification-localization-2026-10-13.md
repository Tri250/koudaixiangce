# 多语言验证记录

日期：2026-10-13。交付分支：`feature/multilingual-support`。
基线：`origin/development` 的 `12d6cc17b2b92233657d3eb548ee146dbdcb8530`。
本记录描述迁移到该基线后的代码和验证，不沿用原分支上的构建结果。

## 交付范围

macOS、iOS、Android、Windows 支持简体中文、英语、德语、法语、日语和西班牙语。
默认跟随系统，未支持的语言回退英语；提供持久化的手动语言选择。
菜单、控件、辅助功能标签、应用提示和应用控制的错误文案已资源化。

已补齐 development 新增的大师配方描述、HSL、曝光和照片效果界面。
配方及胶片专有名称、用户外观名称、照片路径、枚举 raw value、配方 ID 和保存格式保持不变。
本次没有修改显影算法、共享 C ABI、RAW 或配方 JSON。
无关的 AI 仿色、JPEG XL、视口预览和降噪缓存改动未带入本交付分支；
原工作区及其已有改动保留。

## 已通过

| 范围 | 检查与结果 |
| --- | --- |
| 资源回归 | `python3 -m unittest discover -s tests -p test_localization.py`：5/5，通过六语言 key、占位符、源调用覆盖、14 个配方描述及 HSL 覆盖检查 |
| macOS 构建 | `bash RawLabMac/build.sh`：当前核心/UI 构建、六语言资源打包、依赖及签名检查通过 |
| macOS 模型 | `localization.sh` 12 项、`master-recipe.sh` 1624 项、`horizontal-picker.sh` 13 项、`presentation.sh` 83 项、`update-check.sh` 2 项，以及 `edit-model.sh`、`edit-memory.sh`、`batch-export.sh` 均通过 |
| 核心 | `ctest --test-dir lutools/build-macos --output-on-failure`：15/15，通过现有 RAW、CPU/Metal、降噪、HSL/照片效果相关回归 |
| iOS 构建 | `bash lutools/platform/ios/build-framework.sh`：当前核心三架构 XCFramework 构建通过；最新模拟器应用和 UI 测试构建通过 |
| iOS 模型 | `presentation.sh` 211 项、`edit-memory.sh` 20 项、`batch-export.sh` 51 项、`photo-information.sh` 9 项通过 |
| iOS UI | iPhone 17e / iOS 26.4，`testLanguageSelectionPersists`：1/1，通过德语、法语实时切换、重启持久化及恢复系统语言 |
| Android 构建 | `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 通过；61 项单元测试，失败/错误/跳过均为 0 |
| Android 设备 | 独立 API 35 ARM64 模拟器：5/5，六语言资源、空态编辑器、RAW 导入/旋转/失败恢复/切换语言保留编辑、关于页及降噪控件通过 |
| Windows 可移植检查 | 本地化 29 项、编辑工作流 2 项、降噪 22 项、更新 2 项通过；macOS 上应用/WPF 测试交叉编译通过 |
| Windows 原生 WPF | 隔离目录内直接 `dotnet build`：应用、WPF 测试和本地化 UI 回归均为 0 warning / 0 error；`LocalizationUiChecks` 75/75 通过 |
| diff | `git diff --check` 通过 |

### macOS 原生界面

在当前 worktree 的 `build/RawLab Mac.app` 中完成六语言实时切换及直方图标签检查。
使用仓库合成 `linear-uncompressed.dng`，检查德语/法语配方描述与 HSL 标签。
已选配方、Yellow 通道、色相/饱和度数值、曝光及其他调整在语言切换后保持。
法语选择退出重启后保留。原生截图检查未见文字重叠。
结束后恢复跟随系统、测试调整和当前 development 的 Standard 曝光基准，并关闭本线应用。

### Windows 原生 WPF

提交的 `RawLabWindows/tests/localization-ui` 使用真实 App 启动后的 MainWindow。
测试对该窗口实际的 settings 应用配方、曝光 1.25、HSL 和八项照片效果，
点击真实 Vignette 工具，再逐一切换六语言。对象引用和完整 Capture JSON 均保持；
DynamicResource、工具/HSL/重置按钮辅助功能文案实时更新，六次 Changed 事件及偏好写入正确。
测试前后语言偏好文件均不存在，测试写入已清理。
此结论覆盖托管 WPF 运行，不代表原生显影 DLL、硬件 GPU 或桌面视觉验收通过。

### 设备与样片隔离

Android 在本线新建 AVD `rawlab-i18n-api35` 的 `emulator-5584` 上，
通过显式 `adb -s emulator-5584 install` 和 `am instrument` 执行，
没有使用会枚举全部设备的 Gradle connected 任务。
未操作其他任务使用的 `emulator-5582` 或 Android 真机。
设备回归使用仓库合成 DNG，不修改样片，也不构成真机 GPU 证明；结束后关闭本线模拟器。

## 评审与失败处理

两位独立评审分别检查 Apple/Shared 与 Windows/Android 调用链，并回查已确认问题：
大师配方在批量摘要中误显示为中性、iOS 批量中性名称被翻译后持久化、
Windows 动态标签及工具辅助功能名称未刷新、iOS 降噪错误及 Mac 16-bit 标签漏翻译，均已修复。

Android API 33+ 的测试曾与系统自动 locale 重建重复调用 recreate；
实际复现 ActivityScenario DESTROYED 错误后，测试与生产代码对齐，仅旧 API 手动重建，
等待新 locale 生效后重新绑定模型。上述最终设备回归通过。
早期共享 AVD/端口冲突不作为通过证据；最终结果来自新建的独立 AVD。

Windows 完整 `build.ps1 -Test` 被当前主机脚本执行策略拒绝；
未绕过或修改策略。必要的本地化验证改为直接运行托管项目。
新增 WPF 回归曾因测试 harness 给 StartupUri 赋 null 失败；
改为等待真实应用正常启动后，最终 75 项检查通过。

## 未验证与剩余限制

- Windows 完整 C++/CTest、原生 DLL/RAW/GPU、手动桌面/RDP 视觉验收：`NOT REACHED`。
- Android API 26-32 运行时语言切换：没有设备实测；有版本守卫、解析测试及 lint。
- iOS 编辑器所有原生 Picker、macOS/iOS 批量导出界面尚未做完整六语言交互遍历。
  已有资源覆盖、构建及模型检查不替代该项验收。
- Android 批量来源为自定义导入外观时，原有 snapshot 不保存用户显示名，
  查不到内置胶片/配方后仍回退原始 ID；未在本次多语言工作中迁移该持久化结构。
- 六语言尚未完成母语校对；历史任务日志、已有错误详情、用户数据和底层诊断不重新翻译。
- 本报告的检查是本地或专用构建主机验证，不等同于 GitHub exact-head CI 结果。

## 产物与证据

构建产物保持忽略，不提交：

- macOS：`build/RawLab Mac.app`
- Android：`RawLabAndroid/app/build/outputs/apk/debug/app-debug.apk`
- iOS：`/tmp/rawlab-localized-development-ios/Build/Products/Debug-iphonesimulator/RawLab.app`
- Windows 托管程序集：`RawLabWindows/bin/Release/net8.0-windows/RawLab.dll`，
  不是包含 native DLL 的完整 Windows 发布包。

主要验证日志保留在临时目录：
`rawlab-i18n-mac-final-build.log`、`rawlab-development-ctest.log`、
`rawlab-i18n-ios-final-test.log`、`rawlab-i18n-final-language.xcresult`、
`rawlab-i18n-android-build-final.log`、`rawlab-i18n-android-instrumentation.log`、
`rawlab-i18n-native-wpf-ui-build.log` 和 `rawlab-i18n-native-wpf-ui-run.log`。
PR 的提交 SHA、远端同步及 CI 状态在交付时单独核对。
