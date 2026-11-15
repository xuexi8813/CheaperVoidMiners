# Cheaper Void Miners for GTNH

A GTNH 2.9.0 fork of Cheaper Void Miners that makes void mining accessible from early game, with five-tier void excavators, five-tier void fluid drillers, three enhanced Void Miner EX stages and three non-electric wooden machines.

## 项目定位

本项目是 GTNH 2.9.0（MC 1.7.10）环境下 kuolemax/CheaperVoidMiners 的降档加强分支：在游戏早期即可获得低门槛的虚空采矿能力，并通过逐档加强把产能延伸到中后期。衍生出的虚空流体钻机、虚空挖土机与木制三机覆盖了流体、普通资源方块与矿石三类虚空产出。

## 机器清单（16 台）

### 虚空矿机 EX I / II / III（多方块）

- 电压档位 LV / HV / IV，构造继承上游 TIER_MULTIPLIER（4 / 8 / 16），输出端统一 ×16 加强。
- 可向多方块输入惰性气体提升产量（氖 / 氪 / 氙 / 鿫 依次加成），带白/黑名单过滤器（见下文）。
- 继承 GT 原版虚空矿机行为并叠加本分支功能（维度覆写、输出加强）。

### 虚空流体钻机（五档，多方块）

- 铁 LV / 钢 MV / 钛 HV / 硅岩 LuV / 无尽 UV。
- 无需钻井管道、不耗尽区块流体；以 1000mB × 档位系数（16 / 64 / 256 / 1024 / 4096）× 并行数 × 钻头加成的速率抽取所选维度的地下流体。
- 编程电路（配置 1..N）选择该维度流体表第 N 项；**必须插入编程电路才会启动**。
- 输入总线中的钻头提升产量（铁 ×1.25 → 无尽 ×3.0，取最高档），每周期有小概率消耗钻头。

### 虚空挖土机（五档，多方块）

- 铁 LV / 钢 MV / 钛 HV / 硅岩 LuV / 无尽 UV。
- 从虚空挖取所选维度的非矿物资源方块；资源表由配置文件驱动（`cheapervoidminers.cfg`）。
- 产出为档位系数 16 / 64 / 256 / 1024 / 4096 × 并行数 × 钻头加成（数值加强前为 1 / 4 / 16 / 64 / 256）。
- 编程电路可选：配置 1..N 直接锁定资源表第 N 项，配置 0 或不插电路按表内权重加权随机。
- 无尽档并行随输入电压指数提升（8^(输入等级 − UV)，EU/t 同步多安培倍增）。

### 木制三机（无电单方块，GT 标准小机器形态）

- **木制虚空矿机**：从主世界矿物生成表拉取矿石物品（不破坏方块），每 100 tick 产出 1 件；6 个输出槽，满仓待机、手动或传送带取出。GUI 提供幽灵编程电路槽直接点选：配置 1..N 选定单一矿脉（含其贫矿），N+1 为贫矿池，0/无电路为全部矿石加权混合。
- **木制虚空流体泵**：抽取主世界地下流体，每 200 tick 产 100mB 至内部储罐；GUI 幽灵电路槽选择目标流体，储罐满或不兼容时待机。合成仅需原木、青铜中型流体管道与烧制粘土桶（Iguana Tweaks）。
- **木制虚空挖土机**：按所选维度产出非矿物资源方块；资源表由配置文件驱动。

## 多方块结构

- 流体钻机 / 挖土机 / EX 矿机均为 3×3×7 立柱结构，与 GT 原版流体钻井机同形（控制器位于正面底层中央）。
- 机壳与 GT 框架随档位变化：铁（ULV 机壳）/ 钢 / 钛 / 硅岩 / 无尽档对应各自主机壳与框架材料；硅岩 / 无尽档无专用机壳，分别以钨钢钻井机壳与中子素采矿机壳代替。
- 主机制造走组装机配方：机壳 ×4、GT 框架 ×4-8、超级缸、电泵、钻头与配置 12 编程电路；框架数与时长随档位递增。

## 内置维度覆写（合并自 Void-Miner-Tweak-Mod）

- 在控制器槽位放入 GTNEI 维度显示方块，即可让 EX 矿机挖取任意维度的矿表——包括没有 GalacticGreg 注册的私人/个人维度（空矿表时过滤按钮仍可用）。
- 每个维度需要先到访一次（原版虚空矿机机制），否则 GUI 显示警告。
- 独立版 vmtweak 在场时本 mod 的注入自动让位，行为与 vmtweak 一致；卸载 vmtweak 后自动接管，旧存档 NBT 键自动迁移。

## 过滤器：按矿物材料宽匹配

- 白/黑名单条目按**矿物材料**匹配而非精确 meta：自然挖取的任意石器/大小矿变体均可直接作为过滤条目，命中产出矿石（修复上游精确匹配导致白名单永不命中、产出被销毁的问题）。
- GUI 过滤弹窗勾选与输入总线放矿石两条通道同时生效；维度覆写变化时过滤弹窗自动刷新。

## NEI 支持

- 按维度分页的产出配方：钻机流体、挖土机资源、木矿机按矿脉拆分（含贫矿归属），与机器实际产出同源。

## 兼容性

- **GT-Miao-Blueprint 蓝图投影仪**：多方块结构定义按具体类缓存，避免高频读取时反复重建；机壳贴图索引全部取自 GT Casings 注册表，投影构建不再触发 `casingIndex` 校验异常。
- **vmtweak 共存**：见"内置维度覆写"。
- **VMT 槽位约定**：控制器槽位沿用上游 vmtweak 的 `mInventory[1]` 约定，NBT 键 `vmd$lastDimOverride` 与旧键 `vmtweak$lastDimOverride` 兼容读取。

## 本地化

- zh_CN 机器名 / GUI / tooltip 全覆盖；GT 语言覆盖文件由 mod 自动写入游戏根目录，无需额外 lang 包。

## 构建

```bash
gradle build        # 完整构建（发布 jar）
gradle runServer    # 开发环境服务器冒烟
```

- 需要 JDK 21+（GTNH 标准工具链，经 Jabel 以 Java 8 字节码输出，兼容 1.7.10 运行时）。
- 依赖 GT5u / ModularUI2 / NEI 等 GTNH maven 产物，开发运行时对齐整合包实际版本。

## CI

- push / PR 到 master 自动构建与测试。
- 版本 tag 触发 GitHub Release 发布。

## 致谢

- 上游项目 [kuolemax/CheaperVoidMiners](https://github.com/kuolemax/CheaperVoidMiners)。
- [GTNH 团队](https://github.com/GTNewHorizons)（GT5u、StructureLib、ModularUI 等全生态）。
- [Void-Miner-Tweak-Mod（reobf）](https://github.com/reobf/Void-Miner-Tweak-Mod)——维度覆写功能的出处，已原生合并。
