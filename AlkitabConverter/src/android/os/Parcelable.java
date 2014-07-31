package android.os;

// Stub version of Android's Parcelable
public interface Parcelable {
    public int describeContents();
    public void writeToParcel(Parcel dest, int flags);

    public interface Creator<T> {
        public T createFromParcel(Parcel source);
        public T[] newArray(int size);
    }
}
