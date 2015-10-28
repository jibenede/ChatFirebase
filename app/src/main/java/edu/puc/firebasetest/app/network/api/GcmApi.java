package edu.puc.firebasetest.app.network.api;

import android.content.Context;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Request.Method;
import edu.puc.firebasetest.app.R;
import edu.puc.firebasetest.app.network.NetworkManager;
import edu.puc.firebasetest.app.network.request.GcmRequest;
import edu.puc.firebasetest.app.network.HeaderRequest;
import edu.puc.firebasetest.app.network.request.GcmRequest.GcmMessage;


/**
 * An abstraction of GCM SEND API.
 */
public class GcmApi extends AbstractApi<NetworkResponse> {
    private static final String GCM_URL = "https://gcm-http.googleapis.com/gcm/send";

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final String HEADER_VALUE_JSON = "application/json";

    private Context mContext;
    private GcmRequest mGcmRequest;

    public GcmApi(Context context, String target, GcmMessage data) {
        mGcmRequest = new GcmRequest(target, data);
        mContext = context;
    }

    @Override
    public Request buildRequest() {
        HeaderRequest request = new HeaderRequest(Method.POST, GCM_URL, mGcmRequest.toJson(), getListener(), getErrorListener());
        request.addHeader(HEADER_AUTHORIZATION, "key=" + mContext.getString(R.string.gcm_server_api));
        request.addHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_JSON);

        return request;
    }
}
