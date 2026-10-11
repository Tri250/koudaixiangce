# 滨田英明风格：神经模型部署与多参考训练执行计划

> **For agentic workers:** 使用 `superpowers:executing-plans` 按任务执行；有独立工作项时可使用 `superpowers:subagent-driven-development`。勾选项仅在交付物及验证结果实际存在后更新。本会话只整理文档，部署和训练由用户在其他会话执行。

**Goal:** 在 `192.168.88.9` 验证 NLUT、CanonCGT 的参考仿色与 LUT 导出，并训练仅由参考组决定的风格模型，面向滨田英明明亮日光方向生成跨内容可复用的调色 LUT。

**Architecture:** 两条路线共用颜色契约、CUBE 导出和评测工具。预训练模型先作为逐素材匹配基线；自训模型将参考集合映射为固定风格 LUT。素材适配独立保留，空间修正不纳入普通 3D LUT。

**Tech Stack:** Linux / Tesla T10 / 隔离 Python 环境 / PyTorch；RawLab C++17 原生校验与 CPU/Metal 渲染。先做命令行原型，验证后再讨论 macOS 在线接入。

**Spec:** 本文的“目标与边界”“共用接口”和“验收”是两条路线的共同约定；已有输入/输出规则以 [颜色契约](../../../lutools/docs/color-contract.md) 为准。

日期：2026-10-11。状态：**待执行；照片集待用户提供；没有在 `.9` 安装模型或开始训练。**

执行顺序：`P0 -> P1 -> A1/A2 -> A3`；训练路线可并行准备 B0/B1，在照片用途确认后执行 `B2 -> B3`。部署路线负责 P0/P1 的共用产物，训练路线复用它们，不另建一套相互不兼容的颜色链路。

## 1. 目标与边界

### 风格目标

延续已有研究的 **Haru and Mina 明亮日光方向**，不把滨田的日光、暗调人像、室内作品混成一个平均目标。对照点是明亮受光面、仍有深度的阴影、保有层次的高光、克制但不灰的蓝绿背景、自然暖肤，以及仍然成立的红色和蓝色衣物。

这些是本项目的创作和评测方向，不是摄影师原始配方。曝光、现场光比、散景、颗粒和局部补光不由固定 LUT 保证。不能通过全图染青、统一去饱和或统一提曝光来代替目标风格。

当前工作区有 [v3 创作说明](../../../output/hamada-daylight-v3/README.txt) 和 [风格研究](../../../output/hamada-daylight-v3/RESEARCH.txt)。`output/` 不受 Git 跟踪，另一台机器可能没有这些文件；应单独交接合法可用的说明材料，不自动复制第三方图片或个人照片。

### Global Constraints

- 第一版仅支持显示参照 SDR、sRGB 编码、BT.709/sRGB 原色、D65、RGB 数值域 `[0,1]`；RAW 先经过 RawLab 中性显影，不能直接送入模型。Log、HDR 和相机输入变换不在本计划内。
- 保留现有 RAW/WB/曝光、C ABI、旧配方、外观导入和 CPU/Metal 行为。研究原型不要求修改这些接口。
- 参数必须先在真实输入上冻结，再采样颜色网格；不能把恒等 RGB 网格当图片送入分析网络重新预测。
- `sourceMatch` 表含当前素材补偿；`referenceStyle` 表只能由参考组及明确的风格控件决定。命名不能代替跨内容验证。
- 用户尚未提供照片集。可先准备环境、适配器和合成测试，但不得自行把 NAS 照片、公开滨田作品或教程图片纳入训练或传到 `.9`。
- `.9` 是共享机器；不终止现有任务、不更新驱动/全局 CUDA/系统 Python、不修复全局 Miniconda、不占用全部 GPU 启动未经验证的任务。
- 本文不是 commit、push、公开服务或对外发布指令；这些操作须遵循执行会话中的用户授权。

### 当前工程起点

|项目|2026-10-11 已确认状态|
|---|---|
|工作区|`/Users/xupeng/mycode/rawtools`，分支 `codex/macos-ai-color-match`|
|Git HEAD|`6c9223ae11c14dbbcf1a4f41890b5855a3899eb1`；工作区另有未提交的仿色优化，HEAD 不包含全部当前代码|
|已有本地方法|Oklab Reinhard 工程变体、正则化二维色度 OT、每图统计与参考权重、独立颜色/影调控制|
|已有 AI 模式|HTTPS 图片输入模型返回严格 v1 的 40 参数配方；不是 NLUT 或 CanonCGT|
|已有导出|冻结配方后 65-grid，必要时 129-grid；原生解析重放的采样最大 RGB 通道误差门槛 `0.02`|
|已有验证|Core 21/21、Mac 构建、统计/数据/工作流、真实 RAW CPU/Metal、单张/批量输出及 GUI 检查通过，详见验收记录|
|未完成|神经权重部署、GPU 实测、神经密集 LUT 导出、多参考神经模型、共享风格蒸馏、真实训练数据清单|

