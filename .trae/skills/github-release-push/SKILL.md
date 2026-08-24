---
name: "github-release-push"
description: "Publishes a Java/Gradle plugin to GitHub: confirm version + release notes, build, commit, push, then create a GitHub Release with the jar. Invoke when the user asks to push / publish / release the plugin to GitHub, or after code changes need publishing."
---

# GitHub 发布推送工作流（Release & Push Workflow）

适用于本地已改完代码、需要发布到 GitHub 仓库的 Java/Gradle 项目（如 Minecraft Paper 插件）。
本 skill 封装了「推送前确认 → 构建 → 提交 → 推送 → 建 Release 传 jar → 交付链接」的完整流程。

## 流程（按顺序执行，不可跳过）

### 1. 推送前确认（必须）
在 push 或建 Release 之前，先向用户确认两件事（用 `AskUserQuestion`）：
- **版本号**：如 `1.2.0`，需与 `build.gradle` 的 `version`、`plugin.yml` 的 `version` 一致
- **更新描述草稿**（Release Notes）：列出本次改动，用户确认或修改后再执行

### 2. 同步版本号
- `build.gradle`：`version = 'X.Y.Z'`
- `src/main/resources/plugin.yml`：`version: X.Y.Z`
- 文档中 jar 名 / 版本引用：如 `build/libs/<name>-<version>.jar`、README/USAGE 中的版本字样
- 删除 `build/libs` 下旧版本残留 jar

### 3. 构建
```powershell
.\gradlew.bat build --no-daemon --console=plain
```
产物：`build/libs/<name>-<version>.jar`

### 4. 提交
```powershell
git add -A
git -c user.name="<name>" -c user.email="<name>@local" commit -m "<message>"
```
- commit message 用常规格式（feat:/fix:/perf:/refactor: 等）并写清本次更新内容
- 破坏性配置变更需在 message 中标注 `breaking change`

### 5. 推送
- 优先尝试 `git push origin main`
- **连接器失败时的替代**：若 GitHub 连接器（MCP）报 `fetch failed`，改用 git + 本机 Windows 凭据管理器令牌（`git credential fill` 会自动取 token），无需额外登录
- **网络超时（HTTP 408）**：先 `git fetch origin` 确认远端实际状态（可能未推上去），然后重试 `git push origin main` 一次即可
- 推送后验证：`git fetch origin; git status -sb`（无 ahead/behind 即同步）

### 6. 创建 GitHub Release + 上传 jar
用本机凭据令牌直接调 GitHub API（curl 需加 `--ssl-no-revoke`，本机 schannel 吊销检查会失败）：

```powershell
# 取 token（不要打印 token 本身）
$cred = "protocol=https`nhost=github.com`n`n" | git credential fill
$token = (($cred | Select-String '^password=').ToString().Split('=')[1])

# 建 Release（tag 不存在会自动创建）
$body = @{ tag_name="vX.Y.Z"; target_commitish="main"; name="vX.Y.Z"; body=$notes } | ConvertTo-Json
curl.exe --ssl-no-revoke -s -X POST -H "Authorization: token $token" `
  -H "Content-Type: application/json" -d $body `
  https://api.github.com/repos/{owner}/{repo}/releases

# 上传 jar 资产（用返回的 release id）
curl.exe --ssl-no-revoke -s -X POST -H "Authorization: token $token" `
  -H "Content-Type: application/java-archive" `
  --data-binary "@build/libs/<name>-X.Y.Z.jar" `
  "https://uploads.github.com/repos/{owner}/{repo}/releases/{id}/assets?name=<name>-X.Y.Z.jar"
```

### 7. 交付
向用户提供：
- Release 页面链接：`https://github.com/{owner}/{repo}/releases/tag/vX.Y.Z`
- jar 直接下载链接：`https://github.com/{owner}/{repo}/releases/download/vX.Y.Z/<name>-X.Y.Z.jar`

## 关键细节与注意事项
- **令牌来源**：Windows 凭据管理器（`git credential fill`，host=github.com），scope 需含 `repo` 才能建 Release
- **curl 必须 `--ssl-no-revoke`**：本机 schannel 吊销检查会报 `CRYPT_E_NO_REVOCATION_CHECK`
- **禁止泄露 token**：token 只存变量，绝不打印；输出只展示结果（id/url/state）
- **连接器 vs git**：GitHub 连接器授权后需"新会话"才能用；本会话内请用 git 直连 + 本机凭据
- **幂等性**：同 tag 重复建 Release 会报错；已存在的版本直接更新或跳过
- 大 jar 上传用 `--data-binary`，Content-Type 用 `application/java-archive`
