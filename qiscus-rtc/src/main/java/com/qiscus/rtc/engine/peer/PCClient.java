package com.qiscus.rtc.engine.peer;

import android.content.Context;
import android.util.Log;

import com.qiscus.rtc.engine.util.LooperExecutor;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by fitra on 2/10/17.
 */

public class PCClient {
    public static final String TAG = PCClient.class.getSimpleName();

    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String VIDEO_TRACK_TYPE = "video";
    private static final String VIDEO_CODEC_VP8 = "VP8";
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String VIDEO_CODEC_H264 = "H264";
    private static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
    private static final String VIDEO_CODEC_H264_HIGH = "H264 High";
    private static final String AUDIO_CODEC_OPUS = "opus";
    private static final String AUDIO_CODEC_ISAC = "ISAC";
    private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    private static final String VIDEO_H264_HIGH_PROFILE_FIELDTRIAL = "WebRTC-H264HighProfile/Enabled/";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    private static final String AUDIO_LEVEL_CONTROL_CONSTRAINT = "levelControl";
    private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
    private static final int HD_VIDEO_WIDTH = 1280;
    private static final int HD_VIDEO_HEIGHT = 720;
    private static final int BPS_IN_KBPS = 1000;

    private static final PCClient instance = new PCClient();

    private final PCClient.PCObserver pcObserver = new PCClient.PCObserver();
    private final PCClient.SDPObserver sdpObserver = new PCClient.SDPObserver();
    private final LooperExecutor executor;

    private Context context;
    private PeerConnection peerConnection;
    private PCClient.PeerConnectionEvents events;
    private AudioSource audioSource;
    private VideoSource videoSource;
    private VideoCapturer videoCapturer;
    private VideoRenderer.Callbacks localRender;
    private List<VideoRenderer.Callbacks> remoteRenders;
    private MediaConstraints pcConstraints;
    private MediaConstraints audioConstraints;
    private MediaConstraints sdpMediaConstraints;
    private MediaStream mediaStream;
    private SessionDescription localSdp;
    private LinkedList<IceCandidate> queuedRemoteCandidates;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private RtpSender localVideoSender;
    private PCFactory pcFactory;
    private boolean isInitiator;
    private boolean isError;
    private boolean audioEnabled;
    private boolean preferIsac;
    private boolean videoEnabled;
    private boolean renderVideo;
    private boolean videoCapturerStopped;
    private int videoWidth;
    private int videoHeight;
    private int videoFps;
    private String preferredVideoCodec = VIDEO_CODEC_VP9;

    private PCClient() {
        executor = new LooperExecutor();
        executor.requestStart();
    }

    public static PCClient getInstance() {
        return instance;
    }

    public void init(final Context context, final PCFactory pcFactory, boolean videoEnabled, final PCClient.PeerConnectionEvents events) {
        this.context = context;
        this.pcFactory = pcFactory;
        this.videoEnabled = videoEnabled;
        this.events = events;
        isInitiator = false;
        isError = false;
        audioEnabled = true;
        preferIsac = false;
        renderVideo = videoEnabled;
        videoCapturerStopped = false;
    }

