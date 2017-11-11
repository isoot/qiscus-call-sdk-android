package com.qiscus.rtc.engine.peer;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioRecord;
import org.webrtc.voiceengine.WebRtcAudioTrack;
import org.webrtc.voiceengine.WebRtcAudioUtils;

/**
 * Created by fitra on 2/10/17.
 */

public class PCFactory {
    private static final String TAG = PCFactory.class.getSimpleName();

    private static final String VIDEO_CODEC_VP8 = "VP8";
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String VIDEO_CODEC_H264 = "H264";
    private static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
    private static final String VIDEO_CODEC_H264_HIGH = "H264 High";
    private static final String AUDIO_CODEC_OPUS = "opus";
    private static final String AUDIO_CODEC_ISAC = "ISAC";
    private static final String VIDEO_FLEXFEC_FIELDTRIAL = "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    private static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
    private static final String DISABLE_WEBRTC_AGC_FIELDTRIAL = "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";

    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnectionFactory.Options options;
    private boolean videoEnabled;
    private String preferredVideoCodec = VIDEO_CODEC_VP9;

    public PCFactory(Context context) {
        peerConnectionFactory = null;
        options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0;
        create(context);
    }

    public void setVideoEnabled(boolean videoEnabled) {
        this.videoEnabled = videoEnabled;
    }

    private void create(Context context) {
        PeerConnectionFactory.initializeInternalTracer();

        Log.d(TAG, "Create peer connection factory. Use video: " + videoEnabled);

        String fieldTrials = "";
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        //Log.d(TAG, "Enable FlexFEC field trial");
        //fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
        //Log.d(TAG, "Disable WebRTC AGC field trial");
        //fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;

        Log.d(TAG, "Preferred video codec: " + preferredVideoCodec);
        PeerConnectionFactory.initializeFieldTrials(fieldTrials);
        Log.d(TAG, "Field trials: " + fieldTrials);

        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(false);
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(false);
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(false);

        WebRtcAudioRecord.setErrorCallback(new WebRtcAudioRecord.WebRtcAudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                Log.e(TAG, "WebRtcAudioRecordInitError: " + errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordStartError(WebRtcAudioRecord.AudioRecordStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "WebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                Log.e(TAG, "WebRtcAudioRecordError: " + errorMessage);
            }
        });

        WebRtcAudioTrack.setErrorCallback(new WebRtcAudioTrack.WebRtcAudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "WebRtcAudioTrackInitError: " + errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(String errorMessage) {
                Log.e(TAG, "WebRtcAudioTrackStartError: " + errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "WebRtcAudioTrackError: " + errorMessage);
            }
        });

        PeerConnectionFactory.initializeAndroidGlobals(context, true);

        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }

        peerConnectionFactory = new PeerConnectionFactory(options);
        Log.d(TAG, "Peer connection factory created.");
    }

    public void setVideoHwAccelerationOptions(EglBase.Context localEglContext, EglBase.Context remoteEglContext) {
        peerConnectionFactory.setVideoHwAccelerationOptions(localEglContext, remoteEglContext);
    }

    public PeerConnection createPeerConnection(PeerConnection.RTCConfiguration rtcConfig, MediaConstraints pcConstraints, PeerConnection.Observer pcObserver) {
        return peerConnectionFactory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
    }

    public MediaStream createLocalMediaStream(String label) {
        return peerConnectionFactory.createLocalMediaStream(label);
    }

    public AudioSource createAudioSource(MediaConstraints audioConstraints) {
        return peerConnectionFactory.createAudioSource(audioConstraints);
    }

    public AudioTrack createAudioTrack(String label, AudioSource audioSource) {
        return peerConnectionFactory.createAudioTrack(label, audioSource);
    }

    public VideoSource createVideoSource(VideoCapturer capturer) {
        return peerConnectionFactory.createVideoSource(capturer);
    }

    public VideoTrack createVideoTrack(String label, VideoSource videoSource) {
        return peerConnectionFactory.createVideoTrack(label, videoSource);
    }

    public void dispose() {
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }

        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        options = null;
    }
}

