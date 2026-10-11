# 仿色统计基线与工作流验证

日期：2026-10-11。范围：macOS 第一阶段 M0/M1；依据用户提供的技术分析报告，不把研究建议当成已完成能力。

## 已实现

- 本地 Oklab Reinhard 工程变体、正则化二维色度 Gaussian OT，不依赖 API Key 或模型下载。
- 每图独立统计、参考权重、0 权重排除及相同准备 JPEG 去重。聚合图内协方差，不把不同参考均值的散布当成更大的色彩变化；不是 Bures 重心或语义风格分离。
- 颜色/影调独立强度，默认本地影调为 0；原有整体 LUT 强度独立保留。真实图像上的拟合固定后再烘焙，调整控件不重拟合、不请求 AI。
- AI 当前素材匹配和仅参考风格目标。仅参考目标不上传源图/候选图；参考权重进入请求。继续调整传递未缩放基础配方和实际控件，避免重复缩放。
- 输入变化使旧候选失效；失败、取消与快速调整保留最后有效结果，并阻止未完成结果应用/导出。
- 私有元数据记录配方、冻结矩阵、控件、方法、目标、色域策略与诊断；公开导出只保留安全的资产类别以及原颜色契约/表值。

实现入口：[AIColorStatistics](../RawLabMac/Sources/AIColorStatistics.swift)、[工作流](../RawLabMac/Sources/AIColorMatchModel.swift)、[原生配方](../lutools/src/core/color_recipe.cpp)。完整数值约定见 [颜色契约](../lutools/docs/color-contract.md)。

## 有意的数值取舍

OT 拟合使用对称 Gaussian 最优映射，不是普通 WCT。协方差收缩为 `.95*Sigma + .05*trace(Sigma)/2*I + 1e-6*I`；生成器主增益限制在 `[.25,4]`。这是工作空间明确的工程参数，不是跨空间通用最佳值。C ABI 验证矩阵元素及偏移边界，并不把任意输入矩阵强制解释为 SPD OT。

实际渲染对偏移使用 `4*input_L*(1-input_L)` 端点保护。真实 RAW 同源 JPEG 参考产生接近恒等的矩阵，却曾在原灰轴色域压缩下达到 129-grid 误差 `0.0349248`。问题位于高 R、低 B 的饱和边界：轻微 R 越界通过灰轴压缩不成比例地抬升 B。新 v3 明确采用线性 RGB 通道裁切，旧 v1/v2 行为不变；近恒等例通过真实序列化/重读门槛。该选择可能裁切色域外细节，不是审美或明度保真的保证。

独立影调曲线为保守的端点保持九节点曲线，不是完整直方图匹配。Oklab L 在色度处理步骤保留，不代表裁切/量化后的显示亮度严格不变。

## 验证结果

|检查|结果与证据|
|---|---|
|`ctest --test-dir lutools/build-macos --output-on-failure`|21/21 通过，包含旧 v1/v2、v3 烘焙与公开导出、RAW/WB、降噪和 Metal 回归|
|`bash RawLabMac/tests/ai-statistics.sh`|通过：非交换协方差、对称映射、均值、Reinhard 对角比率、平方根/逆、增益限制、无组间散布、排序/像素数/权重、退化及实际非零图像协方差|
|`bash RawLabMac/tests/ai-data.sh`|通过：旧严格 v1 JSON、实际独立控件上下文、仅参考角色/数量/权重、图片方向与隐私、HTTP 错误和取消|
|`bash RawLabMac/tests/ai-workflow.sh <RAW>`|`DSC08882.ARW`、仓库 `DSC00340.ARW` 均通过：本地生成无 HTTP、参考去重/权重失效、独立控件、冻结拟合、失败/取消恢复、仅参考上传、echo 配方不重复缩放、应用/恢复/持久化|
|`bash RawLabMac/tests/ai-render.sh <RAW> --local-ot`|`DSC08882.ARW` 通过：真实预览拟合后冻结的 OT CUBE，65-grid，最大通道误差 `0.0031943682`；强度 0/1/2 的 CPU/Force Metal 最大差异 1 DN|
|全分辨率输出|4672x7008 JPEG、16-bit PNG 单张/批量输出像素一致；原始 RAW 文件身份未改变|
|`bash RawLabMac/build.sh`|构建、依赖打包和签名检查通过；本地构建位于 `build/RawLab Mac.app`|
|图形界面 fixture|通过：1000x740pt 普通窗口、820x640pt 最小窗口及820x740pt展开状态；照片非空且比例正确，文字和控件无明显遮挡/截断|

HTTP 检查只使用 `URLProtocol` 本地替身。没有执行 `ai-live.sh`，没有读取或发送真实 API Key，没有上传用户照片，没有安装/运行神经模型。数值误差是固定函数与 LUT 重放误差，不是风格准确率、用户偏好或商业产品胜率。

## 图形界面补验

用户解锁 Mac 后，用 `RAWLAB_AI_UI=1` 启动本地 fixture；`RAWLAB_AI_UI_COMPACT=1` 固定最小初始窗口，避免依赖手动拖动尺寸。两次窗口均正常关闭、脚本退出 0，没有应用到正式编辑记录。

- 在真实控件上修改颜色与影调强度，分别显示70%与30%；整体强度保持80%。
- 选中固定分区后切换本地方法，旧候选被清空，对照回到中性基线；应用按钮禁用。
- 唯一参考权重设为0时生成按钮禁用，恢复1后可在本地生成 OT 候选；默认影调强度为0。
- 实际导出 `local-ot.cube`：65-grid、显示sRGB、`sourceMatch`；没有私有配方、分区或模型信息。
- 仅参考目标的界面提示明确“不发送当前照片”，未触发实际 AI 生成请求。
- 展开分区时最小宽度保持820pt，窗口自动增高到740pt；footer和四个分区控件仍完整可见。
- 澄清导出/分享提示：颜色、影调及分区已烘焙，整体强度和照片曝光/白平衡不包含在表内。新提示在原生导出对话框中完整显示。

本地证据保存在 `output/color-transfer-ui-2026-10-11/`（不纳入Git）：`normal.png`、`expanded.png`、`compact.png`、`compact-expanded.png`、`local-ot.png`、`reference-only.png`、`export-contract.png`及实际导出的`local-ot.cube`。截图只证明这些布局和控件状态，不是主观仿色质量评分。

## 后续边界

这轮未实现：全通道 OT、相机/RAW/Log/HDR 新契约、语义蒙版、自动规范化网络、多参考神经训练、共享风格蒸馏或商业产品实测。仅参考 AI 的输入与当前素材隔离，不等于已经证明通用风格提取。

NLUT 与 CanonCGT 仅完成官方论文/仓库静态核验。NLUT 有测试时微调及自定义 PyTorch 插值扩展；CanonCGT 官方 demo 使用 CUDA，生成表仍依赖内容。两者均需取得权重、验证设备路径、增加 CUBE 写出和复用评测，不能直接当成现成的原生多参考模块。复核入口：[NLUT 作者仓库](https://github.com/semchan/NLUT)、[CanonCGT 作者仓库](https://github.com/Jinwon-Ko/CanonCGT)。

数学依据：[POT Gaussian 文档](https://pythonot.github.io/gen_modules/ot.gaussian.html)、[Reinhard 原文](https://users.cs.northwestern.edu/~bgooch/PDFs/ColorTransfer.pdf)。原始 Reinhard 使用 l-alpha-beta，本实现明确为 Oklab 变体。
