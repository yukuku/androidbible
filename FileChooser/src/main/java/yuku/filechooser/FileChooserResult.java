package yuku.filechooser;

import android.os.*;

public class FileChooserResult implements Parcelable {
	public static final String TAG = FileChooserResult.class.getSimpleName();

	public String currentDir;
	public String firstFilename;
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(currentDir);
		dest.writeString(firstFilename);
	}
	
	public static final Creator<FileChooserResult> CREATOR = new Creator<FileChooserResult>() {
		@Override
		public FileChooserResult[] newArray(int size) {
			return new FileChooserResult[size];
		}
		
		@Override
		public FileChooserResult createFromParcel(Parcel in) {
			FileChooserResult res = new FileChooserResult();
			res.currentDir = in.readString();
			res.firstFilename = in.readString();
			return res;
		}
	};
}
