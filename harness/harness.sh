#!/usr/bin/env bash
# harness.sh — Signal-M 全流程编排器
#
# 在安卓手机 (Termux/PRoot) 上进行完整 app 开发的端到端工作流：
#   env     一次性环境就位（JDK17 / Android SDK 34 / Gradle 8.9）
#   sync    开发前 git 拉取远端最新
#   build   构建 APK（默认 debug；release 需 keystore.properties）
#   serve   启动 8888 端口 HTTP 分发
#   install 下载 APK 到宿主存储并触发系统安装器
#   test    单元测试 + lint（+ 可选设备冒烟）
#   publish 测试通过后回收：commit / tag v<x> / push 回 origin
#   all     完整循环：sync -> build -> test -> serve -> install -> publish
#   status  打印当前阶段快照
#   clean   清理产物（dist/、build/、HTTP 服务）
#
# 用法:
#   ./harness/harness.sh <command> [args...]
#   ./harness/harness.sh all --release
#   ./harness/harness.sh status

set -uo pipefail

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HARNESS_DIR/config.sh"
source "$HARNESS_DIR/mirror.sh"

# 子命令分发
COMMAND="${1:-}"
[[ -n "$COMMAND" ]] || COMMAND=help
shift || true

run_stage() {
    local script="$1"; shift
    [[ -x "$script" ]] || chmod +x "$script" 2>/dev/null || true
    bash "$script" "$@"
}

