package com.yuzuki.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 播放列表拖动排序的全局标记（纯 UI 状态，不进 PlayState）。
 *
 * 拖动期间要禁掉横向 Pager 的翻页手势：手指斜着往上拖时横向分量一旦超过 touch slop，
 * Pager 会抢走手势并消费事件，拖拽当场被 cancel —— 用户看到的就是「没松手却被放下」。
 */
object PlaylistDrag {
    var active by mutableStateOf(false)
}
