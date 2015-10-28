package edu.puc.firebasetest.app.tasks;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.MimeTypeMap;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.drive.DriveApi;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Permission;
import edu.puc.firebasetest.app.ChatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A background task for handling the expensive operation of uploading files to Google Drive.
 */
public class DriveTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "DriveTask";
    private static final String DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String DRIVE_FOLDER = "Memeticame";

    private Activity mActivity;
    private Drive mDriveService;
    private Exception mLastError;
    private Uri mUri;
    private int mFileType;
    private String mUuid;

    private GoogleAccountCredential mCredential;
    private FileSentListener mListener;

    public DriveTask(Activity activity, GoogleAccountCredential credential, Uri uri, int fileType) {
        this(activity, credential, uri, fileType, null);
    }

    public DriveTask(Activity activity, GoogleAccountCredential credential, Uri uri, int fileType, String uuid) {
        mActivity = activity;
        mCredential = credential;
        mUri = uri;
        mFileType = fileType;
        mUuid = uuid;

        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mDriveService = new com.google.api.services.drive.Drive.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("Memeticame")
                .build();
    }

    public void setListener(FileSentListener listener) {
        mListener = listener;
    }

    @Override
    protected Void doInBackground(Void... params) {
        java.io.File localFile = new java.io.File(mUri.getPath());
        // Will crash if file has no extension
        // TODO: fix this
        String extension = mUri.getPath().substring(mUri.getPath().lastIndexOf(".") + 1);
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);

        try {
            // Step 1: we retrieve the folder where we will store our files. If it does not exist, we create it.
            String parentFolderId;

            Files.List request = mDriveService.files().list().setQ(
                    "mimeType='application/vnd.google-apps.folder' and trashed=false and title='" + DRIVE_FOLDER + "' and 'root' in parents");
            FileList files = request.execute();
            List<File> folders = files.getItems();
            if (folders.size() >= 1) {
                parentFolderId = folders.get(0).getId();
            } else {
                File folder = new File();
                folder.setTitle(DRIVE_FOLDER);
                folder.setMimeType(DRIVE_FOLDER_MIME_TYPE);
                File uploadedFolder = mDriveService.files().insert(folder).execute();
                parentFolderId = uploadedFolder.getId();
            }

            // Step 2: We create an empty file with the appropriate metadata.

            File driveFile = new File();
            driveFile.setTitle(localFile.getName());
            driveFile.setMimeType(mimeType);
            driveFile.setDescription("Memeticame file");

            ParentReference reference = new ParentReference();
            reference.setId(parentFolderId);
            List<ParentReference> references = new ArrayList<>();
            references.add(reference);
            driveFile.setParents(references);

            // Step 3: We upload the content.

            FileContent fileContent = new FileContent(mimeType, localFile);
            File uploadedFile = mDriveService.files().insert(driveFile, fileContent).execute();

            // Step 4: We set permissions so anyone can read it. That way we can share the link.

            Permission permission = new Permission();
            permission.setRole("reader");
            permission.setType("anyone");
            permission.setValue("");

            mDriveService.permissions().insert(uploadedFile.getId(), permission).execute();

            // Step 5: We retrieve the link.

            String fileUrl = uploadedFile.getWebContentLink();

            Log.i(TAG, "Uploaded file to: " + fileUrl);
            if (mListener != null) {
                mListener.onFileSent(this, mUri, fileUrl, mFileType, mUuid);
            }

        } catch (Exception e) {
            e.printStackTrace();
            mLastError = e;
            cancel(true);
            if (mListener != null) {
                mListener.onFileSentError();
            }
        }

        return null;
    }



    @Override
    protected void onCancelled() {
        if (mLastError != null) {
            if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                showGooglePlayServicesAvailabilityErrorDialog(
                        ((GooglePlayServicesAvailabilityIOException) mLastError)
                                .getConnectionStatusCode());
            } else if (mLastError instanceof UserRecoverableAuthIOException) {
                mActivity.startActivityForResult(
                        ((UserRecoverableAuthIOException) mLastError).getIntent(),
                        ChatActivity.DRIVE_AUTHORIZATION_CODE);
            } else {
                Log.i(TAG, "The following error occurred:\n"
                        + mLastError.getMessage());
            }
        } else {
            Log.i(TAG, "Request cancelled.");
        }
    }

    private void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                connectionStatusCode,
                mActivity,
                ChatActivity.GOOGLE_PLAY_SERVICES_CODE);
        dialog.show();
    }

    public interface FileSentListener {
        void onFileSent(DriveTask task, Uri uri, String webUrl, int fileType, String uuid);
        void onFileSentError();
    }
}
