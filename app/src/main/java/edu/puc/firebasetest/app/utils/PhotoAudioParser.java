package edu.puc.firebasetest.app.utils;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import edu.puc.firebasetest.app.PhotoAudioActivity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by jose on 10/26/15.
 */
public class PhotoAudioParser {
    public static final String ZIP_PHOTO = "photo.png";
    public static final String ZIP_AUDIO = "audio.3gpp";

    private static final String TEMP_PHOTO = "photo%d.png";
    private static final String TEMP_AUDIO = "audio%d.3gpp";

    private static AtomicInteger sCounter = new AtomicInteger(0);

    public static final String PHOTO_AUDIO_EXTENSION = ".ptad";
    public static final String MEMETICAME_FOLDER = "Memeticame";

    private Context mContext;
    private Uri mUri;

    private Uri mBitmapUri;
    private Uri mAudioUri;

    private File mTempFile1;
    private File mTempFile2;

    public static File getPhotoAudioFile(Context context, String uuid) {
        // In some devices this may return null and crash. An option should be given to store in internal memory
        // on disable this functionality if no external memory exists.
        // TODO: fix this
        File folder = context.getExternalFilesDir(MEMETICAME_FOLDER);
        File outputFile = new File(folder, "PhotoAudio_" + uuid + PHOTO_AUDIO_EXTENSION);
        return outputFile;
    }

    public PhotoAudioParser(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
    }

    public void prepare() {
        try {
            InputStream is = new FileInputStream(mUri.getPath());
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));

            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, count);
                }
                String filename = ze.getName();
                byte[] bytes = baos.toByteArray();

                if (filename.equals(ZIP_PHOTO)) {
                    mTempFile1 = new File(mContext.getCacheDir(), String.format(TEMP_PHOTO, sCounter.addAndGet(1)));
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mTempFile1));
                    bos.write(bytes);
                    bos.close();

                    mBitmapUri = Uri.fromFile(mTempFile1);
                } else if (filename.equals(ZIP_AUDIO)) {
                    mTempFile2 = new File(mContext.getCacheDir(), String.format(TEMP_AUDIO, sCounter.addAndGet(1)));
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mTempFile2));
                    bos.write(bytes);
                    bos.close();

                    mAudioUri = Uri.fromFile(mTempFile2);
                }
            }
            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Uri getAudioUri() {
        return mAudioUri;
    }

    public Uri getBitmapUri() {
        return mBitmapUri;
    }

    /**
     * Deletes the temporary files that store audio and bitmaps.
     */
    public void cleanup() {
        mTempFile1.delete();
        mTempFile2.delete();
    }


}
