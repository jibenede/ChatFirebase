package edu.puc.firebasetest.app.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import edu.puc.firebasetest.app.PhotoAudioActivity;
import edu.puc.firebasetest.app.R;
import edu.puc.firebasetest.app.cache.ContactsCache;
import edu.puc.firebasetest.app.model.FirebaseModel;
import edu.puc.firebasetest.app.model.entities.FileMessage;
import edu.puc.firebasetest.app.model.entities.Message;
import edu.puc.firebasetest.app.utils.FileDownloader;
import edu.puc.firebasetest.app.utils.FileDownloader.FileDownloaderListener;
import edu.puc.firebasetest.app.utils.PhotoAudioDownloader;
import edu.puc.firebasetest.app.utils.PhotoAudioParser;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter for managing data and view creation of the chat window.
 */
public class ChatAdapter extends ArrayAdapter<Message> implements FileDownloaderListener {
    private LayoutInflater mLayoutInflater;
    private ChatAdapterListener mListener;
    private Context mContext;
    private ContactsCache mContacts;
    private MediaPlayer mMediaPlayer;
    private SharedPreferences mFilesPreferences;

    private Map<Integer, PhotoAudioDownloader> mDownloaders;

    public ChatAdapter(Context context, List<Message> objects, SharedPreferences filesPreferences) {
        super(context, R.layout.listview_item_message, objects);
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
        mContacts = ContactsCache.getInstance(context);
        mDownloaders = new HashMap<>();
        mFilesPreferences = filesPreferences;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        // We have 3 view types: texts, files and photoAudio
        final Message message = getItem(position);
        if (convertView == null) {
            if (message.getType() == Message.MESSAGE_TYPE_TEXT) {
                convertView = mLayoutInflater.inflate(R.layout.listview_item_message, parent, false);
            } else if (message.getType() == Message.MESSAGE_TYPE_FILE) {
                convertView = mLayoutInflater.inflate(R.layout.listview_item_file, parent, false);
            } else {
                convertView = mLayoutInflater.inflate(R.layout.listview_item_photo_audio, parent, false);
            }
        }

        if (message.getType() == Message.MESSAGE_TYPE_TEXT) {
            TextView txtMessage = (TextView) convertView.findViewById(R.id.txt_message);
            // For texts the message format is: #user: #message
            String messageContent = mContacts.get(message.getUsername()).getName() + ": " + message.getMessage();
            txtMessage.setText(messageContent);
        } else if (message.getType() == Message.MESSAGE_TYPE_FILE) {
            configureFileView(convertView, message);
        } else {
            final FileMessage fileMessage = (FileMessage) message;

            final PhotoAudioDownloader downloader = mDownloaders.get(position);
            if (downloader == null) {
                File outputFile = PhotoAudioParser.getPhotoAudioFile(mContext, fileMessage.getUuid());
                PhotoAudioDownloader newDownloader = new PhotoAudioDownloader(mContext, fileMessage.getUrl(), outputFile);
                newDownloader.setListener(this, position);
                newDownloader.execute();
                mDownloaders.put(position, newDownloader);
            } else if (downloader.getBitmap() != null) {
                ImageView imgPicture = (ImageView) convertView.findViewById(R.id.img_picture);
                imgPicture.setEnabled(true);
                imgPicture.setImageBitmap(downloader.getBitmap());

                imgPicture.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        playAudio(downloader.getAudioUri());
                    }
                });
            } else {
                ImageView imgPicture = (ImageView) convertView.findViewById(R.id.img_picture);
                imgPicture.setEnabled(false);
                imgPicture.setImageBitmap(null);
            }
        }

        return convertView;
    }

    private void configureFileView(View view, final Message message) {
        // For files we have to check whether the user is a sender or recipient. If he is a sender, he gets
        // a default message. Otherwise, he gets the option to download.
        final FileMessage fileMessage = (FileMessage) message;

        String messageContent;
        if (message.getUsername().equals(FirebaseModel.getUser(mContext))) {
            messageContent = mContext.getString(R.string.chat_uploaded_file, fileMessage.getFileName());
        } else {
            messageContent = mContext.getString(R.string.chat_received_file,
                    mContacts.get(message.getUsername()).getName(), fileMessage.getFileName(), fileMessage.getSize() / 1024);
        }

        // Buttons for download will be disabled if file has already been accepted or refused; or if the sender is
        // the current user
        final LinearLayout layout = (LinearLayout) view.findViewById(R.id.layout_buttons);
        if (fileMessage.getUsername().equals(FirebaseModel.getUser(mContext)) || mFilesPreferences.contains(fileMessage.getUuid())) {
            layout.setVisibility(View.GONE);
        }

        // Buttons for accepting or refusing download configuration
        // TODO: when refusing download, state is not saved and will be asked again when reentering room
        final Button btnOk = (Button) view.findViewById(R.id.btn_ok);
        final Button btnNo = (Button) view.findViewById(R.id.btn_no);
        btnOk.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onFileAccepted((FileMessage) message);
                }

                Editor editor = mFilesPreferences.edit();
                editor.putBoolean(fileMessage.getUuid(), true);
                editor.apply();

                layout.setVisibility(View.GONE);
            }
        });
        btnNo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onFileRefused((FileMessage) message);
                }

                Editor editor = mFilesPreferences.edit();
                editor.putBoolean(fileMessage.getUuid(), true);
                editor.apply();

                layout.setVisibility(View.GONE);
            }
        });

        TextView txtMessage = (TextView) view.findViewById(R.id.txt_message);
        txtMessage.setText(messageContent);
    }

    private void cleanupMediaPlayer() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
        }
    }

    /**
     * Plays back an audio file.
     */
    private void playAudio(Uri uri) {
        cleanupMediaPlayer();

        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(uri.getPath());
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        int type;
        if (message.getType() == Message.MESSAGE_TYPE_TEXT) {
            type = 0;
        } else if (message.getType() == Message.MESSAGE_TYPE_FILE) {
            type = 1;
        } else {
            type = 2;
        }
        return type;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    public void setListener(ChatAdapterListener listener) {
        mListener = listener;
    }

    @Override
    public void onFileDownloaded(FileDownloader downloader, Uri uri, int id) {
        notifyDataSetChanged();
    }

    public void cleanup() {
        for (PhotoAudioDownloader downloader : mDownloaders.values()) {
            downloader.setListener(null, 0);
            if (!downloader.cancel(true)) {
                downloader.cleanup();
            }
        }
    }

    public interface ChatAdapterListener {
        void onFileAccepted(FileMessage message);
        void onFileRefused(FileMessage message);
    }
}
