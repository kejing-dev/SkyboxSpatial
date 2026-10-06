package com.kejing.skyboxspatial.domain

/** Pure rules for the description field, shared by the ViewModel and tests. */
object PromptRules {
    /** The design caps the description at 300 characters; a style may allow fewer. */
    const val DEFAULT_MAX_CHARS = 300

    fun maxChars(styleMax: Int?): Int = if (styleMax == null || styleMax <= 0) DEFAULT_MAX_CHARS else minOf(DEFAULT_MAX_CHARS, styleMax)

    fun clamp(raw: String, max: Int): String = if (raw.length <= max) raw else raw.substring(0, max)

    fun canGenerate(prompt: String): Boolean = prompt.trim().length >= 2

    /**
     * Short title for a result: the text up to the first clause break, cut to a readable length.
     * "清晨的高山湖泊，松林环绕…" becomes "清晨的高山湖泊"; Latin text keeps whole words up to [latinMax].
     */
    fun titleFor(prompt: String, cjkMax: Int = 14, latinMax: Int = 32, fallback: String = "Environment"): String {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return fallback
        val breakIndex = trimmed.indexOfFirst { it in CLAUSE_BREAKS }
        val head = (if (breakIndex > 0) trimmed.substring(0, breakIndex) else trimmed).trim()
        val base = if (head.isEmpty()) trimmed else head
        val cjk = base.any { Character.UnicodeScript.of(it.code) in CJK_SCRIPTS }
        val max = if (cjk) cjkMax else latinMax
        if (base.length <= max) return base
        val cut = base.substring(0, max)
        val atWord = if (cjk) cut else cut.substringBeforeLast(' ', cut)
        return atWord.trimEnd() + "…"
    }

    private val CJK_SCRIPTS = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL)
    private val CLAUSE_BREAKS = setOf('，', ',', '。', '.', '；', ';', '、', '\n', '!', '！', '?', '？', ':', '：')
}
