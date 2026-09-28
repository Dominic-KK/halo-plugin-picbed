# 图床附件

`plugin-picbed-dominickonode` —— Halo 的**附件存储插件**，将 GitHub 图床添加为附件策略，配合 Cloudflare 代理，实现国内也能顺畅访问，支持重命名格式，避免文件名冲突。

## 特性

- 基于 Halo `AttachmentHandler` 扩展点，安装启用后，在「附件 → 存储策略」里新建「🌹图床附件🌹」即可使用。
- 上传/删除走 **GitHub REST API**（可配置 API 基地址，直连不稳时可走自建代理）；公开访问走你的 **Cloudflare 自定义域名**（干净、不带 token，仓库私有也能读）。
- **数据安全**：附件删除按上传时固化的仓库/分支/object-key 定位，修改存储策略也不会误删；公开链接基于固化 objectKey + 当前自定义域名动态生成，更换域名后存量链接自动更新。
- **资源限制**：单文件大小上限可配置（默认 50MB，范围 1-50MB）；GitHub 请求完整超时（连接 15s + 响应 30s）。
- 支持重命名格式：`{y}/{m}/{d}{h}{i}`、`{origin}`、`{timestamp}`、`{rand:N}`。
- GitHub Token 存于 Halo **Secret**，绝不明文落库；推荐 **fine-grained token**（仅授予图床仓库 Contents 读写权限）。
- 上传/删除按「仓库+分支」串行，兼容 GitHub 并发写分支的 ref 竞态问题。

## 使用教程

1. 前往 [Releases](https://github.com/Dominic-KK/halo-plugin-picbed/releases) 下载最新版 JAR（如 `halo-plugin-picbed-1.1.1.jar`）。
2. Halo 后台 → **插件 → 安装** → 上传 JAR 文件 → 安装并启用。
3. 前往 **附件 → 存储策略 → 新建**，选择「🌹图床附件🌹」。
4. 填写配置。

> ⚠ 存储策略创建后**请勿修改仓库与分支**，更换请新建策略。

### 私有仓库 + Cloudflare 代理（可选，推荐）

搭配 **GitHub 私有仓库 + Cloudflare Worker 反代 + 自定义域名**：免费、私有、国内可访问，公开链接不携带 token。部署步骤（GitHub 令牌、Worker 脚本与环境变量、域名绑定、验证输出）详见：

- [作者博客《GitHub + Cloudflare + Picgo 搭建你的免费图床》](https://blog.dominickk.top/archives/l47gY0Vr)

### 配置项说明

| 配置             | 说明                                                                  | 示例                                                                                    |
| -------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| 图床类型           | 目前仅**GitHub**；阿里云 OSS 敬请期待（开摆）                                      | `github`                                                                              |
| 仓库             | 存放附件的仓库，格式`owner/repo`，创建后请勿修改                                      | `Dominic-KK/xxxxxx`                                                                   |
| 分支             | 仓库分支，创建后请勿修改                                                        | `main`                                                                                |
| 存储路径前缀         | 仓库内文件前缀，建议以`/` 结尾                                                   | `halo-atta/`                                                                          |
| GitHub API 基地址 | 上传/删除接口。默认`https://api.github.com`；国内直连不稳时再填自建代理，如果你不知道这是什么，建议保持默认。 | `https://api.github.com`                                                              |
| 自定义域名          | 公开访问域名，推荐 Cloudflare 代理，这是本插件推荐的方式，走这里拼接 permalink                  | `https://your-domain.com`                                                             |
| 单文件大小上限        | 单文件最大体积（MB），默认 50，取值范围 1-50                                         | `50`                                                                                  |
| GitHub Token   | 写入 GitHub 仓库的访问令牌（存 Secret）                                         | 创建 **fine-grained token**：限定你的图床仓库，权限必选 **Contents: Read and write**（自动附带只读 Metadata） |
| 重命名格式          | 生成仓库内文件名                                                            | `{y}/{m}/{d}{h}{i}-{rand:3}`                                                          |

> 与 PicGo-github 插件的对应关系：`仓库/分支/存储路径/自定义域名/重命名格式` 分别对应你 PicGo 里的
> `github.repo / branch / path / customUrl / picgo-plugin-rename-file.format`，可直接照搬。

```json
// picgo github 插件配置
{
  "picBed": {
    "current": "github",
    "github": {
      "repo": "Dominic-KK/xxxxxx",
      "branch": "main",
      "token": "ghp_xxxxxx",
      "path": "picgo/",
      "customUrl": "https://your-domain.com"
    }
  },
  "picgoPlugins": {
    "github": true,
    "picgo-plugin-rename-file": true
  },
  "picgo-plugin-rename-file": {
    "format": "{y}/{m}/{d}{h}{i}-{rand:3}"
  }
}
```

## 更新日志

- [更新日志-用户](./CHANGELOG.md)
- [更新日志-开发者](./CHANGELOG-DEV.md)

## 维护计划

- **Issue 双通道**：GitHub [Issues](https://github.com/Dominic-KK/halo-plugin-picbed/issues) 与上架评论区同步跟进。
- **跟随迭代**：随 Halo `AttachmentHandler` 扩展点持续适配更新。
- **已知问题清单**：可参考仓库 Issues，定期梳理到 README「已知问题」。
- 目标：快速响应、优先修复影响附件可用性和数据安全的问题。

## 开发环境

- Java 21+
- Node.js 22+（插件暂无 UI）
- Halo >= 2.26.0

## 开发与构建

```bash
# 本地起一个 Halo 调试实例（自动加载插件）
./gradlew haloServer

# 打包（产物在 build/libs/*.jar）
./gradlew build
```

## 许可证

[GPL-3.0](./LICENSE) © Dominic-KK
