package edu.puc.firebasetest.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.Toolbar;
import com.android.volley.Request.Method;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.nononsenseapps.filepicker.FilePickerActivity;
import edu.puc.firebasetest.app.adapters.ChatAdapter;
import edu.puc.firebasetest.app.adapters.ChatAdapter.ChatAdapterListener;
import edu.puc.firebasetest.app.cache.ContactsCache;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.entities.Contact;
import edu.puc.firebasetest.app.model.entities.FileMessage;
import edu.puc.firebasetest.app.model.entities.Message;
import edu.puc.firebasetest.app.model.entities.Room;
import edu.puc.firebasetest.app.network.NetworkManager;
import edu.puc.firebasetest.app.network.api.GcmApi;
import edu.puc.firebasetest.app.services.ChatService;
import edu.puc.firebasetest.app.services.ChatService.ChatListener;
import edu.puc.firebasetest.app.services.ChatService.LocalBinder;
import edu.puc.firebasetest.app.tasks.DriveTask;
import edu.puc.firebasetest.app.tasks.DriveTask.FileSentListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Activity for handling chat interactions.
 */
public class ChatActivity extends Activity implements ChatListener, FileSentListener, ChatAdapterListener {
    public static final int FILE_PICKER_CODE = 100;
    public static final int DRIVE_AUTHORIZATION_CODE = 101;
    public static final int GOOGLE_PLAY_SERVICES_CODE = 102;
    public static final int PHOTO_AUDIO_CODE = 103;

    private static final String TAG = "Chat";
    private static final String[] SCOPES = { DriveScopes.DRIVE_FILE };

    public static final String KEY_ROOM = "room";
    public static final String KEY_PARTICIPANTS = "participants";
    public static final String KEY_GROUP = "group";

    private Firebase mFirebase;
    private Firebase mFirebaseRoom;
    private Firebase mFirebaseRoomParticipants;

    private NetworkManager mNetworkManager;
    private Handler mHandler = new Handler();
    private boolean mInitialDataLoaded;

    private boolean mIsGroup;
    private Room mRoom;

    private List<Contact> mParticipants;
    private Map<String, String> mTokens;
    private GoogleAccountCredential mCredential;
    private List<DriveTask> mDriveTasks;

    private ListView mListChat;
    private ChatAdapter mChatAdapter;
    private List<Message> mMessageList;
    private ContactsCache mContacts;

    private ParticipantsListener mParticipantsListener = new ParticipantsListener();
    private MessagesListener mMessagesListener = new MessagesListener();


