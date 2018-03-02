package com.qiscus.rtc.engine.hub;

import android.os.Handler;
import android.util.Log;

import com.qiscus.rtc.QiscusRTC;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

/**
 * Created by fitra on 22/11/17.
 */

public class WebSocketChannel {
    private static final String TAG = WebSocketChannel.class.getSimpleName();

    private final WebSocketChannelEvents events;
    private final Handler handler;
    private final LinkedList<String> wsSendQueue;
    private final Object closeEventLock = new Object();

    private WebSocketConnection ws;
    private WebSocketObserver wsObserver;
    private WebSocketConnectionState state;
    private boolean closeEvent;
    private boolean pendingSendAccept;
    private String room_id;
    private String client_id;
    private String target_id;

    public interface WebSocketChannelEvents {
        void onWebSocketOpen();
        void onWebSocketMessage(final String message);
        void onWebSocketClose();
        void onWebSocketError(final String description);
    }

    public enum WebSocketConnectionState {
        NEW, CONNECTED, REGISTERED, LOGGEDIN, CLOSED, ERROR
    }

    public WebSocketChannel(Handler handler, WebSocketChannelEvents events) {
        this.handler = handler;
        this.events = events;
        room_id = null;
        client_id = null;
        wsSendQueue = new LinkedList<String>();
        state = WebSocketConnectionState.NEW;
    }

    public WebSocketConnectionState getState() {
        return state;
    }

    public void setState(WebSocketConnectionState state) {
        this.state = state;
    }

    public void setTargetId(String target_id) {
        this.target_id = target_id;
    }

    public boolean getPendingSendAccept() {
        return pendingSendAccept;
    }

