package yuku.kpri.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Lyric implements Serializable, Parcelable {
	private static final long serialVersionUID = 4661867042967439L;

	public String caption;
	public List<Verse> verses;
	

	public Lyric() {}
	
    public Lyric(Parcel in) {
    	caption = in.readString();
		in.readList(verses = new ArrayList<>(), getClass().getClassLoader());
	}

	@Override public void writeToParcel(Parcel out, int flags) {
		out.writeString(caption);
    	out.writeList(verses);
    }

    @Override public int describeContents() {
    	return 0;
    }
    
    public static final Parcelable.Creator<Lyric> CREATOR = new Parcelable.Creator<Lyric>() {
        @Override public Lyric createFromParcel(Parcel in) {
            return new Lyric(in);
        }

        @Override public Lyric[] newArray(int size) {
            return new Lyric[size];
        }
    };
}
