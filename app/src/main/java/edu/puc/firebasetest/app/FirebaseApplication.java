package edu.puc.firebasetest.app;

import android.app.Application;
import com.firebase.client.Firebase;

/**
 * Created by jose on 7/8/15.
 */
public class FirebaseApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Firebase.setAndroidContext(this);
        Firebase.getDefaultConfig().setPersistenceEnabled(true);
    }
}
