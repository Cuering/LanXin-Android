/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.pet.domain

/**
 * 悬浮窗位置（相对屏幕左上角，单位 px；Gravity.TOP|START）。
 *
 * [UNSET] 表示从未保存，启动时用默认右上角偏移。
 */
data class OverlayPosition(
    val x: Int = UNSET,
    val y: Int = UNSET
) {
    val isSet: Boolean get() = x != UNSET && y != UNSET

    companion object {
        const val UNSET: Int = Int.MIN_VALUE
    }
}

/**
 * 悬浮窗位置纯函数：默认落点、拖拽 clamp、触控 slop 判定。
 * 无 Android 依赖，便于单测。
 */
object OverlayPositionMath {
    /** 默认：距右 12dp、距顶 120dp。 */
    fun defaultPosition(
        screenWidthPx: Int,
        screenHeightPx: Int,
        windowWidthPx: Int,
        windowHeightPx: Int,
        density: Float
    ): OverlayPosition {
        val marginEnd = (12f * density).toInt().coerceAtLeast(0)
        val marginTop = (120f * density).toInt().coerceAtLeast(0)
        val x = (screenWidthPx - windowWidthPx - marginEnd).coerceAtLeast(0)
        val y = marginTop.coerceAtMost(
            (screenHeightPx - windowHeightPx).coerceAtLeast(0)
        )
        return clamp(
            x = x,
            y = y,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
    }

    fun resolveInitial(
        saved: OverlayPosition,
        screenWidthPx: Int,
        screenHeightPx: Int,
        windowWidthPx: Int,
        windowHeightPx: Int,
        density: Float
    ): OverlayPosition {
        if (!saved.isSet) {
            return defaultPosition(
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                density = density
            )
        }
        return clamp(
            x = saved.x,
            y = saved.y,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
    }

    fun clamp(
        x: Int,
        y: Int,
        windowWidthPx: Int,
        windowHeightPx: Int,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): OverlayPosition {
        val maxX = (screenWidthPx - windowWidthPx).coerceAtLeast(0)
        val maxY = (screenHeightPx - windowHeightPx).coerceAtLeast(0)
        return OverlayPosition(
            x = x.coerceIn(0, maxX),
            y = y.coerceIn(0, maxY)
        )
    }

    /** 是否超过 touch slop，进入拖拽（避免吞掉轻点）。 */
    fun exceedsTouchSlop(dx: Float, dy: Float, touchSlopPx: Int): Boolean {
        val slop = touchSlopPx.coerceAtLeast(0)
        return kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop
    }
}