    public void connect() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.NEW) {
            Log.e(TAG, "WebSocket is already connected");
            return;
        }

        closeEvent = false;

        Log.d(TAG, "Connecting WebSocket to: " + QiscusRTC.getHost() );

        ws = new WebSocketConnection();
        wsObserver = new WebSocketObserver();

        try {
            ws.connect(new URI(QiscusRTC.getHost()), wsObserver);
        } catch (URISyntaxException e) {
            reportError("URI error: " + e.getMessage());
        } catch (WebSocketException e) {
            reportError("WebSocket connection error: " + e.getMessage());
        }
    }

    public void register(final String client_id) {
        checkIfCalledOnValidThread();

        this.client_id = client_id;

        if (state != WebSocketConnectionState.CONNECTED) {
            Log.w(TAG, "WebSocket register in state " + state);
            return;
        }

        Log.d(TAG, "Registering WebSocket for client  " + client_id);

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "register");
            data.put("app_id", QiscusRTC.getAppId());
            data.put("app_secret", QiscusRTC.getAppSecret());
            data.put("username", client_id);
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());

            // Send any previously accumulated messages.
            for (String sendMessage : wsSendQueue) {
                send(sendMessage);
            }

            wsSendQueue.clear();
        } catch (JSONException e) {
            reportError("WebSocket register error: " + e.getMessage());
        }
    }

    public void createRoom(String roomId, String token) {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.REGISTERED) {
            Log.e(TAG, "WebSocket create room in state " + state);
            return;
        }

        this.room_id = roomId;

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_create");
            object.put("room", room_id);
            data.put("max_participant", 2);
            data.put("token", token);
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket create room error: " + e.getMessage());
        }
    }

    public void joinRoom(String roomId, String token) {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.REGISTERED) {
            Log.e(TAG, "WebSocket join room in state " + state);
            return;
        }

        this.room_id = roomId;

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_join");
            object.put("room", room_id);
            data.put("token", token);
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket join room error: " + e.getMessage());
        }
    }

    public void sync() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket sync call in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_data");
            object.put("room", room_id);
            object.put("recipient", target_id);
            data.put("event", "call_sync");
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket sync call error: " + e.getMessage());
        }
    }

    public void ack() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket ack call in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_data");
            object.put("room", room_id);
            object.put("recipient", target_id);
            data.put("event", "call_ack");
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket ack call error: " + e.getMessage());
        }
    }

    public void acceptCall() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket accept call in state " + state + ". Waiting for logged in");
            pendingSendAccept = true;
            return;
        } else {
            pendingSendAccept = false;

            try {
                JSONObject object = new JSONObject();
                JSONObject data = new JSONObject();
                object.put("request", "room_data");
                object.put("room", room_id);
                object.put("recipient", target_id);
                data.put("event", "call_accept");
                object.put("data", data.toString());

                Log.d(TAG, "C->WSS: " + object.toString());

                ws.sendTextMessage(object.toString());
            } catch (JSONException e) {
                reportError("WebSocket accept call error: " + e.getMessage());
            }
        }
    }

    public void rejectCall() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket reject call in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_data");
            object.put("room", room_id);
            object.put("recipient", target_id);
            data.put("event", "call_reject");
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket reject call error: " + e.getMessage());
        }
    }

    public void cancelCall() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket reject call in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_data");
            object.put("room", room_id);
            object.put("recipient", target_id);
            data.put("event", "call_cancel");
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket cancel call error: " + e.getMessage());
        }
    }

    public void endCall() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket end call in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            object.put("request", "room_leave");
            object.put("room", room_id);

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket end call error: " + e.getMessage());
        }
    }

    public void notifyConnect() {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket notify connect in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_notify");
            object.put("room", room_id);
            data.put("event", "notify_connect");
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket notify connect error: " + e.getMessage());
        }
    }

    public void notifyState(final String st, final String v) {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket notify state in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_notify");
            object.put("room", room_id);
            data.put("event", "notify_" + st);
            data.put("message", v);
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket notify state error: " + e.getMessage());
        }
    }

    public void sendOffer(SessionDescription sdp) {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket send offer in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_data");
            object.put("room", room_id);
            object.put("recipient", target_id);
            data.put("type", "offer");
            data.put("sdp", sdp.description);
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket send offer error: " + e.getMessage());
        }
    }

    public void sendAnswer(SessionDescription sdp) {
        checkIfCalledOnValidThread();

        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.e(TAG, "WebSocket send answer in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_data");
            object.put("room", room_id);
            object.put("recipient", target_id);
            data.put("type", "answer");
            data.put("sdp", sdp.description);
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            reportError("WebSocket send answer error: " + e.getMessage());
        }
    }

    public void sendCandidate(IceCandidate cnd) {
        if (state != WebSocketConnectionState.LOGGEDIN) {
            Log.w(TAG, "WebSocket send trickle in state " + state);
            return;
        }

        try {
            JSONObject object = new JSONObject();
            JSONObject data = new JSONObject();
            object.put("request", "room_data");
            object.put("room", room_id);
            object.put("recipient", target_id);
            data.put("type", "candidate");
            data.put("sdpMid", cnd.sdpMid);
            data.put("sdpMLineIndex", cnd.sdpMLineIndex);
            data.put("candidate", cnd.sdp);
            object.put("data", data.toString());

            Log.d(TAG, "C->WSS: " + object.toString());

            ws.sendTextMessage(object.toString());
        } catch (JSONException e) {
            Log.e(TAG, "WebSocket send candidate error: " + e.getMessage());
        }
    }

    public void disconnect(boolean waitForComplete) {
        checkIfCalledOnValidThread();

        Log.d(TAG, "Disconnect WebSocket. State: " + state);

        if (state == WebSocketConnectionState.LOGGEDIN) {
            // Send "unregister" to WebSocket server.
            try {
                JSONObject object = new JSONObject();
                object.put("request", "unregister");

                Log.d(TAG, "C->WSS: " + object.toString());

                ws.sendTextMessage(object.toString());
            } catch (JSONException e) {
                Log.e(TAG, "WebSocket unregister error: " + e.getMessage());
            }

            state = WebSocketConnectionState.CONNECTED;
        }

        // Close WebSocket in CONNECTED or ERROR states only.
        if (state == WebSocketConnectionState.CONNECTED || state == WebSocketConnectionState.ERROR) {
            ws.disconnect();
            state = WebSocketConnectionState.CLOSED;

            if (waitForComplete) {
                synchronized (closeEventLock) {
                    while (!closeEvent) {
                        try {
                            closeEventLock.wait(1000);
                            break;
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Wait error: " + e.toString());
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Disconnecting WebSocket done");
    }

    public void send(String message) {
        checkIfCalledOnValidThread();

        switch (state) {
            case NEW:
            case CONNECTED:
            case REGISTERED:
                // Store outgoing messages and send them after websocket client is logged in.
                Log.d(TAG, "WS ACC: " + message);
                wsSendQueue.add(message);
                return;
            case ERROR:
            case CLOSED:
                Log.e(TAG, "WebSocket send in error or closed state: " + message);
                return;
            case LOGGEDIN:
                JSONObject json = new JSONObject();

                try {
                    json.put("cmd", "send");
                    json.put("msg", message);
                    message = json.toString();

                    Log.d(TAG, "C->WSS: " + message);

                    ws.sendTextMessage(message);
                } catch (JSONException e) {
                    Log.e(TAG, "WebSocket send error: " + e.getMessage());
                }

                break;
        }
    }

    private class WebSocketObserver implements WebSocket.WebSocketConnectionObserver {
        @Override
        public void onOpen() {
            Log.d(TAG, "WebSocket connection opened to: " + QiscusRTC.getHost());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    state = WebSocketConnectionState.CONNECTED;
                    events.onWebSocketOpen();
                }
            });
        }

        @Override
        public void onClose(WebSocketCloseNotification code, String reason) {
            Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason + ". State: "
                    + state);

            synchronized (closeEventLock) {
                closeEvent = true;
                closeEventLock.notify();
            }

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (state != WebSocketConnectionState.CLOSED) {
                        state = WebSocketConnectionState.CLOSED;
                        events.onWebSocketClose();
                    }
                }
            });
        }

        @Override
        public void onTextMessage(String payload) {
            Log.d(TAG, "WSS->C: " + payload);

            final String message = payload;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == WebSocketConnectionState.CONNECTED
                            || state == WebSocketConnectionState.REGISTERED
                            || state == WebSocketConnectionState.LOGGEDIN) {
                        events.onWebSocketMessage(message);
                    }
                }
            });
        }

        @Override
        public void onRawTextMessage(byte[] payload) {}

        @Override
        public void onBinaryMessage(byte[] payload) {}
    }

    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != WebSocketConnectionState.ERROR) {
                    state = WebSocketConnectionState.ERROR;
                    events.onWebSocketError(errorMessage);
                }
            }
        });
    }
}
