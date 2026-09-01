# Signal-M Harness

在安卓手机（Termux / PRoot）上进行完整 app 开发、HTTP 下载到宿主系统安装测试、开发前 git 同步、开发后测试通过自动回收的端到端工作流。

## 1. 设计动机

| 痛点 | harness 解决方案 |
|---|---|
| Termux 环境缺 JDK / Android SDK / Gradle | `env.sh` 一键安装并锁版本 |
| `start_server.sh` 写死错误路径 `/root/SignalMonitor` | 已重写为 `serve.sh`，路径动态解析 |
| 开发前易忘拉代码导致冲突 | `sync.sh` fetch+rebase，自动保护未提交改动 |
| 手动 mv/cp APK、手动算版本号 | `build.sh` 归集到 `dist/`，文件名带版本号 |
| 手动在文件管理器找 APK 安装 | `install.sh` 下载到 `/sdcard/Download` 并唤起系统安装器 |
| 提交前忘跑测试 | `publish.sh` 以 `test.sh` 通过为门槛 |
| 提交后忘打 tag / 忘 push | `publish.sh` 自动 `tag v<x>` 并 push |

## 2. 与 app 内置 HTTP 服务器的区别

仓库内有两套 HTTP 服务器，**职责不同，端口区分**：

| 组件 | 路径 | 端口 | 运行时机 | 用途 |
|---|---|---|---|---|
| **harness serve.sh** | `harness/serve.sh` | **8888** | 开发构建后 | 把刚构建的 APK 分发给宿主 Android 下载安装 |
| app ApkHttpServer.kt | `app/src/main/java/.../ApkHttpServer.kt` | 8080 | 应用运行时 | 已安装的 app 把自身 APK 分发给其他设备 |

本 harness 仅使用前者（8888）。

## 3. 目录结构

```
harness/
├── config.sh     # 集中配置（路径、版本、端口、工具函数）
├── env.sh        # 环境就位（JDK17 / SDK35 / Gradle8.9 / wrapper）
├── sync.sh       # 开发前 git 同步（fetch + rebase + stash 保护）
├── build.sh      # 构建 APK，归集到 dist/
├── serve.sh      # 8888 端口 HTTP 静态分发
├── install.sh    # 下载到 /sdcard/Download 并触发系统安装器
├── test.sh       # 单元测试 + lint 门槛（可选设备冒烟）
├── publish.sh    # 测试通过后回收：commit / tag v<x> / push
├── harness.sh    # 主编排器（单一入口）
├── README.md     # 本文档
└── logs/         # 各阶段日志（被 .gitignore 忽略）
```

## 4. 版本约束（与 `app/build.gradle.kts` 对齐，禁止随意改动）

| 组件 | 锁定版本 | 依据 |
|---|---|---|
| JDK | **17** | `compileOptions { JavaVersion.VERSION_17 }` |
| Kotlin | **1.9.20** | root `build.gradle.kts` plugin |
| Android Gradle Plugin | **8.2.0** | root `build.gradle.kts` plugin |
| Gradle | **8.9** | `.gitignore` 已锁定 `gradle-8.9`；AGP 8.2.0 要求 ≥8.2 |
| compileSdk | **34** | `app/build.gradle.kts`（aarch64 无官方 arm64 aapt2，lint 豁免 GradleCompatible） |
| Build-Tools | **34.0.0** | 与 AGP 8.2 对齐（SDK 构建工具）|
| minSdk | **26** (Android 8.0) | `app/build.gradle.kts` |
| targetSdk | **35** (Android 15) | `app/build.gradle.kts` |

`env.sh` 在安装后逐项校验，不满足则非零退出。

## 5. 完整流程

### 5.1 首次：环境就位（一次性）

```bash
cd /root/Signal-M
./harness/harness.sh env
```

`env.sh` 做什么：
1. `apt-get install openjdk-17-jdk-headless unzip wget`
2. `apt-get install android-sdk android-sdk-platform-34 android-sdk-build-tools`（Debian 路径 `/usr/lib/android-sdk`，与 `gradle.properties` 的 `aapt2FromMavenOverride` 对齐）
3. 若 Debian 包缺失组件，回退到 `sdkmanager` 自管 cmdline-tools
4. 下载并解压 Gradle 8.9 二进制，执行 `gradle wrapper --gradle-version 8.9` 生成 `gradlew`
5. 写 `local.properties`（`sdk.dir=...`）
6. 输出 `logs/env-check.txt` 终态自检报告

### 5.2 每次开发：sync → 改代码 → build

```bash
./harness/harness.sh sync        # 拉取远端最新（保护未提交改动）
# ... 编辑 app/src/main/... 下的 Kotlin 文件 ...
./harness/harness.sh build       # 默认 debug 构建
# 或 release（需 keystore.properties）：
./harness/harness.sh build release
```

`build.sh` 行为：
- 调用 `./gradlew clean assemble<Debug|Release>`（构建时 `version.properties` 的 minor 自增）
- 产物 `app/build/outputs/apk/<type>/app-<type>.apk` 复制到 `dist/app-<type>-v<版本>.apk`
- 维护软链 `dist/app-latest.apk` → 最新构建
- 输出 SHA-256、大小、构建日志路径