执行前阅读 [当前验收记录](../../verification-color-transfer-baselines-2026-10-11.md)、[Mac AI 工作流](../../../RawLabMac/README.md)、[本地基线规格](../specs/2026-10-11-color-transfer-baselines.md)。不要在脏工作区执行 `git pull`、覆盖文件或应用/删除旧 stash；若迁移到服务器，记录选取的代码快照和未提交补丁，不能声称只克隆当前远程 HEAD 就复现了本地优化。

## 2. `.9` 资源与隔离

此前只读 SSH 快照如下，**不是执行时的空闲资源承诺**：

|GPU|型号/显存|当时已用显存|UUID|
|---|---|---|---|
|0|Tesla T10 / 16384 MiB|0 MiB|`GPU-fcc91647-9ee9-fe39-fbb3-22227d4e15d9`|
|1|Tesla T10 / 16384 MiB|0 MiB|`GPU-b24bd8e5-7dbc-319f-3427-41bfee0c1365`|
|2|Tesla T10 / 16384 MiB|0 MiB|`GPU-e6f10fd5-a7c0-a40c-694b-93c8c728e347`|
|3|Tesla T10 / 16384 MiB|199 MiB|`GPU-0fe5c676-2bf2-ee90-0327-5f1560517204`|

当时驱动 `610.43.02`；GPU 3 有 `/root/run/venvs/comfyui-zimage-runtime/bin/python` 进程，必须保留。系统为 Linux x86_64/PVE，`/usr/bin/python3` 指向 Python 3.11；根文件系统当时约有 434 GiB 空间。SSH 使用已有 `192.168.88.9` 配置、用户 `root`，不得将密钥复制进项目。

实际 CUDA Toolkit、编译器、PyTorch wheel 兼容性、网络/权重可下载性尚未验证。四张卡不是一张 64 GiB 卡；数据并行仍需每卡容纳完整模型及本卡工作负载。

### Task P0：刷新资源、冻结来源、建立专用目录

- [ ] 在 Mac 记录 `git status --short --branch`、`git rev-parse HEAD`，确认本次研究的文件所有权和代码快照；不带走无关 iOS/Android/Windows/降噪/批量导出改动。
- [ ] 执行下面的只读资源检查，记录 GPU 进程、显存、驱动、磁盘和工具链；重新选取一张空闲 GPU，按 UUID 固定。
- [ ] 在得到执行会话的部署授权后建立专用目录 `/root/run/rawlab-hamada`。这是拟定路径，尚未创建；已有目录时先检查来源，不清空或覆盖。
- [ ] 分别建立 NLUT、CanonCGT、自训模型的隔离环境。记录 Python/PyTorch/torchvision/CUDA/编译器及完整依赖锁定文件，不混装三个旧/新环境。
- [ ] 把上游 Git SHA、权重来源/校验值、许可证、下载时间、适配补丁和环境信息写入 `environment.json`、`resources.json`。只校验本项目下载资源，不扫描整台机器。

```bash
# 在 Mac 执行；这些命令只读取服务器状态。
ssh -o BatchMode=yes -o ConnectTimeout=10 192.168.88.9 \
  'nvidia-smi --query-gpu=index,uuid,name,memory.total,memory.used,utilization.gpu,driver_version --format=csv,noheader,nounits'
ssh -o BatchMode=yes -o ConnectTimeout=10 192.168.88.9 \
  'nvidia-smi --query-compute-apps=gpu_uuid,pid,process_name,used_memory --format=csv,noheader'
ssh -o BatchMode=yes -o ConnectTimeout=10 192.168.88.9 'df -h /root/run'
ssh -o BatchMode=yes -o ConnectTimeout=10 192.168.88.9 'python3 --version'
ssh -o BatchMode=yes -o ConnectTimeout=10 192.168.88.9 'nvcc --version'
```

拟定目录职责：`code/` 为锁定源码及适配补丁，`envs/` 为独立环境，`cache/torch/` 为本项目骨干权重，`weights/` 为模型权重，`data/` 为经用户确认的数据，`runs/<run_id>/` 为日志/预览/CUBE/评测/checkpoint。源码及小配置入 Git；图片、权重和训练结果不入 Git。`nvcc` 缺失应作为 NLUT 扩展安装问题处理，不因此更新全局 CUDA。

验收：能够明确指出本项目目录、环境、代码版本及选用 GPU；既有服务未改变。单卡先跑通，之后才允许增加并发。

## 3. 共用接口与产物

以下是 **待执行会话创建的研究代码**，当前没有 `hamada_neural` 包。建议集中在 `experiments/hamada-neural/`，不先改生产端：

