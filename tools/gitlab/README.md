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

`.gitlab-ci.yml` 对齐 GitHub Actions：递归子模块、固定 SDK/NDK、Boost/vSomeIP
源码构建、Debug APK、JVM 测试、APK 库校验、JaCoCo 报告；产物保留 14 天。
不包含设备测试或双机网络联调。

GitHub upstream 保持 `git@github.com:MrWubingyi/Carlauncher.git`。
GitLab SSH 地址为 `ssh://git@localhost:2229/root/Carlauncher.git`。
不要使用 `docker compose down -v`，该命令会删除持久卷。
