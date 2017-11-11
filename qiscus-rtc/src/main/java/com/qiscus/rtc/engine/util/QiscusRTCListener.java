package com.qiscus.rtc.engine.util;

import org.webrtc.RendererCommon;

/**
 * Created by fitra on 2/10/17.
 */

public interface QiscusRTCListener {
    void onCallConnected();
    void onPeerDown();
    void onPeerError();
}
