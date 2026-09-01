#!/usr/bin/env bash
# mirror.sh — 国内加速镜像配置
# 覆盖范围：
#   1. apt 源        → 清华 TUNA (ports.ubuntu.com → mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/)
#   2. Gradle 发行版 → 腾讯云 (services.gradle.org → mirrors.cloud.tencent.com/gradle/)
#   3. Maven 依赖    → 阿里云 (maven central + google + plugin portal → maven.aliyun.com)
#   4. Android SDK   → 沿用 dl.google.com（可达，约 3s；sdkmanager 仓库 URL 内建，无干净国内镜像）
#
# 设计：source 本文件无副作用；显式调用 setup_* 函数执行配置。
# 所有镜像可通过环境变量覆盖（见下方默认值）。

# 仅在尚未设置时填默认值
: "${APT_MIRROR_HOST:=mirrors.tuna.tsinghua.edu.cn}"
: "${GRADLE_DIST_MIRROR:=https://mirrors.cloud.tencent.com/gradle}"
: "${MAVEN_PUBLIC:=https://maven.aliyun.com/repository/public}"
: "${MAVEN_GOOGLE:=https://maven.aliyun.com/repository/google}"
: "${MAVEN_PLUGIN:=https://maven.aliyun.com/repository/gradle-plugin}"
: "${MAVEN_CENTRAL:=https://maven.aliyun.com/repository/central}"

# Gradle 用户级 init 脚本目录（对所有 gradle 调用生效）
GRADLE_USER_INIT_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/init.d"
GRADLE_INIT_FILE="$GRADLE_USER_INIT_DIR/zz-harness-mirror.gradle"

# ---------------------------------------------------------------------------
# 1. apt 镜像
# ---------------------------------------------------------------------------
# ARM/aarch64 用 ubuntu-ports 镜像；x86_64 用 ubuntu 镜像
apt_mirror_uri() {
    local arch; arch="$(dpkg --print-architecture 2>/dev/null || uname -m)"
    case "$arch" in
        arm64|aarch64|armhf) echo "https://$APT_MIRROR_HOST/ubuntu-ports/" ;;
        *) echo "https://$APT_MIRROR_HOST/ubuntu/" ;;
    esac
}

