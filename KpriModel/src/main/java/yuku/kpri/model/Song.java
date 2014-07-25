package yuku.kpri.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Song implements Serializable, Parcelable {
	public static final String TAG = Song.class.getSimpleName();
	private static final long serialVersionUID = 3264548831320138544L;
	
	public String code;
	public String title;
	public String title_original;
	public List<String> authors_lyric;
	public List<String> authors_music;
	public String tune;
	public String keySignature;
	public String timeSignature;
	public List<Lyric> lyrics;
	public String scriptureReferences; // added in dataFormatVersion 2
	
	public Song() {}
	
    @Override public void writeToParcel(Parcel out, int flags) {
    	writeToParcelCompat(Integer.MAX_VALUE, out, flags);
    }

	@Override public int describeContents() {
    	return 0;
    }
    
    public static Song createFromParcelCompat(int dataFormatVersion, Parcel in) {
    	Song res = new Song();
		res.code = in.readString();                    
		res.title = in.readString();                 
		res.title_original = in.readString();            
		in.readStringList(res.authors_lyric = new ArrayList<>());
		in.readStringList(res.authors_music = new ArrayList<>());
		res.tune = in.readString();                  
		res.keySignature = in.readString();             
		res.timeSignature = in.readString();                
		in.readList(res.lyrics = new ArrayList<>(), res.getClass().getClassLoader());

    	if (dataFormatVersion >= 2) {
    		res.scriptureReferences = in.readString();
    	}

    	return res;
    }
    
    public void writeToParcelCompat(int dataFormatVersion, Parcel out, int flags) {
    	out.writeString(code);
    	out.writeString(title);
    	out.writeString(title_original);
    	out.writeStringList(authors_lyric);
    	out.writeStringList(authors_music);
    	out.writeString(tune);
    	out.writeString(keySignature);
    	out.writeString(timeSignature);
    	out.writeList(lyrics);
    	
    	if (dataFormatVersion >= 2) {
    		out.writeString(scriptureReferences);
    	}
    }
    
    public static final Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {
        @Override public Song createFromParcel(Parcel in) {
            return Song.createFromParcelCompat(Integer.MAX_VALUE, in);
        }

        @Override public Song[] newArray(int size) {
            return new Song[size];
        }
    };
}
