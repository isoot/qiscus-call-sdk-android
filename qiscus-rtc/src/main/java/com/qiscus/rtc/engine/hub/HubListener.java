package com.qiscus.rtc.engine.hub;

/**
 * Created by fitra on 2/10/17.
 */

public interface HubListener {
    public void onPnReceived();
    public void onCallingAccepted();
    public void onCallingRejected();
    public void onCallingCanceled();
}
