package yuku.alkitab.base.util

import android.widget.Toast
import yuku.alkitab.base.App
import yuku.alkitab.util.IntArrayList

object Levenshtein {
    private const val INSERTION = 100
    private const val DELETION = 500
    private const val SUBSTITUTION = 400

    @JvmStatic
    fun distance(s: String, t: String): Int = try {
        distance0(s, t)
    } catch (e: Exception) {
        Toast.makeText(App.context, "Unexpected exception!\n\n${e.javaClass.name} ${e.message}", Toast.LENGTH_SHORT).show()
        distanceSlow(s, t)
    }

    /**
     * [i] indexes [s] and [j] indexes [t], both 1-based; the arguments are the costs of the three neighboring cells.
     */
    private fun cellCost(s: String, t: String, i: Int, j: Int, diagonal: Int, above: Int, left: Int) =
        if (s[i - 1] == t[j - 1]) {
            // a match costs more the further into t it lies
            diagonal + j + j
        } else {
            minOf(
                above + DELETION,
                left + INSERTION + (j - i),
                diagonal + SUBSTITUTION,
            )
        }

    private fun distance0(s: String, t: String): Int {
        val m = s.length
        val n = t.length
        val d = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) d[i][0] = i * DELETION
        for (j in 0..n) d[0][j] = j * INSERTION

        for (j in 1..n) {
            for (i in 1..m) {
                d[i][j] = cellCost(s, t, i, j, diagonal = d[i - 1][j - 1], above = d[i - 1][j], left = d[i][j - 1])
            }
        }

        return d[m][n]
    }

    private fun distanceSlow(s: String, t: String): Int {
        val m = s.length
        val n = t.length
        val d = List(m + 1) { IntArrayList(n + 1).apply { repeat(n + 1) { add(0) } } }

        for (i in 0..m) d[i][0] = i * DELETION
        for (j in 0..n) d[0][j] = j * INSERTION

        for (j in 1..n) {
            for (i in 1..m) {
                d[i][j] = cellCost(s, t, i, j, diagonal = d[i - 1][j - 1], above = d[i - 1][j], left = d[i][j - 1])
            }
        }

        return d[m][n]
    }
}
