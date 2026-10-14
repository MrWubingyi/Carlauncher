# 本地 GitLab / Runner

GitLab 页面：http://localhost:8929；Git SSH 端口：2229。
仅监听 Windows 本机回环地址。Docker 卷持久化 GitLab 数据和 Runner 配置。

```powershell
docker compose -f tools/gitlab/compose.yaml up -d
docker build -t carlauncher-android-ci:local -f tools/gitlab/Dockerfile tools/gitlab
```

初次启动需要等待数据库迁移和服务初始化。root 初始密码可在本机读取：

```powershell
docker exec carlauncher-gitlab cat /etc/gitlab/initial_root_password
```

初始化脚本 `bootstrap.rb` 创建私有项目、加入工作站公钥、禁用开放注册并生成项目
Runner 和有效期 7 天的 API token。执行前将公钥复制到容器 `/tmp/carlauncher-key.pub`。
输出凭据保存在 Git 忽略的 `.gitlab-local/bootstrap.json`；不要提交或分享。
随后执行 `register-runner.ps1`，其 Docker executor 使用独立 `carlauncher-gitlab`
网络，从内部 `http://gitlab:8929` 拉取代码，使用本机构建镜像。
Runner 持有 Docker socket，供本仓库的可信流水线使用。
本机网络无法连接 Boost 默认归档站时，GitLab CI 使用 SourceForge Boost 镜像，
继续验证仓库固定 SHA-256；仅缓存源码归档，每次 Job 仍重新编译原生库。
Gradle 官方分发跳转在本机网络下不可用时，从镜像下载同版本发行包，验证 Wrapper
中固定 SHA-256 后写入官方 URL 对应缓存，保持 Wrapper 配置不变。

`.gitlab-ci.yml` 对齐 GitHub Actions：递归子模块、固定 SDK/NDK、Boost/vSomeIP
源码构建、Debug APK、JVM 测试、APK 库校验、JaCoCo 报告；产物保留 14 天。
不包含设备测试或双机网络联调。

GitHub upstream 保持 `git@github.com:MrWubingyi/Carlauncher.git`。
GitLab SSH 地址为 `ssh://git@localhost:2229/root/Carlauncher.git`。
首次上传使用 `codex/gitlab-ci` 快照分支包含当前未提交源码；原工作分支和索引保留。
GitLab 默认分支指向该快照，日常提交后可推送对应分支：

```powershell
git push gitlab codex/gitlab-ci
```

需要再次上传未提交的本地源码快照时，执行 `publish-snapshot.ps1`；脚本通过独立
Git index 生成快照提交，保留原有工作区。`pipeline.ps1` 查看最新快照流水线，
加 `-Start` 可手动运行；Job 日志写入 `.gitlab-local`。
不要使用 `docker compose down -v`，该命令会删除持久卷。
