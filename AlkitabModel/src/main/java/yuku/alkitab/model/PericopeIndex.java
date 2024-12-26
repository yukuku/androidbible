package yuku.alkitab.model;

import java.util.Arrays;

public class PericopeIndex {
    /**
     * Aris are sorted in ascending order
     */
    public int[] aris;
    public int[] offsets;

    /**
     * Finds the lowest ari that is >= ariMin up to (ariMin + 0xff), or returns -1 if no such ari exists
     */
    public int findFirst(int ariMin) {
        final int x = Arrays.binarySearch(aris, ariMin);

        // x == -1 (-0-1) if ariMin == 0
        /*
         * Example of aris
         * [0] 0x000101
         * [1] 0x000125
         * [2] 0x000201
         * [3] 0x000308
         *
         * Search for 0x000000 -> would insert at 0 -> x == -1 -> wanted 0, so -(-1 +1)
         * Search for 0x000100 -> would insert at 0 -> x == -1
         * Search for 0x000200 -> would insert at 2 -> x == -3 -> wanted 2, so -(-3 +1)
         * Search for 0x000201 -> found at 2 -> x == 2 -> wanted 2, so 2
         * Search for 0x000300 -> would insert at 3 -> x == -4
         * Search for 0x000400 -> would insert at 4 -> x == -5 -> wanted 4, so -(-5 +1)
         */

        int res;

        if (x < 0) res = -(x + 1);
        else res = x;

        if (res >= aris.length) return -1;

        int ari = aris[res];
        final int ariMax = ariMin + 0xff;
        if (ari <= ariMax) {
            return res;
        }

        return -1;
    }

    public int getAri(int index) {
        if (index >= aris.length) {
            return 0x00ffffff; // EOF
        }

        return aris[index];
    }
}
