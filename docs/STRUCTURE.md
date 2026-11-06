# 项目结构

SunRShader AstralSky 由两个可交付物和一套离线资产流水线组成：

| 可交付物 | 产物 | 作用 |
| --- | --- | --- |
| Fabric 伴生模组 | `build/libs/astralsky-<version>.jar` | 校验观测配置，并把配置写入光影包 |
| Iris 光影包 | `build/distributions/SunRShader-AstralSky-<version>.zip` | 星空、日月与大气渲染本体 |

目标环境：Minecraft 26.3、Fabric Loader 0.20.1、Iris 1.11.7、Java 25。

## 顶层目录

```text
E:\ProjectB
├─ src\                         Fabric 模组源码
│  ├─ main\java\dev\astralsky\astro\        纯计算层（无 Minecraft 依赖）
│  ├─ main\resources\                        fabric.mod.json、语言文件
│  ├─ client\java\dev\astralsky\client\      客户端入口、配置、编辑器界面、Iris 桥
│  └─ test\java\dev\astralsky\               JUnit 5 黄金参考测试
├─ shaderpack\
│  └─ SunRShader-AstralSky\     生产光影包（SunRShader/Photon 全量管线 + 天文层）
├─ tools\                       星表栅格化与 VSOP87 系数表生成（独立 javac 编译，不进 Gradle）
│  └─ vsop87d\                  VSOP87D 完整级数（8 个天体的权威原始数据）
├─ scripts\                     build / deploy / 星表与 VSOP87 生成（PowerShell）
├─ docs\                        阶段验证记录、配置说明、星表与行星说明、路线图
├─ THIRD_PARTY_LICENSES\        NASA LRO 与 Tycho-2 声明
├─ build.gradle                 构建、测试堆上限、光影包打包任务
└─ gradle.properties            版本与依赖坐标（唯一版本来源）
```

规模：Java 20 个文件（含 10 个测试类），手写代码约 2.9k 行，另有 1,066 行由工具生成的 VSOP87 系数表；GLSL 与 program 593 个文件约 30.9k 行；光影包 640 个文件约 35.5 MB。

## 计算层：Java 与 GLSL 的镜像实现

天文算法存在两份必须保持一致的实现，这是本仓库最重要的结构约束：

- `src/main/java/dev/astralsky/astro/AstronomyMath.java`：double 精度参考实现；
- `shaderpack/SunRShader-AstralSky/shaders/include/sky/astronomy.glsl`：float 安全实现
  （用整数儒略日 + 小数日避免 32 位浮点丢精度）。

unit 测试拿 Java 实现当黄金参考，去校验着色器侧公式与运行时集成结果，所以改动任一侧都必须同步另一侧。

`astronomy.glsl` 分两段条件编译：上半段 `ASTRO_POSITION_ONLY` 之外也供光照/阴影使用，下半段是星空、银河、月面渲染，只有渲染阶段引入。

行星层是三份镜像，靠生成器保证一致：

| 侧 | 文件 | 角色 |
| --- | --- | --- |
| Java | `astro/Vsop87.java`、`astro/Vsop87Tables.java` | double 精度级数求值与生成系数表 |
| Java | `astro/PlanetEphemeris.java` | 地心赤经赤纬、相位角、照度、星等、视半径、距角 |
| GLSL | `include/sky/vsop87.glsl` | 同源同截断的 float 级数表（仅计算通道引入） |
| GLSL | `include/sky/planet_state.glsl` | 每帧一次的行星状态计算，写入 `planet_data_img` |
| GLSL | `include/sky/planets.glsl` | 延迟着色阶段按图取回并绘制 |

`tools/Vsop87Generator.java` 从 `tools/vsop87d/*.csv` 同时产出 Java 与 GLSL 表，截断阈值只写一处。`Vsop87Test` 校验两侧数组长度一致，并复测截断误差。

## 配置数据流

```text
<game>/config/astralsky.json
  → AstralSkyConfigStore.load()       解析 + JSON 字段迁移
  → AstralSkyConfig.validated()       范围/日期钳制
  → ShaderConfigBridge.render()       生成 ASTRO_CFG_* 宏
  → <pack>/shaders/lib/astro_runtime_config.glsl
  → lib/astro_config.glsl             校验 ASTRO_CFG_SCHEMA，回退内置默认值
  → include/sky/astronomy.glsl        编译期常量参与计算
```

- `K` 键打开 `AstralSkyConfigScreen`；保存后写 JSON、重新下发 GLSL 宏，并通过
  `IrisReloadBridge`（反射调用 Iris v0 API）重建管线，无需 `F3+T`。
- 三处版本号必须同代更新：`AstralSkyConfig.CURRENT_SCHEMA_VERSION`(2)、
  `ShaderConfigBridge.RUNTIME_CONFIG_SCHEMA`(3)、`astro_config.glsl` 中的
  `ASTRO_CFG_SCHEMA != 3` 判断。不匹配时着色器忽略运行时配置并退回包内默认值。
- `ShaderConfigBridge` 同时支持解包目录和 `shaderpacks/*.zip` 两种形态，并按
  `config/iris.properties` 的 `shaderPack` 定位当前选中的包。

## 光影包结构