    // Service connection necessary for service binding.
    private ChatService mChatService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mChatService = ((LocalBinder) service).getService();
            mChatService.addListener(ChatActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mChatService = null;
        }
    };

    public static Intent getIntentForSingleChat(Context context, Room room, Contact user) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(KEY_ROOM, room);
        ArrayList<Contact> users = new ArrayList<>();
        users.add(user);
        intent.putParcelableArrayListExtra(KEY_PARTICIPANTS, users);
        intent.putExtra(KEY_GROUP, false);
        return intent;
    }

    public static Intent getIntentForGroupChat(Context context, Room room, ArrayList<Contact> users) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(KEY_ROOM, room);
        intent.putParcelableArrayListExtra(KEY_PARTICIPANTS, users);
        intent.putExtra(KEY_GROUP, true);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        mNetworkManager = NetworkManager.getNetworkManager(this);
        mTokens = new ConcurrentHashMap<>();
        mDriveTasks = new ArrayList<>();
        mContacts = ContactsCache.getInstance(this);

        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(FirebaseModel.getAccountName(this));

        mIsGroup = getIntent().getBooleanExtra(KEY_GROUP, false);
        mRoom = getIntent().getParcelableExtra(KEY_ROOM);
        mParticipants = getIntent().getParcelableArrayListExtra(KEY_PARTICIPANTS);

        // Firebase reference initialization.
        mFirebase = new Firebase(getString(R.string.firebase_root));
        mFirebaseRoom = mFirebase.child(FirebaseModel.ROOMS).child(mRoom.getKey());
        mFirebaseRoomParticipants = mFirebaseRoom.child(FirebaseModel.PARTICIPANTS);
        mFirebaseRoom.keepSynced(true);

        // We seek to retrieve all the room's participants GCM tokens to send them push notifications.
        // TODO: This should never be done locally, but it is good enough for a demo.
        for (Contact participant : mParticipants) {
            loadToken(FirebaseModel.escapeCharacters(participant.getEmail()));
        }

        // We fetch all initial messages in the room.
        mFirebaseRoom.child(FirebaseModel.MESSAGES).addListenerForSingleValueEvent(mMessagesListener);

        mMessageList = new ArrayList<>();

        mChatAdapter = new ChatAdapter(this, mMessageList, mContacts);
        mChatAdapter.setListener(this);
        mListChat = (ListView) findViewById(R.id.list_chat);
        mListChat.setAdapter(mChatAdapter);

        // Message sending button customization.
        final EditText mEditMessage = (EditText) findViewById(R.id.edit_message);
        Button btnSubmit = (Button) findViewById(R.id.btn_submit);
        btnSubmit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // We check if we have loaded the necessary data for sending chat messages.
                // We need both the list of participants and their tokens.
                if (!isChatReady()) {
                    Toast.makeText(ChatActivity.this, getString(R.string.chat_participants_not_loaded), Toast.LENGTH_LONG).show();
                } else if (mParticipants.size() == 0) {
                    Toast.makeText(ChatActivity.this, getString(R.string.chat_empty_room), Toast.LENGTH_LONG).show();
                } else if (!mInitialDataLoaded) {
                    Toast.makeText(ChatActivity.this, getString(R.string.chat_loading_messages), Toast.LENGTH_LONG).show();
                } else {
                    String messageContent = mEditMessage.getText().toString();
                    if (!messageContent.isEmpty()) {
                        Message message = new Message(FirebaseModel.getUser(ChatActivity.this), messageContent, mRoom.getKey(), Message.MESSAGE_TYPE_TEXT);
                        sendMessage(message);
                        mEditMessage.setText("");
                    }
                }

            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mFirebaseRoomParticipants.addChildEventListener(mParticipantsListener);
        // We bind to the chat service to listen for incoming push notifications.
        Intent intent = new Intent(this, ChatService.class);
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mFirebaseRoomParticipants.removeEventListener(mParticipantsListener);
        // Cleanup of chat binding.
        if (mChatService != null) {
            mChatService.removeListener(null);
            unbindService(mServiceConnection);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cleanup of firebase, adapter, and background task resources.
        mFirebaseRoom.keepSynced(false);
        for (DriveTask task : mDriveTasks) {
            task.setListener(null);
        }
        mChatAdapter.setListener(null);
        mChatAdapter.cleanup();
    }

    /**
     * Adds a message to the local room, then sends it both to firebase and GCM.
     *
     * @param message The message to send.
     */
    private void sendMessage(Message message) {
        mMessageList.add(message);
        mFirebaseRoom.child(FirebaseModel.MESSAGES).push().setValue(message);

        for (String token : mTokens.values()) {
            GcmApi gcmApi = new GcmApi(this, token, message);
            mNetworkManager.executeRequest(gcmApi);
        }

        updateUI();
    }

    /**
     * Adds a participant to the local room, then records said participant in the firebase server model and sends a GCM
     * notification to the target.
     *
     * If the participant already belongs in the room, a UI warning is issued.
     *
     * @param contact The person to invite.
     */
    private void sendInvitation(Contact contact) {
        boolean flag = false;
        for (Contact c : mParticipants) {
            if (c.getEmail().equals(contact.getEmail())) {
                flag = true;
                break;
            }
        }
        if (flag) {
            Toast.makeText(this, getString(R.string.chat_contact_already_present), Toast.LENGTH_LONG).show();
            return;
        }

        mParticipants.add(contact);

        Firebase contactUserRoomsNode = mFirebase.child(FirebaseModel.USERS).child(FirebaseModel.escapeCharacters(contact.getEmail())).child(FirebaseModel.ROOMS);
        contactUserRoomsNode.child(mRoom.getKey()).setValue(mRoom);
        contactUserRoomsNode.child(mRoom.getKey()).child(FirebaseModel.PARTICIPANTS).child(FirebaseModel.getUser(ChatActivity.this)).setValue(true);

        mFirebaseRoom.child(FirebaseModel.PARTICIPANTS).child(FirebaseModel.escapeCharacters(contact.getEmail())).setValue(true);

        loadToken(FirebaseModel.escapeCharacters(contact.getEmail()), new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (String token : mTokens.values()) {
                    GcmApi gcmApi = new GcmApi(ChatActivity.this, token, mRoom);
                    mNetworkManager.executeRequest(gcmApi);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    /**
     * Refreshes the UI.
     */
    private void updateUI() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mChatAdapter.notifyDataSetChanged();
                mListChat.smoothScrollToPosition(mChatAdapter.getCount());
            }
        });
    }

    /**
     * Loads a GCM token for a given contact.
     *
     * @param participant The contact to retrieve.
     */
    private void loadToken(final String participant) {
        loadToken(participant, null);

    }

    /**
     * Loads a GCM token for a given contact and adds a completion listener.
     *
     * @param participant The username of the contact to retrieve his token.
     * @param listener A completion listener that will be invoked once the operation completes. Can be null.
     */
    private void loadToken(final String participant, final ValueEventListener listener) {
        if (mTokens.get(participant) == null && !participant.equals(FirebaseModel.getUser(this))) {
            mFirebase.child(FirebaseModel.USERS).child(participant).child(FirebaseModel.TOKEN)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            mTokens.put(participant, dataSnapshot.getValue().toString());
                            if (listener != null) {
                                listener.onDataChange(dataSnapshot);
                            }
                        }

                        @Override
                        public void onCancelled(FirebaseError firebaseError) {
                            // TODO: Handle network error
                        }
                    });
        }
    }

    /**
     * Checks if all necessary data for chat interaction has already been downloaded. This is the other users'
     * GCM tokens and the initial room messages.
     *
     * @return true if ready for interaction, false otherwise.
     */
    private boolean isChatReady() {
        boolean ready = false;
        if (mIsGroup && mParticipants.size() == mTokens.size() + 1) {
            ready = true;
        }
        if (!mIsGroup && mTokens.size() == 1) {
            ready = true;
        }
        if (!mInitialDataLoaded) {
            ready = false;
        }
        return ready;
    }

    // Chat service listener interface

    @Override
    public boolean onMessageReceived(Message message) {
        mMessageList.add(message);
        updateUI();
        return true;
    }

    @Override
    public boolean onInvitationReceived(Room room) {
        // We do not act upon invitations inside a chat room. May be changed in the future.
        return false;
    }

    // Menu interface

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_chat, menu);
        if (!mIsGroup) menu.removeItem(R.id.action_invite_contact);

        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (item.getItemId() == R.id.action_send_file) {
            startFilePicker();

            return true;
        }
        if (item.getItemId() == R.id.action_send_photo_audio) {
            startActivityForResult(PhotoAudioActivity.getIntent(this), PHOTO_AUDIO_CODE);

            return true;
        }
        if (item.getItemId() == R.id.action_invite_contact) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Invite a contact");
            // TODO: remove from contact lists those already in the room.
            builder.setItems(mContacts.getContactsAsStringArray(), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // We add the contact to the local list
                    Contact contact = mContacts.getValidContacts().get(which);
                    sendInvitation(contact);
                }
            });
            builder.show();
        }
        return super.onMenuItemSelected(featureId, item);
    }

    // ---

    /**
     * Starts a file picker using a FilePicker library.
     *
     * For more information refer to https://github.com/spacecowboy/NoNonsense-FilePicker
     */
    private void startFilePicker() {
        Intent i = new Intent(this, FilePickerActivity.class);

        // Set these depending on your use case. These are the defaults.
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);

        // Configure initial directory by specifying a String.
        i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

        startActivityForResult(i, FILE_PICKER_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == FILE_PICKER_CODE || requestCode == PHOTO_AUDIO_CODE) && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            File file = new File(uri.getPath());

            DriveTask task;
            if (requestCode == FILE_PICKER_CODE) {
                int fileType = Message.MESSAGE_TYPE_FILE;
                Toast.makeText(this, getString(R.string.chat_sending_file, file.getName()), Toast.LENGTH_LONG).show();
                task = new DriveTask(this, mCredential, uri, fileType);
            } else {
                int fileType = Message.MESSAGE_TYPE_PHOTO_AUDIO;
                Toast.makeText(this, getString(R.string.chat_sending_photo_audio), Toast.LENGTH_LONG).show();
                String uuid = data.getStringExtra(PhotoAudioActivity.KEY_UUID);
                task = new DriveTask(this, mCredential, uri, fileType, uuid);
            }

            task.setListener(this);
            task.execute();
            mDriveTasks.add(task);
        }
    }

    // Drive task interface

    @Override
    public void onFileSent(DriveTask task, Uri uri, String webUrl, int fileType, String uuid) {
        File file = new File(uri.getPath());
        String fileName = file.getName();
        int size = (int) file.length();

        FileMessage message;
        if (uuid == null) {
            message = new FileMessage(FirebaseModel.getUser(ChatActivity.this), mRoom.getKey(), fileType);
        } else {
            message = new FileMessage(FirebaseModel.getUser(ChatActivity.this), mRoom.getKey(), fileType, uuid);
        }

        message.setSize(size);
        message.setUrl(webUrl);
        message.setFileName(fileName);
        message.setUri(uri);

        sendMessage(message);

        task.setListener(null);
        mDriveTasks.remove(task);
    }

    @Override
    public void onFileSentError() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ChatActivity.this, getString(R.string.chat_failed_to_upload_file), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Chat adapter interface

    @Override
    public void onFileAccepted(FileMessage message) {
        Toast.makeText(this, getString(R.string.chat_downloading_file, message.getFileName()), Toast.LENGTH_LONG).show();

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(message.getUrl());
        DownloadManager.Request request = new Request(uri);
        request.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        // request.setDescription(message.getMessage());
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, message.getFileName());
        downloadManager.enqueue(request);

        updateUI();
    }

    @Override
    public void onFileRefused(FileMessage message) {
        updateUI();
    }

    // ---

    private class ParticipantsListener implements ChildEventListener {

        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            // The contact will not be added into our participant's list if we are in a shared room and someone
            // else invites him. Here we make sure of catching these scenarios.
            if (!mParticipants.contains(mContacts.get(dataSnapshot.getKey()))) {
                mParticipants.add(mContacts.get(dataSnapshot.getKey()));
            }

            // We load the GCM token of the contact to communicate with him.
            loadToken(dataSnapshot.getKey());
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) { }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) { }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) { }

        @Override
        public void onCancelled(FirebaseError firebaseError) { }
    }

    private class MessagesListener implements ValueEventListener {

        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            if (dataSnapshot != null) {
                for (DataSnapshot message : dataSnapshot.getChildren()) {
                    int type = ((Long) message.child(Message.KEY_TYPE).getValue()).intValue();
                    Message m;
                    if (type == Message.MESSAGE_TYPE_TEXT) {
                        m = new Message(message);
                    } else {
                        m = new FileMessage(message);
                        if (!m.getUsername().equals(FirebaseModel.getUser(ChatActivity.this))) {
                            ((FileMessage) m).setDownloadable(true);
                        }
                    }

                    Log.i(TAG, "Message: " + m.getMessage());
                    mMessageList.add(m);
                }
                updateUI();
            }
            mInitialDataLoaded = true;
        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {

        }
    }
}
