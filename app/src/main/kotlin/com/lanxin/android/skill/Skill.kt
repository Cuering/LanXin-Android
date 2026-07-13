package com.lanxin.android.skill

/**
 * Skill（能力指令包）数据模型。
 *
 * Skill 比单次 MCP 工具更复杂：包含多步骤指令（SKILL.md）、
 * 可选辅助脚本与资源文件。AI 通过工具调用加载后按步骤执行。
 */
data class Skill(
    val name: String,
    val description: String,
    /** SKILL.md 全文（含 frontmatter 或纯指令） */
    val instructionMd: String,
    /** 脚本名 → 脚本内容 */
    val scripts: Map<String, String> = emptyMap(),
    /** 资源相对路径列表（assets/ 下） */
    val assets: List<String> = emptyList(),
    /** 来源：assets 或 files */
    val source: SkillSource = SkillSource.ASSETS
)

enum class SkillSource {
    ASSETS,
    FILES
}