`shaders.properties` 是入口：屏幕布局、选项声明、自定义纹理绑定都在这里
（`screen.astronomy` 只暴露视觉效果，观测参数只走 `K` 界面）。核心分层：

- `lib/`：运行时配置注入点（`astro_config.glsl` 与 `astro_runtime_config.glsl`）；
  `include/global.glsl`、`include/buffers.glsl` 提供全局宏、采样器与缓冲区声明；
- `include/sky/`：`astronomy.glsl`（天文）、`sky.glsl`（日月盘、月相、星空合成）、
  `vsop87.glsl` + `planet_state.glsl`（行星级数与每帧状态）、`planets.glsl`（行星绘制）、
  `atmosphere.glsl`（大气散射）、`clouds/`（多层体积云）、`aurora`、`rainbow`；
- `include/lighting/`：`astronomical_light.glsl` 把真实日月方向换算给直射光、
  阴影相机、云影与 SSRT；`light_color`/`diffuse`/`specular` 使用同一方向；
- `program/`：68 个渲染程序（d0 天空图、d1 云、d4 延迟着色、c1..c22 后处理链）；
  `d4a_generate_sky_sh.csh` 是天空 SH 与行星表的唯一计算通道；
- `world0` / `world1` / `world-1`：主世界、下界、末地的阶段程序；`world1` 与
  `world-1` 各 139 个文件，比 142 个文件的 `world0` 少了 `prepare.*` 与
  `deferred4_a.csh`（因此下界与末地不计算行星）；
- `textures/`：四份星表/月面纹理，由 `ASTRO_CATALOG_MODE` 在
  `shaders.properties` 中切换绑定；
- `settings.glsl`：Photon/SunR 原有开关 + `ASTRO_*` 默认值。

天文层用 `ASTRO_REAL_SKY` 作为开关，行星另外要求 `ASTRO_PLANETS`，并且绝大多数
接入点都同时要求 `WORLD_OVERWORLD`，下界与末地保持 SunR 原始行为。

## 资产与生成工具

| 资产 | 尺寸 | 来源 |
| --- | --- | --- |
| `textures/bsc5-stars.png` | 4096×2048 | BSC5P |
| `textures/hipparcos-stars.png` | 8192×4096 | Hipparcos I/239 |
| `textures/tycho2-stars.png` | 16384×8192 | Tycho-2 I/259 |
| `textures/lroc-moon-2k.png` | 2K | NASA LRO/LOLA 彩色图 |

纹理已提交进仓库，构建与启动都不需要联网。重新生成顺序固定：Hipparcos 必须在
Tycho-2 之前（Tycho-2 合并 Hipparcos 亮星层，且尺寸恰为两倍）。工具链为
`tools/StarAtlasGenerator.java`，由 `scripts/generate-*.ps1` 直接 `javac` 调用，
不参与 Gradle 依赖图；下载缓存在 `build/catalog-source/`（已被忽略）。

VSOP87 走同一条离线路线：`tools/vsop87d/*.csv` 是完整级数（约 1.3 MB，CDS VI/81
经 PyMeeus 导出），`tools/Vsop87Generator.java` 按振幅截断并同时写出
`Vsop87Tables.java` 与 `vsop87.glsl`，入口是 `scripts/generate-vsop87.ps1`。
两份产物都提交进仓库，平时不需要重跑；改截断阈值后必须一起提交生成结果，因为
`Vsop87Test` 会校验两侧数组长度一致。详见 `docs/PLANETS.md`。

## 构建、测试与部署

```powershell
.\scripts\build.ps1                 # gradlew build（含测试与光影包打包）
.\scripts\build.ps1 -Task test      # 只跑测试，maxHeapSize = 2g
.\scripts\deploy-dev.ps1 -Build     # 部署到 D:\PCL2\.minecraft\versions\26.3-Fabric
```

`build.ps1` 会校验 `JAVA_HOME` 指向 Java 25；`deploy-dev.ps1` 会先删除目标包再整目录
复制，并对目标路径做前缀与名称校验以防误删。测试共 10 个类：纯数学、配置与迁移、
星表解码、VSOP87 截断与表一致性、行星星历，以及时间模式/月面/日食/阴影/行星
五组着色器集成黄金参考。

## 已知遗留与可清理项

- `tools/ImageFormatConverter.java`、`tools/StarAtlasPointEncoder.java` 没有被任何
  脚本引用。
- 星表使用 J2000 赤道坐标，行星使用当日平赤道坐标；2026 年两者相差约 0.36°，
  恒星相对行星与日月有一处已知的岁差偏移。统一到当日坐标系属于路线图的
  「岁差与章动」项，本轮未改动。
- 仓库没有 remote，也没有 tag；`gradle.properties` 的 `mod_version` 是唯一版本来源。

## 版本管理约定

- 所有改动都提交到本仓库，不直接改 `build/`、`logs/`、目标实例目录里的副本。
- 提交粒度按「一处可验证的改动」划分，提交信息沿用现有 `<type>: <祈使句>` 风格
  （`feat` / `fix` / `chore` / `docs` / `test`）。
- 修改天文公式时，Java 参考实现、GLSL 实现、黄金参考测试必须在同一提交内保持一致。
- 发布时给 `mod_version` 对应的提交打 tag，并同时产出 JAR 与 ZIP。
