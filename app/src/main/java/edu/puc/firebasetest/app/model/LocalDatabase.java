package edu.puc.firebasetest.app.model;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import edu.puc.firebasetest.app.model.entities.Entity;

/**
 * Created by jose on 10/22/15.
 */
public class LocalDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "memeticame.db";
    private static final int VERSION = 1;

    private static LocalDatabase sDatabase;

    public static LocalDatabase getDatabase(Context context) {
        // Not thread safe, but should be good enough
        if (sDatabase == null) {
            sDatabase = new LocalDatabase(context, DATABASE_NAME, null, VERSION);
        }
        return sDatabase;
    }

    private LocalDatabase(Context context, String name, CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLES.CONTACTS + " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                CONTACTS_COLUMNS.NAME + " TEXT, " +
                CONTACTS_COLUMNS.EMAIL + " TEXT, " +
                ");");
        db.execSQL("CREATE TABLE " + TABLES.ROOMS + " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ROOMS_COLUMNS.NAME + " TEXT, " +
                ROOMS_COLUMNS.AUTHOR + " TEXT, " +
                ROOMS_COLUMNS.TIMESTAMP + " INTEGER, " +
                ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void insert(Entity entity) {
        SQLiteDatabase db = getWritableDatabase();
        db.insert(entity.getTable(), null, entity.getContentValues());
    }

    public interface TABLES {
        String CONTACTS = "contacts";
        String ROOMS = "rooms";
    }

    public interface CONTACTS_COLUMNS {
        String NAME = "name";
        String EMAIL = "email";
    }

    public interface ROOMS_COLUMNS {
        String NAME = "name";
        String AUTHOR = "author";
        String TIMESTAMP = "timestamp";
        String KEY = "key";
    }
}
