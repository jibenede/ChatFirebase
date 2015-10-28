package edu.puc.firebasetest.app.network;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;
import java.util.Map;

/**
 * A modified Volley request to support header customization.
 */
public class HeaderRequest extends JsonRequest<NetworkResponse> {
    private Map<String, String> mHeaders;

    public HeaderRequest(int method, String url, String requestBody, Listener<NetworkResponse> listener, ErrorListener errorListener) {
        super(method, url, requestBody, listener, errorListener);
        mHeaders = new HashMap<>();
    }

    @Override
    protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
        return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
    }

    public void addHeader(String key, String value) {
        mHeaders.put(key, value);
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return mHeaders;
    }
}
