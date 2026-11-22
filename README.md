# Cheaper Void Miners for GTNH

A GTNH 2.9.0 fork of Cheaper Void Miners that makes void mining accessible from early game, with five-tier void fluid drillers, three enhanced Void Miner EX stages and two non-electric wooden machines.

## 项目定位

本项目是 GTNH 2.9.0（MC 1.7.10）环境下 kuolemax/CheaperVoidMiners 的降档加强分支：在游戏早期即可获得低门槛的虚空采矿能力，并通过逐档加强把产能延伸到中后期。衍生出的虚空流体钻机、虚空矿机 EX 与木质两机覆盖了流体与矿石两类虚空产出。

## 机器清单（10 台）

### 虚空矿机 EX I / II / III（多方块）

- 电压档位 LV / HV / IV，构造继承上游 TIER_MULTIPLIER（4 / 8 / 16），输出端统一 ×16 加强。
- 能源仓电压每高于本机档位一级，产量 ×4（低于或等于本机档位不加成，按最高单仓电压档计算），与 ×16 输出加强、惰性气体加成、批量模式全部叠乘（例：EX I 插 HV 仓 ×16、插 UV 仓 ×16384；EX III 插 UV 仓 ×64）。
- 可向多方块输入惰性气体提升产量（氖 ×4 / 氪 ×8 / 氙 ×16 / 鿫 ×64），带白/黑名单过滤器（见下文）。
- 继承 GT 原版虚空矿机行为并叠加本分支功能（维度覆写、输出加强）。

### 虚空流体钻机（五档，多方块）

- 铁 LV / 钢 MV / 钛 HV / 硅岩 LuV / 无尽 UV。
- 无需钻井管道、不会耗尽区块流体；结构不含流体输入仓，直接抽取所选维度的地下流体。
- 每 100 tick 产出 1000mB × 4^(能源仓电压档 − LV + 1) × 16^(机器档 − LV) × 4^(钻头阶级 + 1)；电压项按 GT 电压档线性顺延（LV=4、MV=16、HV=64、EV=256、IV=1024、LuV=4096、ZPM=16384、UV=65536、UHV=262144，取实际可用的最高输入电压档），无尽钻机的电压项基数换 16（每档 ×16）。
- 能源仓接受 LV（最低机器电压）起的任意电压档。
- 耗电跟随能源仓电压档（每档 1 安培），不随机器档位固定。
- 输入总线中的钻头提升产量（铁 ×4 / 钢 ×16 / 钛 ×64 / 硅岩 ×256 / 无尽 ×1024，取最高阶级；无钻头不提升），每完成一个周期有 0.1% 概率消耗 1 个钻头。
- 编程电路（配置 1..N）选择该维度流体表第 N 项（顺序与 NEI 展示一致）；**必须插入编程电路才会启动**。
- 支持 GT5u 销毁模式切换（控制器 GUI 按钮）：默认"销毁过量流体"，输出仓满时过量流体被销毁、机器继续运行；切至"不销毁"后输出仓满时机器待机，腾空后自动恢复。

### 木质两机（无电单方块，GT 标准小机器形态）

- **木质虚空矿机**：从主世界矿物生成表拉取矿石物品（不破坏方块），每 100 tick 产出一组（64 件）；6 个输出槽，一组恰好占满 1 个输出槽，满仓待机、手动或传送带取出。GUI 提供幽灵编程电路槽直接点选：配置 1..N 选定单一矿脉（含其贫矿），N+1 为贫矿池，0/无电路为全部矿石加权混合。
- **木质虚空流体泵**：抽取主世界地下流体，每 200 tick 产 100mB 至内部储罐；GUI 幽灵电路槽选择目标流体，储罐满或不兼容时待机。合成仅需原木、青铜中型流体管道与烧制粘土桶（Iguana Tweaks）。

## 多方块结构

