package edu.puc.firebasetest.app.services;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.firebase.client.Firebase;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import edu.puc.firebasetest.app.R;
import edu.puc.firebasetest.app.cache.ContactsCache;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.entities.FileMessage;
import edu.puc.firebasetest.app.model.entities.Message;
import edu.puc.firebasetest.app.model.entities.Room;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * A service for handling common background operation in the chat application.
 */
public class ChatService extends Service {
    private static final String TAG = "ChatService";
    private static final String SERVICE_NAME = "ChatService";

    public static final String ACTION_INITIALIZE = "initialize";
    public static final String ACTION_GCM_REGISTER = "gcm_register";
    public static final String ACTION_GCM_MESSAGE_RECEIVED = "gcm_received";
    public static final String ACTION_GCM_FILE_MESSAGE_RECEIVED = "gcm_file_received";
    public static final String ACTION_GCM_ROOM_RECEIVED = "gcm_room";
    public static final String ACTION_GCM_REFRESH_TOKEN = "gcm_refresh";

    private static final int NOTIFICATION_ID = 1000;

    public static final String ACTION_INITIALIZATION_FAILURE = "failure";

    public static final String PREFERENCES_FILENAME = "preferences";
    public static final String KEY_INITIALIZED = "initialized";
    public static final String KEY_TOKEN = "token";

    private static final int LOADER_INITIALIZE_ID = 100;

    private IBinder mBinder = new LocalBinder();
    private ContactsCache mContactsCache;
    private ArrayBlockingQueue<Intent> mCommandQueue;
    private WorkingThread mWorkingThread;

    private List<ChatListener> mListeners;

    public static Intent getIntent(Context context, String action) {
        Intent intent = new Intent(context, ChatService.class);
        intent.setAction(action);
        return intent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mListeners = new ArrayList<>();
        mCommandQueue = new ArrayBlockingQueue<>(10);
        mWorkingThread = new WorkingThread();
        mWorkingThread.start();

        Log.i(TAG, "Chat service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mCommandQueue.add(intent);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWorkingThread.stopRunning();
        mWorkingThread.interrupt();

        Log.i(TAG, "Chat service destroyed");
    }

    protected void handleIntent(Intent intent) {
        if (mContactsCache == null) {
            mContactsCache = ContactsCache.getInstance(this);
            if (!mContactsCache.isLoaded()) {
                mContactsCache.loadLocalContacts();
            }
        }

        switch (intent.getAction()) {
            case ACTION_INITIALIZE:
                initialize();
                break;
            case ACTION_GCM_REGISTER:
                register();
                break;
            case ACTION_GCM_FILE_MESSAGE_RECEIVED:
            case ACTION_GCM_MESSAGE_RECEIVED:
                processMessage(intent);
                break;
            case ACTION_GCM_ROOM_RECEIVED:
                processRoom(intent);
                break;
            case ACTION_GCM_REFRESH_TOKEN:
                refreshToken();
                break;
        }
    }

    /**
     * Initializes the contacts list in the server. It goes through all the contacts given by the local contacts provider
     * (including GMail contacts) and creates and entry for each and everyone of them in the server.
     *
     * It later adds to each entry the current user as a contact. That means the only way someone else may appear in your
     * contact list is if that person itself adds you first.
     *
     * If successful, this action is recorded on a preferences files to avoid having to this everytime the application
     * starts. This also means new contacts may not be properly updated. You can invoke this service manually
     * to solve this cases.
     */
    private void initialize() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_FILENAME, MODE_PRIVATE);
        boolean initialized = preferences.getBoolean(KEY_INITIALIZED, false);

