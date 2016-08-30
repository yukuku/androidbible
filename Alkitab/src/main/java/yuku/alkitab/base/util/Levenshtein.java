package yuku.alkitab.base.util;

public class Levenshtein {
	private static final int insertion = 100;
	private static final int deletion = 500;
	private static final int substitution = 400;

	public static int distance(String s, String t) {
		// d is a table with m+1 rows and n+1 columns
		final int m = s.length();
		final int n = t.length();

		final int[][] d = new int[m + 1][n + 1];

		for (int i = 0; i <= m; i++) {
			// workaround for seemingly bad VM: http://stackoverflow.com/questions/37193263
			if (d[i] == null) {
				d[i] = new int[n + 1];
			}
			d[i][0] = i * deletion; // deletion
		}

		for (int j = 0; j <= n; j++) {
			d[0][j] = j * insertion; // insertion
		}

		for (int j = 1; j <= n; j++) { // j is the index to t
			for (int i = 1; i <= m; i++) { // i is the index to s
				if (s.charAt(i - 1) == t.charAt(j - 1)) {
					d[i][j] = d[i - 1][j - 1] + j + j; // the longer the offset difference between the same character, the less relevant it is
				} else {
					d[i][j] = minimum(
						d[i - 1][j] + deletion, // deletion
						d[i][j - 1] + insertion + (j - i), // insertion
						d[i - 1][j - 1] + substitution // substitution
					);
				}
			}
		}

		return d[m][n];
	}

	private static int minimum(int a, int b, int c) {
		int d = (a < b) ? a : b;
		return (c < d) ? c : d;
	}
}
