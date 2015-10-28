package edu.puc.firebasetest.app.model.entities;

import android.content.ContentValues;

/**
 * Created by jose on 10/22/15.
 */
public interface Entity {
    String getTable();
    ContentValues getContentValues();
}
