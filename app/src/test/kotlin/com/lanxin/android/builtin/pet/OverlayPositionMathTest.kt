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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.OverlayPosition
import com.lanxin.android.builtin.pet.domain.OverlayPositionMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPositionMathTest {

    @Test
    fun clamp_keepsInsideScreen() {
        val p = OverlayPositionMath.clamp(
            x = -40,
            y = 9999,
            windowWidthPx = 180,
            windowHeightPx = 240,
            screenWidthPx = 1080,
            screenHeightPx = 1920
        )
        assertEquals(0, p.x)
        assertEquals(1920 - 240, p.y)
    }

    @Test
    fun clamp_handlesTinyScreen() {
        val p = OverlayPositionMath.clamp(
            x = 50,
            y = 50,
            windowWidthPx = 400,
            windowHeightPx = 500,
            screenWidthPx = 300,
            screenHeightPx = 400
        )
        assertEquals(0, p.x)
        assertEquals(0, p.y)
    }

    @Test
    fun defaultPosition_placesNearTopEnd() {
        val p = OverlayPositionMath.defaultPosition(
            screenWidthPx = 1080,
            screenHeightPx = 1920,
            windowWidthPx = 180,
            windowHeightPx = 240,
            density = 2f
        )
        // marginEnd=24, marginTop=240
        assertEquals(1080 - 180 - 24, p.x)
        assertEquals(240, p.y)
    }

    @Test
    fun resolveInitial_usesSavedWhenSet() {
        val saved = OverlayPosition(x = 100, y = 200)
        val p = OverlayPositionMath.resolveInitial(
            saved = saved,
            screenWidthPx = 1080,
            screenHeightPx = 1920,
            windowWidthPx = 180,
            windowHeightPx = 240,
            density = 2f
        )
        assertEquals(100, p.x)
        assertEquals(200, p.y)
    }

    @Test
    fun resolveInitial_defaultsWhenUnset() {
        val p = OverlayPositionMath.resolveInitial(
            saved = OverlayPosition(),
            screenWidthPx = 1080,
            screenHeightPx = 1920,
            windowWidthPx = 180,
            windowHeightPx = 240,
            density = 1f
        )
        assertTrue(p.isSet)
        assertEquals(1080 - 180 - 12, p.x)
        assertEquals(120, p.y)
    }

    @Test
    fun resolveInitial_clampsSavedOffscreen() {
        val p = OverlayPositionMath.resolveInitial(
            saved = OverlayPosition(x = 5000, y = -10),
            screenWidthPx = 1080,
            screenHeightPx = 1920,
            windowWidthPx = 180,
            windowHeightPx = 240,
            density = 1f
        )
        assertEquals(1080 - 180, p.x)
        assertEquals(0, p.y)
    }

    @Test
    fun exceedsTouchSlop_requiresThreshold() {
        assertFalse(OverlayPositionMath.exceedsTouchSlop(4f, 3f, touchSlopPx = 8))
        assertTrue(OverlayPositionMath.exceedsTouchSlop(9f, 0f, touchSlopPx = 8))
        assertTrue(OverlayPositionMath.exceedsTouchSlop(0f, -9f, touchSlopPx = 8))
    }
}
