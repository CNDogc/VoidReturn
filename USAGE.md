# VoidReturn 虚空回溯 · 使用文档

> 版本 1.0.0 · Paper 26.2（Java 25）· 轻量来源记忆型虚空传送插件

---

## 1. 简介

玩家跨世界传送后，插件自动记录「传送前的世界 + 坐标」。当玩家在已启用世界中掉入虚空（低于阈值）时，自动传送回该来源位置。

- 来源记忆仅存于内存（`Map<UUID, Location>`），**每次跨世界都会覆盖旧记录**，只保留最近一次，服务器重启即清空。
- 只有 `config.yml` 中显式启用的世界才会触发检测。

## 2. 安装

1. 将 `VoidReturn-1.0.0.jar` 放入服务器 `plugins/` 目录。
2. 重启服务器（首次启动自动生成 `plugins/VoidReturn/config.yml`）。
3. 编辑 `config.yml`，把实际世界名加入 `enabled-worlds`，执行 `/voidreturn reload` 热重载，无需重启。

## 3. 权限（Permissions）

**安全边界**：本插件的「虚空救援」是对所有玩家生效的被动安全功能（玩家掉虚空被救回，无需任何权限）；只有**管理命令**需要权限，仅 OP 或拥有对应权限节点者可用。

| 权限节点 | 默认 | 说明 |
| --- | --- | --- |
| `voidreturn.reload` | op | 允许执行 `/voidreturn reload` 重载配置 |

- OP 默认拥有（`default: op`）。
- 给某管理员单独授权：`/lp user <玩家> permission set voidreturn.reload true`（或你使用的权限插件对应命令）。
- 取消默认 OP 授权：将 plugin.yml 中 `default: op` 改为 `default: false` 后重启，再手动授给需要的管理员。

## 4. 命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/voidreturn reload` | `voidreturn.reload` | 重新读取 `config.yml` 并应用。成功后返回：`VoidReturn reloaded. Enabled worlds: [世界列表]` |
| `/voidreturn` | `voidreturn.reload` | 无参数时显示用法提示 |

- 无权限执行时会提示 `You do not have permission to use this command.`
- 控制台（Console）默认拥有全部权限，可用于重载。

## 5. 配置文件（config.yml）

```yaml
enabled-worlds:
  spawn:                       # 世界名（文件夹名，例如 spawn / world_nether）
    void-threshold: -64        # Y 阈值：玩家 Y 坐标低于此值视为掉入虚空
    cooldown-secs: 3           # 救援冷却（秒），防止连续传送抖动
    fallback:                  # 无来源记录时的回退落点（如玩家进服第一次就在该世界）
      x: 0.5
      y: 70.0
      z: 0.5
      yaw: 0
      pitch: 0
```

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `enabled-worlds.<world>` | Section | — | 键名为启用世界的名称；**不在此列表的世界完全不触发检测** |
| `void-threshold` | double | -64 | 玩家 Y 低于该值触发救援 |
| `cooldown-secs` | int | 3 | 两次救援之间的冷却秒数 |
| `fallback.x / y / z` | double | 0.5 / 70.0 / 0.5 | 无记录时的回退坐标 |
| `fallback.yaw / pitch` | float | 0 / 0 | 回退朝向 |

修改后执行 `/voidreturn reload` 生效（或重启服务器）。

## 6. 工作原理

1. **记录来源**：玩家发生任何跨世界传送（`from` 世界 ≠ `to` 世界）时，把「传送前的世界 + 坐标」写入内存，覆盖旧记录。不跨世界的传送不记录。
2. **检测虚空**：玩家在启用世界内移动且 Y 低于 `void-threshold`、且不在冷却内，则触发救援。
3. **选择落点**：
   - 有来源记录 → 回来源位置；
   - 无记录或来源世界已卸载 → 用该世界配置的 `fallback`；
   - 传送前做安全落点检测（脚下为实心方块、站立块与头顶一块为空气），不满足则在 3 格半径内搜索；
   - 仍找不到安全位置 → 回该世界出生点并输出告警日志（如 `No safe spot near ... using spawn of world '...'`）。
4. **防死循环**：救援瞬间标记玩家，避免把「虚空中的位置」记为新的来源，否则会反复传回虚空。

> 安全落点搜索思路参考开源插件 [NoVoidX](https://github.com/UnknowUser0/NoVoidX)（Apache-2.0），已在源码文件头保留其版权声明。

## 7. 常见问题

**Q：玩家掉虚空没被传回？**
- 确认该世界名已加入 `enabled-worlds`（世界名是文件夹名，注意大小写）。
- 确认玩家的 Y 确实低于 `void-threshold`（主世界虚空阈值通常用 `-64`）。
- 确认不在 `cooldown-secs` 冷却内。

**Q：想临时禁用某个世界？**
- 删除或注释掉 `enabled-worlds` 下对应的世界段，然后 `/voidreturn reload`。

**Q：想改回退点？**
- 修改 `fallback` 坐标后重载即可。

**Q：来源记录会保存多久？**
- 仅保存在内存：每次跨世界覆盖、玩家退出服务器时清除、服务器重启后清空。掉虚空时的「最近一次来源」会被恢复。

## 8. 兼容性与构建

- 目标：Paper 26.2（api-version `26.2`），Java 25。
- 依赖：`io.papermc.paper:paper-api:26.2.build.112-stable`（compileOnly）。
- 构建：`.\gradlew.bat build`，产物位于 `build/libs/VoidReturn-1.0.0.jar`。
