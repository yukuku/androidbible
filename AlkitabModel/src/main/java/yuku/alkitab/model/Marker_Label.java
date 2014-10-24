package yuku.alkitab.model;

public class Marker_Label {
	public long _id;
	public String gid;
	public String marker_gid;
	public String label_gid;

	private Marker_Label() {}

	public static Marker_Label createEmptyMarker_Label() {
		return new Marker_Label();
	}
}