    public void createPeerConnection(final EglBase.Context renderEGLContext, final VideoRenderer.Callbacks localRender, final List<VideoRenderer.Callbacks> remoteRenders, final VideoCapturer videoCapturer) {
        this.localRender = localRender;
        this.remoteRenders = remoteRenders;
        this.videoCapturer = videoCapturer;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                createMediaConstraintsInternal();
                createPeerConnectionInternal(renderEGLContext);
            }
        });
    }

    private void createMediaConstraintsInternal() {
        pcConstraints = new MediaConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));

        if (videoEnabled) {
            if (videoCapturer == null) {
                Log.w(TAG, "No camera on device. Switch to audio only call.");
                videoEnabled = false;
            }
        }

        if (videoEnabled) {
            videoWidth = 640;
            videoHeight = 480;
            videoFps = 24;
        }

        audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT , "false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_LEVEL_CONTROL_CONSTRAINT , "true"));

        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        if (videoEnabled) {
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        } else {
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        }
    }

    private void createPeerConnectionInternal(EglBase.Context renderEGLContext) {
        if (pcFactory == null || isError) {
            Log.e(TAG, "Peer connection factory is not created");
            return;
        }

        Log.d(TAG, "Create peer connection using constraints: " + pcConstraints.toString());

        queuedRemoteCandidates = new LinkedList<IceCandidate>();

        if (videoEnabled) {
            Log.d(TAG, "EGLContext: " + renderEGLContext);
            pcFactory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);
        }

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:139.59.110.14:3478"));
        iceServers.add(new PeerConnection.IceServer("turn:139.59.110.14:3478", "sangkil", "qiscuslova"));

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        peerConnection = pcFactory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
        mediaStream = pcFactory.createLocalMediaStream("ARDAMS");

        if (videoEnabled) {
            mediaStream.addTrack(createVideoTrack(videoCapturer));
        }

        mediaStream.addTrack(createAudioTrack());
        peerConnection.addStream(mediaStream);

        if (videoEnabled) {
            findVideoSender();
        }

        Log.d(TAG, "Peer connection created");
    }

    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        videoSource = pcFactory.createVideoSource(capturer);
        capturer.startCapture(videoWidth, videoHeight, videoFps);
        localVideoTrack = pcFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(renderVideo);
        localVideoTrack.addRenderer(new VideoRenderer(localRender));
        return localVideoTrack;
    }

    private AudioTrack createAudioTrack() {
        audioSource = pcFactory.createAudioSource(audioConstraints);
        localAudioTrack = pcFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(audioEnabled);
        return localAudioTrack;
    }

    private void findVideoSender() {
        for (RtpSender sender : peerConnection.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();

                if (trackType.equals(VIDEO_TRACK_TYPE)) {
                    Log.d(TAG, "Found video sender");
                    localVideoSender = sender;
                }
            }
        }
    }

    public void createOffer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    Log.d(TAG, "Create offer");
                    isInitiator = true;
                    peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void createAnswer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    Log.d(TAG, "Create answer");
                    isInitiator = false;
                    peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void setRemoteDescription(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null || isError) {
                    return;
                }

                String sdpDescription = sdp.description;

                if (preferIsac) {
                    sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
                }

                if (videoEnabled) {
                    sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
                }

                SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
                peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
            }
        });
    }

    public void setRemoteCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null || isError) {
                    return;
                }

                peerConnection.addIceCandidate(candidate);
            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    if (queuedRemoteCandidates != null) {
                        queuedRemoteCandidates.add(candidate);
                    } else {
                        peerConnection.addIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null || isError) {
                    return;
                }

                drainCandidates();
                peerConnection.removeIceCandidates(candidates);
            }
        });
    }

    public void setVideoEnabled(final boolean enable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                renderVideo = enable;

                if (localVideoTrack != null) {
                    localVideoTrack.setEnabled(renderVideo);
                }

                if (remoteVideoTrack != null) {
                    remoteVideoTrack.setEnabled(renderVideo);
                }
            }
        });
    }

    public void switchCamera() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                switchCameraInternal();
            }
        });
    }

    private void switchCameraInternal() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            if (!videoEnabled || isError || videoCapturer == null) {
                Log.e(TAG, "Failed to switch camera. Video: " + videoEnabled + ". Error : " + isError);
                return;
            }

            Log.d(TAG, "Switch camera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }
    }

    public void setAudioEnabled(final boolean enable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (localAudioTrack != null) {
                    localAudioTrack.setEnabled(enable);
                }
            }
        });
    }

    public void startVideoSource() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (videoCapturer != null && videoCapturerStopped) {
                    Log.d(TAG, "Restart video source");
                    videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
                    videoCapturerStopped = false;
                }
            }
        });
    }

    public void stopVideoSource() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (videoCapturer != null && !videoCapturerStopped) {
                    Log.d(TAG, "Stop video source");
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        //
                    }

                    videoCapturerStopped = true;
                }
            }
        });
    }

    public void close() {
        closeInternal();
    }

    private void closeInternal() {
        Log.d(TAG, "Closing peer connection");
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }

        Log.d(TAG, "Closing audio source");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        Log.d(TAG, "Stopping capture");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            videoCapturerStopped = true;
            videoCapturer.dispose();
            videoCapturer = null;
        }

        Log.d(TAG, "Closing video source");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        localRender = null;
        remoteRenders = null;

        Log.d(TAG, "Closing peer connection factory");
        if (pcFactory != null) {
            pcFactory.dispose();
            pcFactory = null;
        }

        events.onPeerConnectionClosed();
        events = null;
    }

    private static String setStartBitrate(String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);

            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }

        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);

        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);

            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }

                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");

            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;

                if (isVideoCodec) {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }

                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }

        return newSdpDescription.toString();
    }

    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        final String[] lines = sdpDescription.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);

        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
            return sdpDescription;
        }

        final List<String> codecPayloadTypes = new ArrayList<String>();
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");

        for (int i = 0; i < lines.length; ++i) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);

            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }

        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name " + codec);
            return sdpDescription;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);

        if (newMLine == null) {
            return sdpDescription;
        }

        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";

        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }

        return -1;
    }

    private static String movePayloadTypesToFront(List<String> preferredPayloadTypes, String mLine) {
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));

        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }

        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes = new ArrayList<String>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        final List<String> newLineParts = new ArrayList<String>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    private static String joinString(Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();

        if (!iter.hasNext()) {
            return "";
        }

        StringBuilder buffer = new StringBuilder(iter.next());

        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }

        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }

        return buffer.toString();
    }

    private static String setStartBitrate(String codec, String sdpDescription, int bitrateKbps) {
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        String codecRtpMap = null;
        String[] lines = sdpDescription.split("\r\n");
        Pattern codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);

            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }

        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }

        Log.d(TAG, "Found " +  codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);

            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " +  codec + " " + lines[i]);
                lines[i] += "; " + "x-google-start-bitrate" + "=" + bitrateKbps;
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");

            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet = "a=fmtp:" + codecRtpMap + " " + "x-google-start-bitrate" + "=" + bitrateKbps;
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }

        return newSdpDescription.toString();
    }

    private static String setAudioStartBitrate(String codec, String sdpDescription, int bitrateKbps) {
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        String codecRtpMap = null;
        String[] lines = sdpDescription.split("\r\n");
        Pattern codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);

            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }

        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }

        Log.d(TAG, "Found " +  codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);

            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " +  codec + " " + lines[i]);
                lines[i] += "; " + "maxaveragebitrate" + "=" + (bitrateKbps * 1000);
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");

            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet = "a=fmtp:" + codecRtpMap + " " + "maxaveragebitrate" + "=" + (bitrateKbps * 1000);
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }

        return newSdpDescription.toString();
    }

    private static String setBandwith(String sdpDescription, int audioBw, int videoBw) {
        String regex1 = "^a=mid:audio+[\r]?$";
        String regex2 = "^a=mid:video+[\r]?$";
        String[] lines = sdpDescription.split("\r\n");
        Pattern codecPattern1 = Pattern.compile(regex1);
        Pattern codecPattern2 = Pattern.compile(regex2);

        StringBuilder newSdpDescription = new StringBuilder();

        // Set audio bandwith
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher1 = codecPattern1.matcher(lines[i]);

            if (codecMatcher1.matches()) {
                Log.d(TAG, "Found mid:audio at " + lines[i]);
                lines[i] += "\r\n" + "b=AS:" + audioBw;
                break;
            }
        }

        // Set video bandwith
        for (int j = 0; j < lines.length; j++) {
            Matcher codecMatcher2 = codecPattern2.matcher(lines[j]);

            if (codecMatcher2.matches()) {
                Log.d(TAG, "Found mid:video at " + lines[j]);
                lines[j] += "\r\n" + "b=AS:" + videoBw;
                break;
            }
        }

        for (int k = 0; k < lines.length; k++) {
            newSdpDescription.append(lines[k]).append("\r\n");
        }

        Log.d(TAG, newSdpDescription.toString());
        return newSdpDescription.toString();
    }

    public static interface PeerConnectionEvents {
        void onLocalDescription(final SessionDescription sdp);
        void onIceState(final String state);
        void onIceCandidate(final IceCandidate candidate);
        void onIceCandidatesRemoved(final IceCandidate[] candidates);
        void onIceConnected();
        void onIceDisconnected();
        void onPeerConnectionClosed();
        void onPeerConnectionStatsReady(final StatsReport[] reports);
        void onPeerConnectionError(final String description);
    }

    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidate(candidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] iceCandidates) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidatesRemoved(iceCandidates);
                }
            });
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
            Log.d(TAG, "Signaling state: " + newState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "IceConnection state: " + newState);

                    if (newState == PeerConnection.IceConnectionState.NEW ||
                            newState == PeerConnection.IceConnectionState.CONNECTED ||
                            newState == PeerConnection.IceConnectionState.FAILED ) {
                        events.onIceState(newState.name());
                    }

                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        events.onIceConnected();
                    } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        events.onIceDisconnected();
                    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                        Log.e(TAG, "IceConnection failed");
                    }
                }
            });
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGathering state: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "IceConnection receiving changed to: " + receiving);
        }

        @Override
        public void onAddStream(final MediaStream stream){
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null || isError) {
                        return;
                    }

                    if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                        Log.e(TAG, "Weird-looking stream: " + stream);
                        return;
                    }

                    if (stream.videoTracks.size() == 1) {
                        remoteVideoTrack = stream.videoTracks.get(0);
                        remoteVideoTrack.setEnabled(renderVideo);

                        for (VideoRenderer.Callbacks remoteRender : remoteRenders) {
                            remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
                        }
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.w(TAG, "Stream removed");
                    remoteVideoTrack = null;
                }
            });
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            //
        }

        @Override
        public void onRenegotiationNeeded() {
            //
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            //
        }
    }

    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            if (localSdp != null) {
                Log.e(TAG, "Multiple SDP created");
                return;
            }

            String sdpDescription = origSdp.description;

            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
            }

            if (videoEnabled) {
                sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
            }

            sdpDescription = preferCodec(sdpDescription, "opus", true);

            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
            localSdp = sdp;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection != null && !isError) {
                        Log.d(TAG, "Set local SDP from " + sdp.type);
                        peerConnection.setLocalDescription(sdpObserver, sdp);
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null || isError) {
                        return;
                    }

                    if (isInitiator) {
                        if (peerConnection.getRemoteDescription() == null) {
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                        } else {
                            Log.d(TAG, "Remote SDP set succesfully");
                            drainCandidates();
                        }
                    } else {
                        if (peerConnection.getLocalDescription() != null) {
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                            drainCandidates();
                        } else {
                            Log.d(TAG, "Remote SDP set succesfully");
                        }
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(final String error) {
            Log.e(TAG, "Create SDP error: " + error);
        }

        @Override
        public void onSetFailure(final String error) {
            Log.e(TAG, "Set SDP error: " + error);
        }
    }

    private void drainCandidates() {
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");

            for (IceCandidate candidate : queuedRemoteCandidates) {
                peerConnection.addIceCandidate(candidate);
            }

            queuedRemoteCandidates = null;
        }
    }
}
