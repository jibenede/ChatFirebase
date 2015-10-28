package edu.puc.firebasetest.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.Toolbar;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import edu.puc.firebasetest.app.adapters.ContactsAdapter;
import edu.puc.firebasetest.app.cache.ContactsCache;
import edu.puc.firebasetest.app.model.entities.Contact;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.entities.Message;
import edu.puc.firebasetest.app.model.entities.Room;
import edu.puc.firebasetest.app.services.ChatService;
import edu.puc.firebasetest.app.services.ChatService.ChatListener;
import edu.puc.firebasetest.app.services.ChatService.LocalBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Entry point for the application. This activity shows a list of contacts such that they appear both locally
 * on the device's contact list and in the firebase server. We use the google email as a contact identifier.
 *
 * The logic proceeds as follows: a list of all locally stored potential contacts is retrieved. We define a potential
 * contact as one that appears in the contact provider and features an email. Those contacts are stored in a hashmap.
 * Afterwards, all firebase stored contacts are fetched from the web. Once they are retrieved, they are cross-referenced
 * against the list of potential contacts to decide which to show on the device. Finally, a listener is attached
 * to the firebase object to receive updates of new contacts that may be added while inside the application.
 */
public class MainActivity extends Activity implements ChatListener {
    private static final String TAG = "Memeticame";

    private SharedPreferences mPreferences;
    private ContactsChangedListener mContactsChangedListener = new ContactsChangedListener();
    private Handler mHandler = new Handler();
    private Timer mTimer = new Timer();
    private InitFailureBroadcastReceiver mReceiver = new InitFailureBroadcastReceiver();

    private ContactsCache mContacts;
    private List<Contact> mValidContacts;

    private ContactsAdapter mAdapter;
    private ListView mListView;
    private Firebase mContactsNode;

    private ChatService mChatService;

    // TODO: bind service to update rooms with pending messages
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mChatService = ((LocalBinder) service).getService();
            mChatService.addListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mChatService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        mPreferences = getSharedPreferences(ChatService.PREFERENCES_FILENAME, MODE_PRIVATE);
        mContacts = ContactsCache.getInstance(this);
        mValidContacts = mContacts.getValidContacts();

        mListView = (ListView) findViewById(R.id.list_contacts);
        mAdapter = new ContactsAdapter(MainActivity.this, mValidContacts);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Contact contact = mAdapter.getItem(position);

                Room room = Room.singleContactRoom(MainActivity.this, contact);
                startActivity(ChatActivity.getIntentForSingleChat(MainActivity.this, room, contact));
            }
        });

        // TODO: Move this block to a background thread
        {
            mContacts.loadLocalContacts();

            Firebase root = new Firebase(getString(R.string.firebase_root));
            mContactsNode = root.child(FirebaseModel.USERS).child(FirebaseModel.getUser(this)).child(FirebaseModel.CONTACTS);
        }

        startService(ChatService.getIntent(this, ChatService.ACTION_INITIALIZE));
    }

    @Override
    protected void onStart() {
        super.onStart();
        mContactsNode.addChildEventListener(mContactsChangedListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ChatService.ACTION_INITIALIZATION_FAILURE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mContactsNode.removeEventListener(mContactsChangedListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (item.getItemId() == R.id.action_reload_contacts) {
            Editor editor = mPreferences.edit();
            editor.putBoolean(ChatService.KEY_INITIALIZED, false);
            editor.commit();
            startService(ChatService.getIntent(this, ChatService.ACTION_INITIALIZE));
            return true;
        } else if (item.getItemId() == R.id.action_groups) {
            startActivity(GroupsActivity.getIntent(this));
        }
        return super.onMenuItemSelected(featureId, item);
    }



    @Override
    public boolean onMessageReceived(Message message) {
        // TODO: add indicator in room that received the message
        return true;
    }

    @Override
    public boolean onInvitationReceived(Room room) {
        // TODO: add indicator somewhere
        return true;
    }

    private class ContactsChangedListener implements ChildEventListener {

        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String previousChild) {
            String mail = dataSnapshot.getKey();
            Log.i(TAG, "user: " + mail);
            Contact contact = mContacts.get(mail);
            if (contact != null && !mValidContacts.contains(contact)) {
                mValidContacts.add(mContacts.get(mail));
            }

            // Hack to call notifyDataSetChanged only once after all children have been added.
            mTimer.cancel();
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }, 100);
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String previousChild) {

        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {

        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String previousChild) {

        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {

        }
    }

    private class InitFailureBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, Intent intent) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, getString(R.string.contacts_network_error), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
