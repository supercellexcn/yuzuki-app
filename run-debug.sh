#!/usr/bin/env bash
# 一键把 Debug 包装到已连接的手机并启动，省掉手动传 APK 到手机安装。
# 只做构建 / 安装 / 启动，不改动任何业务代码。
set -euo pipefail

PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if command -v cygpath >/dev/null 2>&1; then
  PROJ_WIN="$(cygpath -m "$PROJ_DIR")"
else
  PROJ_WIN="$PROJ_DIR"
fi
GRADLEW="$PROJ_DIR/gradlew"
ADB="$PROJ_DIR/.toolchain/sdk/platform-tools/adb.exe"
APK="$PROJ_DIR/app/build/outputs/apk/debug/app-debug.apk"
ACT="com.yuzuki.app/.MainActivity"

FAST=0
LAUNCH=1
FOLLOW_LOG=0
CONNECT=""

usage() {
  cat <<'EOF'
用法: ./run-debug.sh [选项]

默认: 编译 Debug 包 -> 安装到已连接设备 -> 启动 App

选项:
  --fast              跳过编译，直接安装现有 app-debug.apk（最快）
  --connect IP:PORT   先连接无线调试设备再执行
  --no-launch         只安装，不启动
  --log               启动后跟随 Logcat（Ctrl+C 结束）
  -h, --help          显示本说明

前置条件:
  手机开启「USB 调试」并在弹窗里授权，adb devices 能看到 device 状态。
  无线调试需先用 USB 执行一次: adb tcpip 5555，然后 --connect 手机IP:5555。
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --fast) FAST=1 ;;
    --no-launch) LAUNCH=0 ;;
    --log) FOLLOW_LOG=1 ;;
    --connect)
      CONNECT="${2:-}"
      if [ -z "$CONNECT" ]; then echo "错误: --connect 需要 IP:PORT"; exit 1; fi
      shift
      ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1"; usage; exit 1 ;;
  esac
  shift
done

if [ -d "$PROJ_DIR/.toolchain/jdk17" ]; then
  export JAVA_HOME="$PROJ_WIN/.toolchain/jdk17"
elif [ -z "${JAVA_HOME:-}" ]; then
  echo "错误: 未找到 .toolchain/jdk17，也未设置 JAVA_HOME（需要 JDK 17）"
  exit 1
fi

if [ ! -f "$ADB" ]; then
  ADB="$(command -v adb || true)"
fi
if [ -z "$ADB" ] || [ ! -f "$ADB" ]; then
  echo "错误: 找不到 adb（.toolchain/sdk/platform-tools/adb.exe 或 PATH 中均可）"
  exit 1
fi
if [ ! -f "$GRADLEW" ]; then
  echo "错误: 找不到 gradlew: $GRADLEW"
  exit 1
fi

if [ -n "$CONNECT" ]; then
  echo "[1/3] 连接无线调试 $CONNECT ..."
  "$ADB" connect "$CONNECT" || true
fi

# 先查设备再编译：没连上就别浪费一次构建
DEVICE="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
if [ -z "$DEVICE" ]; then
  echo "错误: 没有已授权的 adb 设备。请开启 USB 调试并接受授权弹窗；无线调试用 --connect IP:PORT。"
  "$ADB" devices
  exit 1
fi
ALL_DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
DEVICE_COUNT="$(printf '%s\n' "$ALL_DEVICES" | grep -c '[^[:space:]]' || true)"
if [ "${DEVICE_COUNT:-0}" -gt 1 ]; then
  echo "提示: 检测到 $DEVICE_COUNT 台设备，本次使用 $DEVICE（可设 ANDROID_SERIAL 指定）"
fi
export ANDROID_SERIAL="$DEVICE"
echo "[1/3] 设备: $DEVICE"

START="$(date +%s)"
if [ "$FAST" -eq 1 ]; then
  if [ ! -f "$APK" ]; then
    echo "错误: 未找到 $APK，请先不带 --fast 跑一次完整构建"
    exit 1
  fi
  echo "[2/3] 跳过编译，安装现有 APK ..."
  "$ADB" install -r "$PROJ_WIN/app/build/outputs/apk/debug/app-debug.apk"
else
  echo "[2/3] 编译并安装 ..."
  "$GRADLEW" -p "$PROJ_WIN" :app:installDebug --console=plain
fi

if [ "$LAUNCH" -eq 1 ]; then
  echo "[3/3] 启动 App ..."
  "$ADB" shell am start -n "$ACT"
else
  echo "[3/3] 已跳过启动"
fi

echo "完成，用时 $(( $(date +%s) - START ))s"
echo "APK: $PROJ_WIN/app/build/outputs/apk/debug/app-debug.apk"

if [ "$FOLLOW_LOG" -eq 1 ]; then
  echo "跟随 Logcat（Ctrl+C 结束）..."
  "$ADB" logcat -v time | grep --line-buffered -Ei 'yuzuki|AndroidRuntime|ExoPlayer|Compose' || true
fi
