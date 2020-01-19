import {
    NativeModules,
    NativeEventEmitter,
    Platform,
} from 'react-native'

const ANDROID = 'android'
const IOS = 'ios'

const TwilioVoice = NativeModules.RNTwilioVoice

const NativeAppEventEmitter = new NativeEventEmitter(TwilioVoice)

const _eventHandlers = {
    registeredForCallInvites: new Map(),
    unregisteredForCallInvites: new Map(),
    registrationError: new Map(),
    incomingCallInvite: new Map(),
    connectionDidConnect: new Map(),
    connectionDidDisconnect: new Map(),
    connectionIsRinging: new Map(),
    connectionIsReconnecting: new Map(),
    connectionDidReconnect: new Map(),
    // iOS specific
    callRejected: new Map(),
    // Android specific
    incomingCallCancelled: new Map(),
}

const Twilio = {
    // initialize the library with Twilio access token
    // return {initialized: true} when the initialization started
    // Listen to deviceReady and deviceNotReady events to see whether
    // the initialization succeeded
    registerForCallInvites(voipToken, pushToken = null) {
        if (typeof voipToken !== 'string') {
            return {
                initialized: false,
                err:         'Invalid token, token must be a string'
            }
        };

        if (typeof pushToken !== 'string') {
            return {
                initialized: false,
                err:         'Invalid push token must be a string'
            }
        };

        TwilioVoice.registerForCallInvites(voipToken, pushToken)
    },
    unregisterForCallInvites(voipToken, pushToken = null) {
        if (typeof voipToken !== 'string') {
            return {
                initialized: false,
                err:         'Invalid token, token must be a string'
            }
        };

        if (typeof pushToken !== 'string') {
            return {
                initialized: false,
                err:         'Invalid push token must be a string'
            }
        };

        TwilioVoice.unregisterForCallInvites(voipToken, pushToken)
    },
    initWithTokenUrl(url) {
        if (Platform.OS === IOS) {
            TwilioVoice.initWithAccessTokenUrl(url)
        }
    },
    async handleTwilioMessage(params = {}) {
        const result = await TwilioVoice.handleTwilioMessage(params)
        return result
    },
    connect(params = {}), accessToken {
        TwilioVoice.connect(params, accessToken)
    },
    disconnect() {
        TwilioVoice.disconnect()
    },
    accept() {
        if (Platform.OS === IOS) {
            return
        }
        TwilioVoice.accept()
    },
    reject() {
        if (Platform.OS === IOS) {
            return
        }
        TwilioVoice.reject()
    },
    ignore() {
        if (Platform.OS === IOS) {
            return
        }
        TwilioVoice.ignore()
    },
    setMuted(isMuted) {
        TwilioVoice.setMuted(isMuted)
    },
    setSpeakerPhone(value) {
        TwilioVoice.setSpeakerPhone(value)
    },
    sendDigits(digits) {
        TwilioVoice.sendDigits(digits)
    },
    requestPermissions(senderId) {
        if (Platform.OS === ANDROID) {
            TwilioVoice.requestPermissions(senderId)
        }
    },
    getActiveCall() {
        return TwilioVoice.getActiveCallData()
    },
    configureCallKit(params = {}) {
        if (Platform.OS === IOS) {
            TwilioVoice.configureCallKit(params)
        }
    },
    unregister() {
        if (Platform.OS === IOS) {
            TwilioVoice.unregister()
        }
    },
    addEventListener(type, handler) {
        if (_eventHandlers[type].has(handler)) {
            return
        }
        _eventHandlers[type].set(handler, NativeAppEventEmitter.addListener(type, rtn => { handler(rtn) }))
    },
    removeEventListener(type, handler) {
        if (!_eventHandlers[type].has(handler)) {
            return
        }
        _eventHandlers[type].get(handler).remove()
        _eventHandlers[type].delete(handler)
    }
}

export default Twilio
