package com.qiscus.rtc.sample;

import android.app.Application;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.qiscus.rtc.QiscusRTC;
import com.qiscus.rtc.sample.utils.Config;
import com.qiscus.sdk.Qiscus;
import com.qiscus.sdk.data.model.QiscusComment;
import com.qiscus.sdk.data.model.QiscusNotificationBuilderInterceptor;
import com.qiscus.sdk.event.QiscusCommentReceivedEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by fitra on 04/10/17.
 */

public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Qiscus.init(this, Config.CHAT_APP_ID);
        QiscusRTC.init(this, Config.CALL_APP_ID, Config.CALL_APP_SECRET);
        Qiscus.getChatConfig().setNotificationBuilderInterceptor(new QiscusNotificationBuilderInterceptor() {
            @Override
            public boolean intercept(NotificationCompat.Builder notificationBuilder, QiscusComment qiscusComment) {
                if (qiscusComment.getType() == QiscusComment.Type.SYSTEM_EVENT) {
                    return false;
                }
                return true;
            }
        });

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Subscribe
    public void onReceivedComment(QiscusCommentReceivedEvent event) {
        if (event.getQiscusComment().getExtraPayload() != null && !event.getQiscusComment().getExtraPayload().equals("null")) {
            handleCallPn(event.getQiscusComment());
        }
    }

    private void handleCallPn(QiscusComment remoteMessage) {
        JSONObject json;
        try {
            json = new JSONObject(remoteMessage.getExtraPayload());
            JSONObject payload = json.getJSONObject("payload");

            if (payload.get("type").equals("call")) {
                String event = payload.getString("call_event");
                switch (event.toLowerCase()) {
                    case "incoming":
                        final Boolean isVideo = (Boolean) payload.get("call_is_video");
                        final String roomId = payload.get("call_room_id").toString();

                        JSONObject caller = payload.getJSONObject("call_caller");
                        final String caller_email = caller.getString("username");
                        final String caller_name = caller.getString("name");
                        final String caller_avatar = caller.getString("avatar");
                        JSONObject callee = payload.getJSONObject("call_callee");
                        final String callee_email = callee.getString("username");
                        final String callee_name = callee.getString("name");
                        final String callee_avatar = callee.getString("avatar");

                        if (Qiscus.getQiscusAccount().getEmail().equals(callee_email)) {
                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    QiscusRTC.buildCallWith(roomId)
                                            .setCallAs(QiscusRTC.CallAs.CALLEE)
                                            .setCallType(isVideo == true ? QiscusRTC.CallType.VIDEO : QiscusRTC.CallType.VOICE)
                                            .setCalleeUsername(callee_email)
                                            .setCallerUsername(caller_email)
                                            .setCallerDisplayName(caller_name)
                                            .setCallerDisplayAvatar(caller_avatar)
                                            .show(getApplicationContext());
                                }
                            }, 2500);
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
