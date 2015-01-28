package yuku.alkitab.model;

import yuku.alkitab.model.util.Gid;

public class Marker_Label {
	public long _id;
	public String gid;
	public String marker_gid;
	public String label_gid;

	private Marker_Label() {}

	public static Marker_Label createEmptyMarker_Label() {
		return new Marker_Label();
	}

	/**
	 * Create without _id
	 */
	public static Marker_Label createNewMarker_Label(final String marker_gid, final String label_gid) {
		final Marker_Label res = new Marker_Label();

		res.gid = Gid.newGid();
		res.marker_gid = marker_gid;
		res.label_gid = label_gid;

		return res;
	}

}
