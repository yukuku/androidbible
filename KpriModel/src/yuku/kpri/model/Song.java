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
	
	
	public Song() {}
	
	private Song(Parcel in) {
		code = in.readString();                    
		title = in.readString();                 
		title_original = in.readString();            
		in.readStringList(authors_lyric = new ArrayList<String>());
		in.readStringList(authors_music = new ArrayList<String>());
		tune = in.readString();                  
		keySignature = in.readString();             
		timeSignature = in.readString();                
		in.readList(lyrics = new ArrayList<Lyric>(), getClass().getClassLoader());
	}

    @Override public void writeToParcel(Parcel out, int flags) {
    	out.writeString(code);
    	out.writeString(title);
    	out.writeString(title_original);
    	out.writeStringList(authors_lyric);
    	out.writeStringList(authors_music);
    	out.writeString(tune);
    	out.writeString(keySignature);
    	out.writeString(timeSignature);
    	out.writeList(lyrics);
    }

    @Override public int describeContents() {
    	return 0;
    }
    
    public static final Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {
        @Override public Song createFromParcel(Parcel in) {
            return new Song(in);
        }

        @Override public Song[] newArray(int size) {
            return new Song[size];
        }
    };
}
