# 开发者变更日志

本文件面向**开发者/维护者**：记录每个版本在技术层面的具体改动，涉及关键类、机制与行为变更。版本号与 [更新日志-用户](./CHANGELOG.md) 一一对应。

## \[v1.1.1] - 2026-09-27

### 固位注解与删除安全（GitHubAttachmentHandler）

- 上传时把实际存储位置（仓库、分支、object-key）写入附件元数据固位注解：`REPO_ANNO` / `BRANCH_ANNO` / `OBJECT_KEY_ANNO`，之后不可变。
- `delete()` 改为按固位注解定位删除目标；任一固位信息缺失（旧版附件/策略被修改过）时仅删除 Halo 附件记录、不处理 GitHub 文件（`log.warn` 后直接返回）。
- 删除走 `readToken → deleteRemote`，远端删除异常仅 `doOnError` 告警 + `onErrorResume(Mono.empty())`，不阻塞 Halo 附件删除。

### 公开链接动态化（getPermalink）

- 有 `OBJECT_KEY_ANNO` 时基于 `buildPublicUrl(props, objectKey)`（当前 customUrl + 固化 objectKey）动态重建；更换自定义域名后存量链接自动更新。
- 无固位注解时回退兼容旧版 `EXTERNAL_LINK_ANNO_KEY`。

### 策略变更告警（PolicyConfigWatcher，新增）

- 新增 `PolicyConfigWatcher`（实现 Halo `Watcher`），监听存储策略配置变更，检测仓库/分支被修改时输出 WARN 告警。
- 实现完整生命周期：`registerDisposeHook` / `dispose` / `isDisposed`，插件停用/卸载时显式释放监听，杜绝残留 listener。

### 隐私披露

- `plugin-settings.yaml` 新增「隐私与数据流向」设置页，内置数据流转披露与隐私承诺。

## \[v1.1.0] - 2026-09-21

### 文件大小限制（可配置）

- 新增 `maxFileSizeMB` 配置项（默认 50，取值 1-50），表单 `min`/`max` 校验拦截越界。
- `GithubProperties.maxFileSizeBytes()` 做边界收敛，非法值回落默认 50MB。

### 流式大小校验（upload）

- `DataBufferUtils.join` + `handle` 逐缓冲累积字节数，超过配额即 `sink.error`，不再把整个文件读入内存后再校验。

### 超时与 Token

- 统一 `REQUEST_TIMEOUT = 30s`（连接 15s + 完整请求响应 30s），避免异常请求长期占用线程。
- Token 收敛为 fine-grained（Contents 读写 + 自动只读 Metadata），README/表单同步更新。

### 元信息

- 移除插件显示名称后缀；修正仓库地址大小写（`Dominic-KK/halo-plugin-picbed`）。

## \[v1.0.1] - 2026-09-21

### Bug 修复

- 修复 `Attachment.AttachmentStatus` 空指针：上传完成后未初始化 status，粘贴图片时报"请重试"。
- 修复上传文件名未按格式重命名：新增格式化文件名方法（接入 `FileNameGenerator`）。

### CI

- 修复 `gradlew` 可执行权限（100644 -> 100755，`git update-index --chmod=+x`）。
- 调整 CI/CD，跳过 Node 环境搭建（项目无 UI、无 pnpm-lock.yaml）。

## \[v1.0.0] - 2026-09-20

### 核心实现

- 基于 Halo `AttachmentHandler` 扩展点实现 GitHub 图床存储：上传（Contents PUT）、删除（GET sha → DELETE）、`getPermalink` / `getSharedURL`。
- 支持自定义 API 基地址（国内代理）、仓库分支、路径前缀、自定义访问域名。
- Token 存 Halo **Secret**，经 `ReactiveExtensionClient` 读取，绝不落地 ConfigMap。

### 文件名生成（tools/FileNameGenerator）

- 支持占位符 `{y}/{m}/{d}{h}{i}`、`{origin}`、`{timestamp}`、`{rand:N}`，兼容 PicGo 命名。

### 并发写安全

- 按「仓库+分支」`ReentrantLock` 串行化上传/删除，规避 GitHub 并发写分支的 ref 竞态。
- 上传 409 冲突重试（最多 4 次），重新生成 objectKey 后重放。

### 构建与发布

- 归档名 `halo-plugin-picbed`；新增 `.github/workflows/release.yml`，tag 推送自动构建并发布 JAR。