        if (!initialized) {
            Log.i(TAG, "Initializing server data");

            if (!isRegistered()) {
                boolean success = register();
                if (!success) {
                    Intent intent = new Intent(ACTION_INITIALIZATION_FAILURE);
                    LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
                    localBroadcastManager.sendBroadcast(intent);
                    return;
                }
            }

            String[] projection = new String[] { ContactsContract.Contacts.DISPLAY_NAME, Email.ADDRESS };
            String selection =
                    Email.ADDRESS + " <> '' AND " +
                            Email.ADDRESS + " NOT NULL AND " +
                            ContactsContract.Contacts.DISPLAY_NAME + " <> '' AND " +
                            ContactsContract.Contacts.DISPLAY_NAME + " NOT NULL";
            CursorLoader cursorLoader = new CursorLoader(this, Email.CONTENT_URI, projection, selection, null, null);
            cursorLoader.registerListener(LOADER_INITIALIZE_ID, new OnLoadCompleteListener<Cursor>() {
                @Override
                public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
                    updateContacts(data);
                }
            });
            cursorLoader.startLoading();
        }
    }

    /**
     * Updates the firebase server data.
     *
     * @param data A cursor returned by the system's contact provider.
     */
    private void updateContacts(Cursor data) {
        // Stores the GCM server token in firebase so other people may send us push notifications.
        Firebase root = new Firebase(getString(R.string.firebase_root));
        Firebase tokenNode = root.child(FirebaseModel.USERS).child(FirebaseModel.getUser(this)).child(FirebaseModel.TOKEN);
        tokenNode.setValue(getRegistrationToken());

        while(data.moveToNext()) {
            String contactEmail = data.getString(data.getColumnIndex(Email.ADDRESS));
            contactEmail = FirebaseModel.escapeCharacters(contactEmail);

            if (!contactEmail.equals(FirebaseModel.getUser(this))) {
                Firebase otherContactNode = root.child(FirebaseModel.USERS).child(contactEmail).child(FirebaseModel.CONTACTS);
                Firebase otherContact = otherContactNode.child(FirebaseModel.getUser(this));
                otherContact.child(FirebaseModel.ENABLED).setValue(true);
            }
        }

        // Saves on the file system that initialization was completed successfully.
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_FILENAME, MODE_PRIVATE);
        Editor editor = preferences.edit();
        editor.putBoolean(KEY_INITIALIZED, true);
        editor.commit();

        Log.i(TAG, "Initialization completed");
    }

    private void refreshToken() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_FILENAME, MODE_PRIVATE);
        Editor editor = preferences.edit();
        editor.remove(KEY_TOKEN);
        editor.commit();
        register();
    }

    /**
     * Registers this device in GCM servers.
     *
     * @return true if successful, false otherwise.
     */
    private boolean register() {
        InstanceID instanceID = InstanceID.getInstance(this);
        try {
            if (!isRegistered()) {
                String token = instanceID.getToken(getString(R.string.gcm_sender_id),
                        GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                SharedPreferences preferences = getSharedPreferences(PREFERENCES_FILENAME, MODE_PRIVATE);
                preferences.edit().putString(KEY_TOKEN, token).commit();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isRegistered() {
        return getRegistrationToken() != null;
    }

    private String getRegistrationToken() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_FILENAME, MODE_PRIVATE);
        return preferences.getString(KEY_TOKEN, null);
    }

    /**
     * Processes a message received through GCM.
     *
     * @param intent The intent containing the message inside a parcelable.
     */
    private void processMessage(Intent intent) {
        Message message;
        if (intent.getAction() == ACTION_GCM_MESSAGE_RECEIVED) {
            message = intent.getParcelableExtra(Message.PARCELABLE_KEY);
        } else {
            FileMessage temp = intent.getParcelableExtra(FileMessage.PARCELABLE_KEY);
            message = temp;
        }

        if (mListeners.size() > 0) {
            // If someone is bound to this services and has registered himself as a listener, we delegate the
            // handling of the message to him.
            boolean flag = false;
            for (ChatListener listener : mListeners) {
                if (listener.onMessageReceived(message)) {
                    flag = true;
                }
            }
            if (flag) return;
        }
        // If not or if the bound listener does not handle the message, that means no one is currently listening for
        // incomming messages. Therefore we send a notification.

        String notificationMessage;
        if (message.getMessage().isEmpty()) {
            notificationMessage = getString(R.string.notification_message_received_content,
                    mContactsCache.get(message.getUsername()).getName());
        } else {
            notificationMessage = mContactsCache.get(message.getUsername()).getName() + ": " + message.getMessage();
        }
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getString(R.string.notification_message_received_title))
                .setContentText(notificationMessage)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);

    }

    public void processRoom(Intent intent) {
        Room room = intent.getParcelableExtra(Room.PARCELABLE_KEY);


        if (mListeners.size() > 0) {
            for (ChatListener listener : mListeners) {
                listener.onInvitationReceived(room);
            }
        } else {
            // TODO: send notification
        }
    }

    public void addListener(ChatListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(ChatListener listener) {
        mListeners.remove(listener);
    }

    public class LocalBinder extends Binder {
        public ChatService getService() {
            return ChatService.this;
        }
    }

    public interface ChatListener {
        boolean onMessageReceived(Message message);
        boolean onInvitationReceived(Room room);
    }

    private class WorkingThread extends Thread {
        private boolean mRun;

        @Override
        public void run() {
            mRun = true;
            while(mRun) {
                try {
                    Intent intent = mCommandQueue.take();
                    handleIntent(intent);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stopRunning() {
            mRun = false;
        }
    }
}
