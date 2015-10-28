package edu.puc.firebasetest.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import edu.puc.firebasetest.app.utils.PhotoAudioParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Activity for managing the creation and visualization of PhotoAudio files.
 *
 * PhotoAudio files are saved in files with the custom extension ".ptad". These files are actually zips that contain
 * an audio file in .3gpp format and an image file in .png format.
 *
 * If invoked from the chat application, this activity allows the creation of a PhotoAudio file by specifying a
 * picture taken from the camera and an audio recorded from the microphone. If invoked from a file explorer,
 * this activity allows the opening of .ptad files and shows its associated image and audio in read-only mode.
 */
public class PhotoAudioActivity extends Activity {
    private static final String TAG = "PhotoAudio";
    private static final int ACTIVITY_RECORD_SOUND = 1;
    private static final int ACTIVITY_TAKE_PICTURE = 2;

    public static final String KEY_UUID = "uuid";

    private boolean mAudioReady;
    private boolean mPictureReady;

    private Uri mAudioUri;
    private Bitmap mPictureBitmap;

    private ImageButton mBtnPlay;
    private Button mBtnAudio;
    private ImageView mImgPicture;
    private Button mBtnProceed;

    private MediaPlayer mMediaPlayer;

    private boolean mReadMode;
    private UUID mUUID;

    public static Intent getIntent(Context context) {
        Intent intent = new Intent(context, PhotoAudioActivity.class);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_audio);

        mBtnPlay = (ImageButton) findViewById(R.id.btn_play);
        mBtnPlay.setEnabled(false);
        mBtnAudio = (Button) findViewById(R.id.btn_audio);
        mImgPicture = (ImageView) findViewById(R.id.img_picture);
        mBtnProceed = (Button) findViewById(R.id.btn_ok);

        Uri data = getIntent().getData();
        mBtnPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
            }
        });
        mBtnAudio.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                recordAudio();
            }
        });
        mImgPicture.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        mBtnProceed.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri = createPhotoAudio();
                Intent result = new Intent();
                result.setData(uri);
                result.putExtra(KEY_UUID, mUUID.toString());
                setResult(RESULT_OK, result);
                finish();
            }
        });

        // The data value is not null only when the intent carries a reference to a file to be opened. This should
        // only happen when invoked from a file explorer to open a .ptad file.
        if (data != null) {
            readZip(data);
            mImgPicture.setImageBitmap(mPictureBitmap);
            mImgPicture.setScaleType(ScaleType.FIT_CENTER);
            mImgPicture.setEnabled(false);
            mBtnAudio.setEnabled(false);

            mBtnPlay.setEnabled(true);
            mReadMode = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        cleanupMediaPlayer();
    }

    /**
     * Parses a .ptad file and sets this class' attributes in order to allow access to the file's audio and picture.
     *
     * @param uri
     */
    private void readZip(Uri uri) {
        PhotoAudioParser parser = new PhotoAudioParser(this, uri);
        parser.prepare();

        mAudioUri = parser.getAudioUri();
        mPictureBitmap = BitmapFactory.decodeFile(parser.getBitmapUri().getPath());
    }

    /**
     * Start the device's default audio recorder.
     */
    private void recordAudio() {
        cleanupMediaPlayer();

        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        startActivityForResult(intent, ACTIVITY_RECORD_SOUND);
    }

    /**
     * Cleans up resources attached to the local media player object.
     */
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
    private void playAudio() {
        cleanupMediaPlayer();

        try {
            mMediaPlayer = new MediaPlayer();
            if (mReadMode) {
                mMediaPlayer.setDataSource(mAudioUri.getPath());
            } else {
                mMediaPlayer.setDataSource(getApplicationContext(), mAudioUri);
            }
            mMediaPlayer.prepare();
            mMediaPlayer.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Takes a picture using the device's default camera application.
     */
    private void takePicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, ACTIVITY_TAKE_PICTURE);
        }
    }

    /**
     * Saves a photo audio as a custom file with extension .ptad. PhotoAudios are stored as a zip with 2 entries:
     * one containing the bitmap and another containing the audio. The resulting file is saved in a public directory.
     */
    private Uri createPhotoAudio() {
        mUUID = UUID.randomUUID();
        File file = PhotoAudioParser.getPhotoAudioFile(this, mUUID.toString());
        try {
            ZipOutputStream stream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            ZipEntry audioEntry = new ZipEntry(PhotoAudioParser.ZIP_AUDIO);
            stream.putNextEntry(audioEntry);

            byte[] buffer = new byte[1024];
            FileInputStream fis = new FileInputStream(getAudioFilePathFromUri(mAudioUri));
            int length;
            while((length = fis.read(buffer)) != -1) {
                stream.write(buffer, 0, length);
            }
            fis.close();

            ZipEntry photoEntry = new ZipEntry(PhotoAudioParser.ZIP_PHOTO);
            stream.putNextEntry(photoEntry);
            ByteArrayOutputStream pictureStream = new ByteArrayOutputStream();
            mPictureBitmap.compress(Bitmap.CompressFormat.PNG, 100, pictureStream);
            stream.write(pictureStream.toByteArray());

            stream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return Uri.fromFile(file);
    }

    private String getAudioFilePathFromUri(Uri uri) {
        Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null);
        cursor.moveToFirst();
        int index = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATA);
        String result = cursor.getString(index);
        cursor.close();
        return  result;
    }

    /**
     * Handles the results from the audio recorder application and camera application.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_RECORD_SOUND) {
            if (resultCode == Activity.RESULT_OK) {
                mAudioUri = data.getData();
                mAudioReady = true;
                mBtnPlay.setEnabled(true);
            } else {
                mAudioUri = null;
                mAudioReady = false;
                mBtnPlay.setEnabled(false);
            }
        } else if (requestCode == ACTIVITY_TAKE_PICTURE) {
            if (resultCode == Activity.RESULT_OK) {
                // Applications should use the full sized photo saved in the filesystem, however for this demo, and
                // in order to save space, we will only use the thumbnail.
                Bundle extras = data.getExtras();
                mPictureBitmap = (Bitmap) extras.get("data");

                //mPictureUri = data.getData();
                mPictureReady = true;

                mImgPicture.setImageBitmap(mPictureBitmap);
                mImgPicture.setScaleType(ScaleType.FIT_CENTER);
            } else {
                mPictureBitmap = null;
                mPictureReady = false;
                mImgPicture.setImageResource(R.drawable.icon_camera_picture);
                mImgPicture.setScaleType(ScaleType.CENTER);
            }
        }

        mBtnProceed.setEnabled(mPictureReady && mAudioReady);
    }

}
