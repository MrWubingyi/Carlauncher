# 本地 GitLab 与 Android CI 搭建指南

本指南随 CarLauncher 源码分享，说明如何在 Windows + Docker Desktop 上搭建本地
GitLab CE、Linux Docker 构建 Runner 和 Windows 设备 Runner。所有命令均从**仓库根目录**执行。
配置依据本目录脚本及根目录 `.gitlab-ci.yml`，不是通用的一键安装包。

> 分享时请上传源码和脚本，不要直接压缩整个工作目录。`.gitlab-local/` 内含 API token、
> Runner token、日志和工作副本；Git 忽略规则不会自动保护普通压缩包。具体范围见文末。

## 1. 架构与前提

```text
Windows 工作站
  ├─ Docker Desktop（Linux containers）
  │    ├─ GitLab CE：HTTP 8929 / SSH 2229
  │    ├─ Docker Runner：标签 carlauncher-docker
  │    └─ 构建容器：carlauncher-android-ci:local
  │         原生源码 → 静态检查 → APK/AAR、测试、覆盖率、JMH
  └─ Windows shell Runner：标签 carlauncher-device
       下载本次 Profiling APK 与符号 → ADB 设备 → simpleperf 与性能门禁
```

准备 Git（含 SSH）、Docker Desktop 与 Compose v2、PowerShell；设备 Runner 另需
PowerShell 7（`pwsh`）、Python 3.9+、完整 JDK 21、Android SDK/NDK 和可用的专用 AAOS 设备。
Docker 需同时容纳 GitLab 与构建负载：当前单个构建容器限制为 6 GB 内存、4 CPU，
还需为 GitLab、Docker 系统及模拟器留出资源。首次安装需要下载镜像、SDK、Gradle、Boost 和依赖。

构建镜像安装 JDK 21、Platform 36.1、Build Tools 36.0.0、NDK 28.2.13676358、
CMake 3.22.1 和 Platform Tools；版本以 [Dockerfile](Dockerfile) 和项目构建文件为准。
基础镜像固定 digest，Android CLI 安装入口和 Platform Tools 并未完全锁定版本。

```powershell
docker version
docker compose version
git submodule update --init --recursive
```

子模块当前从 GitHub 拉取，接收者和构建容器必须能访问 `.gitmodules` 中的地址及固定提交。
若仅拿到不带 `.git` 的源码压缩包，应先获取原始 Git 仓库与子模块历史，再使用发布和来源校验流程；
不要把源码压缩包误认为完整 Git 仓库备份。

## 2. 配置本机访问地址

本指南仅使用 Windows 本机与 Docker 内部网络：

| 使用位置 | HTTP 地址 | Git SSH 地址 |
| --- | --- | --- |
| Windows 本机 | `http://localhost:8929` | `ssh://git@localhost:2229/root/Carlauncher.git` |
| Docker Runner 内部 | `http://gitlab:8929` | 通过内部 HTTP 拉取 |

启动前编辑 [compose.yaml](compose.yaml)，将 `GITLAB_OMNIBUS_CONFIG` 中的
`external_url` 设置为 `http://localhost:8929`，并将 `gitlab` 服务的 `ports`
列表替换为以下两项，确保只监听本机：

```yaml
    ports:
      - '127.0.0.1:8929:8929'
      - '127.0.0.1:2229:22'
```

`external_url` 决定 GitLab 生成的网页链接；Docker Runner 使用独立网络
`carlauncher-gitlab` 内的 `http://gitlab:8929`，保留该内部地址。
保持 HTTP 8929、SSH 2229 可避免修改脚本。若更换端口、项目路径或容器名，
需同步检查 `bootstrap.rb`、`register-runner.ps1`、`publish-snapshot.ps1`、
`pipeline.ps1` 和 `setup-device-runner.ps1`，这些值尚未统一参数化。

Runner 可执行仓库代码，Docker Runner 容器持有 Docker socket，Windows Runner
拥有登录用户权限，只接收可信代码。

## 3. 启动服务并构建 CI 镜像

```powershell
docker compose -f tools/gitlab/compose.yaml up -d
docker compose -f tools/gitlab/compose.yaml ps
docker logs --tail 100 carlauncher-gitlab
```

首次初始化包含数据库迁移，需要等待服务就绪。浏览器打开本机地址，确认登录页可访问后继续。
初始 root 密码只在自己终端查看，不要粘贴到分享材料中；登录后修改密码。

```powershell
docker exec carlauncher-gitlab cat /etc/gitlab/initial_root_password
docker build -t carlauncher-android-ci:local -f tools/gitlab/Dockerfile tools/gitlab
```

构建镜像必须存在于 Runner 使用的同一 Docker daemon；Runner 使用 `if-not-present`，
不会自动构建或更新该镜像。修改 Dockerfile 后需重新执行构建。

## 4. 初始化项目与凭据

准备用于 Git push 的 SSH 公钥；下面假定已有 `~/.ssh/id_ed25519.pub`。
若没有，先用 `ssh-keygen -t ed25519` 创建专用密钥，勿覆盖已有密钥。只复制 `.pub` 公钥。

