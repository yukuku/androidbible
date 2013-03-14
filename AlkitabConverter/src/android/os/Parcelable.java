package android.os;

//Just a stub to make desktop projects that include android projects work

public interface Parcelable {
	public int describeContents();

	public void writeToParcel(Parcel dest, int flags);

	public interface Creator<T> {
		public T createFromParcel(Parcel source);

		public T[] newArray(int size);
	}

	public interface ClassLoaderCreator<T> extends Creator<T> {
		public T createFromParcel(Parcel source, ClassLoader loader);
	}
}
