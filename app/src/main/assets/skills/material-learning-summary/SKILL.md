---
name: material-learning-summary
description: >-
  When the user (哥哥) gives me learning materials (documents, links, code repos) to study and digest, 
  I use this skill to systematically extract key points, compare with my existing capabilities, 
  generate structured notes, propose improvement suggestions, and report back.
---

# Skill: material-learning-summary

## When to use
Use this skill when 哥哥 asks me to learn something new — a document, a GitHub repo, a blog post, or any technical material.

## Steps

### 1. Read the material
- If it's a URL → use `web_search_tavily` or `tavily_extract_web_page` to fetch content
- If it's direct text → read and process directly
- Note the material name, source, and core purpose

### 2. Extract key points
- Identify 3-5 most important design ideas / concepts
- Note relevance to my current capabilities

### 3. Compare with my existing abilities
- For each key point: "Do I have this?" → ✅ Have / ⚠️ Partial / ❌ Missing
- Focus on the top 1-2 gaps that matter most

### 4. Generate structured notes
- Format: "What I learned → Current status → Action plan"
- Save to workspace as `evolution_xxx_notes.md`

### 5. Propose improvements ⭐
- Based on what I learned, suggest 1-2 actionable improvements
- Explain the benefit
- Mark as "pending哥哥's approval"

### 6. Report back
- Key findings (2-3 most valuable points)
- Gap analysis summary
- ⭐ Improvement proposal (awaiting approval)
- Next steps

## Output artifacts
- Structured notes in workspace
- Skill library updated if applicable

## Examples
- Learning GenericAgent → created skill library, identified missing L3 skill layer
- Learning OpenTracy → upgraded skill to v1.1 with proactive proposal step