```powershell
New-Item -ItemType Directory -Force .gitlab-local | Out-Null
docker cp "$env:USERPROFILE/.ssh/id_ed25519.pub" carlauncher-gitlab:/tmp/carlauncher-key.pub
docker cp tools/gitlab/bootstrap.rb carlauncher-gitlab:/tmp/carlauncher-bootstrap.rb
docker exec carlauncher-gitlab gitlab-rails runner /tmp/carlauncher-bootstrap.rb
docker cp carlauncher-gitlab:/tmp/carlauncher-bootstrap.json .gitlab-local/bootstrap.json
```

每条命令成功后再执行下一条。`bootstrap.rb` 会关闭开放注册、创建私有项目
`root/Carlauncher`、加入公钥、创建项目 Docker Runner，并生成有效期 **7 天**的 API token。
凭据写入容器临时文件及本机 `.gitlab-local/bootstrap.json`，不要提交或分享。
重复执行会生成新的 API token；过期后可重新初始化并复制凭据，同时撤销不再使用的旧 token。
脚本使用 GitLab 内部 Rails API，升级固定 GitLab 镜像前需重新验证兼容性。

## 5. 注册 Docker Runner

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gitlab/register-runner.ps1
```

脚本读取 bootstrap 凭据、写入 Runner 配置并执行 `gitlab-runner verify`。
在 GitLab 项目的 Runner 页面确认 `carlauncher-docker` 在线。

脚本同时创建 `carlauncher-debug-signing` 持久卷，供构建容器复用专用 Debug 签名，
避免 Profiling APK 每次更换证书导致覆盖安装失败。这不是 Release 发布签名，不要分享卷内容。
Runner 配置持久化在 Compose 的 `runner-config` 卷内，并发设为 1。

## 6. 注册设备 Runner

完整流水线强制执行 simpleperf，因此首次推送前应准备设备 Runner。
在 Windows 登录用户会话中运行，路径替换成接收者自己的环境：

```powershell
pwsh -NoProfile -File tools/gitlab/setup-device-runner.ps1 `
  -Sdk 'C:\path\to\Android\Sdk' `
  -JavaHome 'C:\path\to\jdk-21' `
  -Serial 'emulator-5554'
```

显式传入 `-JavaHome` 可避免依赖未分享的 `local-build.json`。脚本下载并校验 Runner 19.4.1，
创建标签 `carlauncher-device` 的项目 Runner，并在后台启动；不会安装 Windows 服务。
重启电脑后需重新运行。配置、PID 和日志位于 `.gitlab-local/device-runner/`。

`.gitlab-ci.yml` 当前也定义 `PERF_SERIAL: emulator-5554`；更换设备时同步核对 Job 变量，
不要只改启动参数。采样要求、设备授权和基线见 [simpleperf 指南](../../docs/simpleperf-profiling.md)
及 [CI 报告说明](../../docs/gitlab-reports.md)。

当前性能基线绑定原设备 serial、系统 fingerprint、屏幕与动作配置。换电脑或设备后不能假设
可直接通过：需按审核流程记录新环境的有效测量和基线依据，不得放宽阈值掩盖失败。
没有设备 Runner 时 Job 会等待；设备离线或采样不合格时失败，这都不是完整 CI 通过。

## 7. 上传源码并触发流水线

推荐使用已经审查、提交的源码。以下命令不创建提交；将 `<branch>` 替换为实际分支名：

```powershell
git status --short
git remote -v
# 仅在尚无 gitlab remote 时执行；已有 remote 先核对地址
git remote add gitlab ssh://git@localhost:2229/root/Carlauncher.git
git push -u gitlab <branch>
```

首次 SSH 连接核对服务主机指纹。首次上传后，在 GitLab 项目中确认默认分支。
子模块更改需先发布到 `.gitmodules` 所指远端，保证 CI 可以拉取 gitlink 对应的提交。

也可选择仓库提供的未提交源码快照流程，执行前必须审查待上传内容：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gitlab/publish-snapshot.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gitlab/pipeline.ps1
```

**快照脚本不是只上传当前分支**：它使用独立 Git index 执行 `git add -A`，生成/更新
`codex/gitlab-ci` 快照提交，保留原工作区和原索引，但随后推送**所有本地分支及所有标签**。
它不会包含子模块内部未提交内容。脚本还检查/添加固定 GitHub `upstream` 和本机 `gitlab`
远端；接收者若使用其他仓库，应先调整脚本，或使用上面的普通 `git push` 流程。

推送通常自动触发流水线。`pipeline.ps1` 默认只查询 `codex/gitlab-ci` 的最新流水线并下载
Job 日志到 `.gitlab-local/`；其他分支请使用网页查看。需要手动再运行快照时：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gitlab/pipeline.ps1 -Start
```

`-Start` 还会将项目默认分支设为 `codex/gitlab-ci`，不能作为任意分支的通用重试命令。
API token 过期会导致查询/设备注册失败，但不等于构建失败。

## 8. CI 执行顺序与验收

