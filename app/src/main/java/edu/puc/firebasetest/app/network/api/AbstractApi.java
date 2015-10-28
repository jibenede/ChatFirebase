package edu.puc.firebasetest.app.network.api;

import com.android.volley.Request;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.RequestFuture;

/**
 * Created by jose on 10/22/15.
 */
public abstract class AbstractApi<T> {
    private Listener<T> mListener;
    private ErrorListener mErrorListener;

    private String mUsername;
    private String mToken;

    AbstractApi() {
        mListener = new Listener<T>() {
            @Override
            public void onResponse(T t) { }
        };
        mErrorListener = new ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) { }
        };
    }

    public Listener<T> getListener() {
        return mListener;
    }

    public ErrorListener getErrorListener() {
        return mErrorListener;
    }

    public void setListener(Listener<T> listener) {
        mListener = listener;
    }

    public void setErrorListener(ErrorListener errorListener) {
        mErrorListener = errorListener;
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public void setToken(String token) {
        mToken = token;
    }

    public abstract Request buildRequest();

}
