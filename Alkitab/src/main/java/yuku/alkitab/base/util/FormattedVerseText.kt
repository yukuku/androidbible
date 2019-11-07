package yuku.alkitab.base.util

object FormattedVerseText {
    /**
     * If text is null, this returns null.
     * If verse doesn't start with @: don't do anything.
     * Otherwise, remove all @'s and one character after that and text between @< and @>.
     */
    @JvmStatic
    @JvmOverloads
    fun removeSpecialCodes(text: String?, force: Boolean = false): String? {
        if (text == null) return null
        if (text.isEmpty()) return text
        if (!force && text[0] != '@') return text

        val sb = StringBuilder(text.length)
        var pos = 0

        while (true) {
            val p = text.indexOf('@', pos)
            if (p == -1) {
                break
            }

            sb.append(text, pos, p)
            pos = p + 2

            if (p + 1 < text.length) {
                when (text[p + 1] /* skipped character */) {
                    // did we skip "@<"?
                    '<' -> {
                        // look for matching "@>"
                        val q = text.indexOf("@>", pos)
                        if (q != -1) {
                            pos = q + 2
                        }
                    }
                    // did we skip a paragraph marker, new paragraph, or newline?
                    // if so, add a space if needed
                    '0', '1', '2', '3', '4', '^', '8' -> {
                        // only add if the last character output is not already a whitespace
                        if (sb.isNotEmpty() && !Character.isWhitespace(sb[sb.length - 1])) {
                            sb.append(' ')
                        }
                        // otherwise we do not need to put extra space
                    }
                }
            }
        }

        sb.append(text, pos, text.length)
        return sb.toString()
    }
}