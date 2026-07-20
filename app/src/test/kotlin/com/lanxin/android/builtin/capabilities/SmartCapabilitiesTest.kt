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

package com.lanxin.android.builtin.capabilities

import com.lanxin.android.builtin.capabilities.domain.LocationConfig
import com.lanxin.android.builtin.capabilities.domain.LocationGate
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesGate
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesMigration
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilityId
import com.lanxin.android.builtin.platform.domain.SceneSensingGate
import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能能力：默认值、v1/v2 迁移、master 级联、聚合门闸、本地脑仍默认 false。
 */
class SmartCapabilitiesTest {

    @Test
    fun `defaults match product table v2`() {
        val c = SmartCapabilitiesConfig()
        assertTrue(c.masterEnabled)
        assertFalse(c.localInferenceEnabled)
        assertTrue(c.voiceEnabled)
        assertTrue(c.assistantToolsEnabled)
        assertTrue(c.locationAroundEnabled)
        assertFalse(c.sceneVisionEnabled)
        // 兼容属性映射到聚合
        assertTrue(c.systemToolsEnabled)
        assertTrue(c.webSearchEnabled)
        assertTrue(c.deviceSensingEnabled)
        assertTrue(c.locationEnabled)
        assertFalse(c.navigateEnabled)
        assertFalse(c.guideEnabled)
        assertFalse(c.migratedV1)
        assertFalse(c.migratedV2)
        assertEquals(5, SmartCapabilitiesConfig.PRIMARY_IDS.size)
        assertFalse(SmartCapabilitiesConfig.DEFAULT_NAVIGATE)
        assertFalse(SmartCapabilitiesConfig.DEFAULT_GUIDE)
    }

    @Test
    fun `local brain stays default OFF`() {
        assertFalse(SmartCapabilitiesConfig.DEFAULT_LOCAL_INFERENCE)
        assertFalse(SmartCapabilitiesConfig().localInferenceEnabled)
        assertFalse(
            SmartCapabilitiesGate.effective(
                SmartCapabilitiesConfig(),
                SmartCapabilityId.LOCAL_INFERENCE
            )
        )
    }


    @Test
    fun `navigate and guide default OFF and gateable`() {
        val off = SmartCapabilitiesConfig()
        assertFalse(
            SmartCapabilitiesGate.effective(off, SmartCapabilityId.NAVIGATE)
        )
        assertFalse(
            SmartCapabilitiesGate.effective(off, SmartCapabilityId.GUIDE)
        )
        assertTrue(
            SmartCapabilitiesGate.effective(
                SmartCapabilitiesConfig(navigateEnabled = true),
                SmartCapabilityId.NAVIGATE
            )
        )
        assertTrue(
            SmartCapabilitiesGate.effective(
                SmartCapabilitiesConfig(guideEnabled = true),
                SmartCapabilityId.GUIDE
            )
        )
        assertFalse(
            SmartCapabilitiesGate.effective(
                SmartCapabilitiesConfig(masterEnabled = false, navigateEnabled = true),
                SmartCapabilityId.NAVIGATE
            )
        )
    }

    @Test
    fun `master off denies all children including aliases`() {
        val c = SmartCapabilitiesConfig(
            masterEnabled = false,
            voiceEnabled = true,
            assistantToolsEnabled = true,
            localInferenceEnabled = true,
            sceneVisionEnabled = true
        )
        for (id in SmartCapabilityId.entries) {
            assertFalse(SmartCapabilitiesGate.effective(c, id))
            assertEquals(
                SmartCapabilitiesGate.DENIED_MASTER,
                SmartCapabilitiesGate.denyReason(c, id)
            )
        }
    }

    @Test
    fun `assistant tools off denies system tools web search device sensing`() {
        val c = SmartCapabilitiesConfig(assistantToolsEnabled = false)
        assertFalse(SmartCapabilitiesGate.effective(c, SmartCapabilityId.ASSISTANT_TOOLS))
        assertFalse(SmartCapabilitiesGate.effective(c, SmartCapabilityId.WEB_SEARCH))
        assertFalse(SmartCapabilitiesGate.effective(c, SmartCapabilityId.SYSTEM_TOOLS))
        assertFalse(SmartCapabilitiesGate.effective(c, SmartCapabilityId.DEVICE_SENSING))
        assertEquals(
            SmartCapabilitiesGate.DENIED_CHILD,
            SmartCapabilitiesGate.denyReason(c, SmartCapabilityId.WEB_SEARCH)
        )
        assertTrue(SmartCapabilitiesGate.effective(c, SmartCapabilityId.VOICE))
        assertTrue(SmartCapabilitiesGate.effective(c, SmartCapabilityId.LOCATION_AROUND))
    }

