package com.qiscus.rtc.ui.call.activity;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.view.View;
import android.widget.FrameLayout;

import com.qiscus.rtc.QiscusRTC;
import com.qiscus.rtc.R;
import com.qiscus.rtc.data.model.QiscusRTCCall;
import com.qiscus.rtc.engine.QiscusRTCClient;
import com.qiscus.rtc.engine.QiscusRTCViewRenderer;
import com.qiscus.rtc.engine.hub.HubListener;
import com.qiscus.rtc.engine.util.QiscusRTCListener;
import com.qiscus.rtc.ui.base.BaseActivity;
import com.qiscus.rtc.ui.base.CallFragment;
import com.qiscus.rtc.ui.base.CallingFragment;
import com.qiscus.rtc.util.RingManager;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.SurfaceViewRenderer;

import static com.qiscus.rtc.data.config.Constants.CALL_DATA;
import static com.qiscus.rtc.data.config.Constants.ON_GOING_NOTIF_ID;

/**
 * Created by fitra on 2/10/17.
 */

public class QiscusCallActivity extends BaseActivity implements CallingFragment.OnCallingListener, CallFragment.OnCallListener, HubListener, QiscusRTCListener {
    // Permission
    private String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private QiscusRTCClient rtcClient;
    private SurfaceViewRenderer pipRenderer;
    private SurfaceViewRenderer fullscreenRenderer;
    private QiscusRTC.CallEventData callEventData;
    private QiscusRTCCall callData;

    private FrameLayout callFragmentContainer;
    private CallingFragment callingFragment;
    private CallFragment callFragment;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private int field = 0x00000020;

    public static Intent generateIntent(Context context, QiscusRTCCall callData) {
        Intent intent = new Intent(context, QiscusCallActivity.class);
        intent.putExtra(CALL_DATA, callData);

        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parseIntentData();
        initView();
        requestPermission(permissions);
        setAlwaysOn();
        setFullscreen();
        configureProximity();
        autoDisconnect();

        if (QiscusRTC.Call.getCallConfig().isOngoingNotificationEnable()) {
            showOnGoingCallNotification();
        }

        callEventData = new QiscusRTC.CallEventData();
    }