### 5.3 测试 + 分发 + 安装

```bash
./harness/harness.sh test        # 单元测试 + lint（必过门槛）
./harness/harness.sh test --device  # 额外做设备冒烟（需先 install）
./harness/harness.sh serve       # 后台启动 8888 HTTP 服务
./harness/harness.sh install     # 下载到 /sdcard/Download 并唤起安装器
```

`install.sh` 行为：
1. 校验 Termux 工具链（`am`/`pm`/`termux-open`）
2. `curl http://127.0.0.1:8888/app-latest.apk -o /sdcard/Download/app-<版本>.apk`
3. `pm uninstall` 卸载旧版本（避免签名冲突）
4. `termux-open` 或 `am start -a VIEW -t vnd.android/package-archive` 唤起系统安装器
5. 轮询 `pm list packages` 最多 120 秒确认安装成功

### 5.4 测试通过后回收

```bash
./harness/harness.sh publish --yes     # 测试门槛通过后 commit + tag + push
# 或预演：
./harness/harness.sh publish --dry-run
# 自定义提交信息：
./harness/harness.sh publish --yes -m "feat: 新增 XXX"
```

`publish.sh` 安全机制：
- **测试门槛**：必须存在 `logs/test-last.txt` 且包含 `总测试   : 通过`，否则拒绝执行
- 工作树无改动时直接退出，不创建空提交
- 默认交互确认，`--yes` 跳过
- `--dry-run` 仅打印计划不执行
- 自动打标签 `v<version.properties 中的 major.minor.patch>` 并 push

### 5.5 一行全流程

```bash
./harness/harness.sh all --yes              # debug 全流程
./harness/harness.sh all --release --yes    # release 全流程（需 keystore）
```

`all` 顺序：`sync → build → test → serve → install → publish`

## 6. 常用辅助命令

```bash
./harness/harness.sh status    # 工具链/产物/服务/设备状态快照
./harness/harness.sh clean     # 清理 dist/、build/、停 HTTP 服务
./harness/harness.sh serve --stop  # 仅停 HTTP 服务
./harness/harness.sh help      # 用法
```

## 7. 配置项

集中配置在 `harness/config.sh`，可通过环境变量覆盖：

| 变量 | 默认 | 说明 |
|---|---|---|
| `GIT_REMOTE` | `origin` | git 远端名 |
| `GIT_BRANCH` | `main` | 工作分支 |
| `ANDROID_HOME` | `/usr/lib/android-sdk` | SDK 根 |
| `HTTP_PORT` | `8888` | HTTP 分发端口（与 AGENTS.md 约定） |
| `HTTP_BIND` | `0.0.0.0` | HTTP 绑定地址 |
| `DEFAULT_BUILD_TYPE` | `debug` | 默认构建类型 |
| `HOST_DOWNLOAD_DIR` | `/sdcard/Download` | 宿主共享存储下载目录 |

示例：

```bash
HTTP_PORT=9999 ./harness/harness.sh serve
GIT_BRANCH=develop ./harness/harness.sh sync
```

## 8. 日志

各阶段日志写入 `harness/logs/`（已加入 `.gitignore`）：

| 日志 | 来源 |
|---|---|
| `env-check.txt` | env.sh 终态自检 |
| `build-*.log` | build.sh gradle 输出 |
| `build-last.txt` | build.sh 产物摘要 |
| `test-unit-*.log` / `lint-*.log` | test.sh |
| `test-last.txt` | test.sh 摘要（publish 门槛） |
| `http-server.log` | serve.sh Python http.server 输出 |
| `http-server.pid` | serve.sh 进程 PID |
| `serve-endpoints.txt` | serve.sh 下载地址清单 |
| `publish-plan.txt` / `publish-history.txt` | publish.sh |

## 9. 与 AGENTS.md 的关系

本 harness 严格遵循 `AGENTS.md` 约定：
- HTTP 端口 8888（与 AGENTS.md "固定端口: 8888" 一致）
- 测试门槛 = `./gradlew test` + `./gradlew lint`（AGENTS.md 第 270-271 条）
- 用户界面文案保持中文（harness 输出为中文）
- 不修改 `keystore.properties` 或签名配置
- 自定义 View 仍放 `view/`，数据模型仍放主包

## 10. 故障排查

| 症状 | 处置 |
|---|---|
| `env.sh: aapt2 not found` | 确认 `/usr/lib/android-sdk/build-tools/debian/aapt2` 存在；否则 `apt install android-sdk-build-tools` |
| `build.sh: release 构建需 keystore.properties` | 放置签名配置到仓库根，或改用 `build debug` |
| `install.sh: 无法访问宿主存储` | 在 Termux 主 shell 运行 `termux-setup-storage` 授权 |
| `install.sh: 签名不一致安装失败` | harness 已先 `pm uninstall`，若仍失败需手机「允许未知来源」+ 同签名 |
| `publish.sh: 测试门槛未满足` | 先 `./harness/harness.sh test` |
| `gradlew: Network is unreachable` | Termux 内执行 `termux-wake-lock` 防休眠断网 |
