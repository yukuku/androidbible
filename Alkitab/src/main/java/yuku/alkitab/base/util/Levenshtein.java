package yuku.alkitab.base.util;

import android.widget.Toast;
import yuku.alkitab.base.App;
import yuku.alkitab.util.IntArrayList;

import java.util.ArrayList;
import java.util.List;

public class Levenshtein {
	private static final int insertion = 100;
	private static final int deletion = 500;
	private static final int substitution = 400;

	public static int distance(String s, String t) {
		try {
			return distance0(s, t);
		} catch (Exception e) {
			Toast.makeText(App.context, "Unexpected exception!\n\n" + e.getClass().getName() + " " + e.getMessage(), Toast.LENGTH_SHORT).show();
			return distanceSlow(s, t);
		}
	}

	private static int distance0(String s, String t) {
		// d is a table with m+1 rows and n+1 columns
		final int m = s.length();
		final int n = t.length();

		final int[][] d = new int[m + 1][n + 1];

		for (int i = 0; i <= m; i++) {
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

	private static int distanceSlow(String s, String t) {
		// d is a table with m+1 rows and n+1 columns
		final int m = s.length();
		final int n = t.length();

		List<IntArrayList> d = new ArrayList<>();
		for (int i = 0; i < m + 1; i++) {
			final IntArrayList a = new IntArrayList(n + 1);
			d.add(a);

			for (int j = 0; j < n + 1; j++) {
				a.add(0);
			}
		}

		for (int i = 0; i <= m; i++) {
			d.get(i).set(0, i * deletion); // deletion
		}

		for (int j = 0; j <= n; j++) {
			d.get(0).set(j, j * insertion); // insertion
		}

		for (int j = 1; j <= n; j++) { // j is the index to t
			for (int i = 1; i <= m; i++) { // i is the index to s
				if (s.charAt(i - 1) == t.charAt(j - 1)) {
					d.get(i).set(j, d.get(i - 1).get(j - 1) + j + j); // the longer the offset difference between the same character, the less relevant it is
				} else {
					d.get(i).set(j, minimum(
						d.get(i - 1).get(j) + deletion, // deletion
						d.get(i).get(j - 1) + insertion + (j - i), // insertion
						d.get(i - 1).get(j - 1) + substitution // substitution
					));
				}
			}
		}

		return d.get(m).get(n);
	}

	private static int minimum(int a, int b, int c) {
		int d = (a < b) ? a : b;
		return (c < d) ? c : d;
	}
}
