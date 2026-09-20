# 🌹 图床附件 🌹

`plugin-picbed-dominickonode` —— Halo 的**附件存储插件**，将GitHub图床添加为附件策略，配合Cloudflare代理，实现国内也能顺畅访问，支持重命名格式，避免文件名冲突。

## 特性

- 基于 Halo `AttachmentHandler` 扩展点，安装启用后，在「附件 → 存储策略」里新建「🌹图床附件🌹 @DKK」即可使用。
- 上传/删除走 **GitHub REST API**，公开访问默认走你的 **Cloudflare 自定义域名**（干净、不带 token，仓库私有也能读）。
- 支持重命名格式：`{y}/{m}/{d}{h}{i}`、`{origin}`、`{timestamp}`、`{rand:N}`。
- GitHub Token 存于 Halo **Secret**，绝不明文落库。
- 上传/删除按「仓库+分支」串行，兼容 GitHub 并发写分支的 ref 竞态问题。

## 使用教程

1. 安装本插件并启用。
2. 前往 **附件 → 存储策略 → 新建**，选择「🌹图床附件🌹 @DKK」。
3. 填写以下配置。

### 配置项说明

| 配置             | 说明                                                                   | 示例                                                                                |
| -------------- | -------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| 图床类型           | 目前仅 **GitHub**；阿里云 OSS 敬请期待（开摆）                                      | `github`                                                                          |
| 仓库             | 存放附件的仓库，格式 `owner/repo`                                              | `Dominic-KK/xxxxxx`                                                               |
| 分支             | 仓库分支                                                                 | `main`                                                                            |
| 存储路径前缀         | 仓库内文件前缀，建议以 `/` 结尾                                                   | `halo-atta/`                                                                      |
| GitHub API 基地址 | 上传/删除接口。默认 `https://api.github.com`；国内直连不稳时再填自建代理，如果你不知道这是什么，建议保持默认。 | `https://api.github.com`                                                          |
| 自定义域名          | 公开访问域名，推荐 Cloudflare 代理，这是本插件推荐的方式，走这里拼接 permalink                   | `https://your-domain.com`                                                         |
| GitHub Token   | 只读/可写 Personal Access Token（存 Secret）                                | 在GitHub设置中生成一个Personal Access Token，权限列表必选：`repo`（完整仓库权限），`write:packages`（写入包权限） |
| 重命名格式          | 生成仓库内文件名                                                             | `{y}/{m}/{d}{h}{i}-{rand:3}`                                                      |


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

[GPL-3.0](./LICENSE) © Dominic-kk
