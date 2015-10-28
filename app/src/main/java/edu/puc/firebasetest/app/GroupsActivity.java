package edu.puc.firebasetest.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toolbar;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import edu.puc.firebasetest.app.adapters.GroupAdapter;
import edu.puc.firebasetest.app.cache.ContactsCache;
import edu.puc.firebasetest.app.dialogs.GroupDialog;
import edu.puc.firebasetest.app.dialogs.GroupDialog.GroupDialogListener;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.entities.Contact;
import edu.puc.firebasetest.app.model.entities.Message;
import edu.puc.firebasetest.app.model.entities.Room;
import edu.puc.firebasetest.app.services.ChatService;
import edu.puc.firebasetest.app.services.ChatService.ChatListener;
import edu.puc.firebasetest.app.services.ChatService.LocalBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by jose on 10/27/15.
 */
public class GroupsActivity extends Activity implements GroupDialogListener, ChatListener {
    private static final String DIALOG_GROUP_TAG = "group";

    private Timer mTimer = new Timer();
    private Handler mHandler = new Handler();
    private List<Room> mGroups;
    private GroupAdapter mAdapter;
    private GroupsChangedListener mGroupsChangedListener = new GroupsChangedListener();
    private ContactsCache mContactsCache;

    private Firebase mFirebase;
    private Firebase mUserRoomsNode;

    // Service connection necessary for service binding.
    private ChatService mChatService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mChatService = ((LocalBinder) service).getService();
            mChatService.addListener(GroupsActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mChatService = null;
        }
    };

    public static Intent getIntent(Context context) {
        Intent intent = new Intent(context, GroupsActivity.class);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        mContactsCache = ContactsCache.getInstance(this);

        mFirebase = new Firebase(getString(R.string.firebase_root));
        mUserRoomsNode = mFirebase.child(FirebaseModel.USERS).child(FirebaseModel.getUser(this)).child(FirebaseModel.ROOMS);

        mGroups = new ArrayList<>();
        ListView list = (ListView) findViewById(R.id.list);
        mAdapter = new GroupAdapter(this, mGroups, ContactsCache.getInstance(this));
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Room room = mGroups.get(position);
                // We retrieve the participants in the room.
                Firebase firebaseRoomParticipants = mFirebase.child(FirebaseModel.ROOMS).child(room.getKey()).child(FirebaseModel.PARTICIPANTS);
                firebaseRoomParticipants.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        ArrayList<Contact> participants = new ArrayList<>();
                        for (DataSnapshot data : dataSnapshot.getChildren()) {
                            participants.add(mContactsCache.get(data.getKey()));
                        }

                        startActivity(ChatActivity.getIntentForGroupChat(GroupsActivity.this, room, participants));
                    }

                    @Override
                    public void onCancelled(FirebaseError firebaseError) {
                        // TODO: show error dialog
                    }
                });

            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUserRoomsNode.addChildEventListener(mGroupsChangedListener);

        // We bind to the chat service to listen for incoming push notifications.
        Intent intent = new Intent(this, ChatService.class);
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mUserRoomsNode.removeEventListener(mGroupsChangedListener);

         // Cleanup of chat service binding.
         if (mChatService != null) {
             mChatService.removeListener(this);
             unbindService(mServiceConnection);
         }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_groups, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (item.getItemId() == R.id.action_add_group) {
            GroupDialog groupDialog = new GroupDialog();
            groupDialog.setListener(this);
            groupDialog.show(getFragmentManager(), DIALOG_GROUP_TAG);

            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    public void onGroupCreated(String name) {
        // We create the room, add to the local room list and update the UI. Afterwards, we set ourserlves as a participant
        // of the room in firebase, and later we add the room to the room list. Data is replicated in the Users and Rooms nodes.
        Room room = new Room(name, FirebaseModel.getUser(this));
        mGroups.add(room);
        mAdapter.notifyDataSetChanged();

        mFirebase.child(FirebaseModel.ROOMS).child(room.getKey()).child(FirebaseModel.PARTICIPANTS).child(FirebaseModel.getUser(GroupsActivity.this)).setValue(true);
        mUserRoomsNode.child(room.getKey()).setValue(room);
        // mUserRoomsNode.child(room.getKey()).child(FirebaseModel.PARTICIPANTS).child(FirebaseModel.getUser(GroupsActivity.this)).setValue(true);
    }

    @Override
    public boolean onMessageReceived(Message message) {
        // Groups do not process messages

        return false;
    }

    @Override
    public boolean onInvitationReceived(Room room) {
        /*
        mGroups.add(room);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
        */

        return true;

    }

    private class GroupsChangedListener implements ChildEventListener {

        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String previousChild) {
            String groupKey = dataSnapshot.getKey();
            boolean flag = false;
            for (Room room : mGroups) {
                if (room.getKey().equals(groupKey)) {
                    flag = true;
                }
            }

            if (!flag) {
                Room room = new Room(dataSnapshot);
                mGroups.add(room);

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

}
