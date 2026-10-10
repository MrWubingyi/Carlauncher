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

`CarLauncherApplication` 和跨页面使用的 `ThemePreferences` 保留在应用根包。现有 Compose 组件仍位于 `ui/compose/`，数据库实现仍位于 `data/local/`。资源文件继续遵循 Android 的 `res/layout` 等目录约定。

Manifest、显式 Intent 的类引用、Service 通知返回首页的入口和布局预览的 `tools:context` 已使用新包名。应用 ID 与 Activity 类名保持不变；外部若通过完整组件名启动页面，需要使用新包名，例如 `com.example.carlauncher/.ui.home.ComposeMainActivity`。
