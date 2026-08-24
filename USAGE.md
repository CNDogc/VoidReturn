# VoidReturn 虚空回溯 · 使用文档 / Usage Guide

> 版本 / Version 2.0.1 · Paper 26.2（Java 25）· 轻量来源记忆型虚空传送插件
> Lightweight source-memory void teleport plugin

---

## 1. 简介 / Introduction

玩家跨世界传送后，插件自动记录「传送前的世界 + 坐标」。当玩家在已启用世界中掉入虚空（低于阈值）时，自动传送回该来源位置。
After a player teleports across worlds, the plugin records the "world + coords" they came from. When the player falls into the void (below the threshold) in an enabled world, they are automatically sent back to that source.

- 来源记忆：内存热读 + 持久化到 `plugins/VoidReturn/data.yml`，每次跨世界覆盖旧记录，只保留最近一次，**重启后仍保留**。
  Sources are read from memory (fast) and persisted to `plugins/VoidReturn/data.yml`; every cross-world teleport overwrites the old record, keeping only the latest. **They survive restarts.**
- 只有 `config.yml` 中显式启用的世界才触发检测。
  Only worlds explicitly listed in `config.yml` trigger detection.

## 2. 安装 / Installation

1. 将 `VoidReturn-2.0.1.jar` 放入服务器 `plugins/` 目录。Put the jar into the server's `plugins/` folder.
2. 重启服务器（首次启动自动生成 `plugins/VoidReturn/config.yml`）。Restart the server (config is auto-generated on first start).
3. 编辑 `config.yml` 加入实际世界名，执行 `/voidreturn reload` 热重载。Edit `config.yml` to add your real world names, then `/voidreturn reload` (no restart needed).

## 3. 权限 / Permissions

**安全边界 / Security boundary**：虚空救援对所有玩家生效（被动安全功能，无需权限）；只有管理命令需要权限，仅 OP 或持有权限节点者可用。
The void rescue works for every player (passive safety feature, no permission needed); only the admin command requires permission, available to OPs or holders of the node.

| 权限节点 / Node | 默认 / Default | 说明 / Description |
| --- | --- | --- |
| `voidreturn.reload` | op | 执行 `/voidreturn reload` 重载配置 / Run `/voidreturn reload` |

- OP 默认拥有 / OPs have it by default.
- 单独授权 / Grant to a specific admin: `/lp user <玩家> permission set voidreturn.reload true`
- 如需取消默认 OP 授权，将 plugin.yml 中 `default: op` 改为 `default: false` 后重启。To remove the default op grant, change `default: op` to `default: false` in plugin.yml and restart.

## 4. 命令 / Commands

| 命令 / Command | 权限 / Permission | 说明 / Description |
| --- | --- | --- |
| `/voidreturn reload` | `voidreturn.reload` | 重读配置并应用 / Reload and apply config. Returns: `VoidReturn reloaded. Enabled worlds: [...]` |
| `/voidreturn` | `voidreturn.reload` | 无参数时显示用法 / Shows usage when called without arguments |

- 无权限提示 / Without permission you get: `You do not have permission to use this command.`
- 控制台默认可用 / Console works by default.

## 5. 配置 / Configuration（config.yml）

```yaml
enabled-worlds:
  spawn:                       # 世界名（文件夹名）/ World name (folder name)
    void-threshold: -64        # Y 阈值：低于此值视为虚空 / Y threshold: below this is void
    cooldown-secs: 3           # 救援冷却（秒）/ Rescue cooldown (seconds)
    fallback:                  # 无记录时回退落点 / Fallback when no record exists
      x: 0.5
      y: 70.0
      z: 0.5
      yaw: 0
      pitch: 0
```

| 字段 / Field | 类型 / Type | 默认 / Default | 说明 / Description |
| --- | --- | --- | --- |
| `enabled-worlds.<world>` | Section | — | 键名为启用世界名；不在列表的世界完全不检测 / Key is the enabled world name; worlds not listed are never checked |
| `void-threshold` | double | -64 | 玩家 Y 低于该值触发救援 / Rescue triggers below this Y |
| `cooldown-secs` | int | 3 | 两次救援之间冷却秒数 / Seconds between rescues |
| `fallback.x / y / z` | double | 0.5 / 70.0 / 0.5 | 回退坐标 / Fallback coords |
| `fallback.yaw / pitch` | float | 0 / 0 | 回退朝向 / Fallback yaw/pitch |

**倒计时传送 / Countdown rescue**：`delay-secs` 指定传回前的倒计时秒数（0 = 立即传送）；配置了 `countdown` 列表则倒计时期间每秒发送对应消息，`{seconds}` 会自动替换为剩余秒数。多种形式可同时叠加，形式支持：`TITLE` / `SUBTITLE` / `ACTION_BAR` / `CHAT` / `BOSS_BAR`。倒计时期间玩家正常下坠且不会死亡。