| Job | 执行环境 | 工作与交付 |
| --- | --- | --- |
| `native-build` | Docker | 从源码编译 Boost/vSomeIP，保存原生库、来源记录及日志 |
| `code-review` | Docker | 下载本次原生产物，运行报告工具自测、Detekt、Checkstyle、两模块 Debug Lint，生成审查报告 |
| `android-build` | Docker | 等待审查通过，构建 Debug/Profiling APK 和 AAR、两模块 JVM 测试、Profiling Lint、APK 库哈希校验、app JaCoCo 报告、JMH 与回归检查 |
| `simpleperf` | Windows shell | 获取本次 APK 与符号，固定动作重放、CPU 采样、报告及性能回归检查 |

MR 使用 MR 流水线；有开放 MR 时抑制同分支重复 push 流水线。simpleperf 通过
`resource_group` 串行占用设备，失败阻断，不是可选手动作业。
原生阶段缓存源码下载包，仍重新编译原生库；Gradle 可复用有效缓存，`UP-TO-DATE`
不代表本次实际重跑。Boost/Gradle 下载镜像仍校验固定 SHA-256，不应跳过校验解决下载失败。

在 GitLab 的 Pipeline/Tests 查看状态，在 Job Artifacts 下载报告；保留期为 14 天。
HTML 可下载后离线打开，不能假定网页直接内嵌显示。

- APK/AAR：`app/build/outputs/apk/`、`someip-android/build/outputs/aar/`。
- 静态审查：`build/reports/code-review/` 及各模块 `build/reports/`。
- JVM 测试：各模块 `build/reports/tests/testDebugUnitTest/`。
- 覆盖率：`app/build/reports/jacoco/fullDebugUnitTestCoverageReport/`。
- JMH：`build/reports/jvm-performance/`；设备报告：`build/reports/simpleperf/`。
- 离线火焰图：`build/reports/simpleperf/raw/flamegraph.html`。

覆盖率报告生成不等于阈值通过：CI 未调用行 90% / 分支 85% 的校验任务。
Checkstyle `NO-SOURCE` 不表示扫描了 Java。构建、JVM 测试、性能门禁、设备仪器测试和
双机 SOME/IP 协议验收应分别记录；此流水线没有执行完整设备仪器测试或双机网络联调。
具体性能预算与固定重放规则见 [报告说明](../../docs/gitlab-reports.md)。

## 9. 排障与停止

| 现象 | 排查方向 |
| --- | --- |
| Compose 端口绑定失败 | 确认仅绑定 127.0.0.1；8929/2229 是否被占用 |
| 页面暂时不可访问 | 查看 `docker logs --tail 100 carlauncher-gitlab`，等待首次初始化 |
| Docker Job 一直 pending | Runner 是否在线、标签是否匹配、Runner verify 是否成功 |
| 构建镜像找不到 | 在同一 Docker daemon 构建 `carlauncher-android-ci:local` |
| 子模块拉取失败 | GitHub 连通性、权限与固定提交是否已发布 |
| API 返回 401 | bootstrap API token 是否过期；重新生成并复制凭据 |
| simpleperf 等待或失败 | Windows Runner 会话、ADB 设备、SDK/NDK、基线环境和原始日志 |
| APK 签名不兼容 | 签名卷是否丢失、设备是否装有不同签名版本；处理卸载前确认数据影响 |
| 下载/来源校验失败 | 检查网络、固定版本及哈希，不引入旧 `.so` 或关闭门禁 |

```powershell
# 停止容器但保留数据
docker compose -f tools/gitlab/compose.yaml stop
# 再次启动
docker compose -f tools/gitlab/compose.yaml up -d
```

不要执行 `docker compose down -v`：它会删除 Compose 持久卷。
源码分享不包含 GitLab 用户、项目服务数据或 Runner 状态；迁移已有服务需另行备份和验证恢复。

## 10. 分享目录范围

建议分享完整的已审查项目源码，并从根 README 链接到本指南。仅分享 `tools/gitlab/`
无法运行 Android CI；还需要根 `.gitlab-ci.yml`、Gradle 配置/Wrapper、`app/`、
`someip-android/`、`config/`、原生及 profiling 工具和可获取的 vSomeIP 子模块。

打包时排除 `.gitlab-local/`、`local-build.json`、`local.properties`、`.gradle/`、
`.gradle-ci/`、`.native-download-cache/`、`.kotlin/`、各层 `build/`、`.cxx/`、
`.externalNativeBuild/`、本机 IDE 状态，以及 SSH 私钥、签名文件、token 和运行日志。
不要包含 Docker 数据卷或 bootstrap JSON；需要示例配置时只使用占位符。

若以 Git 仓库方式分享，还需检查历史、分支和标签是否适合公开；若以压缩包分享，
`.gitignore` 不会自动排除文件，必须检查实际包内容。接收者自行生成密钥与凭据。

本次文档按仓库配置整理，未重新部署 GitLab 或运行整条 CI；部署完成后应保存实际
流水线、提交、Job 状态与产物证据，不能将本指南视为新环境验收记录。