- 流体钻机 / EX 矿机均为 3×3×7 立柱结构，与 GT 原版流体钻井机同形（控制器位于正面底层中央）。
- 机壳与 GT 框架随档位变化：铁（ULV 机壳）/ 钢 / 钛 / 硅岩 / 无尽档对应各自主机壳与框架材料；硅岩 / 无尽档无专用机壳，分别以坚固钨钢机器机壳与中子素采矿机壳代替。
- 流体钻机走组装机配方：对应档位机壳 ×4、GT 框架 ×4-8、超级缸、电泵、钻头与配置 12 编程电路（电路为不消耗的配置标记，不会作为材料被消耗）；框架数与时长随档位递增。
- EX 矿机获取走无序合成 1:1 兑换：GT 采矿机 LV（Basic Miner）→ EX I、GT 采矿机 HV（Advanced Miner）→ EX II、矿石钻井机 Mk I（Ore Drilling Plant 控制器）→ EX III。
- 木质虚空矿机为工作台有序合成：原木 ×6、燧石 ×2、木棍 ×1（3×3 排布：中列上下为燧石、中心为木棍，其余为原木）。

## 内置维度覆写（合并自 Void-Miner-Tweak-Mod）

- 在控制器槽位放入 GTNEI 维度显示方块，即可让 EX 矿机挖取任意维度的矿表——包括没有 GalacticGreg 注册的私人/个人维度（空矿表时过滤按钮仍可用）。
- 独立版 vmtweak 在场时本 mod 的注入自动让位，行为与 vmtweak 一致；卸载 vmtweak 后自动接管，旧存档 NBT 键自动迁移。

## 过滤器：按矿物材料宽匹配

- 白/黑名单条目按**矿物材料**匹配而非精确 meta：自然挖取的任意石器/大小矿变体均可直接作为过滤条目，命中产出矿石（修复上游精确匹配导致白名单永不命中、产出被销毁的问题）。
- GUI 过滤弹窗勾选与输入总线放矿石两条通道同时生效；维度覆写变化时过滤弹窗自动刷新。

## NEI 支持

- 按维度分页的产出配方：钻机流体、木矿机按矿脉拆分（含贫矿归属），与机器实际产出同源。

## 兼容性

- **双端必需**：FML 握手经 `@NetworkCheckHandler` 双向校验，客户端或服务器任一端未装本 mod 均拒连（本 mod 物品全部挂在 GT 方块 meta 上，不注册独立 Block/Item id，不会触发 FML 的 id 映射拒连，因此必须显式双向要求）。
- **GT-Miao-Blueprint 蓝图投影仪**：多方块结构定义按具体类缓存，避免高频读取时反复重建；机壳贴图索引全部取自 GT Casings 注册表，投影构建不再触发 `casingIndex` 校验异常。
- **vmtweak 共存**：见"内置维度覆写"。
- **VMT 槽位约定**：控制器槽位沿用上游 vmtweak 的 `mInventory[1]` 约定，NBT 键 `vmd$lastDimOverride` 与旧键 `vmtweak$lastDimOverride` 兼容读取。

## 本地化

- zh_CN 机器名 / GUI / tooltip 全覆盖；GT 语言覆盖文件由 mod 自动写入游戏根目录，无需额外 lang 包。

## 构建

```bash
./gradlew build        # 完整构建（发布 jar）
./gradlew runServer    # 开发环境服务器冒烟
```

- 需要 JDK 21+（GTNH 标准工具链，经 Jabel 以 Java 8 字节码输出，兼容 1.7.10 运行时）。
- 依赖 GT5u / ModularUI2 / NEI 等 GTNH maven 产物，开发运行时对齐整合包实际版本。

## CI

- push / PR 到 master / main 自动构建与测试。
- 版本 tag 触发 GitHub Release 发布。

## 致谢

- 上游项目 [kuolemax/CheaperVoidMiners](https://github.com/kuolemax/CheaperVoidMiners)。
- [GTNH 团队](https://github.com/GTNewHorizons)（GT5u、StructureLib、ModularUI 等全生态）。
- [Void-Miner-Tweak-Mod（reobf）](https://github.com/reobf/Void-Miner-Tweak-Mod)——维度覆写功能的出处，已原生合并。
