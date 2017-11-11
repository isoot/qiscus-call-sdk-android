package com.qiscus.rtc.engine;

import android.content.Context;
import android.util.Log;

import com.qiscus.rtc.engine.hub.HubListener;
import com.qiscus.rtc.engine.hub.HubSignal;
import com.qiscus.rtc.engine.hub.WSSignal;
import com.qiscus.rtc.engine.peer.PCClient;
import com.qiscus.rtc.engine.peer.PCFactory;
import com.qiscus.rtc.engine.util.LooperExecutor;
import com.qiscus.rtc.engine.util.QiscusRTCListener;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fitra on 2/10/17.
 */

public class QiscusRTCClient implements HubSignal.SignalEvents, PCClient.PeerConnectionEvents {
    private static final String TAG = QiscusRTCClient.class.getSimpleName();

    private class ProxyRenderer implements VideoRenderer.Callbacks {
        private VideoRenderer.Callbacks target;

        synchronized public void renderFrame(VideoRenderer.I420Frame frame) {
            if (target == null) {
                Log.d(TAG, "Dropping frame in proxy because target is null");
                VideoRenderer.renderFrameDone(frame);
                return;
            }

            target.renderFrame(frame);
        }

        synchronized public void setTarget(VideoRenderer.Callbacks target) {
            this.target = target;
        }
    }

    private final ProxyRenderer remoteProxyRenderer = new ProxyRenderer();
    private final ProxyRenderer localProxyRenderer = new ProxyRenderer();
    private final List<VideoRenderer.Callbacks> remoteRenderers = new ArrayList<VideoRenderer.Callbacks>();
    private final Context context;

    private PCFactory pcFactory;
    private PCClient pcClient;
    private EglBase rootEglBase;
    private QiscusRTCViewRenderer pipRenderer;
    private QiscusRTCViewRenderer fullscreenRenderer;
    private HubSignal hubSignal;
    private HubListener hubListener;
    private QiscusRTCListener rtcListener;
    private boolean initiator;
    private boolean videoEnabled;
    private boolean isSwappedFeeds;
    private String clientId;
    private String roomId;

    public QiscusRTCClient(Context context, QiscusRTCViewRenderer pipRenderer, QiscusRTCViewRenderer fullscreenRenderer, HubListener hubListener, QiscusRTCListener rtcListener) {
        this.context = context;
        this.pipRenderer = pipRenderer;
        this.fullscreenRenderer = fullscreenRenderer;
        this.rtcListener = rtcListener;
        this.hubListener = hubListener;

        pcFactory = new PCFactory(context);
        rootEglBase = EglBase.create();

        this.pipRenderer.init(rootEglBase.getEglBaseContext(), null);
        this.pipRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        this.pipRenderer.setZOrderMediaOverlay(true);
        this.pipRenderer.setEnableHardwareScaler(true);
        this.fullscreenRenderer.init(rootEglBase.getEglBaseContext(), null);
        this.fullscreenRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        this.fullscreenRenderer.setEnableHardwareScaler(true);
        setSwappedFeeds(true);
    }

    public void start(String clientId, String roomId, boolean initiator, boolean videoEnabled, String target) {
        this.clientId = clientId;
        this.roomId = roomId;
        this.initiator = initiator;
        this.videoEnabled = videoEnabled;

        HubSignal.SignalParameters parameters = new HubSignal.SignalParameters(clientId, roomId, initiator, videoEnabled, target);
        hubSignal = new WSSignal(QiscusRTCClient.this, parameters, new LooperExecutor());
        hubSignal.connect();

        VideoCapturer videoCapturer = null;
        if (videoEnabled) {
            videoCapturer = createVideoCapturer();
        }

        pcClient = PCClient.getInstance();
        pcClient.init(context, pcFactory, videoEnabled, QiscusRTCClient.this);
        pcClient.createPeerConnection(rootEglBase.getEglBaseContext(), localProxyRenderer, remoteRenderers, videoCapturer);
    }

    public void acceptCall() {
        hubSignal.acceptCall();
    }

    public void rejectCall() {
        hubSignal.rejectCall();
    }

    public void end() {
        if (pcClient != null) {
            pcClient.close();
            pcClient = null;
        }

        if (pipRenderer != null) {
            pipRenderer.release();
        }

        if (fullscreenRenderer != null) {
            fullscreenRenderer.release();
        }

        if (pcFactory != null) {
            if (pcClient == null) {
                pcFactory.dispose();
                pcFactory = null;
            }
        }

        if (hubSignal != null) {
            hubSignal.close();
            hubSignal = null;
        }
    }

    public void setVideoEnabled(boolean on) {
        if (pcClient != null) {
            pcClient.setVideoEnabled(on);
        }
    }

    public void switchCamera() {
        if (pcClient != null) {
            pcClient.switchCamera();
        }
    }

    public void setAudioEnabled(boolean on) {
        if (pcClient != null) {
            pcClient.setAudioEnabled(on);
        }
    }

    public void endCall() {
        hubSignal.endCall();
    }

    private void setSwappedFeeds(boolean isSwappedFeeds) {
        Log.d(TAG, "SetSwappedFeeds: " + isSwappedFeeds);
        localProxyRenderer.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
        remoteProxyRenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
        fullscreenRenderer.setMirror(isSwappedFeeds);
        pipRenderer.setMirror(!isSwappedFeeds);
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer = null;

        if (useCamera2()) {
            Log.d(TAG, "Creating capturer using camera2 API");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(context));
        } else {
            Log.d(TAG, "Creating capturer using camera1 API");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
        }

        if (videoCapturer == null) {
            Log.e(TAG, "Failed to open camera");
            return null;
        }

        return videoCapturer;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(context);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        Log.d(TAG, "Looking for front facing cameras");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating front facing camera capturer");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        Log.d(TAG, "Looking for other cameras");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating other camera capturer");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @Override
    public void onLoggedinToRoom() {
        hubSignal.ping();
    }

    @Override
    public void onPNReceived() {
        hubListener.onPNReceived();
    }

    @Override
    public void onCallAccepted() {
        hubListener.onCallAccepted();
        pcClient.createOffer();
    }

    @Override
    public void onCallRejected() {
        hubListener.onCallRejected();
    }

    @Override
    public void onCallCanceled() {
        hubListener.onCallCanceled();
    }

    @Override
    public void onSDPOffer(SessionDescription sdp) {
        pcClient.setRemoteDescription(sdp);
        pcClient.createAnswer();
    }

    @Override
    public void onSDPAnswer(SessionDescription sdp) {
        pcClient.setRemoteDescription(sdp);
    }

    @Override
    public void onICECandidate(IceCandidate candidate) {
        pcClient.addRemoteIceCandidate(candidate);
    }

    @Override
    public void onClose() {
        rtcListener.onPeerDown();
    }

    @Override
    public void onError(String description) {
        rtcListener.onPeerError();
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        if (sdp.type.canonicalForm().equals("offer")) {
            hubSignal.sendOffer(sdp);
        } else if (sdp.type.canonicalForm().equals("answer")) {
            hubSignal.sendAnswer(sdp);
        }
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        hubSignal.trickleCandidate(candidate);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        //
    }

    @Override
    public void onIceConnected() {
        rtcListener.onCallConnected();
        setSwappedFeeds(false);
    }

    @Override
    public void onIceDisconnected() {
        rtcListener.onPeerDown();
    }

    @Override
    public void onPeerConnectionClosed() {
        //
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {
        //
    }

    @Override
    public void onPeerConnectionError(String description) {
        rtcListener.onPeerError();
    }
}
