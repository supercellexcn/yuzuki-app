package com.yuzuki.app.web

/**
 * 锁屏 / 蓝牙按键的换曲命令（方案 §6.3）：原生会话不自己管队列，
 * 转发到 WebView 点击前端现有的上一曲 / 下一曲按钮，队列仍由 player.ts 维护。
 * 选择器对应 frontend/src/components/BottomPlayer.vue:33,39,43。
 */
object PlayerCommands {
    val SCRIPT: String = """
        (function () {
          'use strict';
          if (window.__YUZUKI_COMMANDS_READY__) return;
          window.__yuzukiPlayerCommand = function (cmd) {
            var sel = cmd === 'next' ? '.bottom-player .ctrl[title="下一曲"]'
                    : cmd === 'prev' ? '.bottom-player .ctrl[title="上一曲"]'
                    : cmd === 'toggle' ? '.bottom-player .btn.main'
                    : null;
            if (!sel) return false;
            try {
              var b = document.querySelector(sel);
              return !!b && (b.click(), true);
            } catch (e) { return false; }
          };
          window.__YUZUKI_COMMANDS_READY__ = true;
        })();
    """.trimIndent()
}
