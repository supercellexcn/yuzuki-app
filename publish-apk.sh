#!/usr/bin/env bash
# 把 Release APK 发布到局域网共享目录，方便手机直接下载安装。
# 只做文件拷贝，不构建、不碰设备；构建流程（run-debug.sh）会自动调用本脚本。
set -uo pipefail

PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST_DEFAULT="//192.168.5.4/FileTemp"
APK="$PROJ_DIR/app/build/outputs/apk/release/app-release.apk"
DEST="$DEST_DEFAULT"
STRICT=0
LATEST_NAME="yuzuki-latest.apk"

usage() {
  cat <<'EOF'
用法: ./publish-apk.sh [选项]

把 app/build/outputs/apk/release/app-release.apk 拷到局域网共享目录，
文件名带版本号与时间（yuzuki-0.1.0-1111-0244.apk），另存一份 yuzuki-latest.apk。

选项:
  --apk PATH          指定要发布的 APK（默认 release/app-release.apk）
  --dest PATH         指定目标目录（默认 //192.168.5.4/FileTemp）
  --strict            目标不可写时返回非 0（默认只警告，不影响构建）
  -h, --help          显示本说明
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --apk) APK="${2:-}"; shift ;;
    --dest) DEST="${2:-}"; shift ;;
    --strict) STRICT=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1"; usage; exit 1 ;;
  esac
  shift
done

fail() {
  echo "发布跳过: $*"
  [ "$STRICT" -eq 1 ] && exit 1
  exit 0
}

[ -f "$APK" ] || fail "未找到 APK: $APK"
[ -d "$DEST" ] || fail "共享目录不可访问: $DEST（检查网络 / 共享是否在线）"

# 版本号取自 build.gradle.kts，APK 名里带上便于区分新旧包
VERSION="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' "$PROJ_DIR/app/build.gradle.kts" | head -1)"
[ -n "$VERSION" ] || VERSION="0.0.0"
STAMP="$(date +%m%d-%H%M)"
TARGET_NAME="yuzuki-${VERSION}-${STAMP}.apk"

if ! cp -f "$APK" "$DEST/$TARGET_NAME" 2>/dev/null; then
  fail "写入失败（共享可能只读或已断开）: $DEST"
fi
# latest 副本：手机上固定用这个地址下载最新包（拷不上不影响主流程）
cp -f "$APK" "$DEST/$LATEST_NAME" 2>/dev/null || true

SIZE="$(du -h "$APK" | cut -f1 | tr -d ' ')"
echo "已发布: $DEST/$TARGET_NAME  ($SIZE, 版本 $VERSION)"
echo "        $DEST/$LATEST_NAME"
