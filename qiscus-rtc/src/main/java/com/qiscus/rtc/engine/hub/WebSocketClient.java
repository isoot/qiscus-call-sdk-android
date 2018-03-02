package com.qiscus.rtc.engine.hub;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by fitra on 22/11/17.
 */

public class WebSocketClient implements HubSignal, WebSocketChannel.WebSocketChannelEvents {
    private static final String TAG = WebSocketClient.class.getSimpleName();

    private final Handler handler;
    private final SignalParameters parameters;

    private SignalEvents events;
    private WebSocketChannel wsChannel;
    private ConnectionState roomState;
    private boolean initiator;

    private enum ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }

    private enum MessageType {
        MESSAGE, LEAVE
    }

    public WebSocketClient(SignalEvents events, SignalParameters parameters) {
        this.events = events;
        this.parameters = parameters;
        this.initiator = parameters.initiator;
        roomState = ConnectionState.NEW;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void onWebSocketOpen() {
        roomState = ConnectionState.CONNECTED;
        wsChannel.register(parameters.clientId);
    }

    @Override
    public void onWebSocketMessage(String msg) {
        try {
            JSONObject object = new JSONObject(msg);

            if (object.has("response")) {
                String response = object.getString("response");
                String strData = object.getString("data");
                JSONObject data = new JSONObject(strData);

                if (response.equals("register")) {
                    boolean success = data.getBoolean("success");
                    final String token = data.getString("token");

                    if (success) {
                        wsChannel.setState(WebSocketChannel.WebSocketConnectionState.REGISTERED);

                        if (parameters.initiator) {
                            wsChannel.createRoom(parameters.roomId, token);
                        } else {
                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    wsChannel.joinRoom(parameters.roomId, token);
                                }
                            }, 1000);
                        }
                    } else {
                        String message = data.getString("message");
                        reportError(message);
                        events.onClose();
                    }
                } else if (response.equals("room_create") || response.equals("room_join")) {
                    boolean success = data.getBoolean("success");

                    if (success) {
                        wsChannel.setState(WebSocketChannel.WebSocketConnectionState.LOGGEDIN);
                        events.onLoggedinToRoom();

                        if (response.equals("room_join")) {
                            String message = data.getString("message");
                            JSONObject user = new JSONObject(message);
                            JSONArray users = user.getJSONArray("users");

                            for (int i=0; i<users.length(); i++) {
                                if (users.get(i).equals(parameters.target)) {
                                    wsChannel.setTargetId(parameters.target);
                                    wsChannel.ack();
                                }
                            }

                            if (wsChannel.getPendingSendAccept()) {
                                wsChannel.acceptCall();
                            }
                        }
                    } else {
                        String message = data.getString("message");
                        reportError(message);
                        events.onClose();
                    }
                }
            } else if (object.has("event")) {
                String event = object.getString("event");
                String sender = object.getString("sender");
                String strData = object.getString("data");
                JSONObject data = new JSONObject(strData);

                if (event.equals("user_new")) {
                    if (sender.equals(parameters.target)) {
                        wsChannel.setTargetId(parameters.target);
                        wsChannel.sync();
                    }
                } else if (event.equals("user_leave")) {
                    if (sender.equals(parameters.target)) {
                        events.onClose();
                    }
                } else if (event.equals("room_data_private")) {
                    if (data.has("event")) {
                        String evt = data.getString("event");

                        if (evt.equals("call_ack")) {
                            if (sender.equals(parameters.target)) {
                                events.onPnReceived();
                                wsChannel.setTargetId(parameters.target);
                            }
                        } else if (evt.equals("call_accept")) {
                            events.onCallAccepted();
                        } else if (evt.equals("call_reject")) {
                            events.onCallRejected();
                        } else if (evt.equals("call_cancel")) {
                            events.onCallCanceled();
                        }
                    } else if (data.has("type")) {
                        String type = data.getString("type");

                        if (type.equals("offer")) {
                            if (!initiator) {
                                String description = data.getString("sdp");
                                SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm("offer"), description);
                                events.onRemoteSdp(sdp);
                            } else {
                                reportError("Received answer for call receiver: " + msg);
                                events.onClose();
                            }
                        } else if (type.equals("answer")) {
                            if (initiator) {
                                String description = data.getString("sdp");
                                SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"), description);
                                events.onRemoteSdp(sdp);
                            } else {
                                reportError("Received answer for call initiator: " + msg);
                                events.onClose();
                            }
                        } else if (type.equals("candidate")) {
                            IceCandidate candidate = new IceCandidate(data.getString("sdpMid"), data.getInt("sdpMLineIndex"), data.getString("candidate"));
                            events.onRemoteCandidate(candidate);
                        }
                    }
                } else {
                    Log.e(TAG, "Unknown event: " + msg);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    @Override
    public void connect() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                wsChannel = new WebSocketChannel(handler, WebSocketClient.this);
                wsChannel.connect();
            }
        });
    }

    @Override
    public void acceptCall() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending accept call signal in non connected state");
                    return;
                }

                wsChannel.acceptCall();
            }
        });
    }

    @Override
    public void rejectCall() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending reject call signal in non connected state");
                    return;
                }

                wsChannel.rejectCall();
            }
        });
    }

    @Override
    public void cancelCall() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending cancel call signal in non connected state");
                    return;
                }

                wsChannel.cancelCall();
            }
        });
    }

    @Override
    public void endCall() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending end call signal in non connected state");
                    return;
                }

                wsChannel.endCall();
            }
        });
    }

    @Override
    public void sendOffer(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state");
                    return;
                }

                wsChannel.sendOffer(sdp);
            }
        });
    }

    @Override
    public void sendAnswer(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending answer SDP in non connected state");
                    return;
                }

                wsChannel.sendAnswer(sdp);
            }
        });
    }

    @Override
    public void sendCandidate(final IceCandidate candidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending candidate SDP in non connected state");
                    return;
                }

                wsChannel.sendCandidate(candidate);
            }
        });
    }

    @Override
    public void notifyConnect() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending connect signal in non connected state");
                    return;
                }

                wsChannel.notifyConnect();
            }
        });
    }

    @Override
    public void notifyState(final String state, final String value) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending state signal in non connected state");
                    return;
                }

                wsChannel.notifyState(state, value);
            }
        });
    }

    @Override
    public void ping() {
        handler.post(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    @Override
    public void disconnect() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Disconnecting, state: " + roomState);

                roomState = ConnectionState.CLOSED;

                if (wsChannel != null) {
                    wsChannel.disconnect(true);
                }

                handler.getLooper().quit();
            }
        });
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onError(errorMessage);
                }
            }
        });
    }
}
