package com.lanxin.android.skill

import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugin.ToolDef
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class SkillMdParserTest {

    @Test
    fun `parse frontmatter name and folded description`() {
        val md = """
            ---
            name: material-learning-summary
            description: >-
              When the user gives learning materials,
              extract key points and report back.
            ---

            # Skill body
            Do the work.
        """.trimIndent()

        val skill = SkillMdParser.parse(md, nameFallback = "fallback")
        assertEquals("material-learning-summary", skill.name)
        assertTrue(skill.description.contains("learning materials"))
        assertTrue(skill.description.contains("report back"))
        assertTrue(skill.instructionMd.contains("# Skill body"))
    }

    @Test
    fun `parse without frontmatter uses fallback and heading`() {
        val md = """
            # LanXin Build Workflow Skill

            Some steps here.
        """.trimIndent()

        val skill = SkillMdParser.parse(md, nameFallback = "lanxin-build-workflow")
        assertEquals("lanxin-build-workflow", skill.name)
        assertEquals("LanXin Build Workflow Skill", skill.description)
    }

    @Test
    fun `parseSimpleYaml handles quoted values`() {
        val yaml = """
            name: "hello-skill"
            description: 'simple desc'
        """.trimIndent()
        val map = SkillMdParser.parseSimpleYaml(yaml)
        assertEquals("hello-skill", map["name"])
        assertEquals("simple desc", map["description"])
    }
}

class SkillEngineTest {

    @Test
    fun `installSkills registers tools on PluginManager`() = runBlocking {
        val skill = Skill(
            name = "demo-skill",
            description = "演示技能",
            instructionMd = "# Demo\nStep 1"
        )
        val manager = PluginManager(FakeAndroidContext())
        val engine = SkillEngine(SkillLoader(FakeAndroidContext()))
        // 通过标准插件路径注册，确保 callTool 可用
        val bridge = object : LanXinPlugin {
            override val id = "lanxin.skill"
            override val name = "skill"
            override val version = "1"
            override val description = ""
            override suspend fun onLoad(context: PluginContext) {
                engine.installSkills(listOf(skill), context)
            }
        }
        manager.register(bridge)
        manager.loadAll()

        val names = manager.getTools().map { it.name }.toSet()
        assertTrue(names.contains("skill_list"))
        assertTrue(names.contains("skill_load"))
        assertTrue(names.contains("demo-skill"))

        val result = manager.callTool(
            "demo-skill",
            buildJsonObject { put("input", "学习资料A") }
        )
        assertTrue(result.toString().contains("demo-skill"))
        assertEquals("demo-skill", result["skill"]?.jsonPrimitive?.contentOrNull)
        assertTrue(result["instruction"]?.jsonPrimitive?.contentOrNull?.contains("Step 1") == true)
        assertEquals("学习资料A", result["input"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `skill_list returns catalog`() = runBlocking {
        val skills = listOf(
            Skill("a", "desc-a", "# A"),
            Skill("b", "desc-b", "# B")
        )
        val manager = PluginManager(FakeAndroidContext())
        val engine = SkillEngine(SkillLoader(FakeAndroidContext()))
        val bridge = object : LanXinPlugin {
            override val id = "lanxin.skill"
            override val name = "skill"
            override val version = "1"
            override val description = ""
            override suspend fun onLoad(context: PluginContext) {
                engine.installSkills(skills, context)
            }
        }
        manager.register(bridge)
        manager.loadAll()

        val result = manager.callTool("skill_list", buildJsonObject { })
        assertEquals("2", result["count"]?.jsonPrimitive?.content)
        val arr = result["skills"]!!.jsonArray
        assertEquals(2, arr.size)
        assertEquals("a", arr[0].jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `skill_load missing skill returns error`() = runBlocking {
        val manager = PluginManager(FakeAndroidContext())
        val engine = SkillEngine(SkillLoader(FakeAndroidContext()))
        val bridge = object : LanXinPlugin {
            override val id = "lanxin.skill"
            override val name = "skill"
            override val version = "1"
            override val description = ""
            override suspend fun onLoad(context: PluginContext) {
                engine.installSkills(emptyList(), context)
            }
        }
        manager.register(bridge)
        manager.loadAll()

        val result = manager.callTool(
            "skill_load",
            buildJsonObject { put("name", "nope") }
        )
        assertTrue(result["error"].toString().contains("未找到"))
    }

    @Test
    fun `loadSkillFromDirectory parses SKILL md and scripts`() {
        val root = File(System.getProperty("java.io.tmpdir"), "skill-loader-test-${System.nanoTime()}")
        try {
            val skillDir = File(root, "my-skill").also { it.mkdirs() }
            File(skillDir, "SKILL.md").writeText(
                """
                ---
                name: my-skill
                description: test skill
                ---
                # Body
                """.trimIndent()
            )
            val scripts = File(skillDir, "scripts").also { it.mkdirs() }
            File(scripts, "helper.sh").writeText("echo hi")

            val loader = SkillLoader(FakeAndroidContext())
            val skill = loader.loadSkillFromDirectory(skillDir, SkillSource.FILES)!!
            assertEquals("my-skill", skill.name)
            assertEquals("test skill", skill.description)
            assertEquals("echo hi", skill.scripts["helper.sh"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `invokeSkill returns instruction payload`() {
        val skill = Skill(
            name = "material-learning-summary",
            description = "学习资料摘要",
            instructionMd = "# Skill: material-learning-summary\n## Steps"
        )
        val engine = SkillEngine(SkillLoader(FakeAndroidContext()))
        val tools = mutableListOf<ToolDef>()
        val ctx = object : PluginContext {
            override fun registerTool(tool: ToolDef) {
                tools += tool
            }
            override val filesDir: File
                get() = File(System.getProperty("java.io.tmpdir"), "skill-test")
            override suspend fun sendMessage(message: String) = Unit
        }
        engine.installSkills(listOf(skill), ctx)

        val activated = runBlocking {
            engine.invokeSkill(
                "material-learning-summary",
                buildJsonObject { put("input", "repo url") }
            )
        }
        assertTrue(activated["instruction"]?.jsonPrimitive?.contentOrNull?.contains("Steps") == true)
        assertEquals("repo url", activated["input"]?.jsonPrimitive?.contentOrNull)
        assertTrue(tools.any { it.name == "material-learning-summary" })
        assertTrue(tools.any { it.name == SkillEngine.TOOL_SKILL_LIST })
    }
}

/**
 * 最小 Context stub，满足 PluginManager / SkillLoader 构造；
 * 不触碰 assets（loadAll 会得到空列表）。
 */
private class FakeAndroidContext : android.content.ContextWrapper(null) {
    override fun getApplicationContext(): android.content.Context = this

    override fun getFilesDir(): java.io.File =
        java.io.File(System.getProperty("java.io.tmpdir"), "lanxin-skill-test-files").also { it.mkdirs() }
}