    @Test
    fun `location around off denies location alias`() {
        val c = SmartCapabilitiesConfig(locationAroundEnabled = false)
        assertFalse(SmartCapabilitiesGate.effective(c, SmartCapabilityId.LOCATION_AROUND))
        assertFalse(SmartCapabilitiesGate.effective(c, SmartCapabilityId.LOCATION))
        assertFalse(c.locationEnabled)
    }

    @Test
    fun `runtime not ready denies`() {
        val c = SmartCapabilitiesConfig()
        assertFalse(
            SmartCapabilitiesGate.effective(
                c,
                SmartCapabilityId.VOICE,
                runtimeReady = false
            )
        )
        assertEquals(
            SmartCapabilitiesGate.DENIED_RUNTIME,
            SmartCapabilitiesGate.denyReason(c, SmartCapabilityId.VOICE, false)
        )
    }

    @Test
    fun `migration never configured lifts safe defaults but not local or scene`() {
        val resolved = SmartCapabilitiesMigration.buildConfig(
            SmartCapabilitiesMigration.LegacyCapabilitySnapshot()
        )
        assertTrue(resolved.masterEnabled)
        assertTrue(resolved.voiceEnabled)
        assertTrue(resolved.assistantToolsEnabled)
        assertTrue(resolved.locationAroundEnabled)
        assertTrue(resolved.systemToolsEnabled)
        assertTrue(resolved.webSearchEnabled)
        assertTrue(resolved.deviceSensingEnabled)
        assertTrue(resolved.locationEnabled)
        assertFalse(resolved.localInferenceEnabled)
        assertFalse(resolved.sceneVisionEnabled)
        assertFalse(resolved.navigateEnabled)
        assertFalse(resolved.guideEnabled)
        assertTrue(resolved.migratedV1)
        assertTrue(resolved.migratedV2)
    }

    @Test
    fun `migration preserves explicit false on members collapses group off`() {
        val resolved = SmartCapabilitiesMigration.buildConfig(
            SmartCapabilitiesMigration.LegacyCapabilitySnapshot(
                webSearch = false,
                deviceSensing = true,
                systemToolsMaster = true,
                voiceAsr = false,
                voiceTts = true
            )
        )
        // 任一项关 → 助手工具组关
        assertFalse(resolved.assistantToolsEnabled)
        assertFalse(resolved.webSearchEnabled)
        assertFalse(resolved.systemToolsEnabled)
        assertFalse(resolved.deviceSensingEnabled)
        assertFalse(resolved.voiceEnabled)
        assertFalse(resolved.localInferenceEnabled)
    }

    @Test
    fun `migration any member false turns assistant tools off`() {
        assertFalse(
            SmartCapabilitiesMigration.resolveGroup(true, false, true)
        )
        assertTrue(
            SmartCapabilitiesMigration.resolveGroup(true, true, true)
        )
        assertTrue(
            SmartCapabilitiesMigration.resolveGroup(null, null, null)
        )
        assertFalse(
            SmartCapabilitiesMigration.resolveGroup(null, false, null)
        )
    }

    @Test
    fun `migration preserves explicit true for local and scene`() {
        val resolved = SmartCapabilitiesMigration.buildConfig(
            SmartCapabilitiesMigration.LegacyCapabilitySnapshot(
                localInference = true,
                sceneVision = true
            )
        )
        assertTrue(resolved.localInferenceEnabled)
        assertTrue(resolved.sceneVisionEnabled)
    }

    @Test
    fun `migration never lifts local when null`() {
        val resolved = SmartCapabilitiesMigration.buildConfig(
            SmartCapabilitiesMigration.LegacyCapabilitySnapshot(localInference = null)
        )
        assertFalse(resolved.localInferenceEnabled)
    }

    @Test
    fun `resolveVoice rules`() {
        assertTrue(SmartCapabilitiesMigration.resolveVoice(null, null))
        assertFalse(SmartCapabilitiesMigration.resolveVoice(false, true))
        assertFalse(SmartCapabilitiesMigration.resolveVoice(true, false))
        assertTrue(SmartCapabilitiesMigration.resolveVoice(true, null))
        assertTrue(SmartCapabilitiesMigration.resolveVoice(null, true))
    }

    @Test
    fun `v2 collapse from fine keys`() {
        val collapsed = SmartCapabilitiesMigration.collapseToV2(
            masterEnabled = true,
            localInferenceEnabled = false,
            voiceEnabled = true,
            systemTools = true,
            webSearch = false,
            deviceSensing = true,
            location = true,
            sceneVisionEnabled = false
        )
        assertFalse(collapsed.assistantToolsEnabled)
        assertTrue(collapsed.locationAroundEnabled)
        assertTrue(collapsed.migratedV2)
    }

