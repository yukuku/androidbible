package yuku.alkitab.base.util

object Levenshtein {
    private const val INSERTION = 100
    private const val DELETION = 500
    private const val SUBSTITUTION = 400

    @JvmStatic
    fun distance(s: String, t: String): Int {
        // d is a table with m+1 rows and n+1 columns
        val m = s.length
        val n = t.length

        val d = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) {
            d[i][0] = i * DELETION // deletion
        }

        for (j in 0..n) {
            d[0][j] = j * INSERTION // insertion
        }

        for (j in 1..n) { // j is the index to t
            for (i in 1..m) { // i is the index to s
                d[i][j] = if (s[i - 1] == t[j - 1]) {
                    d[i - 1][j - 1] + j + j // the longer the offset difference between the same character, the less relevant it is
                } else {
                    minOf(
                        d[i - 1][j] + DELETION, // deletion
                        d[i][j - 1] + INSERTION + (j - i), // insertion
                        d[i - 1][j - 1] + SUBSTITUTION, // substitution
                    )
                }
            }
        }

        return d[m][n]
    }
}
