# PicUp

用**自己的 Git 仓库**当图床的 Android App。选图片 → 提交到仓库 → 拿到可引用的直链 / Markdown / HTML / 自定义格式。

没有服务器、没有账号体系、没有上传配额：图片就是仓库里的提交，你随时可以在网页上、`git log` 里或者换一个客户端管理它们。

---

## 1. 登录方式：为什么是 Token

「浏览器登录 / Token / SSH 密钥」三条路都评估过，最终选择 **Personal Access Token**：

| 方案 | 问题 |
|---|---|
| **浏览器 OAuth 登录** | 移动端没有后端，`client_secret` 只能打包进 APK，等于公开泄漏。GitHub Device Flow 虽不需要 secret，但仍要求注册 OAuth App，且只覆盖 GitHub —— GitLab 与自建 Gitea 依旧得用 Token。 |
| **SSH 密钥** | SSH 只能跑 git 协议。要用它写文件就得内嵌整套 git 实现（JGit 之类），体积大、并发 push 会冲突，而且 GitHub / GitLab 都没有「SSH + REST 写文件」这条路。 |
| **Personal Access Token** | 三家平台通用、可以只授权单个仓库、随时在网页撤销；PicUp 只把它发给用户选的 git 主机，用 Android Keystore 加密保存在本机。 |

对应的工程实现：

- `data/TokenStore.kt`：AES-256-GCM，密钥由 Android Keystore 生成、不可导出；密文存私有 SharedPreferences，且从云备份 / 设备迁移中排除（换机后密文本来就解不开）。
- 登录先校验再落盘：`GET /user` 成功才会写入，Token 打错不会留下半配置的仓库。
- App 没有任何后端，Token 只出现在发往用户所选主机的请求头里。

支持的主机：

| 插件 | API | Token 权限 |
|---|---|---|
| GitHub（含 Enterprise） | `api.github.com` / `*/api/v3` | Fine-grained: `Contents: Read and write`；经典: `repo` |
| GitLab（含自建） | `*/api/v4` | `api` |
| Gitee 码云 | `gitee.com/api/v5` | `projects` |
| Gitea / Forgejo（自建） | `*/api/v1` | `write:repository` |
| Codeberg（托管 Forgejo） | `codeberg.org/api/v1` | `write:repository` |

平台是**插件**：`git/plugins/` 里每个平台实现同一个 `GitHostPlugin` 接口（API 基址、Token 页面与权限说明、raw 链接规则、客户端工厂），在目录里注册即可。
设置 → 插件里勾选后，添加账号的向导才会列出该平台；默认只开启 GitHub。账号里存的是插件 id，旧版本写入的 `GITHUB`/`GITLAB`/`GITEA` 会在读取时自动迁移。

## 2. 功能

- **仓库选择**：登录后列出可写入的仓库（会按 token 的 push 权限过滤），也可以手动填 `owner/repo`；分支可下拉选择或手填。
- **上传**：相册多选（系统 Photo Picker，不需要存储权限）。逐张串行提交，避免同分支并发提交互相顶掉。
- **图片处理**：输出格式可选「原格式 / WebP / JPEG / PNG」，质量三档（原图 100 / 压缩 85 / 轻量 60），可开关「移除 EXIF」与「文件名转小写」。「原格式」路径按容器格式就地剥离 EXIF/XMP/IPTC（JPEG 段、PNG chunk、WebP RIFF chunk），不重新编码；关掉「移除 EXIF」时重编码会把原 EXIF TIFF 块回填进 JPEG / PNG / WebP 输出（无 VP8X 头的 WebP 无法携带，按规范跳过）；GIF 永远原样保留动画；重编码反而变大时保留原文件。
- **路径模板**：`uploads/{yyyy}/{MM}/{name}-{rand6}.{ext}`，支持 `{yyyy} {MM} {dd} {HH} {mm} {ss} {name} {ext} {rand6} {hash} {width} {height} {size}` 等占位符，设置页有实时预览。每个仓库还可以单独覆盖。
- **复制格式**：内置直链 / Markdown / HTML / BBCode / 带标题 Markdown，可增删改、启停、指定「默认」。历史里点一下复制默认格式，长按菜单复制其它格式。
- **CDN**：仓库可设置公开链接前缀，支持 `{owner} {repo} {branch} {path}` 占位符；GitHub 有一键 jsDelivr 前缀（国内直连更稳）。
- **历史记录**：本地保存 500 条记录 + 小缩略图，可搜索、可再次复制、可打开 / 分享、可**从仓库删除**（生成一次删除提交，删除中显示进度圈）、可清空本地历史（不动仓库）；列表 / 卡片两种视图可切换并持久化。
- **图片预览**：点历史缩略图进入全屏预览，左右滑动切换照片、双击或双指缩放、缩放后可拖动；右上角直接复制任意已启用格式，底部显示仓库路径。
- **外观与动效**：跟随系统 / 浅色 / 深色、Android 12+ 动态取色、主题切换颜色插值；Navigation Compose 承载返回栈，`predictivePopEnter/ExitTransition` 与普通 pop 共用同一套 spring 策略，所以系统返回手势会逐帧驱动页面收缩（预览页则随手势下滑关闭）；另有自定义 snackbar 入场、卡片按压回弹、复制反馈、列表增删位移动画。首页左右滑动可直接在上传 / 历史之间切换。

## 3. 构建

