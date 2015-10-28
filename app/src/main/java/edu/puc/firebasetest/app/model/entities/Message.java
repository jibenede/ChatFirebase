package edu.puc.firebasetest.app.model.entities;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.firebase.client.DataSnapshot;
import com.firebase.client.ServerValue;
import edu.puc.firebasetest.app.network.request.GcmRequest;
import edu.puc.firebasetest.app.network.request.GcmRequest.GcmMessage;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A simple chat message including only a text.
 */
public class Message implements Parcelable, GcmMessage {
    public static final String PARCELABLE_KEY = "message";

    public static final String KEY_USERNAME = "username";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_UUID = "uuid";
    public static final String KEY_ROOM = "room";
    public static final String KEY_TYPE = "type";

    @IntDef({MESSAGE_TYPE_TEXT, MESSAGE_TYPE_FILE, MESSAGE_TYPE_PHOTO_AUDIO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MessageType {}

    public static final int MESSAGE_TYPE_TEXT = 0;
    public static final int MESSAGE_TYPE_FILE = 1;
    public static final int MESSAGE_TYPE_PHOTO_AUDIO = 2;

    private String mMessage;
    private String mUsername;
    private String mUuid;
    private String mRoom;
    private int mType;

    public Message() {}

    public Message(DataSnapshot snapshot) {
        HashMap<String, Object> values = (HashMap<String, Object>) snapshot.getValue();
        mUsername = (String) values.get(KEY_USERNAME);
        mMessage = (String) values.get(KEY_MESSAGE);
        mUuid = (String) values.get(KEY_UUID);
        mRoom = (String) values.get(KEY_ROOM);
        mType = ((Long) values.get(KEY_TYPE)).intValue();
    }

    public Message(Parcel in) {
        mMessage = in.readString();
        mUsername = in.readString();
        mUuid = in.readString();
        mRoom = in.readString();
        mType = in.readInt();
    }

    public Message(String username, String message, String room, @MessageType int type) {
        this(username, message, room, type, UUID.randomUUID().toString());
    }

    // This constructor should only be used to recreate message objects received from GCM.
    public Message(String username, String message, String room, int type, String uuid) {
        mMessage = message;
        mUsername = username;
        mUuid = uuid;
        mRoom = room;
        mType = type;
    }

    @JsonProperty(KEY_MESSAGE)
    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        mMessage = message;
    }

    @JsonProperty(KEY_USERNAME)
    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    @JsonProperty(KEY_UUID)
    public String getUuid() {
        return mUuid;
    }

    public void setUuid(String uuid) {
        mUuid = uuid;
    }

    @JsonProperty(KEY_ROOM)
    public String getRoom() {
        return mRoom;
    }

    public void setRoom(String room) {
        mRoom = room;
    }

    @MessageType
    @JsonProperty(KEY_TYPE)
    public int getType() {
        return mType;
    }

    public void setType(@MessageType int type) {
        mType = type;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", mUsername, mMessage);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Message) {
            return mUuid.equals(((Message) o).getUuid());
        } else {
            return super.equals(o);
        }
    }

    @JsonProperty(GcmRequest.KEY_TYPE)
    @Override
    public int getGcmMessageType() {
        return GcmRequest.TYPE_MESSAGE;
    }

    // Parcelable interface

    public static final Parcelable.Creator<Message> CREATOR
            = new Parcelable.Creator<Message>() {
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mMessage);
        dest.writeString(mUsername);
        dest.writeString(mUuid);
        dest.writeString(mRoom);
        dest.writeInt(mType);
    }
}
