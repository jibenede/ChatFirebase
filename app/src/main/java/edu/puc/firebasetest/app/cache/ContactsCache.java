package edu.puc.firebasetest.app.cache;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.entities.Contact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A cache of all valid contacts as returned from the contact provider. These are used in different parts of the
 * program, therefore a singleton instance for repeated use is provided.
 *
 * Contacts are stored in a hashmap where the key is the contact key in the firebase server storage. Firebase servers
 * do not allow dot (.) characters in a key, therefore the emails are escaped and unallowed characters are changed for
 * a dash (-).
 */
public class ContactsCache {
    private static ContactsCache sContactsCache;

    private HashMap<String, Contact> mContacts;
    private List<Contact> mValidContacts;

    private Context mContext;
    private boolean mLoaded;

    public static ContactsCache getInstance(Context context) {
        if (sContactsCache == null) {
            sContactsCache = new ContactsCache(context);
        }
        return sContactsCache;
    }

    private ContactsCache(Context context) {
        mContacts = new HashMap<>();
        mValidContacts = new ArrayList<>();
        mContext = context;
    }

    public void loadLocalContacts() {
        String[] projection = new String[] { ContactsContract.Contacts.DISPLAY_NAME, Email.ADDRESS };
        String selection =
                Email.ADDRESS + " <> '' AND " +
                        Email.ADDRESS + " NOT NULL AND " +
                        ContactsContract.Contacts.DISPLAY_NAME + " <> '' AND " +
                        ContactsContract.Contacts.DISPLAY_NAME + " NOT NULL";
        Cursor cursor = mContext.getContentResolver().query(Email.CONTENT_URI, projection, selection, null, null);

        while (cursor.moveToNext()) {
            Contact contact = Contact.contactFromOS(cursor);
            if (contact.getEmail() != null && contact.getName() != null) {
                mContacts.put(FirebaseModel.escapeCharacters(contact.getEmail()), contact);
            }
        }
        mLoaded = true;
    }

    public Contact get(String key) {
        if (key.equals(FirebaseModel.getUser(mContext))) {
            return Contact.selfContact(mContext);
        } else {
            return mContacts.get(key);
        }

    }

    public List<Contact> getValidContacts() {
        return mValidContacts;
    }

    public String[] getContactsAsStringArray() {
        String[] list = new String[mValidContacts.size()];
        for (int i = 0; i < mValidContacts.size(); i++) {
            list[i] = mValidContacts.get(i).getEmail();
        }
        return list;
    }

    public boolean isLoaded() {
        return mLoaded;
    }
}
