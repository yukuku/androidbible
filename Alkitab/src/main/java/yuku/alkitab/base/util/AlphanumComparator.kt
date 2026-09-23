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
 * This is an updated version with enhancements made by Daniel Migowski,
 * Andre Bogus, and David Koelle
 * <p/>
 * To use this class:
 * Use the static "sort" method from the java.util.Collections class:
 * Collections.sort(your list, new AlphanumComparator());
 */
class AlphanumComparator : Comparator<String> {
    private fun isDigit(ch: Char) = ch in '0'..'9'

    /**
     * Length of string is passed in for improved efficiency (only need to calculate it once) *
     */
    private fun getChunk(s: String, slength: Int, marker: Int): String {
        val digits = isDigit(s[marker])
        var end = marker + 1
        while (end < slength && isDigit(s[end]) == digits) end++
        return s.substring(marker, end)
    }

    override fun compare(s1: String, s2: String): Int {
        var thisMarker = 0
        var thatMarker = 0
        val s1Length = s1.length
        val s2Length = s2.length

        while (thisMarker < s1Length && thatMarker < s2Length) {
            val thisChunk = getChunk(s1, s1Length, thisMarker)
            thisMarker += thisChunk.length

            val thatChunk = getChunk(s2, s2Length, thatMarker)
            thatMarker += thatChunk.length

            // If both chunks contain numeric characters, sort them numerically
            val result = if (isDigit(thisChunk[0]) && isDigit(thatChunk[0])) {
                // Simple chunk comparison by length.
                val lengthDifference = thisChunk.length - thatChunk.length
                // If equal, the first different number counts
                if (lengthDifference != 0) lengthDifference else thisChunk.compareTo(thatChunk)
            } else {
                thisChunk.compareTo(thatChunk)
            }

            if (result != 0) return result
        }

        return s1Length - s2Length
    }
}
