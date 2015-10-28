package edu.puc.firebasetest.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;

/**
 * Created by jose on 10/26/15.
 */
public class PhotoAudioDownloader extends FileDownloader {
    private PhotoAudioParser mParser;
    private Bitmap mBitmap;

    public PhotoAudioDownloader(Context context, String url, File outputFile) {
        super(context, url, outputFile);
    }

    @Override
    protected Void doInBackground(Void... params) {
        super.doInBackground(params);
        if (!isCancelled()) {
            Uri uri = Uri.fromFile(mOutputFile);
            mParser = new PhotoAudioParser(mContext, uri);
            mParser.prepare();

            mBitmap = BitmapFactory.decodeFile(mParser.getBitmapUri().getPath());

            if (isCancelled()) {
                mParser.cleanup();
            }
        }

        return null;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public Uri getAudioUri() {
        return mParser.getAudioUri();
    }

    public void cleanup() {
        if (mParser != null) {
            mParser.cleanup();
        }
    }
}
