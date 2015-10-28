package edu.puc.firebasetest.app.model.entities;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.firebase.client.DataSnapshot;
import com.firebase.client.ServerValue;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.LocalDatabase.CONTACTS_COLUMNS;
import edu.puc.firebasetest.app.model.LocalDatabase.ROOMS_COLUMNS;
import edu.puc.firebasetest.app.model.LocalDatabase.TABLES;
import edu.puc.firebasetest.app.network.request.GcmRequest;
import edu.puc.firebasetest.app.network.request.GcmRequest.GcmMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jose on 10/22/15.
 */
public class Room implements GcmMessage, Parcelable {
    public static final String PARCELABLE_KEY = "room";

    // private Integer mId;
    private long mTimestamp;
    private String mAuthor;
    private String mName;
    private String mKey;

    public static Room singleContactRoom(Context context, Contact contact) {
        // In our model, both group and individual chat rooms have the same backend. For identification purposes,
        // individual rooms have the standard name #user1-#user2 such that #user1.compareTo(#user2) < 0
        String roomKey;
        String contactMail = FirebaseModel.escapeCharacters(contact.getEmail());
        String myMail = FirebaseModel.getUser(context);
        if (contactMail.compareTo(myMail) < 0) {
            roomKey = contactMail + "-" + myMail;
        } else {
            roomKey = myMail + "-" + contactMail;
        }

        Room room = new Room("", "", roomKey);
        return room;
    }

    public Room() {}

    public Room(DataSnapshot snapshot) {
        HashMap<String, Object> values = (HashMap<String, Object>) snapshot.getValue();
        mName = (String) values.get(ROOMS_COLUMNS.NAME);
        mAuthor = (String) values.get(ROOMS_COLUMNS.AUTHOR);
        mTimestamp = (Long) values.get(ROOMS_COLUMNS.TIMESTAMP);
        mKey = snapshot.getKey();
    }

    public Room(Bundle bundle) {
        mName = bundle.getString(ROOMS_COLUMNS.NAME);
        mAuthor = bundle.getString(ROOMS_COLUMNS.AUTHOR);
        mTimestamp = Long.parseLong(bundle.getString(ROOMS_COLUMNS.TIMESTAMP));
        mKey = bundle.getString(ROOMS_COLUMNS.KEY);
    }

    public Room(Parcel in) {
        mName = in.readString();
        mAuthor = in.readString();
        mTimestamp = in.readLong();
        mKey = in.readString();
    }

    public Room(String name, String author) {
        mName = name;
        mAuthor = author;
        mTimestamp = System.currentTimeMillis();
        mKey = mAuthor + "-" + mName;

    }

    public Room(String name, String author, String key) {
        this(name, author);
        mKey = key;
    }

    @JsonProperty(ROOMS_COLUMNS.NAME)
    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    @JsonProperty(ROOMS_COLUMNS.AUTHOR)
    public String getAuthor() {
        return mAuthor;
    }

    public void setAuthor(String author) {
        mAuthor = author;
    }

    @JsonProperty(ROOMS_COLUMNS.TIMESTAMP)
    public long getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    @JsonProperty(GcmRequest.KEY_TYPE)
    @Override
    public int getGcmMessageType() {
        return GcmRequest.TYPE_ROOM;
    }

    public String getKey() {
        return mKey;
    }

    public void setKey(String key) {
        mKey = key;
    }

    // Parcelable interface

    public static final Parcelable.Creator<Room> CREATOR
            = new Parcelable.Creator<Room>() {
        public Room createFromParcel(Parcel in) {
            return new Room(in);
        }

        public Room[] newArray(int size) {
            return new Room[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mAuthor);
        dest.writeLong(mTimestamp);
        dest.writeString(mKey);
    }
}
