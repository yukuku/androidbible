package yuku.kpri.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Verse implements Serializable, Parcelable {
	private static final long serialVersionUID = 3643842595381652L;
	
	public int ordering;
	public VerseKind kind;
	public List<String> lines;
	

	public Verse() {}
	
    public Verse(Parcel in) {
    	ordering = in.readInt();
    	kind = VerseKind.values()[in.readInt()];
		in.readStringList(lines = new ArrayList<>());
	}

	@Override public void writeToParcel(Parcel out, int flags) {
		out.writeInt(ordering);
		out.writeInt(kind.value);
    	out.writeStringList(lines);
    }

    @Override public int describeContents() {
    	return 0;
    }
    
    public static final Parcelable.Creator<Verse> CREATOR = new Parcelable.Creator<Verse>() {
        @Override public Verse createFromParcel(Parcel in) {
            return new Verse(in);
        }

        @Override public Verse[] newArray(int size) {
            return new Verse[size];
        }
    };
}