case "$COMMAND" in
    env)
        run_stage "$HARNESS_DIR/env.sh" "$@" ;;

    sync)
        run_stage "$HARNESS_DIR/sync.sh" "$@" ;;

    build)
        run_stage "$HARNESS_DIR/build.sh" "$@" ;;

    serve)
        run_stage "$HARNESS_DIR/serve.sh" "$@" ;;

    install)
        run_stage "$HARNESS_DIR/install.sh" "$@" ;;

    test)
        run_stage "$HARNESS_DIR/test.sh" "$@" ;;

    publish)
        run_stage "$HARNESS_DIR/publish.sh" "$@" ;;

    mirror)
        # 单独（重新）配置国内镜像
        setup_all_mirrors
        [[ -f "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" ]] && patch_gradle_wrapper_mirror || true
        log "镜像配置完成（apt + Maven + Gradle 发行版）"
        ;;

    all)
        # 完整循环：sync -> build -> test -> serve -> install -> publish
        BUILD_TYPE="debug"
        PUB_YES=0
        for a in "$@"; do
            case "$a" in
                --release) BUILD_TYPE="release" ;;
                --yes|-y)  PUB_YES=1 ;;
                *) warn "all: 忽略未知参数 $a" ;;
            esac
        done

        step "全流程: sync -> build ($BUILD_TYPE) -> test -> serve -> install -> publish"
        run_stage "$HARNESS_DIR/sync.sh"
        run_stage "$HARNESS_DIR/build.sh" "$BUILD_TYPE"
        run_stage "$HARNESS_DIR/test.sh"
        run_stage "$HARNESS_DIR/serve.sh"
        run_stage "$HARNESS_DIR/install.sh"
        if [[ "$PUB_YES" == 1 ]]; then
            run_stage "$HARNESS_DIR/publish.sh" --yes
        else
            run_stage "$HARNESS_DIR/publish.sh"
        fi
        log "全流程完成"
        ;;

    status)
        step "当前状态快照"
        {
            echo "仓库    : $REPO_ROOT"
            echo "版本    : $(read_version)"
            echo "分支    : $(cd "$REPO_ROOT" && git rev-parse --abbrev-ref HEAD 2>/dev/null)"
            echo "HEAD    : $(cd "$REPO_ROOT" && git rev-parse --short HEAD 2>/dev/null)"
            echo
            echo "--- 工具链 ---"
            echo "java    : $([ -x "$(command -v java)" ] && java -version 2>&1 | head -1 || echo MISSING)"
            echo "gradlew: $([ -x "$REPO_ROOT/gradlew" ] && echo OK || echo MISSING)"
            echo "SDK     : ${ANDROID_HOME:-MISSING} ($([ -d "${ANDROID_HOME}/platforms/android-34" ] && echo OK || echo MISSING))"
            echo
            echo "--- 产物 ---"
            if [[ -d "$DIST_DIR" ]] && ls "$DIST_DIR"/*.apk >/dev/null 2>&1; then
                ls -lh "$DIST_DIR"/*.apk | awk '{print "  "$NF, $5}'
            else
                echo "  无 APK"
            fi
            echo
            echo "--- HTTP 服务 ---"
            if pgrep -f "http.server $HTTP_PORT" >/dev/null 2>&1; then
                echo "  运行中 (端口 $HTTP_PORT)"
                [[ -f "$LOG_DIR/http-server.pid" ]] && echo "  PID: $(cat "$LOG_DIR/http-server.pid")"
            else
                echo "  未运行"
            fi
            echo
            echo "--- 设备 ---"
            if command -v pm >/dev/null 2>&1 && pm list packages "$APP_PKG" 2>/dev/null | grep -q "$APP_PKG"; then
                echo "  $APP_PKG 已安装"
            else
                echo "  $APP_PKG 未安装"
            fi
        }
        ;;

    clean)
        step "清理产物"
        # 停 HTTP
        bash "$HARNESS_DIR/serve.sh" --stop 2>/dev/null || true
        # 清构建
        if [[ -x "$REPO_ROOT/gradlew" ]]; then
            ( cd "$REPO_ROOT" && ./gradlew clean --no-daemon 2>/dev/null ) || true
        fi
        rm -rf "$DIST_DIR"
        log "已清理 dist/ 与 HTTP 服务"
        ;;

    help|--help|-h)
        cat <<EOF
${C_BOLD}Signal-M harness${C_RESET} — 安卓手机端到端 app 开发工作流

${C_BOLD}用法:${C_RESET}
  harness/harness.sh <command> [args]

${C_BOLD}命令:${C_RESET}
  env       一次性：安装 JDK17 / Android SDK 34 / Gradle 8.9，生成 wrapper
  sync      开发前：git fetch + rebase origin/main，保护本地未提交改动
  build     构建 APK，产物归集到 dist/，文件名带版本号
              args: [debug|release]   默认 debug
  serve     启动 8888 端口 HTTP 静态服务分发 APK
              args: --stop 停止服务
  install   下载 APK 到 /sdcard/Download 并触发系统安装器
              args: [apk-url]   默认 http://127.0.0.1:8888/app-latest.apk
  test      单元测试 + lint 门槛；args: --device 增加设备冒烟
  publish   测试通过后回收：commit / tag v<x> / push
              args: --yes 跳过确认   --dry-run 预演   -m "msg"
  mirror    单独（重新）配置国内镜像（apt 清华 + Maven 阿里云 + Gradle 腾讯云）
  all       完整循环：sync -> build -> test -> serve -> install -> publish
              args: --release   --yes
  status    打印工具链 / 产物 / 服务 / 设备状态快照
  clean     清理 dist/、build/、HTTP 服务

${C_BOLD}典型流程:${C_RESET}
  # 1. 首次：装环境
  harness/harness.sh env
  # 2. 每次开发：拉代码 -> 改代码 -> 构建
  harness/harness.sh sync
  harness/harness.sh build
  # 3. 测试 + 分发 + 安装
  harness/harness.sh test
  harness/harness.sh serve    # 在另一终端
  harness/harness.sh install
  # 4. 测试通过后回收
  harness/harness.sh publish --yes
  # 或一行全流程
  harness/harness.sh all --yes

${C_BOLD}版本约束 (与 app/build.gradle.kts 对齐):${C_RESET}
  JDK 17 | Kotlin 1.9.20 | AGP 8.2.0 | Gradle 8.9
  compileSdk 34 | build-tools 34.0.0 | minSdk 26 | targetSdk 35

文档详见: harness/README.md
EOF
        ;;

    *)
        err "未知命令: $COMMAND"
        echo "运行: harness/harness.sh help 查看用法"
        exit 2
        ;;
esac
