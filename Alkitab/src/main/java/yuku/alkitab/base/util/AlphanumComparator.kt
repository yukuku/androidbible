package yuku.alkitab.base.util

/*
 * The Alphanum Algorithm is an improved sorting algorithm for strings
 * containing numbers.  Instead of sorting numbers in ASCII order like
 * a standard sort, this algorithm sorts numbers in numeric order.
 *
 * The Alphanum Algorithm is discussed at http://www.DaveKoelle.com
 *
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

/**
 * Kotlin port of the version with enhancements made by Daniel Migowski,
 * Andre Bogus, and David Koelle.
 *
 * Usage: `list.sortWith(AlphanumComparator)`
 */
object AlphanumComparator : Comparator<String> {
    // Deliberately ASCII only: Char.isDigit() would also accept other scripts' digits.
    private fun Char.isAsciiDigit() = this in '0'..'9'

    /**
     * Returns the maximal run of all-digit or all-non-digit characters of [s] starting at [start].
     */
    private fun chunkAt(s: String, start: Int): String {
        val digits = s[start].isAsciiDigit()
        var end = start + 1
        while (end < s.length && s[end].isAsciiDigit() == digits) end++
        return s.substring(start, end)
    }

    override fun compare(s1: String, s2: String): Int {
        var thisMarker = 0
        var thatMarker = 0

        while (thisMarker < s1.length && thatMarker < s2.length) {
            val thisChunk = chunkAt(s1, thisMarker)
            thisMarker += thisChunk.length

            val thatChunk = chunkAt(s2, thatMarker)
            thatMarker += thatChunk.length

            val bothNumeric = thisChunk[0].isAsciiDigit() && thatChunk[0].isAsciiDigit()

            // A longer run of digits is a larger number. Runs of equal length compare numerically
            // by their first differing digit, which is exactly what String.compareTo returns.
            val result = if (bothNumeric && thisChunk.length != thatChunk.length) {
                thisChunk.length - thatChunk.length
            } else {
                thisChunk.compareTo(thatChunk)
            }

            if (result != 0) return result
        }

        return s1.length - s2.length
    }
}