setup_apt_mirror() {
    step "配置 apt 国内镜像 ($APT_MIRROR_HOST)"

    local src_list=/etc/apt/sources.list
    local src_d=/etc/apt/sources.list.d
    local new_uri; new_uri="$(apt_mirror_uri)"

    # DEB822 格式（新 Ubuntu）：*.sources 文件
    local changed=0
    local f
    for f in "$src_d"/*.sources; do
        [[ -f "$f" ]] || continue
        if grep -q "ports.ubuntu.com\|archive.ubuntu.com\|deb.debian.org" "$f"; then
            cp -n "$f" "$f.bak.$(date +%s)" 2>/dev/null || true
            # 同时替换 http 与 https 的官方源
            sed -i -E \
                -e "s|https?://ports\.ubuntu\.com/ubuntu-ports/|$new_uri|g" \
                -e "s|https?://archive\.ubuntu\.com/ubuntu/|$new_uri|g" \
                -e "s|https?://deb\.debian\.org/debian/|https://$APT_MIRROR_HOST/debian/|g" \
                "$f"
            changed=1
            log "已替换 DEB822 源: $f → $new_uri"
        fi
    done

    # 经典 sources.list 格式
    if [[ -f "$src_list" ]] && grep -qE "ports\.ubuntu\.com|archive\.ubuntu\.com|deb\.debian\.org" "$src_list"; then
        cp -n "$src_list" "$src_list.bak.$(date +%s)" 2>/dev/null || true
        sed -i -E \
            -e "s|https?://ports\.ubuntu\.com/ubuntu-ports/|$new_uri|g" \
            -e "s|https?://archive\.ubuntu\.com/ubuntu/|$new_uri|g" \
            -e "s|https?://deb\.debian\.org/debian/|https://$APT_MIRROR_HOST/debian/|g" \
            "$src_list"
        changed=1
        log "已替换经典源: $src_list"
    fi

    if [[ "$changed" == 0 ]]; then
        info "apt 源未发现官方地址，可能已是镜像，无需替换"
    else
        log "apt 源已替换为国内镜像"
    fi
    # 无论是否替换都刷新索引，保证包列表最新
    info "apt-get update（刷新索引）"
    apt-get update -qq 2>&1 | tail -3 | sed 's/^/  /' || warn "apt update 有警告（通常可忽略）"
}

# ---------------------------------------------------------------------------
# 2. Gradle 发行版镜像
# ---------------------------------------------------------------------------
# 输入 Gradle 版本（如 8.9），输出镜像下载 URL
gradle_dist_url() {
    local ver="$1"
    echo "$GRADLE_DIST_MIRROR/gradle-${ver}-bin.zip"
}

# 修补 gradle-wrapper.properties 的 distributionUrl 指向镜像
patch_gradle_wrapper_mirror() {
    local props="$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties"
    [[ -f "$props" ]] || { info "无 wrapper properties，跳过镜像修补"; return 0; }
    local mirror_url; mirror_url="$(gradle_dist_url "$REQUIRED_GRADLE")"
    # 转义为 properties 转义格式（: → \:）
    local esc; esc="${mirror_url//:/\\:}"
    if grep -q "distributionUrl=" "$props"; then
        sed -i -E "s|^distributionUrl=.*|distributionUrl=$esc|" "$props"
        log "wrapper distributionUrl → $mirror_url"
    fi
}

# ---------------------------------------------------------------------------
# 3. Maven 依赖镜像（Gradle init 脚本，全局生效）
# ---------------------------------------------------------------------------
write_gradle_init() {
    step "写入 Gradle init 脚本（Maven → 阿里云）"
    ensure_dir "$GRADLE_USER_INIT_DIR"
    cat > "$GRADLE_INIT_FILE" <<'GRADLE_INIT'
// harness 自动生成：将 Maven 仓库镜像到阿里云（国内加速）
// 兼容项目 settings.gradle.kts 的 FAIL_ON_PROJECT_REPOS：
//   - 不可向项目级 repositories 注入（会被拒绝），仅注入 settings 级 + buildscript 级
//   - 每个仓库显式命名避免与既有 google()/mavenCentral() 重名冲突

allprojects {
    buildscript {
        repositories {
            maven { name 'aliyun-plugin'; url 'https://maven.aliyun.com/repository/gradle-plugin' }
            maven { name 'aliyun-google';  url 'https://maven.aliyun.com/repository/google' }
            maven { name 'aliyun-public';  url 'https://maven.aliyun.com/repository/public' }
            maven { name 'aliyun-central'; url 'https://maven.aliyun.com/repository/central' }
        }
    }
}

settingsEvaluated { settings ->
    settings.pluginManagement {
        repositories {
            maven { name 'aliyun-plugin'; url 'https://maven.aliyun.com/repository/gradle-plugin' }
            maven { name 'aliyun-google';  url 'https://maven.aliyun.com/repository/google' }
            maven { name 'aliyun-public';  url 'https://maven.aliyun.com/repository/public' }
        }
    }
    settings.dependencyResolutionManagement {
        repositories {
            maven { name 'aliyun-plugin'; url 'https://maven.aliyun.com/repository/gradle-plugin' }
            maven { name 'aliyun-google';  url 'https://maven.aliyun.com/repository/google' }
            maven { name 'aliyun-public';  url 'https://maven.aliyun.com/repository/public' }
            maven { name 'aliyun-central'; url 'https://maven.aliyun.com/repository/central' }
        }
    }
}
GRADLE_INIT
    log "init 脚本: $GRADLE_INIT_FILE"
}

# 一次性配置全部镜像（env.sh 在装包前调用）
setup_all_mirrors() {
    setup_apt_mirror
    write_gradle_init
}
