package edu.puc.firebasetest.app.services;


import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * Created by jose on 7/13/15.
 */
public class GcmIdListenerService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();

        startService(ChatService.getIntent(this, ChatService.ACTION_GCM_REFRESH_TOKEN));
    }
}
