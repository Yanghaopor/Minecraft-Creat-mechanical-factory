# Mechanical Factory

A NeoForge mod for Minecraft 1.21.1 that collapses an entire Create production line into a single block.

Mechanical Factory gives every Create contraption a place to live. Place a Construction Block in the world and step inside with a Construction Key to enter a private, persistent 16x16x16 factory room hosted in its own pocket dimension. Build your machinery there, then seal it back up - the whole factory stays loaded and keeps running on the server even when no one is inside.

Each of the block's six faces can be configured as a port that bridges straight into the room:

- **Kinetic** - input or output rotation; Create's stress and speed are mirrored across the dimensional boundary, so an exterior shaft can drive interior machinery or vice versa.
- **Items** - a 9-slot buffer per face, with input and output sides reversed between the outside and the inside.
- **Fluids** - a 16,000 mB buffer per face.
- **Redstone** - read or emit signals through any face.

When it is time to move, sneak-wrench the host to fold the entire factory (room, ports, buffers and all) into a single, uniquely-bound portable item. Place it down elsewhere and everything snaps back exactly as it was. Destroy that dropped item, though, and the room's contents are gone for good. Empty rooms can be cleanly dismantled, and the blast-resistant shell keeps your machinery safe.

Requires Create 6.0.10 or a compatible release.

> 这是一个基于官方 ModDevGradle MDK 的 NeoForge 模组工程。它为 Create 产线提供可进入的 16x16x16 施工舱，将大型机器收纳到外部一个方块中。

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 |
| Create | 6.0.10 |
| Java | 21 |
| Gradle Wrapper | 9.2.1 |

## First Setup

1. 安装 JDK 21，并确认 `java -version` 可以正常执行。
2. 在 `gradle.properties` 中修改 `mod_id`、`mod_name`、`mod_group_id`、`mod_authors`、`mod_description` 和 `mod_license`。
3. 若要更换模组标识，请同步修改 `mod_id`、`ExampleMod.MOD_ID`、Java 包名，以及 `src/main/resources/assets/examplemod` 和 `src/main/resources/data/examplemod`。

`mod_id` 必须只使用小写英文字母、数字和下划线，且以字母开头。

## Common Commands

在 Windows PowerShell 中执行：

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runData
.\gradlew.bat build
```

构建后的模组 JAR 位于 `build/libs/`。首次运行会下载 Gradle、Minecraft 和开发依赖，因此需要网络连接。

## Construction Block

1. 使用 Create 机械合成制作施工方块；施工钥匙使用黄铜板、精密构件和末影珍珠制作。
2. 手持施工钥匙右键施工方块，首次使用时会创建独立的 16x16x16 施工舱并传送玩家进入；舱内出口控制台用于返回外部。
3. 空手潜行右键施工方块的某个面，可循环配置该面为动能、物品、流体、红石端口或关闭。施工舱内同一方位的接口是其对应端。
4. 物品端口每面有 9 格缓冲，流体端口每面有 16,000 mB 缓冲；输入和输出在内外两侧方向相反，区块未加载时自然暂停。
5. 不同面可同时配置 Create 动能输入与输出。存在输入端时由外部网络驱动舱内并从输出面继续传出；只有输出端时由舱内网络反向驱动外部。桥接会同步转速、应力容量和下游负载。
6. 潜行使用扳手右键可将整个工厂打包为唯一迁移物品，重新放置后恢复原施工舱、端口和缓冲；复制品无法重复绑定。迁移物品作为掉落物被销毁时，舱内内容也会被永久销毁。
7. 普通挖除或潜行持施工钥匙右键会永久撤场，且只允许空施工舱执行；对应房间会彻底清理并回收空间。施工舱外壳抗爆炸。

## Project Layout

- `src/main/java`: 施工方块、施工钥匙、口袋维度、端口缓冲、Create 动能代理和安全事件。
- `src/main/resources`: 语言文件、纹理、模型和其他运行时资源。
- `src/main/templates/META-INF/neoforge.mods.toml`: 模组加载元数据模板，由构建任务自动填充，并声明 Create 为强制前置。
- `src/generated/resources`: 数据生成输出目录；不要手动放置数据生成缓存。

## Required Dependency

该工程将机械动力（Create）作为强制前置模组。开发环境会通过官方 Maven 自动下载 Create `6.0.10`；发布模组时不会将其打入 JAR。玩家或服务器必须另外安装与 Minecraft 1.21.1 / NeoForge 匹配的 Create 6.0.10 或更高兼容版本。

## References

Special thanks to mMuzi for assisting with textures.

- NeoForge documentation: https://docs.neoforged.net/
- ModDevGradle: https://github.com/neoforged/ModDevGradle
- Mojang mappings license: https://github.com/NeoForged/NeoForm/blob/main/Mojang.md


## Developers & Contributors / 开发者与贡献者

Mechanical Factory is developed and maintained by the following contributors:

- **Yanghaopor** — Original Author & Developer（原作者、开发者）
  - GitHub: https://github.com/Yanghaopor
- **xiaofa520** — Developer & Contributor（开发者、贡献者）

Special thanks to **mMuzi** for assisting with textures.

## MC百科开发者身份认证

本项目为 Mechanical Factory（机械工厂）的官方开源代码仓库。

项目开发者及贡献者包括：

- **Yanghaopor**：项目原作者、开发者。
- **xiaofa520**：项目开发者、贡献者。

上述人员参与 Mechanical Factory 模组的开发与维护。

本声明用于 MC百科（mcmod.cn）开发者身份认证，以确认上述人员与本项目的关联关系。

项目源代码：
https://github.com/Yanghaopor/Minecraft-Creat-mechanical-factory

模组发布页面：
https://modrinth.com/mod/mechanical-factory
