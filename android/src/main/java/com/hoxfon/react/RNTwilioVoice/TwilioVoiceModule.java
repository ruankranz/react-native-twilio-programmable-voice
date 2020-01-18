package com.hoxfon.react.RNTwilioVoice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.LogLevel;
import com.twilio.voice.MessageListener;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import java.util.HashMap;
import java.util.Map;

import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_CONNECT;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_DISCONNECT;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_INCOMING_CALL_INVITE;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_INCOMING_CALL_CANCELLED;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_NOT_READY;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_READY;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_RECONNECT;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_IS_RECONNECTING;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_IS_RINGING;

public class TwilioVoiceModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

    public static String TAG = "RNTwilioVoice";
    private HashMap<String, String> twiMLParams = new HashMap<>();
    private String accessToken;
    private CallInvite activeCallInvite;
    private RegistrationListener registrationListener = registrationListener();
    private Call.Listener callListener = callListener();
    private Call activeCall;
    private EventManager eventManager;
    private AudioManager audioManager;
    private ProximityManager proximityManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;
    private HeadsetManager headsetManager;
    private SoundPoolManager soundPoolManager;

    public TwilioVoiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        if (BuildConfig.DEBUG) {
            Voice.setLogLevel(LogLevel.DEBUG);
        } else {
            Voice.setLogLevel(LogLevel.ERROR);
        }
        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);

        eventManager = new EventManager(reactContext);
        audioManager = (AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE);
        proximityManager = new ProximityManager(reactContext, eventManager);
        headsetManager = new HeadsetManager(eventManager);
        soundPoolManager = SoundPoolManager.getInstance(reactContext);

    }

    @Override
    public void onHostResume() {
        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        Activity currentActivity = getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }
    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        disconnect();
        cleanupVoiceServices();
    }

    @NonNull
    @Override
    public String getName() {
        return TAG;
    }

    public void onNewIntent(Intent intent) {
        // This is called only when the App is in the foreground
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onNewIntent " + intent.toString());
        }
    }


    @ReactMethod
    public void initWithAccessToken(final String accessToken, final String fcmToken, Promise promise) {
        if (accessToken.equals("")) {
            promise.reject(new JSApplicationIllegalArgumentException("Invalid access token"));
            return;
        }

        if (fcmToken.equals("")) {
            promise.reject(new JSApplicationIllegalArgumentException("Invalid FCM token"));
            return;
        }

        TwilioVoiceModule.this.accessToken = accessToken;
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "initWithAccessToken ACTION_FCM_TOKEN");
        }
        registerForCallInvites(fcmToken);
        WritableMap params = Arguments.createMap();
        params.putBoolean("initialized", true);
        promise.resolve(params);
    }

    @ReactMethod
    public void handleTwilioMessage(ReadableMap notification, final Promise promise) {

        try {

            Map<String, String> data = new HashMap<>();
            ReadableMapKeySetIterator iterator = notification.keySetIterator();
            while (iterator.hasNextKey()) {
                String key = iterator.nextKey();
                ReadableType readableType = notification.getType(key);
                switch (readableType) {
                    case Null:
                        data.put(key, "");
                        break;
                    case Boolean:
                        data.put(key, String.valueOf(notification.getBoolean(key)));
                        break;
                    case Number:
                        // Can be int or double.
                        data.put(key, String.valueOf(notification.getDouble(key)));
                        break;
                    case String:
                        data.put(key, notification.getString(key));
                        break;
                    default:
                        Log.d(TAG, "Could not convert with key: " + key + ".");
                        break;
                }
            }

            boolean valid = Voice.handleMessage(getReactApplicationContext(), data, new MessageListener() {

                @Override
                public void onCallInvite(@NonNull final CallInvite callInvite) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Received call invite: " + callInvite.getCallSid());
                    }

                    final WritableMap result = getEventParams(callInvite,"INCOMING_CALL_INVITE");
                    activeCallInvite = callInvite;
                    eventManager.sendEvent(EVENT_INCOMING_CALL_INVITE, result);
                    SoundPoolManager.getInstance(getReactApplicationContext()).playRinging();
                }

                @Override
                public void onCancelledCallInvite(@NonNull final CancelledCallInvite cancelledCallInvite, @Nullable CallException error) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Received cancelled invite");
                    }

                    final WritableMap result = getEventParams(cancelledCallInvite, error);
                    activeCallInvite = null;
                    eventManager.sendEvent(EVENT_INCOMING_CALL_CANCELLED, result);
                    SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
                    cleanupVoiceServices();
                }

            });

            if (!valid) {
                Log.d(TAG, "The message was not a valid Twilio Voice SDK payload: " + notification.toString());
            }

            promise.resolve(valid);

        } catch (Exception e) {
            Log.e(TAG, "Exception while processing Twilio message: " + notification.toString());
            promise.reject("Exception while processing Twilio message", notification.toString());
        }
    }


    @ReactMethod
    public void accept() {
        if (activeCallInvite != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "accept() activeCallInvite");
            }

            SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
            activeCallInvite.accept(getReactApplicationContext(), callListener);
        } else {
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, null);
        }
    }

    @ReactMethod
    public void reject() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "reject() activeCallInvite");
        }

        if (activeCallInvite != null) {
            SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
            WritableMap params = getEventParams(activeCallInvite, "REJECTED");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
            activeCallInvite.reject(getReactApplicationContext());
        }
    }

    @ReactMethod
    public void ignore() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "ignore() activeCallInvite");
        }

        if (activeCallInvite != null) {
            SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
            WritableMap params = getEventParams(activeCallInvite, "BUSY");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
            activeCallInvite.reject(getReactApplicationContext());
        }
    }

    @ReactMethod
    public void connect(ReadableMap params) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "connect params: " + params);
        }

        WritableMap errParams = Arguments.createMap();
        if (accessToken == null) {
            errParams.putString("error", "Invalid access token");
            eventManager.sendEvent(EVENT_DEVICE_NOT_READY, errParams);
            return;
        }
        if (params == null) {
            errParams.putString("error", "Invalid parameters");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, errParams);
            return;
        } else if (!params.hasKey("To")) {
            errParams.putString("error", "Invalid To parameter");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, errParams);
            return;
        }

        twiMLParams.clear();

        ReadableMapKeySetIterator iterator = params.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType readableType = params.getType(key);
            switch (readableType) {
                case Null:
                    twiMLParams.put(key, "");
                    break;
                case Boolean:
                    twiMLParams.put(key, String.valueOf(params.getBoolean(key)));
                    break;
                case Number:
                    // Can be int or double.
                    twiMLParams.put(key, String.valueOf(params.getDouble(key)));
                    break;
                case String:
                    twiMLParams.put(key, params.getString(key));
                    break;
                default:
                    Log.d(TAG, "Could not convert with key: " + key + ".");
                    break;
            }
        }

        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                .params(twiMLParams)
                .build();
        activeCall = Voice.connect(getReactApplicationContext(), connectOptions, callListener);
    }

    @ReactMethod
    public void disconnect() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "disconnect()");
        }
        if (activeCall != null) {
            soundPoolManager.playDisconnect();
            activeCall.disconnect();
            activeCall = null;
        }
        cleanupVoiceServices();
    }

    @ReactMethod
    public void setMuted(Boolean muteValue) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "setMuted() " + muteValue);
        }
        if (activeCall != null) {
            activeCall.mute(muteValue);
        }
        audioManager.setMicrophoneMute(muteValue);
    }

    @ReactMethod
    public void sendDigits(String digits) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "sendDigits() "+ digits);
        }
        if (activeCall != null) {
            activeCall.sendDigits(digits);
        }
    }

    @ReactMethod
    public void getActiveCallData(Promise promise) {
        if (activeCall != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Active call found state = " + activeCall.getState());
            }
            WritableMap params = getEventParams(activeCall);
            promise.resolve(params);
            return;
        }
        promise.resolve(null);
    }

    @ReactMethod
    public void setSpeakerPhone(Boolean value) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "setSpeakerPhone() " + value);
        }
        audioManager.setSpeakerphoneOn(value);
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Successfully registered FCM");
                }
                eventManager.sendEvent(EVENT_DEVICE_READY, null);
            }

            @Override
            public void onError(@NonNull RegistrationException error, @NonNull String accessToken, @NonNull String fcmToken) {
                Log.e(TAG, String.format("Registration Error: %d, %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                params.putString("error", error.getMessage());
                eventManager.sendEvent(EVENT_DEVICE_NOT_READY, params);
            }
        };
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            /*
             * This callback is emitted once before the Call.Listener.onConnected() callback when
             * the callee is being alerted of a Call. The behavior of this callback is determined by
             * the answerOnBridge flag provided in the Dial verb of your TwiML application
             * associated with this client. If the answerOnBridge flag is false, which is the
             * default, the Call.Listener.onConnected() callback will be emitted immediately after
             * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
             * call to emit the onConnected callback only after the call is answered.
             * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
             * twiML response contains a Say verb, then the call will emit the
             * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
             * raised, irrespective of the value of answerOnBridge being set to true or false
             */
            @Override
            public void onRinging(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Ringing");
                }

                WritableMap params = getEventParams(call);
                eventManager.sendEvent(EVENT_CONNECTION_IS_RINGING, params);
            }

            @Override
            public void onConnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL CONNECTED callListener().onConnected call state = " + call.getState());
                }

                startVoiceServices();
                WritableMap params = getEventParams(call);
                activeCall = call;
                eventManager.sendEvent(EVENT_CONNECTION_DID_CONNECT, params);
            }

            @Override
            public void onDisconnected(@NonNull Call call, @Nullable CallException error) {

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "call disconnected");
                }

                if (error != null) {
                    Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                            error.getErrorCode(), error.getMessage()));
                }

                cleanupVoiceServices();
                WritableMap params = getEventParams(call, error);
                activeCall = null;
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
            }

            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                        error.getErrorCode(), error.getMessage()));

                cleanupVoiceServices();
                WritableMap params = getEventParams(call, error);
                activeCall = null;
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException error) {
                Log.e(TAG, String.format("CallListener onReconnecting error: %d, %s",
                        error.getErrorCode(), error.getMessage()));

                cleanupVoiceServices();
                WritableMap params = getEventParams(call, error);
                eventManager.sendEvent(EVENT_CONNECTION_IS_RECONNECTING, params);
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Reconnected");
                }

                startVoiceServices();

                WritableMap params = getEventParams(call);
                activeCall = call;
                eventManager.sendEvent(EVENT_CONNECTION_DID_RECONNECT, params);
            }
        };
    }

    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }


    private void registerForCallInvites(@NonNull String fcmToken) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Registering with FCM");
        }

        Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
    }


    private void cleanupVoiceServices() {
        setAudioFocus(false);
        soundPoolManager.release();
        proximityManager.stopProximitySensor();
        headsetManager.stopWiredHeadsetEvent(getReactApplicationContext());
    }

    private void startVoiceServices() {
        setAudioFocus(true);
        proximityManager.startProximitySensor();
        headsetManager.startWiredHeadsetEvent(getReactApplicationContext());
    }

    private WritableMap getEventParams(@NonNull CancelledCallInvite cancelledCallInvite, @Nullable CallException error) {
        WritableMap params = Arguments.createMap();
        params.putString("call_sid", cancelledCallInvite.getCallSid());
        params.putString("call_from", cancelledCallInvite.getFrom());
        params.putString("call_to", cancelledCallInvite.getTo());
        params.putString("call_state", "CANCELLED_CALL_INVITE");
        if (error != null) {
            params.putString("error", error.getMessage());
            params.putString("error_explanation", error.getExplanation());
            params.putInt("error_code", error.getErrorCode());
        }
        return params;
    }

    private WritableMap getEventParams(@NonNull Call call, @Nullable CallException error) {
        WritableMap params = Arguments.createMap();
        params.putString("call_sid", call.getSid());
        params.putString("call_state", call.getState().name());
        params.putString("call_from", call.getFrom());
        params.putString("call_to", call.getTo());

        if (error != null) {
            params.putString("error", error.getMessage());
            params.putString("error_explanation", error.getExplanation());
            params.putInt("error_code", error.getErrorCode());
        }

        return params;
    }

    private WritableMap getEventParams(@NonNull Call call) {
        WritableMap params = Arguments.createMap();
        params.putString("call_sid", call.getSid());
        params.putString("call_state", call.getState().name());
        params.putString("call_from", call.getFrom());
        params.putString("call_to", call.getTo());

        return params;
    }

    private WritableMap getEventParams(@NonNull CallInvite callInvite, String callState) {
        WritableMap params = Arguments.createMap();
        params.putString("call_sid", callInvite.getCallSid());
        params.putString("call_from", callInvite.getFrom());
        params.putString("call_to", callInvite.getTo());
        params.putString("call_state", callState);
        return params;
    }


    private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                                @Override
                                public void onAudioFocusChange(int i) {
                                }
                            })
                            .build();
                    audioManager.requestAudioFocus(focusRequest);
                } else {
                    int focusRequestResult = audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {

                        @Override
                        public void onAudioFocusChange(int focusChange) { }

                        }, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }

                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }
}
