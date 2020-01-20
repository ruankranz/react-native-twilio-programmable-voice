package com.hoxfon.react.RNTwilioVoice;

import androidx.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class EventManager {

    private ReactApplicationContext mContext;

    static final String EVENT_CONNECTION_DID_CONNECT = "connectionDidConnect";
    static final String EVENT_CONNECTION_DID_DISCONNECT = "connectionDidDisconnect";
    static final String EVENT_INCOMING_CALL_INVITE = "incomingCallInvite";
    static final String EVENT_INCOMING_CALL_CANCELLED = "incomingCallCancelled";
    static final String EVENT_CONNECTION_IS_RINGING = "connectionIsRinging";
    static final String EVENT_CONNECTION_IS_RECONNECTING = "connectionIsReconnecting";
    static final String EVENT_CONNECTION_DID_RECONNECT = "connectionDidReconnect";
    static final String EVENT_REGISTERED_FOR_CALL_INVITES = "registeredForCallInvites";
    static final String EVENT_UNREGISTERED_FOR_CALL_INVITES = "unregisteredForCallInvites";
    static final String EVENT_REGISTRATION_ERROR = "registrationError";

    static final String EVENT_WIRED_HEADSET = "wiredHeadsetDetected";
    static final String EVENT_PROXIMITY = "proximityEvent";

    public EventManager(ReactApplicationContext context) {
        mContext = context;
    }

    public void sendEvent(String eventName, @Nullable WritableMap params) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "sendEvent "+eventName+" params "+params);
        }
        if (mContext.hasActiveCatalystInstance()) {
            mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "failed Catalyst instance not active");
            }
        }
    }
}
