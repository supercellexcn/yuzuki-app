package com.yuzuki.app.web

/**
 * 与 web 端保持一致的移动断点（`frontend/src/composables/useIsMobile.ts:4`）。
 * WebView 必须让页面 viewport meta 生效，否则会按约 980px 宽视口渲染，
 * 前端会走桌面分支：侧栏被压扁、壁纸读 desktop 开关，两个表现都由此而来。
 */
object ViewportPolicy {
    const val MOBILE_BREAKPOINT_PX = 760
    const val MEDIA_QUERY = "(max-width: 760px)"

    fun isMobileLayout(widthPx: Int): Boolean =
        widthPx in 1..MOBILE_BREAKPOINT_PX

    /** 返回 "true|宽x高" 形式的诊断串，供 Logcat 做确定性断言，不含任何用户数据。 */
    fun probeScript(): String =
        "(function(){try{return matchMedia('$MEDIA_QUERY').matches+'|'+innerWidth+'x'+innerHeight}" +
            "catch(e){return 'error'}})()"
}
