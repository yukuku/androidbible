package yuku.filechooser;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class FolderChooserConfig implements Parcelable {
	public static final String TAG = FolderChooserConfig.class.getSimpleName();

	public String title;
	public String subtitle;
	public List<String> roots;
	public boolean showHidden;
	public boolean mustBeWritable;
	public boolean expandSingularRoot;
	public boolean expandMultipleRoots;

	@Override public int describeContents() {
		return 0;
	}

	@Override public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(title);
		dest.writeString(subtitle);
		dest.writeStringList(roots);
		dest.writeByte((byte) (showHidden? 1: 0));
		dest.writeByte((byte) (mustBeWritable? 1: 0));
		dest.writeByte((byte) (expandSingularRoot? 1: 0));
		dest.writeByte((byte) (expandMultipleRoots? 1: 0));
	}

	public static final Creator<FolderChooserConfig> CREATOR = new Creator<FolderChooserConfig>() {
		@Override public FolderChooserConfig[] newArray(int size) {
			return new FolderChooserConfig[size];
		}

		@Override public FolderChooserConfig createFromParcel(Parcel in) {
			FolderChooserConfig res = new FolderChooserConfig();
			res.title = in.readString();
			res.subtitle = in.readString();
			res.roots = new ArrayList<String>(); in.readStringList(res.roots);
			res.showHidden = in.readByte() != 0;
			res.mustBeWritable = in.readByte() != 0;
			res.expandSingularRoot = in.readByte() != 0;
			res.expandMultipleRoots = in.readByte() != 0;
			return res;
		}
	};
}
