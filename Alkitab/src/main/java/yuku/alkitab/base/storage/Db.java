package yuku.alkitab.base.storage;

public class Db {
	public static final String TABLE_Marker = "Marker";
	public static final class Marker {
		public static final String gid = "gid";
		public static final String ari = "ari";
		public static final String kind = "kind";
		public static final String caption = "caption";
		public static final String verseCount = "verseCount";
		public static final String createTime = "createTime";
		public static final String modifyTime = "modifyTime";
	}

	public static final String TABLE_Version = "Version";
	public static final class Version {
		public static final String locale = "locale";
		public static final String shortName = "shortName";
		public static final String longName = "longName";
		public static final String description = "description";
		public static final String filename = "filename";
		public static final String preset_name = "preset_name";
		public static final String modifyTime = "modifyTime";
		public static final String active = "active";
		public static final String ordering = "ordering";
	}
	
	public static final String TABLE_Label = "Label";
	public static final class Label {
		public static final String gid = "gid";
		public static final String title = "judul";
		public static final String ordering = "urutan";
		public static final String backgroundColor = "warnaLatar";
	}

	public static final String TABLE_Marker_Label = "Marker_Label";
	public static class Marker_Label {
		public static final String gid = "gid";
		public static final String marker_gid = "marker_gid";
		public static final String label_gid = "label_gid";
	}

	public static final String TABLE_ProgressMark = "ProgressMark";
	public static final class ProgressMark {
		public static final String preset_id = "preset_id";
		public static final String caption = "caption";
		public static final String modifyTime = "modifyTime";
		public static final String ari = "ari";
	}

	public static final String TABLE_ProgressMarkHistory = "ProgressMarkHistory";
	public static final class ProgressMarkHistory {
		public static final String progress_mark_preset_id = "progress_mark_preset_id";
		public static final String progress_mark_caption = "progress_mark_caption";
		public static final String ari = "ari";
		public static final String createTime = "createTime";
	}

	public static final String TABLE_ReadingPlan = "ReadingPlan";
	public static final class ReadingPlan {
		public static final String version = "version";
		public static final String name = "name";
		public static final String title = "title";
		public static final String description = "description";
		public static final String duration = "duration";
		public static final String startTime = "startTime";
		public static final String data = "data";
	}

	public static final String TABLE_ReadingPlanProgress = "ReadingPlanProgress";

	/**
	 * Unique in (reading_plan_progress_gid, reading_code)
	 */
	public static final class ReadingPlanProgress {
		public static final String reading_plan_progress_gid = "reading_plan_progress_gid";
		public static final String reading_code = "reading_code";
		public static final String checkTime = "checkTime";
	}

}
