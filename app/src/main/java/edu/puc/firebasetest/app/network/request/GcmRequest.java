package edu.puc.firebasetest.app.network.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A representation of an http request body compatible with the GCM API.
 */
public class GcmRequest {
    public static final String KEY_TYPE = "gcm_message_type";

    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_ROOM = 1;

    private String mTarget;
    private GcmMessage mData;

    public GcmRequest(String target, GcmMessage data) {
        mTarget = target;
        mData = data;
    }

    @JsonProperty("to")
    public String getTarget() {
        return mTarget;
    }

    @JsonProperty("data")
    public GcmMessage getData() {
        return mData;
    }

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public interface GcmMessage {
        int getGcmMessageType();
    }


}
