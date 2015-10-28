package edu.puc.firebasetest.app.utils;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import edu.puc.firebasetest.app.PhotoAudioActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * Task that manages the download of a web url in a background task.
 *
 * This task requires the specification of a local file to output the contents of the download. If the specified
 * file already exists, the download will be aborted and said file will be considered as holding the contents of
 * the url.
 */
public class FileDownloader extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "File Downloader";
    private String mUrl;
    protected Context mContext;
    private FileDownloaderListener mListener;
    private int mId;

    protected File mOutputFile;

    public FileDownloader(Context context, String url, File outputFile) {
        mContext = context;
        mUrl = url;
        mOutputFile = outputFile;
    }

    public void setListener(FileDownloaderListener listener, int id) {
        mId = id;
        mListener = listener;
    }

    @Override
    protected Void doInBackground(Void... params) {
        // We skip the download if the file already exists.
        if (mOutputFile.exists()) {
            return null;
        }

        if (!mOutputFile.getParentFile().exists()) {
            mOutputFile.mkdirs();
        }

        boolean error = true;

        // We retry the download until it succeeds.
        // TODO: implement exponential backoff
        while(error && !isCancelled()) {
            error = false;

            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(mUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.i(TAG,"Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage());
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(mOutputFile.getPath());

                byte data[] = new byte[4096];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                e.printStackTrace();
                error = true;
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException e) {
                }

                if (connection != null)
                    connection.disconnect();
            }
        }


        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (mListener != null) {
            mListener.onFileDownloaded(this, Uri.fromFile(mOutputFile), mId);
        }
    }

    public interface FileDownloaderListener {
        void onFileDownloaded(FileDownloader downloader, Uri uri, int id);
    }
}

