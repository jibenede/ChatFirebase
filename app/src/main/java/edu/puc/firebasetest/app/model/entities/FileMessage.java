package edu.puc.firebasetest.app.model.entities;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.firebase.client.DataSnapshot;

import java.util.HashMap;

/**
 * A message including metadata for downloading a file.
 */
public class FileMessage extends Message implements Parcelable {
    public static final String PARCELABLE_KEY = "file_message";

    public static final String KEY_SIZE = "size";
    public static final String KEY_URL = "url";
    public static final String KEY_FILE_NAME = "file_name";

    private int mSize;
    private String mUrl;
    private String mFileName;
    private Uri mUri;

    public FileMessage() {
    }

    public FileMessage(DataSnapshot snapshot) {
        super(snapshot);
        HashMap<String, Object> values = (HashMap<String, Object>) snapshot.getValue();
        mSize = ((Long) values.get(KEY_SIZE)).intValue();
        mUrl = (String) values.get(KEY_URL);
        mFileName =(String) values.get(KEY_FILE_NAME);
    }

    public FileMessage(Parcel in) {
        super(in);
        mSize = in.readInt();
        mUrl = in.readString();
        mFileName = in.readString();
    }

    public FileMessage(String username, String room, @MessageType int type) {
        super(username, "", room, type);
    }

    public FileMessage(String username, String room, int type, String uuid) {
        super(username, "", room, type, uuid);
    }

    @JsonProperty(KEY_SIZE)
    public int getSize() {
        return mSize;
    }

    public void setSize(int size) {
        mSize = size;
    }

    @JsonProperty(KEY_URL)
    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    @JsonProperty(KEY_FILE_NAME)
    public String getFileName() {
        return mFileName;
    }

    public void setFileName(String fileName) {
        mFileName = fileName;
    }

    @JsonIgnore
    public Uri getUri() {
        return mUri;
    }

    public void setUri(Uri uri) {
        mUri = uri;
    }

    // Parcelable interface

    public static final Parcelable.Creator<FileMessage> CREATOR
            = new Parcelable.Creator<FileMessage>() {
        public FileMessage createFromParcel(Parcel in) {
            return new FileMessage(in);
        }

        public FileMessage[] newArray(int size) {
            return new FileMessage[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mSize);
        dest.writeString(mUrl);
        dest.writeString(mFileName);
    }
}
