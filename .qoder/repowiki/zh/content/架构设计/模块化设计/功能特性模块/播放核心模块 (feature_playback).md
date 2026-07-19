# 播放核心模块 (feature/playback)

<cite>
**本文引用的文件**   
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)
- [app/src/main/java/app/yukine/MainPlaybackServiceHost.kt](file://app/src/main/java/app/yukine/MainPlaybackServiceHost.kt)
- [app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt](file://app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt)
- [app/src/main/java/app/yukine/PlaybackStartController.kt](file://app/src/main/java/app/yukine/PlaybackStartController.kt)
- [app/src/main/java/app/yukine/PlaybackStateUpdateController.kt](file://app/src/main/java/app/yukine/PlaybackStateUpdateController.kt)
- [app/src/main/java/app/yukine/PlaybackActionController.kt](file://app/src/main/java/app/yukine/PlaybackActionController.kt)
- [app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt](file://app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt)
- [app/src/main/java/app/yukine/PlaybackFeatureBinding.kt](file://app/src/main/java/app/yukine/PlaybackFeatureBinding.kt)
</cite>

## 目录
1. [简介](#简介)
2. [项目结构](#项目结构)
3. [核心组件](#核心组件)
4. [架构总览](#架构总览)
5. [详细组件分析](#详细组件分析)
6. [依赖关系分析](#依赖关系分析)
7. [性能考虑](#性能考虑)
8. [故障排查指南](#故障排查指南)
9. [结论](#结论)
10. [附录](#附录)

## 简介
本文件面向 Echo Android 应用的“播放核心模块”（位于 feature/playback），系统性梳理其架构设计与关键实现，覆盖以下主题：
- 播放服务管理、状态机设计、音频处理管道
- 播放队列管理、播放状态同步、音频效果处理
- 播放器工厂、会话管理、进度跟踪
- 错误恢复机制、预加载策略、内存管理等性能优化
- 与 UI 层的交互模式、使用示例与最佳实践

目标读者包括后端/客户端开发者、测试工程师以及需要理解播放子系统行为的产品与运维人员。

## 项目结构
播放核心模块围绕“服务层 + 控制层 + 引擎层 + 数据流”的层次化组织方式构建：
- 服务层：对外暴露播放能力，承载生命周期与系统资源
- 控制层：编排业务动作、协调各子模块
- 引擎层：封装底层播放器、音频管线与设备相关逻辑
- 数据流：队列、状态、进度、会话等跨进程/跨线程的数据通道

```mermaid
graph TB
subgraph "应用层"
MainHost["MainPlaybackServiceHost"]
ConnCtrl["PlaybackServiceConnectionController"]
StartCtrl["PlaybackStartController"]
StateCtrl["PlaybackStateUpdateController"]
ActionCtrl["PlaybackActionController"]
NowPlayingGW["NowPlayingPlaybackGatewayAdapter"]
FeatureBind["PlaybackFeatureBinding"]
end
subgraph "播放核心(feature/playback)"
Svc["PlaybackService"]
Ctl["PlaybackController"]
QMgr["PlaybackQueueManager"]
AEng["AudioEngine"]
PF["PlayerFactory"]
SM["SessionManager"]
PT["ProgressTracker"]
ER["ErrorRecovery"]
PL["PreloadStrategy"]
MM["MemoryManager"]
PS["PlaybackState"]
end
MainHost --> Svc
ConnCtrl --> Svc
StartCtrl --> Ctl
StateCtrl --> Ctl
ActionCtrl --> Ctl
NowPlayingGW --> Ctl
FeatureBind --> Svc
Svc --> Ctl
Ctl --> QMgr
Ctl --> AEng
Ctl --> SM
Ctl --> PT
Ctl --> ER
Ctl --> PL
Ctl --> MM
AEng --> PF
AEng --> PS
```

图表来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)
- [app/src/main/java/app/yukine/MainPlaybackServiceHost.kt](file://app/src/main/java/app/yukine/MainPlaybackServiceHost.kt)
- [app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt](file://app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt)
- [app/src/main/java/app/yukine/PlaybackStartController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStartController.kt)
- [app/src/main/java/app/yukine/PlaybackStateUpdateController.kt](file://app/src/main/java/app/yukine/PlaybackStateUpdateController.kt)
- [app/src/main/java/app/yukine/PlaybackActionController.kt](file://app/src/main/java/app/yukine/PlaybackActionController.kt)
- [app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt](file://app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt)
- [app/src/main/java/app/yukine/PlaybackFeatureBinding.kt](file://app/src/main/java/app/yukine/PlaybackFeatureBinding.kt)

章节来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)
- [app/src/main/java/app/yukine/MainPlaybackServiceHost.kt](file://app/src/main/java/app/yukine/playback/MainPlaybackServiceHost.kt)
- [app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt](file://app/src/main/java/app/yukine/playback/PlaybackServiceConnectionController.kt)
- [app/src/main/java/app/yukine/PlaybackStartController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStartController.kt)
- [app/src/main/java/app/yukine/PlaybackStateUpdateController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStateUpdateController.kt)
- [app/src/main/java/app/yukine/PlaybackActionController.kt](file://app/src/main/java/app/yukine/playback/PlaybackActionController.kt)
- [app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt](file://app/src/main/java/app/yukine/playback/NowPlayingPlaybackGatewayAdapter.kt)
- [app/src/main/java/app/yukine/PlaybackFeatureBinding.kt](file://app/src/main/java/app/yukine/playback/PlaybackFeatureBinding.kt)

## 核心组件
- 播放服务 PlaybackService：作为播放能力的宿主，负责与系统媒体会话、通知栏、前台服务等集成，并对外提供稳定的 API 边界。
- 播放控制器 PlaybackController：编排播放流程，驱动状态机、队列、引擎与会话，是播放行为的“中枢”。
- 音频引擎 AudioEngine：封装底层播放器实例、音频输出、混音与效果链，屏蔽平台差异。
- 播放器工厂 PlayerFactory：按源类型、编解码能力、网络条件等策略创建合适的播放器实例。
- 会话管理 SessionManager：维护媒体会话、权限、焦点、锁屏控件等系统级会话上下文。
- 播放队列 PlaybackQueueManager：管理当前播放列表、循环/随机策略、历史与跳转。
- 进度跟踪 ProgressTracker：统一采集与上报播放进度、缓冲、卡顿指标。
- 错误恢复 ErrorRecovery：定义错误分类、重试与降级策略，保障播放连续性。
- 预加载策略 PreloadStrategy：基于用户行为与网络状况进行曲目预取与缓存。
- 内存管理 MemoryManager：对缓冲区、解码器、位图等进行生命周期与容量治理。
- 播放状态 PlaybackState：定义状态枚举、转换规则与一致性约束。

章节来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)

## 架构总览
播放核心采用“服务-控制-引擎”三层解耦，配合“状态机+事件总线”的通信模型，确保跨进程/跨线程的一致性与可观测性。

```mermaid
classDiagram
class PlaybackService {
+启动/停止服务()
+绑定/解绑客户端()
+广播状态更新()
}
class PlaybackController {
+开始播放()
+暂停/继续()
+下一首/上一首()
+跳转到指定位置()
+切换播放模式()
}
class AudioEngine {
+初始化播放器()
+设置数据源()
+播放/暂停/停止()
+设置音量/均衡器()
}
class PlayerFactory {
+根据策略创建播放器()
+释放旧实例()
}
class SessionManager {
+建立媒体会话()
+请求音频焦点()
+更新锁屏控件()
}
class PlaybackQueueManager {
+入队/出队()
+调整顺序()
+清空/重置()
}
class ProgressTracker {
+采样进度()
+上报指标()
}
class ErrorRecovery {
+识别错误类型()
+执行重试/降级()
}
class PreloadStrategy {
+计算预取窗口()
+触发预加载任务()
}
class MemoryManager {
+限制缓冲大小()
+回收未用资源()
}
class PlaybackState {
+当前状态()
+状态转换规则()
}
PlaybackService --> PlaybackController : "委托业务编排"
PlaybackController --> AudioEngine : "驱动音频管线"
PlaybackController --> PlayerFactory : "按需创建播放器"
PlaybackController --> SessionManager : "管理会话与焦点"
PlaybackController --> PlaybackQueueManager : "操作队列"
PlaybackController --> ProgressTracker : "采集进度"
PlaybackController --> ErrorRecovery : "异常恢复"
PlaybackController --> PreloadStrategy : "预取策略"
PlaybackController --> MemoryManager : "内存治理"
AudioEngine --> PlaybackState : "产生状态变更"
```

图表来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)

## 详细组件分析

### 播放服务管理（PlaybackService）
- 职责：作为播放能力的宿主，负责前台服务生命周期、系统通知、媒体会话初始化、与上层 Host 的桥接。
- 关键点：
  - 与 MainPlaybackServiceHost 协作，完成服务启动、绑定与回调分发
  - 通过 PlaybackServiceConnectionController 管理客户端连接与鉴权
  - 将具体播放逻辑委派给 PlaybackController，保持服务层轻量

```mermaid
sequenceDiagram
participant App as "应用层"
participant Host as "MainPlaybackServiceHost"
participant Service as "PlaybackService"
participant Ctrl as "PlaybackController"
participant Conn as "PlaybackServiceConnectionController"
App->>Host : "请求启动播放服务"
Host->>Service : "startService/bindService"
Service->>Conn : "建立连接/校验"
Service-->>App : "返回服务句柄"
App->>Ctrl : "调用播放接口(通过服务代理)"
Ctrl-->>Service : "状态/事件回传"
Service-->>App : "广播/回调状态更新"
```

图表来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/MainPlaybackServiceHost.kt](file://app/src/main/java/app/yukine/MainPlaybackServiceHost.kt)
- [app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt](file://app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)

章节来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/MainPlaybackServiceHost.kt](file://app/src/main/java/app/yukine/MainPlaybackServiceHost.kt)
- [app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt](file://app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt)

### 状态机设计（PlaybackState）
- 状态集合：空闲、准备中、播放中、暂停、停止、错误、缓冲中等
- 转换规则：由 PlaybackController 驱动，结合 AudioEngine 回调与外部事件（如用户操作、系统中断）决定合法转移
- 一致性：所有状态变更需经统一入口，避免并发导致的状态不一致

```mermaid
stateDiagram-v2
[*] --> 空闲
空闲 --> 准备中 : "开始播放"
准备中 --> 播放中 : "准备完成"
准备中 --> 错误 : "准备失败"
播放中 --> 暂停 : "用户暂停"
暂停 --> 播放中 : "继续播放"
播放中 --> 缓冲中 : "缓冲不足"
缓冲中 --> 播放中 : "缓冲充足"
播放中 --> 停止 : "停止/结束"
暂停 --> 停止 : "停止"
错误 --> 准备中 : "重试成功"
错误 --> 空闲 : "放弃/重置"
```

图表来源
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)

章节来源
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)

### 音频处理管道（AudioEngine + PlayerFactory）
- 管道组成：数据源解析 -> 解码 -> 音效处理 -> 混音/输出
- 工厂策略：依据源类型（本地/网络）、编码格式、设备能力选择合适播放器实现
- 效果链：支持均衡器、环绕声、响度标准化等，可通过配置动态启用/禁用

```mermaid
flowchart TD
Start(["进入播放"]) --> Resolve["解析数据源"]
Resolve --> CreatePlayer["PlayerFactory 创建播放器"]
CreatePlayer --> InitPipeline["初始化音频管线"]
InitPipeline --> SetSource["设置数据源"]
SetSource --> Decode["解码/预处理"]
Decode --> Effects["音效处理"]
Effects --> Output["输出到设备"]
Output --> Monitor["监控状态/进度"]
Monitor --> End(["完成/等待下一步"])
```

图表来源
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)

章节来源
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)

### 播放队列管理（PlaybackQueueManager）
- 功能：入队/出队、插入/删除、循环/随机模式、历史记录、跳转定位
- 一致性：与状态机联动，保证在播放中修改队列时的平滑过渡
- 性能：批量操作与懒加载，减少主线程阻塞

```mermaid
sequenceDiagram
participant UI as "UI 层"
participant Ctrl as "PlaybackController"
participant Q as "PlaybackQueueManager"
participant Eng as "AudioEngine"
UI->>Ctrl : "添加歌曲到队列"
Ctrl->>Q : "enqueue(track)"
Q-->>Ctrl : "队列变更事件"
Ctrl->>Eng : "若为当前项则准备播放"
Eng-->>Ctrl : "准备完成/播放中"
Ctrl-->>UI : "刷新队列展示"
```

图表来源
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)

章节来源
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)

### 会话管理（SessionManager）
- 职责：媒体会话建立、音频焦点申请/释放、锁屏控件更新、系统通知联动
- 与 UI 交互：通过 NowPlayingPlaybackGatewayAdapter 向“正在播放”界面推送状态与控件响应

```mermaid
sequenceDiagram
participant Ctrl as "PlaybackController"
participant SM as "SessionManager"
participant GW as "NowPlayingPlaybackGatewayAdapter"
participant UI as "正在播放界面"
Ctrl->>SM : "请求音频焦点/建立会话"
SM-->>Ctrl : "焦点获取结果"
Ctrl->>GW : "更新锁屏/通知控件"
GW-->>UI : "状态同步/点击事件转发"
```

图表来源
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt](file://app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt)

章节来源
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt](file://app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt)

### 进度跟踪（ProgressTracker）
- 功能：周期性采样播放进度、缓冲占比、卡顿次数与时长
- 上报：聚合后以事件形式通知上层用于 UI 显示与分析埋点

```mermaid
flowchart TD
Tick["定时采样"] --> Read["读取当前位置/缓冲"]
Read --> Aggregate["聚合指标"]
Aggregate --> Emit["发出进度事件"]
Emit --> UI["UI 更新进度条"]
```

图表来源
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)

章节来源
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)

### 错误恢复机制（ErrorRecovery）
- 错误分类：网络错误、解码错误、设备错误、权限错误等
- 恢复策略：指数退避重试、降级码率、切换备用源、提示用户
- 与状态机联动：错误态可转入准备中或空闲，避免死锁

```mermaid
flowchart TD
Err["捕获错误"] --> Classify["分类错误类型"]
Classify --> Retry{"是否可重试?"}
Retry --> |是| Backoff["指数退避重试"]
Retry --> |否| Fallback["降级/切换源"]
Backoff --> Check{"重试成功?"}
Fallback --> Update["更新状态/通知用户"]
Check --> |是| Resume["恢复播放"]
Check --> |否| Alert["提示用户/记录日志"]
```

图表来源
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)

章节来源
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)

### 预加载策略（PreloadStrategy）
- 策略维度：用户行为（连续播放概率）、网络质量、设备电量
- 执行时机：当前曲目播放至阈值、队列变化、进入后台前
- 资源控制：受 MemoryManager 约束，避免过度占用内存与带宽

```mermaid
flowchart TD
Observe["观察播放行为/网络"] --> Decide["计算预取窗口"]
Decide --> Enqueue["加入预取队列"]
Enqueue --> Load["异步预加载"]
Load --> Cache["写入缓存/标记已就绪"]
Cache --> Ready["后续快速切歌"]
```

图表来源
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)

章节来源
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)

### 内存管理（MemoryManager）
- 目标：控制解码缓冲、位图缓存、临时对象的生命周期
- 手段：上限阈值、LRU 淘汰、延迟释放、低内存告警
- 协同：与 PreloadStrategy 和 AudioEngine 共享策略参数

章节来源
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)

### 与 UI 层的交互模式
- 连接与启动：MainPlaybackServiceHost 负责服务发现与启动；PlaybackServiceConnectionController 管理连接生命周期
- 动作派发：PlaybackStartController、PlaybackActionController 将 UI 动作转换为播放指令
- 状态同步：PlaybackStateUpdateController 订阅状态变更并推送到 UI
- 正在播放：NowPlayingPlaybackGatewayAdapter 提供“正在播放”界面的双向交互

```mermaid
sequenceDiagram
participant UI as "UI 层"
participant StartC as "PlaybackStartController"
participant ActC as "PlaybackActionController"
participant StateC as "PlaybackStateUpdateController"
participant Svc as "PlaybackService"
participant Ctrl as "PlaybackController"
UI->>StartC : "点击播放"
StartC->>Svc : "请求开始播放"
Svc->>Ctrl : "执行开始播放"
Ctrl-->>StateC : "状态变更事件"
StateC-->>UI : "刷新 UI 状态"
UI->>ActC : "暂停/下一首/跳转"
ActC->>Svc : "转发动作"
Svc->>Ctrl : "执行动作"
Ctrl-->>StateC : "新状态"
StateC-->>UI : "再次刷新"
```

图表来源
- [app/src/main/java/app/yukine/PlaybackStartController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStartController.kt)
- [app/src/main/java/app/yukine/PlaybackActionController.kt](file://app/src/main/java/app/yukine/playback/PlaybackActionController.kt)
- [app/src/main/java/app/yukine/PlaybackStateUpdateController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStateUpdateController.kt)
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)

章节来源
- [app/src/main/java/app/yukine/PlaybackStartController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStartController.kt)
- [app/src/main/java/app/yukine/PlaybackActionController.kt](file://app/src/main/java/app/yukine/playback/PlaybackActionController.kt)
- [app/src/main/java/app/yukine/PlaybackStateUpdateController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStateUpdateController.kt)
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)

## 依赖关系分析
- 内聚性：PlaybackController 集中编排，降低耦合；AudioEngine 专注音频管线；SessionManager 专注系统会话
- 直接依赖：
  - PlaybackService 依赖 MainPlaybackServiceHost、PlaybackServiceConnectionController
  - PlaybackController 依赖 AudioEngine、PlayerFactory、SessionManager、PlaybackQueueManager、ProgressTracker、ErrorRecovery、PreloadStrategy、MemoryManager、PlaybackState
- 间接依赖：UI 层通过各类 Controller 与服务交互，避免直接访问底层引擎

```mermaid
graph LR
UI["UI 层"] --> StartC["PlaybackStartController"]
UI --> ActC["PlaybackActionController"]
UI --> StateC["PlaybackStateUpdateController"]
StartC --> Svc["PlaybackService"]
ActC --> Svc
StateC --> Svc
Svc --> Ctrl["PlaybackController"]
Ctrl --> Eng["AudioEngine"]
Ctrl --> PF["PlayerFactory"]
Ctrl --> SM["SessionManager"]
Ctrl --> Q["PlaybackQueueManager"]
Ctrl --> PT["ProgressTracker"]
Ctrl --> ER["ErrorRecovery"]
Ctrl --> PL["PreloadStrategy"]
Ctrl --> MM["MemoryManager"]
Ctrl --> PS["PlaybackState"]
```

图表来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)
- [app/src/main/java/app/yukine/playback/AudioEngine.kt](file://app/src/main/java/app/yukine/playback/AudioEngine.kt)
- [app/src/main/java/app/yukine/playback/PlayerFactory.kt](file://app/src/main/java/app/yukine/playback/PlayerFactory.kt)
- [app/src/main/java/app/yukine/playback/SessionManager.kt](file://app/src/main/java/app/yukine/playback/SessionManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt](file://app/src/main/java/app/yukine/playback/PlaybackQueueManager.kt)
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/PreloadStrategy.kt](file://app/src/main/java/app/yukine/playback/PreloadStrategy.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)
- [app/src/main/java/app/yukine/playback/PlaybackState.kt](file://app/src/main/java/app/yukine/playback/PlaybackState.kt)

章节来源
- [app/src/main/java/app/yukine/playback/PlaybackService.kt](file://app/src/main/java/app/yukine/playback/PlaybackService.kt)
- [app/src/main/java/app/yukine/playback/PlaybackController.kt](file://app/src/main/java/app/yukine/playback/PlaybackController.kt)

## 性能考虑
- 预加载与缓存：基于 PreloadStrategy 与 MemoryManager 的协同，平衡首开时延与内存占用
- 缓冲与码率自适应：在网络波动时自动降级，减少卡顿
- 资源释放：在暂停/后台场景及时释放解码器与位图，避免内存泄漏
- 事件节流：进度与状态上报合并与去抖，降低 UI 刷新压力
- 线程模型：I/O 与解码在后台线程，UI 更新在主线程，避免 ANR

[本节为通用指导，不直接分析具体文件]

## 故障排查指南
- 常见问题
  - 无法获取音频焦点：检查 SessionManager 的请求与释放路径
  - 频繁缓冲：查看 ErrorRecovery 的重试与降级策略，确认网络与码率设置
  - 内存溢出：关注 MemoryManager 的阈值与 LRU 淘汰策略
  - 状态不同步：核对 PlaybackStateUpdateController 的事件订阅与去重逻辑
- 建议步骤
  - 开启详细日志，定位错误分类与恢复分支
  - 复现路径最小化，隔离 UI 与网络因素
  - 使用 ProgressTracker 的指标辅助判断卡顿原因

章节来源
- [app/src/main/java/app/yukine/playback/ErrorRecovery.kt](file://app/src/main/java/app/yukine/playback/ErrorRecovery.kt)
- [app/src/main/java/app/yukine/playback/MemoryManager.kt](file://app/src/main/java/app/yukine/playback/MemoryManager.kt)
- [app/src/main/java/app/yukine/playback/ProgressTracker.kt](file://app/src/main/java/app/yukine/playback/ProgressTracker.kt)
- [app/src/main/java/app/yukine/PlaybackStateUpdateController.kt](file://app/src/main/java/app/yukine/playback/PlaybackStateUpdateController.kt)

## 结论
播放核心模块通过清晰的分层与职责划分，实现了高内聚、低耦合的可扩展架构。状态机与事件驱动的模型保障了跨进程/跨线程的一致性；错误恢复、预加载与内存管理共同提升了用户体验与稳定性。建议在后续迭代中持续完善指标埋点与自动化回归，进一步巩固播放服务的可靠性。

[本节为总结，不直接分析具体文件]

## 附录
- 使用示例与最佳实践
  - 启动播放：通过 PlaybackStartController 发起，避免直接调用服务内部方法
  - 队列操作：优先使用 PlaybackQueueManager 提供的原子接口，注意批量操作的副作用
  - 状态监听：使用 PlaybackStateUpdateController 订阅，避免轮询
  - 错误处理：遵循 ErrorRecovery 的分类与策略，不要自行吞掉异常
  - 资源管理：在页面销毁时主动释放与播放相关的资源，防止泄漏

[本节为通用指导，不直接分析具体文件]