|路径|职责/所有权|
|---|---|
|`hamada_neural/cube.py`|共同负责人：轴序、固定变换采样、读写/重放/QA；两条路线共用，不各写一套|
|`hamada_neural/adapters.py`|部署路线：NLUT/CanonCGT 加载、逐对拟合及冻结，不包含训练循环|
|`hamada_neural/data.py`|训练路线：manifest、权限/角色/分组校验和颜色转换|
|`hamada_neural/model.py`、`train.py`|训练路线：集合模型、损失、恢复与 DDP|
|`hamada_neural/evaluate.py`、`cli.py`|统一评测和命令行；对照同一输入、同一插值和同一颜色链路|
|`configs/`、`tests/`、`README.md`|受版本管理的参数、合成测试和运行说明|

两会话并行时先完成共同接口并明确唯一负责人；部署会话拥有适配器，训练会话拥有数据/模型/训练，不同时编辑共同 CUBE 工具。上游代码放服务器 `code/NLUT`、`code/CanonCGT`，不把整份第三方源码和权重复制进 RawLab。

### Task P1：先完成 CUBE 数学链路

约定接口：

```python
# Tensor 默认 float32；接口不在内部重新分析图像。
FrozenTransform.apply(rgb: Tensor[P, 3]) -> Tensor[P, 3]
fit_pair(source: Tensor[1, 3, H, W], reference: Tensor[1, 3, Hr, Wr], seed: int) -> FrozenTransform
predict_style(references: list[Tensor[3, H, W]], weights: list[float]) -> FrozenTransform
export_cube(transform: FrozenTransform, destination: Path, grid_size: int) -> ExportReport
```

`fit_pair` 为当前素材匹配；`predict_style` **没有 source 参数**。冻结对象记录输入/输出契约、插值、边界/裁切策略和方法版本。所有图像尺寸允许不同；先校正方向/ICC、再生成分析代理。未经确认的颜色空间不得静默视为 sRGB。

- [ ] 先写恒等、R/B 交换、非对称通道变换、灰阶/边界及两个变换顺序合成的失败测试，再实现固定变换与 CUBE 工具。
- [ ] CUBE 按 `b -> g -> r` 循环写入，每行 `R G B`，R 最快变化；仅单通道 ramp 不能覆盖轴交换错误，必须有非对称 RGB 探针。
- [ ] 学习型密集 LUT 不塞回 40/44/50 参数配方。写独立 CUBE 后通过 `sony2fuji_validate_look` 和真实原生插值验证；配方编译器不是任意神经 LUT 编译接口。
- [ ] 先冻结变换，以 65³ 采样；重读表后对固定域/灰阶/色相样本、边界、种子固定的随机颜色及保留图像比较。最大通道误差超过 `0.02` 时试 129³，仍失败则不交付可应用的表。
- [ ] QA 分开记录“冻结函数到 CUBE”与“官方模型到冻结函数”的误差；只测前者不能掩盖冻结步骤漏掉处理的问题。均值/p99/最大误差、裁切前越界比例、插值和测试集版本均须保留。
- [ ] 用 [原生 C API](../../../lutools/include/sony2fuji/ffi/sony2fuji_c.h) 校验公开文件，并验证导入 Mac 的中性显影位置正确。不要误用面向 F-Log2 输入的 `lutprep` 输出流程。

公开 CUBE 必须保留：

```text
#Gamma:sRGB to sRGB
#Gamut:ITU-R BT.709 to ITU-R BT.709
#RawLabAssetKind:sourceMatch
LUT_3D_SIZE 65
DOMAIN_MIN 0 0 0
DOMAIN_MAX 1 1 1
```

仅参考模型通过验收后才使用 `referenceStyle`。公开文件不附加照片路径、参考图片、私有配方或 checkpoint 信息。单次运行另存私有 `metadata.json`：`schema_version`、`run_id`、`asset_kind`、颜色契约、模型/源码版本、参考 ID/权重、素材适配是否烘焙、空间修正是否排除、LUT 网格/插值、色域策略、QA 与阶段耗时；未测试的指标写 `null`，状态写 `not_run`。

验收：恒等与已知变换往返、原生解析、轴序、组合顺序全部通过；`0.02` 是有限采样的表示误差门槛，不是滨田风格相似度或任意颜色的数学上界。

## 4. 路线 A：预训练模型部署与导出

本路线可在照片集到位前用授权的上游示例或自建合成图跑通；真实滨田方向评测等待用户允许使用的参考/源图。两模型先逐一跑通，不能仅看到 PNG 输出就宣布接入完成。

### Task A1：NLUT 最小推理与冻结

