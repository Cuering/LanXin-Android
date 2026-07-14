package com.lanxin.android.skill

import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Skill 引擎：加载 Skill 并注册为 MCP 工具供 AI 调用。
 *
 * 调用约定：
 * - 每个 skill 注册为同名工具（如 `material-learning-summary`）
 * - 额外提供 `skill_list` / `skill_load`
 * - AI 调用 skill 后，handler 返回 SKILL.md 指令 + 参数，由后续对话按步骤执行
 *
 * 自身作为 [LanXinPlugin] 走标准 onLoad → registerTool 流程，
 * 不修改 PluginManager / ToolCallEngine。
 */
@Singleton
class SkillEngine @Inject constructor(
    private val skillLoader: SkillLoader
) : LanXinPlugin {

    override val id = "lanxin.skill"
    override val name = "Skill 加载器"
    override val version = "1.0.0"
    override val description = "从 assets/skills 与 filesDir/skills 加载能力指令包，并注册为 AI 可调用工具"

    private val skills = linkedMapOf<String, Skill>()
    private var pluginContext: PluginContext? = null

    /** 当前已加载的 skill（只读快照）。 */
    fun getSkills(): List<Skill> = skills.values.toList()

    fun getSkill(name: String): Skill? = skills[name]

    override suspend fun onLoad(context: PluginContext) {
        pluginContext = context
        installSkills(skillLoader.loadAll(), context)
    }

    /**
     * 安装 skill 列表并注册工具。
     * 生产路径由 [onLoad] 调用；单测可直接传入内存 skill。
     */
    fun installSkills(loaded: List<Skill>, context: PluginContext) {
        skills.clear()
        loaded.forEach { skills[it.name] = it }
        // 不使用 android.util.Log：JVM 单测中 android.jar 为 stub，Log 会抛 RuntimeException

        context.registerTool(buildSkillListTool())
        context.registerTool(buildSkillLoadTool())
        loaded.forEach { skill ->
            context.registerTool(buildSkillTool(skill))
        }
    }

    /**
     * 重新扫描目录并注册工具（需已 onLoad）。
     */
    fun reload() {
        val ctx = pluginContext ?: return
        installSkills(skillLoader.loadAll(), ctx)
    }

    /**
     * 执行 skill：组装指令上下文 JSON。
     */
    fun invokeSkill(skillName: String, args: JsonObject): JsonObject {
        val skill = skills[skillName]
            ?: return buildJsonObject {
                put("error", "Skill 未找到: $skillName")
                put(
                    "available",
                    buildJsonArray {
                        skills.keys.forEach { add(JsonPrimitive(it)) }
                    }
                )
            }
        return formatSkillActivation(skill, args)
    }

    private fun buildSkillTool(skill: Skill): ToolDef {
        return ToolDef(
            name = skill.name,
            description = "加载并执行技能「${skill.name}」: ${skill.description}",
            parameters = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "input",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "触发该技能的任务描述 / 用户输入 / 资料内容")
                            }
                        )
                        put(
                            "context",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "可选的附加上下文")
                            }
                        )
                    }
                )
            },
            handler = { args -> invokeSkill(skill.name, args) }
        )
    }

    private fun buildSkillListTool(): ToolDef {
        return ToolDef(
            name = TOOL_SKILL_LIST,
            description = "列出当前可用的全部 Skill（能力指令包）及其描述",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            },
            handler = {
                buildJsonObject {
                    put("count", skills.size)
                    put(
                        "skills",
                        buildJsonArray {
                            skills.values.forEach { skill ->
                                add(
                                    buildJsonObject {
                                        put("name", skill.name)
                                        put("description", skill.description)
                                        put("source", skill.source.name.lowercase())
                                        put("scripts", skill.scripts.keys.joinToString(","))
                                        put("has_assets", skill.assets.isNotEmpty())
                                    }
                                )
                            }
                        }
                    )
                }
            }
        )
    }

    private fun buildSkillLoadTool(): ToolDef {
        return ToolDef(
            name = TOOL_SKILL_LOAD,
            description = "按名称加载指定 Skill 的完整指令（SKILL.md），用于按多步骤流程执行任务",
            parameters = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "name",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Skill 名称，如 material-learning-summary")
                            }
                        )
                        put(
                            "input",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "任务输入 / 资料内容")
                            }
                        )
                        put(
                            "context",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "可选附加上下文")
                            }
                        )
                    }
                )
                put(
                    "required",
                    buildJsonArray {
                        add(JsonPrimitive("name"))
                    }
                )
            },
            handler = { args ->
                val name = args["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isBlank()) {
                    return@ToolDef buildJsonObject { put("error", "name 不能为空") }
                }
                invokeSkill(name, args)
            }
        )
    }

    private fun formatSkillActivation(skill: Skill, args: JsonObject): JsonObject {
        val input = args["input"]?.jsonPrimitive?.contentOrNull
            ?: args["query"]?.jsonPrimitive?.contentOrNull
            ?: args["material"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val extraContext = args["context"]?.jsonPrimitive?.contentOrNull.orEmpty()

        return buildJsonObject {
            put("ok", true)
            put("skill", skill.name)
            put("description", skill.description)
            put("source", skill.source.name.lowercase())
            put(
                "guidance",
                "已加载技能「${skill.name}」。请严格按 instruction 中的步骤执行，" +
                    "可继续调用其它 MCP 工具完成子步骤；完成后向用户汇报结果。"
            )
            put("input", input)
            if (extraContext.isNotBlank()) {
                put("context", extraContext)
            }
            put("instruction", skill.instructionMd)
            if (skill.scripts.isNotEmpty()) {
                put(
                    "scripts",
                    buildJsonObject {
                        skill.scripts.forEach { (scriptName, content) ->
                            put(scriptName, content)
                        }
                    }
                )
            }
            if (skill.assets.isNotEmpty()) {
                put(
                    "assets",
                    buildJsonArray {
                        skill.assets.forEach { add(JsonPrimitive(it)) }
                    }
                )
            }
        }
    }

    companion object {
        const val TOOL_SKILL_LIST = "skill_list"
        const val TOOL_SKILL_LOAD = "skill_load"
    }
}
