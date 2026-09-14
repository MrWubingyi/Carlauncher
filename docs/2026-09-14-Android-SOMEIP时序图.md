# Android SOME/IP 时序图

按 2026-09-14 当前代码整理。Android 是 Client 和车辆数据发送方，Ubuntu 是 Service。Mermaid 代码可直接在 Obsidian 阅读视图渲染。

服务 `0x1111`，实例 `0x2222`，Method `0x1001`（SetVehicleState）；请求载荷为 16 字节、大端编码。SD 使用 `224.244.224.245:30490`，当前联调服务的 Method 使用 UDP 30509。

## 1. 启动与服务发现

```mermaid
sequenceDiagram
    autonumber
    participant A as MainActivity 主线程
    participant S as VehicleSendService
    participant J as VsomeipClient Java
    participant N as AndroidNetworkMonitor
    participant C as JNI Client
    participant V as vSomeIP 运行时
    participant U as Ubuntu Service

    A->>S: 经 Android 框架启动前台 Service 并绑定
    S->>S: onCreate：startForeground、启动数据源
    S->>S: monitor.start：STARTING；启动 100ms watchdog
    S-->>A: Binder 返回 Service；注册状态监听
    Note over S,J: 进程级单线程执行器串行执行 native start/stop
    S->>S: 执行器准备私有配置；只更新根 unicast
    S->>J: setListener；start(context, configPath)
    opt SD 启用且可取得 WifiManager
        J->>J: acquire 非引用计数 MulticastLock
    end
    J->>N: start：先发布网络未知/不可用，再注册 NetworkCallback
    N->>C: nativeNetworkState：初始 down
    J->>C: nativeStart(configPath)
    C->>C: 配置环境和私有路由目录；注册三类 handler
    C->>V: app.init；创建专用线程执行 app.start
    C-->>J: 初始化成功，调用返回
    J-->>S: started=true，允许后续数据尝试发送
    Note over J,V: start 返回不代表已发现服务或已有成功响应<br/>网络回调与 REGISTERED 异步发生，不保证相对顺序
    V-->>C: ST_REGISTERED
    C->>V: request_service(0x1111, 0x2222)
    C-->>J: onNativeEvent REGISTERED
    Note over S,J: Service 未覆写 onRegistered，不据此设置 ONLINE
    N->>N: LinkProperties / blocked 回调更新网络快照
    N->>C: nativeNetworkState(address, iface, up, route)
    C->>C: 更新 network_ready
    C->>V: 发布真实接口与组播路由状态
    alt 配置启用 SD，且发现所需网络已就绪
        V->>U: FindService 组播
        U-->>V: OfferService，携带业务 endpoint
        Note over V,U: 周期 Offer 也可独立触发发现，不必先收到本次 Find
    else 显式静态端点配置，关闭 SD
        V->>V: 按配置建立远端 endpoint
    end
    V-->>C: availability handler
    C->>C: effectiveAvailable = serviceAvailable AND networkReady
    C-->>J: onNativeEvent AVAILABLE
    J-->>S: 主线程 Handler：onAvailable(true)
    S->>S: monitor：WAITING_RESPONSE，启动响应计时基线
    S-->>A: 状态监听触发 ViewModel / Repository / LiveData 更新
```

网络观察器只判断配置地址对应的接口、阻塞状态和路由，不能证明外部组播互通。图中主成功路径假设最终有效可用性为 true；false 会进入 UNAVAILABLE。静态端点模式下的 available 也不是业务成功证据。

## 2. 数据发送、响应匹配与超时

```mermaid
sequenceDiagram
    autonumber
    participant D as VehicleDataSource 数据线程
    participant S as VehicleSendService
    participant J as VsomeipClient / Codec
    participant C as JNI Client
    participant U as Ubuntu Service
    participant M as SomeipConnectionMonitor
    participant A as Activity / ViewModel / Repository

    loop 数据源产生车辆状态，正常目标频率 10Hz
        D->>S: dataListener.onStateChanged(VehicleState)
        S->>S: 保存 latestVehicleState
        opt vsomeipStarted 为 true
            S->>J: buildAndSend(seq, timestamp, speed, gear, status)
            J->>J: SomeipPayloadCodec.encode：16B 大端载荷
            J->>C: nativeSendState(payload)
            alt app 未启动、有效服务不可用或载荷长度错误
                C-->>J: false：未提交
            else 可以提交
                C->>C: 持发送锁，创建 Request / Payload
                C->>U: 经 app.send 提交 Method 0x1001
                C->>C: 记录 pending[ClientID, SessionID] 与单调时间
                C-->>J: true：仅代表提交
                Note over C,U: 响应处理使用同一把锁，不能早于 pending 插入<br/>pending 匹配使用 SOME/IP ClientID/SessionID，而非载荷 seq
                U-->>C: RESPONSE 或 ERROR，携带 ReturnCode
                C->>C: 检查 Method、消息类型、pending、网络与停止状态
                alt 无匹配、已停止、网络不可用或请求已满 3 秒
                    C->>C: 丢弃；不通知 Java 成功
                else 首个未过期的匹配响应
                    C->>C: 消费 pending，后续重复响应被忽略
                    C-->>J: onNativeEvent(RESPONSE_OK / RESPONSE_ERROR, rc)
                    J-->>S: 主线程 Handler：onResponse(ok, rc)
                    S->>M: onResponse：刷新响应时间基线
                    alt RESPONSE 且 rc 等于 0
                        M->>M: ONLINE
                    else ERROR 或非零 rc
                        M->>M: RESPONSE_ERROR
                    end
                    S-->>A: 主线程状态监听通知
                    A->>S: Repository.refresh 读取 getSomeipStatus
                    A->>A: LiveData 更新连接文字与车辆显示
                end
            end
        end
    end
    Note over S,M: 以下 watchdog 独立运行；即使数据源暂时不发帧也会检查
    loop 主线程每 100ms 检查
        S->>M: checkTimeout(elapsedRealtime)
        opt 服务仍 available 且距响应基线至少 3000ms
            M->>M: RESPONSE_TIMEOUT
            M-->>S: 本次发生状态转换
            S-->>A: 更新超时状态
        end
    end
```

