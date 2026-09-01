#!/usr/bin/env bash
# sync.sh — 开发前 git 代码同步
# 拉取远端最新代码，确保本地分支与 origin 对齐；保护未提交改动。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"

step "git 同步 ($GIT_REMOTE/$GIT_BRANCH)"

cd "$REPO_ROOT"

# 1. 必须是 git 仓库
[[ -d .git ]] || die "$REPO_ROOT 不是 git 仓库"

# 2. 检查远端配置
if ! git remote get-url "$GIT_REMOTE" >/dev/null 2>&1; then
    err "远端 '$GIT_REMOTE' 不存在"
    git remote -v
    die "请用 GIT_REMOTE=<name> 指定，或先 git remote add origin <url>"
fi
info "远端: $(git remote get-url "$GIT_REMOTE")"

# 3. 工作树洁净度
if ! git diff-index --quiet HEAD -- 2>/dev/null || ! git diff --quiet --cached; then
    warn "工作树有未提交改动，已自动 stash 保护"
    git stash push -u -m "harness-sync-$(date +%s)" >/dev/null 2>&1 || true
    STASHED=1
else
    STASHED=0
fi

# 4. 拉取
log "fetch 远端引用..."
git fetch --tags --prune "$GIT_REMOTE" 2>&1 | sed 's/^/  /' || die "fetch 失败"

# 切到目标分支（若不在）
current_branch=$(git rev-parse --abbrev-ref HEAD)
if [[ "$current_branch" != "$GIT_BRANCH" ]]; then
    warn "当前分支 $current_branch，切换到 $GIT_BRANCH"
    git checkout "$GIT_BRANCH" || die "切换分支失败"
fi

log "rebase 到 $GIT_REMOTE/$GIT_BRANCH"
git rebase "$GIT_REMOTE/$GIT_BRANCH" 2>&1 | sed 's/^/  /' || {
    err "rebase 冲突，请手动解决: cd $REPO_ROOT && git rebase --continue"
    exit 1
}

# 5. 恢复 stash
if [[ "$STASHED" == 1 ]]; then
    log "恢复 stash 中本地改动"
    git stash pop 2>&1 | sed 's/^/  /' || warn "stash pop 失败，请手动 git stash pop"
fi

# 6. 状态汇报
step "同步后状态"
git log --oneline -8 | sed 's/^/  /'
echo
info "当前版本: $(read_version)"
info "HEAD: $(git rev-parse --short HEAD)"
log "同步完成"
