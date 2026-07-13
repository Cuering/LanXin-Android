package com.lanxin.android.skill

/**
 * 解析 SKILL.md：提取 YAML frontmatter 中的 name / description，
 * 以及正文指令内容。
 *
 * 兼容无 frontmatter 的纯 Markdown（用目录名 / 首个标题兜底）。
 */
object SkillMdParser {

    private val FRONTMATTER_REGEX = Regex(
        """^---\s*\r?\n([\s\S]*?)\r?\n---\s*\r?\n?([\s\S]*)$"""
    )

    /**
     * @param rawMd SKILL.md 原始文本
     * @param nameFallback 无 frontmatter name 时使用（通常为目录名）
     */
    fun parse(
        rawMd: String,
        nameFallback: String,
        scripts: Map<String, String> = emptyMap(),
        assets: List<String> = emptyList(),
        source: SkillSource = SkillSource.ASSETS
    ): Skill {
        val trimmed = rawMd.trimStart('\uFEFF')
        val match = FRONTMATTER_REGEX.find(trimmed)
        return if (match != null) {
            val yaml = match.groupValues[1]
            val body = match.groupValues[2].trim()
            val fields = parseSimpleYaml(yaml)
            val name = fields["name"]?.takeIf { it.isNotBlank() } ?: nameFallback
            val description = fields["description"]?.takeIf { it.isNotBlank() }
                ?: extractTitleDescription(body)
                ?: name
            Skill(
                name = name,
                description = description,
                instructionMd = trimmed,
                scripts = scripts,
                assets = assets,
                source = source
            )
        } else {
            Skill(
                name = nameFallback,
                description = extractTitleDescription(trimmed) ?: nameFallback,
                instructionMd = trimmed,
                scripts = scripts,
                assets = assets,
                source = source
            )
        }
    }

    /**
     * 极简 YAML 解析：支持 `key: value` 与 `key: >-` 多行折叠。
     * 仅覆盖 Skill frontmatter 所需字段，不依赖外部库。
     */
    fun parseSimpleYaml(yaml: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = yaml.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val keyMatch = Regex("""^([A-Za-z0-9_-]+)\s*:\s*(.*)$""").find(line)
            if (keyMatch == null) {
                i++
                continue
            }
            val key = keyMatch.groupValues[1]
            val rest = keyMatch.groupValues[2].trim()
            when {
                rest == ">-" || rest == ">" || rest == "|" || rest == "|-" -> {
                    val buf = StringBuilder()
                    i++
                    while (i < lines.size) {
                        val next = lines[i]
                        // 缩进行或空行属于多行值；遇到新 key 结束
                        if (next.isNotEmpty() &&
                            !next.startsWith(" ") &&
                            !next.startsWith("\t") &&
                            Regex("""^[A-Za-z0-9_-]+\s*:""").containsMatchIn(next)
                        ) {
                            break
                        }
                        val content = next.trim()
                        if (content.isNotEmpty()) {
                            if (buf.isNotEmpty()) buf.append(' ')
                            buf.append(content)
                        }
                        i++
                    }
                    result[key] = buf.toString().trim()
                    continue
                }
                rest.startsWith("\"") && rest.endsWith("\"") && rest.length >= 2 -> {
                    result[key] = rest.substring(1, rest.length - 1)
                }
                rest.startsWith("'") && rest.endsWith("'") && rest.length >= 2 -> {
                    result[key] = rest.substring(1, rest.length - 1)
                }
                else -> result[key] = rest
            }
            i++
        }
        return result
    }

    private fun extractTitleDescription(md: String): String? {
        val heading = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE).find(md)?.groupValues?.get(1)?.trim()
        return heading?.takeIf { it.isNotBlank() }
    }
}