图中的 `app.send → Ubuntu` 表示逻辑请求链路，实际收发由 vSomeIP 异步完成，多个请求可同时 pending；10Hz 不会等待上一个请求响应。

两个 3 秒机制不同：

- **JNI 请求有效期**：限制每个 ClientID/SessionID 的响应接收窗口；发送时清理旧项，收到响应时再次检查，不会逐个向 Java 抛出超时事件。
- **Service 响应健康计时**：从首次 AVAILABLE 或最近一次有效匹配响应开始，3 秒没有新的响应才进入 RESPONSE_TIMEOUT。成功和错误响应都会刷新基线，重复 AVAILABLE 不延长基线。并非任意单个请求丢失就令 UI 超时。

启动配置读取/解析异常、native 加载或启动失败、执行器调度失败会进入 START_FAILED。丢失网络或服务可用性会进入 UNAVAILABLE，pending 在网络 down 时清空；恢复可用性先回到 WAITING_RESPONSE。

## 3. 后台解绑、恢复绑定与资源释放

```mermaid
sequenceDiagram
    autonumber
    participant A as MainActivity 主线程
    participant S as VehicleSendService
    participant E as 进程级生命周期执行器
    participant J as VsomeipClient Java
    participant N as AndroidNetworkMonitor
    participant C as JNI Client
    participant V as vSomeIP / IO 线程

    A->>A: Home 导致 onStop
    A->>S: clearStateListener；unbindService
    A->>A: ViewModel.detachService
    Note over S,V: 已启动的前台 Service 继续运行，数据与 SOME/IP 不因解绑停止
    A->>A: 返回前台，onStart
    A->>S: bindService(flags=0)
    S-->>A: 获取仍存活的 Service
    A->>A: attachService 后注册监听，读取现有快照

    A->>S: 用户点停止：清监听、解绑、stopService
    S->>S: 框架调用 onDestroy
    S->>S: stopping=true；monitor=STOPPED
    S->>S: 取消 watchdog/主线程任务，停止数据源
    S->>S: vsomeipStarted=false
    S->>E: 排队清理 native 资源
    Note over S,E: stop/join 不占用 Android 主线程<br/>新 Service 的 start 也排到同一个执行器，位于旧 stop 之后
    E->>J: clearListener(旧 Service listener)
    E->>J: stop()
    J->>N: close：先拒绝迟到回调，再注销 NetworkCallback
    N->>C: 发布网络 down，清 pending
    J->>C: nativeStop()
    C->>C: 标记停止、清 pending、移出 app 与 IO 线程句柄
    C->>V: clear_all_handler；release_service；app.stop
    V-->>C: IO 线程退出，join 完成
    C-->>J: onNativeEvent STOPPED
    Note over J,S: 已清除 listener；排队的旧回调通过身份检查被丢弃
    C-->>J: nativeStop 返回
    J->>J: 若持有 MulticastLock 则释放，引用清空
    J-->>E: stop 完成
    E->>E: 记录 SOMEIP_RELEASED
```

重复 native stop 在 app 已为空时直接返回；`MulticastLock` 只有非空且持有时才实际释放。现有“SD multicast lock released”日志在 stop 的 finally 中输出，因此不能只数该日志来统计实际释放次数。

## 阅读与源码对应

| 图中角色 | 当前实现 |
| --- | --- |
| 页面绑定、停止、后台与恢复 | `MainActivity.java` 的 onStart、onStop、serviceConnection、stopVehicleService |
| 生命周期执行器、数据发送、watchdog | `VehicleSendService.java` 的 SomeipLifecycle、runVsomeipStart、dataListener、someipWatchdog、onDestroy |
| 配置、网络观察器、组播锁与主线程回调 | `VsomeipClient.java` 的 start(Context, String)、stop、onNativeEvent |
| 地址与路由监测 | `AndroidNetworkMonitor.java`、`NetworkStateTracker.java` |
| 请求关联、响应去重、运行时启停 | `app/src/main/cpp/carlauncher.cpp` |
| 独立 SOME/IP 状态 | `SomeipConnectionMonitor.java` |
| UI 读取 Service 快照 | `VehicleRepository.java`、`CockpitViewModel.java` |

以上为代码结构图，没有重新执行联调测试。此前 9 月 10 日已分别验证真实 Service 生命周期，以及 socket/TAP 拓扑下的 SD 发现和 Method；两者不是一次联合测试。TCP 二层转接改变底层网络路径，不改变上述 Java/JNI 业务调用顺序。