    @Test
    fun `v2 collapse prefers existing aggregate keys`() {
        val collapsed = SmartCapabilitiesMigration.collapseToV2(
            masterEnabled = true,
            localInferenceEnabled = false,
            voiceEnabled = true,
            systemTools = false,
            webSearch = false,
            deviceSensing = false,
            location = false,
            sceneVisionEnabled = false,
            existingAssistantTools = true,
            existingLocationAround = false
        )
        assertTrue(collapsed.assistantToolsEnabled)
        assertFalse(collapsed.locationAroundEnabled)
    }

    @Test
    fun `location gate respects master and location around`() {
        val smartOff = SmartCapabilitiesConfig(masterEnabled = false)
        val locOn = LocationConfig(enabled = true)
        assertFalse(LocationGate.isPrefsOpen(smartOff, locOn))

        val childOff = SmartCapabilitiesConfig(locationAroundEnabled = false)
        assertFalse(LocationGate.isPrefsOpen(childOff, locOn))

        val ok = SmartCapabilitiesConfig()
        assertTrue(LocationGate.isPrefsOpen(ok, locOn))
        assertFalse(LocationGate.canUse(ok, locOn, permissionGranted = false))
        assertTrue(LocationGate.canUse(ok, locOn, permissionGranted = true))
    }

    @Test
    fun `location filterTools removes when closed`() {
        val tools = listOf(
            ToolDef("system_info", "d", buildJsonObject { }, { buildJsonObject { } }),
            ToolDef(LocationConfig.TOOL_NAME, "d", buildJsonObject { }, { buildJsonObject { } })
        )
        val filtered = LocationGate.filterTools(
            tools,
            SmartCapabilitiesConfig(locationAroundEnabled = false),
            LocationConfig()
        )
        assertEquals(listOf("system_info"), filtered.map { it.name })
    }

    @Test
    fun `location denyIfDisabled codes`() {
        val denied = LocationGate.denyIfDisabled(
            SmartCapabilitiesConfig(masterEnabled = false),
            LocationConfig(),
            permissionGranted = true
        )
        assertNotNull(denied)
        assertEquals(
            LocationGate.DENIED_CODE,
            denied!!["code"]?.jsonPrimitive?.contentOrNull
        )
        val noPerm = LocationGate.denyIfDisabled(
            SmartCapabilitiesConfig(),
            LocationConfig(),
            permissionGranted = false
        )
        assertEquals(
            LocationGate.DENIED_NO_PERMISSION,
            noPerm!!["code"]?.jsonPrimitive?.contentOrNull
        )
        assertNull(
            LocationGate.denyIfDisabled(
                SmartCapabilitiesConfig(),
                LocationConfig(),
                permissionGranted = true
            )
        )
    }

    @Test
    fun `summary line counts primary groups`() {
        val off = SmartCapabilitiesConfig(masterEnabled = false)
        assertTrue(off.summaryLine().contains("主开关已关"))
        val on = SmartCapabilitiesConfig()
        assertTrue(on.summaryLine().contains("主开关开"))
        assertTrue(on.summaryLine().contains("3/5") || on.summaryLine().contains("组已开"))
        // 默认 ON 组：voice + assistant + location = 3；local/scene OFF
        assertTrue(on.summaryLine().contains("3/5"))
    }

    @Test
    fun `legacy effective helper`() {
        assertFalse(SmartCapabilitiesGate.effectiveLegacy(false, true))
        assertFalse(SmartCapabilitiesGate.effectiveLegacy(true, false))
        assertTrue(SmartCapabilitiesGate.effectiveLegacy(true, true))
        assertFalse(SmartCapabilitiesGate.effectiveLegacy(true, true, runtimeReady = false))
    }

    @Test
    fun `assistantToolsEffective helper`() {
        assertTrue(
            SmartCapabilitiesGate.assistantToolsEffective(SmartCapabilitiesConfig())
        )
        assertFalse(
            SmartCapabilitiesGate.assistantToolsEffective(
                SmartCapabilitiesConfig(assistantToolsEnabled = false)
            )
        )
        assertFalse(
            SmartCapabilitiesGate.assistantToolsEffective(
                SmartCapabilitiesConfig(masterEnabled = false)
            )
        )
    }

    @Test
    fun `scene clampEnabled dual-write must use clamped value`() {
        val noConsent = SceneSensingGate.clampEnabled(
            requestedEnabled = true,
            consentGranted = false
        )
        assertFalse(noConsent)
        val withConsent = SceneSensingGate.clampEnabled(
            requestedEnabled = true,
            consentGranted = true
        )
        assertTrue(withConsent)
        assertFalse(
            SceneSensingGate.clampEnabled(requestedEnabled = false, consentGranted = false)
        )
    }
}