需要 JDK 21 与 Android SDK（compileSdk 37、build-tools 37）。

```bash
# 16 GB 内存的机器必须降堆，否则两个 4 GB daemon 会把系统吃光
./gradlew :app:assembleDebug \
  -Dorg.gradle.jvmargs="-Xmx2560m" -Dkotlin.daemon.jvmargs="-Xmx1536m" --no-parallel

# 单测（路径模板 / 复制格式 / URL / 设置归一化）
./gradlew :app:testDebugUnitTest

# Lint，目标 0 error
./gradlew :app:lintDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 3.1 发布签名

签名材料只在本地与 CI secret 里，绝不进 Git：

```bash
./scripts/release-signing.sh          # 首次：生成 release.keystore + release-signing.env（600）
. ./release-signing.env               # 之后每次签名构建前 source
./gradlew :app:assembleRelease        # 产物：app/build/outputs/apk/release/app-release.apk
```

- PKCS12 keystore，RSA 4096 / 10000 天，DN `CN=github.wzk0.picup`，库密码与 key 密码是同一个。
- Gradle 侧只认四个环境变量：`PICUP_KEYSTORE_PATH`、`PICUP_KEYSTORE_PASSWORD`、`PICUP_KEY_ALIAS`、`PICUP_KEY_PASSWORD`；缺任何一个都不会产出半签名包（无变量时产出未签名包）。
- `release.keystore` 与 `release-signing.env` 都已在 `.gitignore` 里；keystore 丢了等于换了 App 身份（已装用户只能卸载重装），务必异地备份。
- CI（`.github/workflows/ci.yml`）读取四个仓库 secret：`PICUP_KEYSTORE_BASE64`、`PICUP_KEYSTORE_PASSWORD`、`PICUP_KEY_ALIAS`、`PICUP_KEY_PASSWORD`（脚本会打印对应值）。
- CI 分三档：push 到 `main` 走 `quick-check`（只编译 debug Kotlin）；PR / `workflow_dispatch` 走 `pull-request-check`（test + assembleDebug + lintDebug，不签名、不发布）；`v*` tag 走 `release`（解码 secret 签名 → `apksigner verify` 校验 → `PicUp-<versionName>.apk` + `SHA256SUMS.txt` 作为 release 资产发布）。
- tag 构建四个 secret 缺任何一个直接失败（绝不发布未签名包）；非 tag 的 release 构建缺 secret 时只 warning 并产出未签名包。
- 发版前改 `version.properties`（`code` / `name`），构建不改版本、也不弄脏工作区。

校验签名：

```bash
$ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs app-release.apk
```

## 4. 结构

```
app/src/main/java/com/example/picup/
├── PicUpApp.kt                  Application + AppContainer（5 个单例，不引 DI 框架）
├── MainActivity.kt              单 Activity，edge-to-edge
├── data/
│   ├── Settings.kt              AppSettings / Remote / AccountProfile / CopyFormat / UploadOptions
│   ├── SettingsRepository.kt    DataStore + 单 JSON 文档 + 归一化
│   ├── AtomicJsonFile.kt        tmp → fsync → bak → rename
│   ├── TokenStore.kt            Keystore AES-GCM + InMemory 实现（测试用）
│   ├── HistoryRepository.kt     上传历史 + 缩略图，上限 500 条
│   ├── ImageProcessor.kt        解码 / EXIF 旋转 / 缩放 / 编码 / 缩略图
│   ├── Naming.kt                slug、字节格式化、路径模板
│   ├── CopyFormats.kt           复制模板渲染
│   ├── RemoteUrls.kt            三个平台的 API / raw / Token 页面地址
│   └── DisplayNames.kt          Uri 显示名
├── net/                         HttpURLConnection + JSON 读取小工具
├── git/
│   ├── GitClient.kt             接口、仓库摘要、错误映射
│   ├── GitHubClient.kt          Contents API
│   ├── GitLabClient.kt          Repository Files API
│   └── GiteaClient.kt           Contents API
├── upload/UploadManager.kt      处理 → 渲染路径 → 提交 → 历史
└── ui/
    ├── PicUpRoot.kt             三个页面（主页 / 向导 / 设置）+ Navigation Compose 返回栈
    ├── main/                    上传队列、历史、复制
    ├── setup/                   三步向导：账号 → 仓库 → 存放位置
    ├── settings/                仓库、账号、上传、复制格式、外观、说明
    ├── components/              分段控件、设置行、卡片、缩略图等
    └── theme/                   品牌色板 + Material 3 Expressive
```

## 5. 已知限制

- **私有仓库**：`raw.githubusercontent.com` 与 jsDelivr 都只对公开仓库有效。私有仓库请自建 CDN 反代，或接受「链接只有带 Token 的请求能打开」。
- 缩放上限当前固定为 2048 px（设置界面只暴露格式与质量）。
- **GitHub Enterprise** 的 raw 地址不是 `raw.githubusercontent.com`，需要手动填写公开链接前缀。
- 不做 git 协议：每次上传是 REST 的一次提交，不支持 LFS，单文件上限 64 MB（内存中解码保护）。
- 上传串行执行，这是刻意的：同分支并发提交会互相冲突，也会放大限流。
- 删除远端文件需要对应账号的 Token 仍然有效；Token 失效时只能从历史移除。

## 6. 许可证

GPL-3.0，见 `LICENSE`。