```yaml
delay-secs: 3
countdown:            # [传送前] 倒计时消息
  - type: TITLE
    text: "&6正在前往伊甸"
  - type: SUBTITLE
    text: "&e虚空回溯中..."
  - type: ACTION_BAR
    text: "&e{seconds} 秒后传送"
  - type: CHAT
    text: "&a即将传回来源位置..."
  - type: BOSS_BAR
    text: "&6虚空回溯"
```

**到达提示 / Arrival notification**：玩家最终被传回时发送一次，格式与 `countdown` 相同（同样支持 `TITLE` / `SUBTITLE` / `ACTION_BAR` / `CHAT` / `BOSS_BAR`），**每个世界各自配置**。

```yaml
arrival:              # [传送后] 到达消息
  - type: TITLE
    text: "&6虚空回溯"
  - type: SUBTITLE
    text: "&e你已被传送回来源位置"
  - type: CHAT
    text: "&a已传回来源位置"
```

修改后 `/voidreturn reload` 生效（或重启）。Apply changes with `/voidreturn reload` (or restart).

## 6. 工作原理 / How It Works

1. **记录来源 / Record source**：跨世界传送（`from` 世界 ≠ `to` 世界）时记录「传送前位置」，覆盖旧记录，并**异步写入 data.yml**。不跨世界不记录。
   On any cross-world teleport (`from` world ≠ `to` world), the pre-teleport location is recorded, overwriting the old one, and **asynchronously written to data.yml**. Same-world teleports are ignored.
2. **检测虚空 / Detect void**：玩家在启用世界移动且 Y 低于阈值、不在冷却内时触发。
   Triggers when a player in an enabled world moves below the threshold and is not on cooldown.
3. **选择落点 / Choose landing spot**：
   - 有来源记录 → 回来源 / Source recorded → back to source
   - 无记录或来源世界已卸载 → 用 `fallback` / No record or source world unloaded → use `fallback`
   - 安全落点检测（脚下实心、站立块与头顶为空气），不满足则在 3 格半径内搜索 / Safe spot check (solid below, air at feet & head); search within 3 blocks if not safe
   - 仍找不到 → 回该世界出生点并打告警日志 / Still nothing → world spawn with a warning log
4. **防死循环 / Anti-loop**：救援瞬间标记玩家，避免把虚空位置记为来源。 Marks the player during rescue so the void position is never recorded as a source.
5. **防摔死 / Anti-fall-damage**：传送后重置下落距离并清空速度，避免沿用掉落伤害。 Resets fall distance and velocity after teleport so the player does not die from the earlier fall.
6. **救援提示 / Rescue notification**：传送成功时发送 `message` 中配置的字幕/聊天消息。 Sends the configured title / chat message on successful rescue.

> 安全落点搜索参考 [NoVoidX](https://github.com/UnknowUser0/NoVoidX)（Apache-2.0），版权声明保留在源码文件头。
> Safe-landing search references NoVoidX (Apache-2.0); its copyright notice is retained in the source file header.

## 7. 常见问题 / FAQ

**Q：玩家掉虚空没被传回 / Player falls but isn't rescued?**
- 确认世界名已加入 `enabled-worlds`（注意大小写）/ Confirm the world name is in `enabled-worlds` (case-sensitive)
- 确认 Y 确实低于 `void-threshold`（主世界常用 `-64`）/ Confirm Y is below `void-threshold` (-64 is common for overworld)
- 确认不在冷却内 / Confirm not on cooldown

**Q：想临时禁用某世界 / Disable a world temporarily?**
- 删除或注释掉对应世界段，`/voidreturn reload` / Delete or comment out that world's section, then `/voidreturn reload`

**Q：想改回退点 / Change the fallback?**
- 修改 `fallback` 坐标后重载 / Edit the `fallback` coords and reload

**Q：来源记录保留多久 / How long is the source kept?**
- 持久保存：直到下一次跨世界覆盖为止；存于 `data.yml`，退出/重启均保留 / Persisted until the next cross-world hop; stored in `data.yml`, survives quit and restart

## 8. 兼容性与构建 / Compatibility & Build

- 目标 / Target：Paper 26.2（api-version `26.2`），Java 25
- 依赖 / Dependency：`io.papermc.paper:paper-api:26.2.build.112-stable`（compileOnly）
- 构建 / Build：`.\gradlew.bat build`，产物 / artifact：`build/libs/VoidReturn-2.0.1.jar`
