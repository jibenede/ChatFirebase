package edu.puc.firebasetest.app.network;

import android.content.Context;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import edu.puc.firebasetest.app.network.api.AbstractApi;

/**
 * A singleton that holds the Volley request queue.
 */
public class NetworkManager {
    private static NetworkManager sNetworkManager;

    private RequestQueue mRequestQueue;

    public static NetworkManager getNetworkManager(Context context) {
        if (sNetworkManager == null) {
            sNetworkManager = new NetworkManager(context);
        }
        return sNetworkManager;
    }

    private NetworkManager(Context context) {
        mRequestQueue = Volley.newRequestQueue(context);
    }

    public void executeRequest(AbstractApi api) {
        mRequestQueue.add(api.buildRequest());
    }

}
