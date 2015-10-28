package edu.puc.firebasetest.app.model.entities;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.LocalDatabase.CONTACTS_COLUMNS;
import edu.puc.firebasetest.app.model.LocalDatabase.TABLES;

/**
 * A representation for a contact as defined in the local contacts provider. Currently it only includes the contact's
 * email and display name.
 */
public class Contact implements Entity, Parcelable {
    public static final String PARCELABLE_KEY = "contact";

    private Integer mId;
    private String mName;
    private String mEmail;

    private Contact(Parcel in) {
        boolean idNotNull = in.readInt() == 1;
        if (idNotNull) mId = in.readInt();
        mName = in.readString();
        mEmail = in.readString();
    }

    private Contact(Integer id, String name, String email) {
        mId = id;
        mName = name;
        mEmail = email;
    }

    /**
     * Builds a new contact entity representing the local user. Has the default name "Me" which will appear inside
     * chat rooms when sending messages
     *
     * @param context The android context.
     * @return A new contact representing the current user.
     */
    public static Contact selfContact(Context context) {
        return new Contact(null, "Me", FirebaseModel.getAccountName(context));
    }

    public static Contact contactFromOS(Cursor cursor) {
        String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
        String email = cursor.getString(cursor.getColumnIndex(Email.ADDRESS));
        return new Contact(null, name, email);
    }

    public static Contact contactFromLocalDB(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndex(BaseColumns._ID));
        String name = cursor.getString(cursor.getColumnIndex(CONTACTS_COLUMNS.NAME));
        String email = cursor.getString(cursor.getColumnIndex(CONTACTS_COLUMNS.EMAIL));
        return new Contact(id, name, email);
    }

    public String getName() {
        return mName;
    }

    public String getEmail() {
        return mEmail;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Contact) {
            Contact c = (Contact) o;
            return mEmail.equals(c.getEmail()) && mName.equals(c.getName());
        }
        return false;
    }

    @Override
    public String getTable() {
        return TABLES.CONTACTS;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues cv = new ContentValues();
        if (mId != null) cv.put(BaseColumns._ID, mId);
        cv.put(CONTACTS_COLUMNS.NAME, mName);
        cv.put(CONTACTS_COLUMNS.EMAIL, mEmail);
        return cv;
    }

    // Parcelable interface

    public static final Parcelable.Creator<Contact> CREATOR
            = new Parcelable.Creator<Contact>() {
        public Contact createFromParcel(Parcel in) {
            return new Contact(in);
        }

        public Contact[] newArray(int size) {
            return new Contact[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId != null ? 1 : 0);
        if (mId != null) dest.writeInt(mId);
        dest.writeString(mName);
        dest.writeString(mEmail);
    }
}
