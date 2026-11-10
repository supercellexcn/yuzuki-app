package com.yuzuki.app.web

/**
 * S2 导航辅助脚本（方案 §5.3）。
 *
 * 独立于 S3 的 bridge.js：后者依赖 window.YuzukiNative，在原生播放内核接入前注入会
 * 让 window.Audio 被替换却无播放服务。因此导航脚本单独做文档级注入；
 * S3 写 bridge.js 时必须用「if (!window.__yuzukiNav)」守卫，不覆盖此处实现。
 *
 * 纯 Kotlin、无 Android 依赖，路径映射为单一真相源，便于 JVM 断言。
 */
object NavScript {
    val ROUTES: Map<String, String> = linkedMapOf(
        "library" to "/",
        "history" to "/history",
        "favorites" to "/favorites",
    )

    fun pathFor(name: String): String = ROUTES[name] ?: "/"

    val SCRIPT: String = buildString {
        append("(function(){'use strict';")
        append("if(window.__YUZUKI_NAV_READY__)return;")
        append("var R={")
        append(ROUTES.entries.joinToString(",") { (k, v) -> "'$k':'$v'" })
        append("};")
        append("window.__yuzukiNav=function(n){")
        append("var p=R[n]||'/';")
        append("if(location.pathname===p){window.scrollTo({top:0});return;}")
        append("try{history.pushState({},'',p);")
        append("window.dispatchEvent(new PopStateEvent('popstate',{state:history.state}));")
        append("}catch(e){location.assign(p);}")
        append("};")
        append("window.__yuzukiOpenPlayer=function(){")
        append("var b=document.querySelector('.bottom-player .btn.expand');")
        append("return !!b&&(b.click(),true);")
        append("};")
        append("window.__YUZUKI_NAV_READY__=true;")
        append("})();")
    }
}
