package edu.puc.firebasetest.app.services;


import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.gcm.GcmListenerService;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.LocalDatabase.ROOMS_COLUMNS;
import edu.puc.firebasetest.app.model.entities.FileMessage;
import edu.puc.firebasetest.app.model.entities.Message;
import edu.puc.firebasetest.app.model.entities.Room;
import edu.puc.firebasetest.app.network.api.GcmApi;
import edu.puc.firebasetest.app.network.request.GcmRequest;

import java.io.File;

/**
 * Entry point for all GCM push notifications received. This service parses the incoming data and resends it to
 * the appropriate handler according to its contents.
 */
public class GcmService extends GcmListenerService {

    @Override
    public void onMessageReceived(String from, Bundle data) {
        int messageType = Integer.parseInt(data.getString(GcmRequest.KEY_TYPE));

        if (messageType == GcmRequest.TYPE_MESSAGE) {
            // TODO: Move all this to Message classes.
            String messageContent = data.getString(Message.KEY_MESSAGE);
            String username = data.getString(Message.KEY_USERNAME);
            String uuid = data.getString(Message.KEY_UUID);
            String room = data.getString(Message.KEY_ROOM);
            int type = Integer.parseInt(data.getString(Message.KEY_TYPE));



            if (type == Message.MESSAGE_TYPE_TEXT) {
                Message message = new Message(username, messageContent, room, type, uuid);

                Intent intent = ChatService.getIntent(this, ChatService.ACTION_GCM_MESSAGE_RECEIVED);
                intent.putExtra(Message.PARCELABLE_KEY, message);
                startService(intent);
            } else {
                FileMessage fileMessage = new FileMessage(username, room, type, uuid);

                String url = data.getString(FileMessage.KEY_URL);
                int size = Integer.parseInt(data.getString(FileMessage.KEY_SIZE));
                String fileName = data.getString(FileMessage.KEY_FILE_NAME);

                fileMessage.setUrl(url);
                fileMessage.setSize(size);
                fileMessage.setFileName(fileName);

                if (!fileMessage.getUsername().equals(FirebaseModel.getUser(this))) {
                    fileMessage.setDownloadable(true);
                }

                Intent intent = ChatService.getIntent(this, ChatService.ACTION_GCM_FILE_MESSAGE_RECEIVED);
                intent.putExtra(FileMessage.PARCELABLE_KEY, fileMessage);
                startService(intent);
            }

        } else if (messageType == GcmRequest.TYPE_ROOM) {
            Room room = new Room(data);

            Intent intent = ChatService.getIntent(this, ChatService.ACTION_GCM_ROOM_RECEIVED);
            intent.putExtra(Room.PARCELABLE_KEY, room);
            startService(intent);
        }




    }
}
