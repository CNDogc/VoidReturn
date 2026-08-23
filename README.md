# VoidReturn 虚空回溯

轻量 Paper 插件：玩家跨世界传送时记录来源位置，掉入虚空时自动传送回来源。

- 仅内存记录来源（`Map<UUID, Location>`），每次跨世界覆盖旧记录
- 只有 `config.yml` 中启用的世界才触发虚空检测
- 安全落点检测 + 3 格半径搜索，无可回记录时回退到世界 fallback
- 要求：Paper 26.2（api-version `26.2`）/ Java 25

## 使用文档

详细配置与指令见 [USAGE.md](USAGE.md)。

## 归属声明

本插件的「安全落点搜索」逻辑参考自 [NoVoidX](https://github.com/UnknowUser0/NoVoidX)（Apache License 2.0），已在相关源文件头保留其版权声明。
