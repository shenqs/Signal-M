#!/usr/bin/env bash
# publish.sh — 开发后回收（commit / tag / push）
# 仅在 test.sh 通过后执行。把工作树改动提交、打版本标签、推送回远端。
# 安全机制：默认展示计划，加 --yes 才真正执行；--dry-run 仅打印命令。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"

step "开发后回收 (publish)"

cd "$REPO_ROOT"

DRY_RUN=0
AUTO_YES=0
MSG=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=1 ;;
        --yes|-y)  AUTO_YES=1 ;;
        --message|-m) shift; MSG="${1:-}" ;;
        -h|--help)
            cat <<EOF
用法: publish.sh [--dry-run] [--yes] [--message "提交说明"]
  --dry-run   仅打印将执行的命令，不实际提交
  --yes       跳过交互确认，直接执行
  --message   自定义提交说明（默认按版本号自动生成）
EOF
            exit 0 ;;
        *) err "未知参数: $1"; exit 2 ;;
    esac
    shift
done

# 1. 测试门槛：必须先通过 test.sh
#    校验最近一次 test-last.txt 是否为通过状态
LAST_TEST="$LOG_DIR/test-last.txt"
if [[ ! -f "$LAST_TEST" ]] || ! grep -q "总测试   : 通过" "$LAST_TEST"; then
    err "未检测到通过状态的测试记录 ($LAST_TEST)"
    die "请先运行: harness/test.sh"
fi
log "测试门槛已满足"

# 2. 检查改动
if git diff-index --quiet HEAD -- 2>/dev/null && \
   git diff --quiet --cached && \
   [[ -z "$(git ls-files --others --exclude-standard)" ]]; then
    warn "工作树无改动，无需提交"
    log "可手动 push: git push $GIT_REMOTE $GIT_BRANCH"
    exit 0
fi

# 3. 生成提交信息
VERSION=$(read_version)
[[ -n "$MSG" ]] || MSG="release: v$VERSION (harness auto-publish)"

step "提交计划"
{
    echo "分支    : $GIT_BRANCH"
    echo "版本    : v$VERSION"
    echo "信息    : $MSG"
    echo "Dry-run : $([ $DRY_RUN == 1 ] && echo 是 || echo 否)"
    echo
    echo "改动概览:"
    git status --short | sed 's/^/  /' | head -40
} | tee "$LOG_DIR/publish-plan.txt"

if [[ "$DRY_RUN" == 1 ]]; then
    log "Dry-run 模式，未执行任何变更"
    exit 0
fi

# 4. 交互确认
if [[ "$AUTO_YES" != 1 ]]; then
    read -r -p $'\n即将提交并 push 到 origin/main，确认? [y/N] ' ANS
    [[ "$ANS" =~ ^[Yy]$ ]] || { log "已取消"; exit 0; }
fi

# 5. 执行 commit
step "提交"
git add -A
git commit -m "$MSG" 2>&1 | sed 's/^/  /' || warn "无改动可提交"

# 6. 打标签（若已存在则跳过）
TAG="v$VERSION"
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1; then
    warn "标签 $TAG 已存在，跳过打标签"
else
    log "打标签 $TAG"
    git tag -a "$TAG" -m "$MSG"
fi

# 7. push
step "推送到 $GIT_REMOTE/$GIT_BRANCH"
git push "$GIT_REMOTE" "$GIT_BRANCH" 2>&1 | sed 's/^/  /' || die "push 失败"
git push "$GIT_REMOTE" "$TAG" 2>/dev/null | sed 's/^/  /' || warn "标签 push 失败（可能已存在）"

# 8. 摘要
step "回收完成"
{
    echo "commit: $(git rev-parse --short HEAD)"
    echo "tag   : $TAG"
    echo "branch: $GIT_BRANCH (已推送)"
} | tee -a "$LOG_DIR/publish-history.txt"

log "开发成果已回收到 $GIT_REMOTE"