    private void autoDisconnect() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!QiscusRTC.Call.getInstance().getCallAccepted()) {
                    disconnect();
                }
            }
        }, 45000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseProximity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        acquireProximity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            releaseProximity();
        } else {
            acquireProximity();
        }
    }

    @Override
    protected void onPermissionGranted() {
        if (callData.getCallType() == QiscusRTC.CallType.VIDEO) {
            startVideoCall();
        } else {
            startVoiceCall();
        }
    }

    private void acquireProximity() {
        if (callData.getCallType() == QiscusRTC.CallType.VOICE) {
            try {
                wakeLock.acquire();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void releaseProximity() {
        if (callData.getCallType() == QiscusRTC.CallType.VOICE) {
            try {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void configureProximity() {
        try {
            if (wakeLock != null) {
                field = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
            }
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }

        powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(field, "PROXIMITY");
    }

    @Override
    public int getLayout() {
        return R.layout.activity_qiscus_call;
    }

    private void parseIntentData() {
        callData = getIntent().getParcelableExtra(CALL_DATA);
    }

    private void startVideoCall() {
        initCallingFragment();
        String id = callData.getCallAs() == QiscusRTC.CallAs.CALLER ? callData.getCallerUsername() : callData.getCalleeUsername();
        String target = callData.getCallAs() == QiscusRTC.CallAs.CALLER ? callData.getCalleeUsername() : callData.getCallerUsername();
        rtcClient.start(id, callData.getRoomId(), callData.getCallAs() == QiscusRTC.CallAs.CALLER, callData.getCallType() == QiscusRTC.CallType.VIDEO, target);
    }

    private void startVoiceCall() {
        initCallingFragment();
        String id = callData.getCallAs() == QiscusRTC.CallAs.CALLER ? callData.getCallerUsername() : callData.getCalleeUsername();
        String target = callData.getCallAs() == QiscusRTC.CallAs.CALLER ? callData.getCalleeUsername() : callData.getCallerUsername();
        rtcClient.setVideoEnabled(false);
        rtcClient.start(id, callData.getRoomId(), callData.getCallAs() == QiscusRTC.CallAs.CALLER, callData.getCallType() == QiscusRTC.CallType.VIDEO, target);
    }

    private void initCallingFragment() {
        callingFragment = CallingFragment.newInstance(callData);
        getSupportFragmentManager().beginTransaction().replace(R.id.call_fragment_container, callingFragment).commit();
    }

    private void initCallFragment() {
        callFragment = CallFragment.newInstance(callData);
        android.support.v4.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.remove(callingFragment);
        fragmentTransaction.add(R.id.call_fragment_container, callFragment);
        fragmentTransaction.commit();
    }

    private void initView() {
        pipRenderer = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
        fullscreenRenderer = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);
        rtcClient = new QiscusRTCClient(QiscusCallActivity.this, pipRenderer, fullscreenRenderer, this, this);
        callFragmentContainer = (FrameLayout) findViewById(R.id.call_fragment_container);
        pipRenderer.setVisibility(callData.getCallType() == QiscusRTC.CallType.VOICE ? View.GONE : View.VISIBLE);
        fullscreenRenderer.setVisibility(callData.getCallType() == QiscusRTC.CallType.VOICE ? View.GONE : View.VISIBLE);

        callFragmentContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (callData.getCallType() == QiscusRTC.CallType.VIDEO && callFragment != null) {
                    callFragment.hidePanelButton();
                }
            }
        });
    }

    // Calling Fragment Listener
    @Override
    public void onAcceptPressed() {
        rtcClient.acceptCall();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callingFragment.setTvCallState("Connecting");
            }
        });
    }

    @Override
    public void onRejectPressed() {
        rtcClient.rejectCall();
        finish();
    }

    @Override
    public void onCancelPressed() {
        disconnect();
    }

    // Call Fragment Listener
    @Override
    public void onSpeakerToggle(boolean speakerOn) {
        if (QiscusRTC.Call.getCallConfig().getOnSpeakerClickListener() != null) {
            QiscusRTC.Call.getCallConfig().getOnSpeakerClickListener().onClick(speakerOn);
        }

        RingManager.getInstance(this).setSpeakerPhoneOn(speakerOn);
    }

    @Override
    public void onMicToggle(boolean micOn) {
        if (QiscusRTC.Call.getCallConfig().getOnMicClickListener() != null) {
            QiscusRTC.Call.getCallConfig().getOnMicClickListener().onClick(micOn);
        }

        rtcClient.setAudioEnabled(micOn);
    }

    @Override
    public void onVideoToggle(boolean videoOn) {
        if (QiscusRTC.Call.getCallConfig().getOnVideoClickListener() != null) {
            QiscusRTC.Call.getCallConfig().getOnVideoClickListener().onClick(videoOn);
        }

        rtcClient.setVideoEnabled(videoOn);
    }

    @Override
    public void onCameraSwitch(boolean frontCamera) {
        if (QiscusRTC.Call.getCallConfig().getOnCameraClickListener() != null) {
            QiscusRTC.Call.getCallConfig().getOnCameraClickListener().onClick(frontCamera);
        }

        rtcClient.switchCamera();
    }

    @Override
    public void onPanelSlide(boolean hidden) {
        if (hidden) {
            pipRenderer.setVisibility(View.INVISIBLE);
        } else {
            pipRenderer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onEndCall(long calldurationMillis) {
        if (QiscusRTC.Call.getCallConfig().getOnEndCallClickListener() != null) {
            QiscusRTC.Call.getCallConfig().getOnEndCallClickListener().onClick(callData, calldurationMillis);
        }

        callEventData.setCallEvent(QiscusRTC.CallEvent.END);
        EventBus.getDefault().post(callEventData);
        rtcClient.endCall();
        disconnect();
    }

    // Calling Listener
    @Override
    public void onPNReceived() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callingFragment.setTvCallState("Ringing");
            }
        });
    }

    @Override
    public void onCallingAccepted() {
        QiscusRTC.Call.getInstance().setCallAccepted(true);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callingFragment.setTvCallState("Connecting");
            }
        });
    }

    @Override
    public void onCallingRejected() {
        callEventData.setCallEvent(QiscusRTC.CallEvent.REJECT);
        EventBus.getDefault().post(callEventData);
    }

    @Override
    public void onCallingCanceled() {
        callEventData.setCallEvent(QiscusRTC.CallEvent.CANCEL);
        EventBus.getDefault().post(callEventData);
    }

    @Override
    public void onCallConnected() {
        QiscusRTC.Call.getInstance().setCallAccepted(true);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (QiscusRTC.Call.getCallConfig().getOnCallConnectedListener() != null) {
                    QiscusRTC.Call.getCallConfig().getOnCallConnectedListener().onConnect();
                }

                initCallFragment();
            }
        });
    }

    @Override
    public void onPeerDown() {
        disconnect();

        if (QiscusRTC.Call.getCallConfig().getOnCallDisconenctedListener() != null) {
            QiscusRTC.Call.getCallConfig().getOnCallDisconenctedListener().onDisconnect(callFragment != null ? callFragment.getCallDurationMillis() : 0);
        }
    }

    @Override
    public void onPeerError() {
        //
    }

    private void disconnect() {
        NotificationManagerCompat.from(this).cancel(ON_GOING_NOTIF_ID);
        releaseProximity();
        QiscusRTC.Call.getInstance().setCallAccepted(false);

        if (rtcClient != null) {
            try {
                rtcClient.end();
                rtcClient = null;
                pipRenderer = null;
                fullscreenRenderer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!isFinishing()) {
            finish();
        }
    }

    private void showOnGoingCallNotification() {
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(this, QiscusCallActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle(getString(R.string.on_going_call_notif))
                .setContentText(getString(R.string.on_going_call_notif))
                .setSmallIcon(QiscusRTC.Call.getCallConfig().getSmallOngoingNotifIcon())
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), QiscusRTC.Call.getCallConfig().getLargeOngoingNotifIcon()))
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManagerCompat
                .from(this)
                .notify(ON_GOING_NOTIF_ID, notification);
    }
}
