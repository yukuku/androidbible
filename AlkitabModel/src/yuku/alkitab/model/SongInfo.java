package yuku.alkitab.model;

import android.os.Parcel;
import android.os.Parcelable;



/** A concise record of a song. */
public class SongInfo implements Parcelable {
	public String bookName;
	public String code;
	public String title;
	public String title_original;
	
	public SongInfo(String bookName, String code, String title, String title_original) {
		this.bookName = bookName;
		this.code = code;
		this.title = title;
		this.title_original = title_original;
	}
	
	public SongInfo() {}
	
	SongInfo(Parcel in) {
		bookName = in.readString();
		code = in.readString();                    
		title = in.readString();                 
		title_original = in.readString();            
	}

    @Override public void writeToParcel(Parcel out, int flags) {
    	out.writeString(bookName);
    	out.writeString(code);
    	out.writeString(title);
    	out.writeString(title_original);
    }

    @Override public int describeContents() {
    	return 0;
    }
    
    public static final Parcelable.Creator<SongInfo> CREATOR = new Parcelable.Creator<SongInfo>() {
        @Override public SongInfo createFromParcel(Parcel in) {
            return new SongInfo(in);
        }

        @Override public SongInfo[] newArray(int size) {
            return new SongInfo[size];
        }
    };
}