依据 [官方 README](https://github.com/semchan/NLUT/blob/66e271dd5740282d1b0f16d42ed5ca3f763ce818/README.md)，静态核验版本 `66e271dd5740282d1b0f16d42ed5ca3f763ce818`：预训练网络在测试时针对 source/reference 微调，然后使用生成的 LUT。官方依赖为 `torch==1.10.1`、`torchvision==0.11.2`，并有 `trilinear_cpp` 扩展；这些是复现起点，不是已验证适配 T10 的运行环境。

- [ ] 固定源码 SHA，从官方入口取得真实 checkpoint，并核对加载键、张量尺寸及 VGG 等外部资源。README 声明 MIT，但仓库 LICENSE 的版权标记与项目不一致；保存原文并核实采用范围，外部权重/数据许可单独记录，不把 README 当作所有资源的统一授权。
- [ ] 检查 `experiments/model.pth` 是否真实存在并正确加载；官方脚本找不到时会打印 `no checkpoint found` 后继续，适配器必须改为失败退出。默认 `dim=33` 与部分可视化示例硬编码 32 不一致，以 checkpoint 实际 shape 为准，不套用示例尺寸。
- [ ] 在隔离环境编译扩展，检查 `setup.sh` 中 CUDA 路径和实际 Toolkit；必要适配只保留最小补丁。若改 PyTorch 版本，必须重新验证插值前后向、微调及导出一致性。
- [ ] 移除/参数化 `parameter.py`、`parameter_finetuning.py`、图像/视频入口硬编码的 `CUDA_VISIBLE_DEVICES='0'`，在任何 CUDA 初始化之前由外层 UUID 固定设备；把补丁入版本记录。仅在外层设置环境变量不足以保证官方脚本不改绑 GPU。
- [ ] 首先运行单 source、单 reference 的官方图像推理。记录首次加载、分析、微调步数/耗时、最终 LUT 提取和全分辨率查表耗时，不能把查表速度当首次生成速度。
- [ ] 通过 [图像入口](https://github.com/semchan/NLUT/blob/66e271dd5740282d1b0f16d42ed5ca3f763ce818/inference_finetuning_image.py) 的 `finetuning_train` / `get_lut` 提取最终表。`others['LUT']` 是残差表，实际输出是 `interpolate(residual, rgb) + rgb`；固定 evaluator 必须包含这次相加和最终输出裁切，不能直接导出残差，也不能先裁切残差节点。
- [ ] 核验内部 `[batch,output_rgb,b,g,r]` 轴序，以及官方扩展的 `binsize=1.000001/(dim-1)` 边界规则；以该固定 evaluator 在标准网格上采样直接映射。残差节点加 identity 只是构造候选直接表，不代替插值/边界等价测试；`utils/LUT.py` 的 `lut_to_cube` 也不是文件 writer。
- [ ] 用同一冻结 LUT 重放原图，确认与该次官方输出一致，再通过 P1 导出和原生重放。保存微调前后 checkpoint/参数来源和 seed；下一张素材不得悄悄复用未标识的优化状态。

官方命令示例，**只在环境、权重与允许使用的图片齐备后执行**：

```bash
# 在 .9 的 NLUT 专用环境、官方源码目录执行。
: "${GPU_UUID:?Select an idle GPU UUID after patching hardcoded device selection}"
: "${CONTENT:?Set approved source image path}"
: "${REFERENCE:?Set approved reference image path}"
: "${OUT:?Set a new output image path}"
export CUDA_VISIBLE_DEVICES="$GPU_UUID"
python inference_finetuning_image.py \
  --content_path "$CONTENT" --style_path "$REFERENCE" --output_path "$OUT"
```

权重入口：[官方 Google Drive 文件夹](https://drive.google.com/drive/folders/1YqCKnfqzOPtmwdYAziGZMQ79iAI0_0ur)。下载/解包/加载尚未实测；失败时记录原因，不以随机初始化模型冒充预训练结果。上面的运行命令以设备选择补丁已完成为前提。

验收：实际单卡运行、有固定表及误差/显存/阶段耗时、有可导入的 `sourceMatch` CUBE。官方单参考入口不等于多参考训练。

### Task A2：CanonCGT 两阶段推理与正确合成

依据 [官方仓库](https://github.com/Jinwon-Ko/CanonCGT)，静态核验版本 `229a7d3ec24af19ea5d97bb136f0317762bb9261`。官方环境为 Python 3.9、PyTorch 1.12.1、torchvision 0.13.1、CUDA 11.3；代码和预训练权重声明 Apache-2.0，第三方数据/预设另行核验。

- [ ] 从 [官方 pretrained ZIP](https://drive.google.com/file/d/1SqzCXjdJ95TAhDYY9Z4TaQPuoqlEyfkT/view?usp=sharing) 取得兼容权重。当前 `pretrained/SSL.pth` 只有 2 bytes，不能加载；demo 默认路径又是 `SSL_updated_251111.pth`。必须显式指定实际权重，不把存在文件或 `strict=False` 无异常当作正确加载。
- [ ] 记录 state dict 缺失/额外键，并验证预期权重确实加载；仓库 `E2E.pth` 不能未经核验替代 SSL 权重。隔离 `TORCH_HOME`，事先准备 MobileNet 外部权重，不让运行服务隐式联网下载。
- [ ] 核对 YAML 覆盖后的 `cfg.gpu` 和路径。官方 demo 会把 `CUDA_VISIBLE_DEVICES` 改成 `cfg.gpu`；只在外层设置 GPU UUID、同时传 `--gpu 0` 会失去隔离。
- [ ] 保存 `canonicalize_LUT`、`restylize_LUT` 和 `restyled`。固定同一次真实 source/reference 分析后，定义 `F(x)=T_restylize(T_canonicalize(x))`，在目标网格上采样；不能相加、平均或倒序。
- [ ] 对照 [官方 LUT 实现](https://github.com/Jinwon-Ko/CanonCGT/blob/229a7d3ec24af19ea5d97bb136f0317762bb9261/models/base.py)：默认表 `[B,3,17,17,17]`，索引为 `[batch,output_rgb,b,g,r]`；`grid_sample` 的 5D `bilinear` 是三维插值，`padding_mode='border'`、`align_corners=True`。复制这些规则到冻结 evaluator，不能把 tensor 展平轴序凭经验猜成 RGB。
- [ ] 保留中间表和组合表，测官方 `restyled`、顺序固定 evaluator、导出重读三者差异。17³ 表上采样到 65³ 只提高组合表示精度，不创造模型原本没有的颜色细节。

```bash
# 在 .9 的 CanonCGT 专用环境、官方源码目录执行。
: "${GPU_UUID:?Select an idle GPU UUID}"
: "${SSL_CHECKPOINT:?Set a verified compatible checkpoint path}"
: "${CONTENT:?Set approved source image path}"
: "${REFERENCE:?Set approved reference image path}"
: "${OUT:?Set a new output image path}"
export TORCH_HOME=/root/run/rawlab-hamada/cache/torch
python demo.py --gpu "$GPU_UUID" --pretrained_path "$SSL_CHECKPOINT" \
  --inp_path "$CONTENT" --ref_path "$REFERENCE" --out_path "$OUT"
```

运行前须确认选用 YAML 没覆盖 GPU/权重选项；适配器应由自己管理设备，不继续依赖 demo 的全局副作用。[官方模型](https://github.com/Jinwon-Ko/CanonCGT/blob/229a7d3ec24af19ea5d97bb136f0317762bb9261/models/networks/SSL_training.py) 的 restyler 还使用 canonicalized source 的内容特征，因此组合表是该素材的 `sourceMatch`，并非仅参考决定的共享风格。

验收：权重正确加载、两阶段顺序和边界策略实测一致、可导入 CUBE、单卡显存/阶段耗时有记录。不得直接运行 `run.sh` 开始整套训练；官方训练路径与阶段前置 checkpoint 还需要适配。

### Task A3：统一滨田方向对照

- [ ] 用户提供数据后，固定同一批中性源图、同一组允许使用的参考、同一曝光/WB/强度，比较 Reinhard、色度 OT、NLUT、CanonCGT 和既有 Hamada v3；一项缺资源则明确标记未运行。
- [ ] 每模型分别测“为当前素材重新生成”和“同一冻结 LUT 作用于新素材”。后者不能再预测 LUT；分别展示风格一致性与素材适配差异。
- [ ] 同时检查人像、草地、蓝天/海水、白色物体、暖逆光与低饱和场景；不只挑好看的日光演示。记录源/参考权重和是否与训练/教师构造重叠。
- [ ] 写对照报告，决定哪个模型适合逐图匹配、哪个可作弱教师、哪些结果不适合共享 LUT。选择依据为实际效果、导出误差和成本，不按年份排名。

交付：`runs/<run_id>/comparison.md`、同尺寸对照预览、两个适配器的可重复命令、各模型 CUBE/私有元数据/QA、环境/权重来源记录。此阶段先用 Mac 现有外观导入验证，不要求新 UI 或公开 HTTP 服务。

## 5. 路线 B：自训多参考共享风格模型

此路线不是重新完整训练 NLUT 或 CanonCGT。先训练 **reference-set -> 固定风格 LUT** 的小型基线；需要逐图规范化时作为独立后续模块，不能偷偷把 source 输入风格分支。

### Task B0：照片集接收与用途确认

**此任务等待用户提供数据，不能用当前本地输出替代。** 接收后先区分：基础/中性照片、目标风格参考、同内容真实前后配对、已有 LUT 产生的合成对。只有 RAW/基础照片时，能够训练已知合成风格恢复，但不能据此声称获得真实滨田风格监督；只有参考成片时，也没有同内容重建真值。

已有资产用途：

|本地资产|可用于什么|不允许默认用于什么|
|---|---|---|
|`output/hamada-style-study/manifest.json` 的 52 张作品/7 个系列|来源追溯、人工观察/研究对照；原 manifest 没训练许可字段|训练、上传到 `.9`、打包再分发|
|Hamada v1/v2/v3 CUBE|经确认后作为人为创作的弱教师/对照|摄影师原始 LUT 或真实调色真值|
|v1/v2/v3 的 12 个共同 NAS 源 ID 及其派生 JPEG|用户允许后作为历史回归样片|新独立 held-out 集或自动获准训练集|
|`output/adaptive-tone-regions/`|既有统计/分区回归及合成教师实验|真实 AI 推断或真实滨田监督|

不要采用已拒绝的 `draft-a` / `drafts` 作为最终教师；对照说明详见各版本 README。同一 RAW 的 neutral、v1/v2/v3、裁切、缩图和拼图必须归入同一来源组。

- [ ] 建立私有 JSONL manifest，每行记录：`asset_id`、相对 `path`、`source_id`、`scene_group`、`role`、`style_id`、`pair_id`、`split`、输入颜色契约/ICC 处理、`rights_status`、允许训练/服务器传输标记、`label_origin`。
- [ ] `role` 限定为 `base`、`reference`、`paired_input`、`paired_target`；`label_origin` 明确为 `real_pair`、`authored_lut`、`model_teacher` 或 `unpaired`。用途/授权未确认的条目不进入训练读取器，也不传输。
- [ ] 先按 `source_id` 和 `scene_group` 分组切分再生成衍生图。固定 train/val/test 清单，测试集不用于挑选教师、调阈值或选择 checkpoint。重复源跨 split、配对缺项、参考与测试源重叠必须使校验失败。
- [ ] 明确主风格组，以明亮日光为主；暗调/室内作品单独标识用于冲突或域外测试，不无条件混合。参考权重按图片而非像素数，重复项不得通过重复添加提高贡献。
- [ ] 用 RawLab 相同中性显影和明确 WB/曝光记录生成 SDR sRGB，优先保留无损训练代理；参考无 ICC 时明确记录人工确认的空间假设。接收只含 JPEG 时记录其已有处理与压缩限制。

验收：数据数量/来源/用途/切分可追溯，读取器拒绝未授权或泄漏样本。用户未提供时状态保持 `awaiting_user_data`，训练未开始；不要自动抓图补齐数量。

### Task B1：可检验的小型集合模型

先实现无需额外骨干权重的小网络，降低部署复杂度：共享三层 stride-2 CNN（16/32/64 通道）加池化/线性层得到 128 维每图表示；归一化权重平均聚合；两层 MLP（128 -> 256 -> `17^3*3`）预测恒等 LUT 的残差。此为首轮基线，不是已验证最佳模型。

固定定义 `T = identity + 0.2*tanh(residual)`，保存裁切前数值，渲染时使用明确的 `[0,1]` 通道裁切和三线性插值。17³ 学习表、256 长边代理、1-6 张参考；参考权重为 0 的图排除，相同 asset 的重复条目按最大权重保留一份、再归一化，不累加重复权重，所有权重为 0 则报错。

- [ ] 先写集合排序、重复图、0 权重、源图隔离、恒等/已知变换、NaN/Inf、输出形状和重放测试，再实现模型与数据读取器。
- [ ] 每个训练样本用不同内容的 `X_a/X_b/X_c` 和同一已知函数 `F_s`：参考为 `F_s(X_b),F_s(X_c)`，预测的表作用于未作参考的 `X_a`，目标为 `F_s(X_a)`。训练源/参考来自不同源 ID。
- [ ] 合成风格必须有多种已知、平滑、合法的全局颜色函数，包括恒等、不同影调、蓝绿选择性及暖色变化。仅训练一个固定 Hamada v3 教师会允许模型忽略参考，不能证明多参考风格提取。
- [ ] 训练损失从重建 L1 开始，加残差二阶平滑、同风格不同参考子集的表一致性、恒等样本的恒等项及裁切前越界惩罚。初始权重设 `1 / 0.01 / 0.1 / 0.01 / 0.01`，写入配置；只在验证集校准，不称通用最佳值。
- [ ] 先比较单参考、等权多参考和质量/用户加权；复杂集合注意力或大骨干只有在这组对照揭示具体不足后才引入。

学术模型可作第二类弱教师，但必须标记 `model_teacher`：同一参考作用于多种标准源图后共同拟合共享函数，保留逐图教师与共享函数间的残差。不能直接平均逐图 CUBE，也不能把局部/素材适配能力无损继承到全局表。仅在教师得到用户认可且拟合残差可接受时加入训练。

验收：固定已知风格时更换参考内容，输出仍能重建该风格；固定内容更换已知风格，模型确实改变表。这两项与滨田方向主观质量分别报告。

### Task B2：单卡试训、恢复，再扩展多卡

- [ ] 自训环境验证实际 CUDA/PyTorch，先 FP32、单张空闲卡。初始 batch size 2、Adam 学习率 `1e-4`；先 200-step 流程测试，再最多 2000-step 小规模试训，每 100 steps 验证。它们是起始预算，不是生产训练完成门槛。
- [ ] 记录 loss 各项、参考数量、残差幅度、裁切比例、显存峰值、吞吐、梯度有效性；在少量已知变换上能过拟合，并证明真实照片不同内容的验证没有退化为恒等/固定表。
- [ ] checkpoint 保存模型/优化器、step、随机状态、配置、源码版本和数据切分版本；中断后恢复，并核验下一步 loss/输出在约定浮点误差内一致。输出写入新的 run，不覆盖旧 checkpoint 或 Hamada v1/v2/v3。
- [ ] 数据和单卡均通过后再启用 AMP/DDP。逐项实测 T10 支持的精度，不预设 BF16；不要因为共有四张卡就强制 4 卡。保持每卡显存余量、现有服务和其他会话资源。
- [ ] DDP 使用不同数据 shard/随机采样，仅 rank 0 写共享产物；保存 global batch size，GPU 数改变时说明训练配置差异。校验重复参考去重和恢复语义没有因多卡改变。

下面是 **B1/B2 需实现的 CLI 目标**，不是当前已有命令。执行会话先让 `--help` 与单元测试通过，再按实际 manifest 和 checkpoint 运行：

```bash
# 在 .9 的自训专用环境与 experiments/hamada-neural/ 目录执行。
: "${GPU_UUID:?Select an idle GPU UUID}"
: "${DATA_MANIFEST:?Set the approved JSONL manifest path}"
: "${RUN_DIR:?Set a new run directory}"
export CUDA_VISIBLE_DEVICES="$GPU_UUID"
python -m hamada_neural.cli validate-data --manifest "$DATA_MANIFEST"
python -m pytest tests -q
python -m hamada_neural.cli train --manifest "$DATA_MANIFEST" \
  --config configs/set-lut-smoke.yaml --max-steps 200 --output "$RUN_DIR"
```

训练 CLI 必须支持 `--resume`，验证 CLI 必须拒绝 `awaiting_user_data`。长任务只在执行会话中明确启动并留下 PID/日志/检查点位置；停止仅停止自己启动的任务，不执行全局 `pkill python`。

### Task B3：滨田方向校准与共享 LUT 交付

- [ ] 在多风格已知监督验证通过后，用用户提供、允许使用的滨田方向参考组检验跨内容风格；有真实配对则用于对应监督，无配对则走人工选择/弱教师校准并保持标签来源。
- [ ] 同一参考组只生成一次 `referenceStyle` LUT，再固定用于所有保留源图，不做逐图自动曝光/WB。另列可关闭的素材适配实验，不能把适配结果计入共享表能力。
- [ ] 测参考排序、增删、重复、1/2/4/6 张子集和权重变化。排序/重复测试表值误差应在数值容差 `1e-6` 内；子集漂移、冲突组和主观质量用验证数据校准并报告分布，不写未经测得的“还原率”。
- [ ] 混合日光/暗调时展示不同候选或明确降低自动强度，允许用户选择主参考；不得输出一个看似高置信度但未经校准的“唯一风格”。
- [ ] 检查灰阶/色相/饱和度斜坡、低色度图像、过亮/过暗输入、肤色与相近木材/墙面、蓝衣与天空。RGB 相同的像素必须有相同输出；语义修正另存配方，不能声称在普通 CUBE 内。

交付：可重复训练/推理 CLI、checkpoint、配置/数据切分记录、`referenceStyle` CUBE/私有元数据/QA、同表跨场景预览、子集/冲突/低信息评测及明确失败案例。只对有真值的同内容图像算 PSNR/SSIM/色差；无配对参考不能逐像素当真值。

## 6. 验收与 Mac 交接

|门槛|必须提供的证据|
|---|---|
|运行真实|正确 checkpoint 加载；实际 GPU/显存/日志；不能以文件存在或静态审查代替运行|
|固定映射|真实图分析后冻结；同 RGB 同输出；两阶段合成顺序、边界、裁切均可复现|
|导出忠实|CUBE 重读及原生重放；采样最大通道误差 `<=0.02`，失败不发布；模型/冻结/序列化误差分开|
|共享风格|同参考组的一张表固定用于新素材；不混入逐图补偿，测试源与训练/参考无泄漏|
|滨田方向|光影层次、肤色、蓝绿背景、暖色点缀和系列一致性分别观察；漂亮单图不是跨素材验收|
|数据/环境|来源、允许用途、代码/权重/依赖版本、设备/精度和冷/热阶段耗时齐全|
|恢复/回退|中断可恢复；失败不安装无效表；可保留已有 OT/配方方法；共享机器既有服务未改变|

Mac 最小交接方式是 **导入生成的显示-sRGB CUBE**，不先增加远程服务。需要在线模型调用时另立接口任务，优先仅本机/SSH 隧道访问的原型，并单独落实鉴权、图片传输允许范围、取消和错误恢复；不默认对 LAN/公网开放端口。

研究代码只改 Python 工具时，跑其定向测试及原生 CUBE 验证；真正改到 Mac/C ABI/渲染后才补相应生产回归：

```bash
# 在 Mac 仓库根目录执行，使用已配置的本机构建。
ctest --test-dir lutools/build-macos --output-on-failure
bash RawLabMac/tests/ai-statistics.sh
bash RawLabMac/tests/ai-data.sh
bash RawLabMac/tests/ai-workflow.sh lutools/test-assets/DSC00340.ARW
bash RawLabMac/tests/ai-render.sh lutools/test-assets/DSC00340.ARW --local-ot
bash RawLabMac/build.sh
```

现有 `ai-render.sh --local-ot` 验证统计基线，不验证新神经表；执行会话还须为生成的表增加真实 RAW 的 CPU/Force Metal、整体强度 0/1/2、单张/批量及预览/导出一致性测试。不能把旧测试通过记成新模型通过。

颜色/影调独立控制可在固定模型映射的输入/输出 Oklab 间分别混合 L 与 a/b，再固定色域策略后烘焙；它必须作为有版本的渲染步骤与测试，不能对密集表硬套 40 参数。整体 LUT 强度继续由应用处理、不烘焙；素材曝光/WB 也不纳入公开风格表。

## 7. 两个执行会话的启动说明

### 部署会话

```text
请执行 docs/superpowers/plans/2026-10-11-hamada-neural-deployment-training.md
的 P0、P1 和路线 A。在 192.168.88.9 用专用目录/隔离环境部署并实际验证
NLUT 和 CanonCGT，提取冻结表、导出显示-sRGB CUBE，完成原生重放 QA。
目标仍是滨田英明明亮日光方向。先单卡，保留已有 ComfyUI 和其他服务。
用户照片集尚未提供；先用许可允许的示例/合成输入，不传 NAS 或公开作品。
保留 RawLab 当前未提交改动，不改训练模块，不自动 commit/push 或公开服务。
完成后交接固定源码/权重/环境、适配器、冻结接口、CUBE、误差、性能和阻塞。
```

### 训练会话

```text
请执行 docs/superpowers/plans/2026-10-11-hamada-neural-deployment-training.md
的路线 B，沿用 P1 的 CUBE/冻结接口；先核对部署会话的共同工具所有权。
目标是仅参考组决定、可跨内容复用的滨田英明明亮日光风格 LUT，不是平均
逐素材匹配表。用户尚未提供照片集：可以准备模型/manifest 校验和合成测试，
但真实训练和照片传输等待用户提供数据并确认用途。`.9` 有四张 16GB T10，
先单卡验证再考虑 DDP，不能终止现有服务或把显存视为一个 64GB 池。
保留当前未提交改动，不改部署适配器，不自动 commit/push、抓图或发布。
交接模型/config/checkpoint、数据切分、同表跨场景评测和明确失败案例。
```

两个会话每完成任务留下：已完成勾选项、实际命令/退出码、文件位置、运行中任务信息、尚未通过的门槛和下一项所需输入。照片集等待只阻止真实数据阶段，不阻止环境与数学链路准备；权重/依赖未取得则明确阻止对应模型验收，不用演示图替代。

## 8. 官方复核入口

- NLUT：[作者仓库](https://github.com/semchan/NLUT)、[论文](https://arxiv.org/abs/2303.09170)、[项目页](https://semchan.github.io/NLUT_Project/)。
- CanonCGT：[作者仓库](https://github.com/Jinwon-Ko/CanonCGT)、[论文](https://arxiv.org/abs/2606.01638)、[固定版本 demo](https://github.com/Jinwon-Ko/CanonCGT/blob/229a7d3ec24af19ea5d97bb136f0317762bb9261/demo.py)、[配置/路径](https://github.com/Jinwon-Ko/CanonCGT/blob/229a7d3ec24af19ea5d97bb136f0317762bb9261/configs/config.py)。
- 工程颜色与导出：[RawLab 颜色契约](../../../lutools/docs/color-contract.md)、[原生导入](../../../RawLabMac/Sources/LookLibrary.swift)、[配方冻结/编译](../../../RawLabMac/Sources/AIColorLook.swift)。

官方入口核验于 2026-10-11；权重可下载性、实际运行及性能仍由执行会话验证。资源许可分开覆盖源码、权重、骨干、数据和预设；公开可读不等于已获训练或再分发授权。
