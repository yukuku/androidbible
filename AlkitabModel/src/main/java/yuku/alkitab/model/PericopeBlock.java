package yuku.alkitab.model;

import java.util.Arrays;

public class PericopeBlock {
	public String title;
	public String[] parallels;

	@Override public String toString() {
		return "PericopeBlock{title=" + title + " parallels=" + Arrays.toString(parallels) + "}";
	}
}
