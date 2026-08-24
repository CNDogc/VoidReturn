# VoidReturn 虚空回溯

轻量 Paper 插件：记录玩家跨世界传送的来源位置，掉入虚空时自动传送回来源。
Lightweight Paper plugin: records where a player came from on cross-world teleports, and sends them back when they fall into the void.

## 版本适用 / Version Compatibility

| 项目 / Item | 值 / Value |
| --- | --- |
| 目标服务器 / Target server | Paper 26.2（`paper-26.2-112`），Minecraft 26.2 |
| API 版本 / api-version | `'26.2'` |
| Java | 25 |
| 依赖 / Dependency | `io.papermc.paper:paper-api:26.2.build.112-stable` (compileOnly) |
| 构建产物 / Artifact | `build/libs/VoidReturn-2.0.1.jar` |

## 功能 / Features

- 跨世界传送时记录来源（世界 + 坐标），每次跨世界覆盖旧记录 / Records the source location (world + coords) on every cross-world teleport, overwriting the previous one
- 来源持久化到 `data.yml`，重启后保留 / Sources are persisted to `data.yml`, surviving restarts
- 仅在 `config.yml` 启用的世界触发虚空检测 / Void detection only in worlds listed in `config.yml`
- 安全落点检测 + 3 格半径搜索，传送后重置下落状态防摔死 / Safe landing spot check + 3-block radius search; resets fall state after teleport to prevent fall damage
- 无可回记录时回退到世界 fallback / Falls back to the world fallback when no source is recorded
- 救援时可发送满屏字幕 / 聊天框提示（可配置）/ Configurable full-screen title / chat notification on rescue
- 可配置倒计时传送：掉虚空后按 TITLE/SUBTITLE/ACTION_BAR/CHAT/BOSS_BAR 多形式消息倒计时，再传回来源 / Configurable countdown: multi-form messages (TITLE/SUBTITLE/ACTION_BAR/CHAT/BOSS_BAR) with auto countdown, then teleport back

## 使用文档 / Documentation

完整指令与配置说明（中英双语）见 [USAGE.md](USAGE.md)。
Full command and configuration guide (bilingual) is in [USAGE.md](USAGE.md).

## 归属声明 / Attribution

本插件的「安全落点搜索」逻辑参考自 [NoVoidX](https://github.com/UnknowUser0/NoVoidX)（Apache License 2.0），已在相关源文件头保留其版权声明。
The "safe landing spot search" logic references [NoVoidX](https://github.com/UnknowUser0/NoVoidX) (Apache License 2.0); its copyright notice is retained in the relevant source file header.
