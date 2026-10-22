# Activity 功能目录

保留单个 `:app` 构建模块，按页面功能组织目录与 Kotlin 包名。生产源码根目录为 `app/src/main/java/com/example/carlauncher/`。

| 功能 | 目录（相对源码根目录） | 页面及关联组件 |
| --- | --- | --- |
| 首页 | `ui/home/` | `ComposeMainActivity.kt`，Launcher 入口 |
| 应用抽屉 | `ui/apps/` | `AppDrawerActivity.kt` |
| 设置 | `ui/settings/` | `FragmentLabActivity.kt`、`SettingsMenuFragment.kt`、`SettingsDetailFragment.kt` |
| 广播实验 | `ui/lab/broadcast/` | `BroadcastLabActivity.kt` |
| 数据库实验 | `ui/lab/database/` | `DatabaseLabActivity.kt` |

对应页面测试放在 `app/src/androidTest/java/com/example/carlauncher/ui/` 下的同名功能目录；首页的 `ComposeDashboardTest` 和 `ClockDisplayTest` 归入 `home`。

## 首页职责边界

- `ComposeMainActivity` 选择 `HomeLayout`、将 ViewModel 持有的卡片模块交给页面、
  将可选地图任务视图挂入容器，并处理权限申请和页面跳转。
- `CarLauncherViewModel` 管理宿主 START/STOP、刷新计时、车辆服务连接和地图任务工厂。
  STOP 取消计时、解绑监听并暂停地图任务；ViewModel 清理时关闭地图任务。
  Activity 重建使用同一 ViewModel，重新开始绑定与计时，不重复创建地图任务。
- `VehicleServiceConnection` 仅持有 Application Context，封装绑定和前台服务启停。
  每次注册使用独立回调身份，忽略已经解绑的迟到回调；页面 STOP 只解绑，
  用户点击停止才停止前台服务。首次点击时，尚未收到 Binder 的注册不算已连接。
- `HomeCardModule` 的车辆和媒体实现组装 Model / Presenter / Compose View。
  `HomeCardModel` 保留共享页面快照以兼容现有组件；两个 Presenter 分别更新车辆和媒体字段。
  ViewModel 上的旧交互方法作为委托保留，页面卡片通过模块装配回调。
- `MapHomeCardModule` 保留离线地图及其手势。`MapTaskFactory` 默认返回 null，
  有平台实现时可在首次 START 前配置，由 ViewModel 创建任务，Activity 通过
  `MapTaskContainer` 挂载视图。视图只归当前 Composition 所有，销毁时调用 `releaseView`；
  实现不得在任务或工厂内保留 Activity/View，且释放视图应允许在 `close` 后安全调用。

此改造没有接入系统 TaskView、真实地图渲染或音乐音源。真实导航仍调起外部应用。
Car/VHAL 的实际连接仍在既有 `VehicleSendService` 数据源链路中，ViewModel 管理的是
桌面对该服务的连接，不新建第二条 Car 连接，也不读取车辆属性。

定向 JVM 回归：`CarLauncherHostTest` 验证宿主启停、单一计时器、地图任务重用与释放；
`MediaCardPresenterTest` 验证媒体队列、进度、循环、暂停及共享状态隔离。
实际 Binder、Activity 重建和地图容器视图行为需要设备验证；JVM 测试不替代这些证据。

`CarLauncherApplication` 和跨页面使用的 `ThemePreferences` 保留在应用根包。现有 Compose 组件仍位于 `ui/compose/`，数据库实现仍位于 `data/local/`。资源文件继续遵循 Android 的 `res/layout` 等目录约定。

Manifest、显式 Intent 的类引用、Service 通知返回首页的入口和布局预览的 `tools:context` 已使用新包名。应用 ID 与 Activity 类名保持不变；外部若通过完整组件名启动页面，需要使用新包名，例如 `com.example.carlauncher/.ui.home.ComposeMainActivity`。
