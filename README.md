# Qiscus RTC SDK Android

[![Release](https://jitpack.io/v/qiscus/qiscus-rtc-sdk-android.svg)](https://jitpack.io/#qiscus/qiscus-rtc-sdk-android)

<p align="center"><br/><img src="https://github.com/qiscus/qiscus-rtc-sdk-android/blob/master/screenshoot/calling.png" width="37%" /><br/></p>

Qiscus RTC SDK is a product that makes adding voice calling to mobile apps easy. It handles all the complexity of signaling and audio management while providing you the freedom to create a stunning user interface.
On this example we use our simple websocket push notification for handle call notification. We highly recommend that you implement a better push notification for increasing call realiability, for example GCM, FCM, MQTT, or other standard messaging protocol.

## Quick Start

Below is a step-by-step guide on setting up the Qiscus RTC SDK for the first time

### Dependency

Add to your project build.gradle

```groovy
allprojects {
  repositories {
    maven { url "https://artifactory.qiscus.com/artifactory/qiscus-library-open-source" }
  }
}
```

```groovy
dependencies {
  implementation 'com.qiscus.sdk:call-rtc:1.2.0'
}
```

### Permission

Add to your project AndroidManifest.xml

```xml
<uses-feature android:name="android.hardware.camera"/>
<uses-feature android:name="android.hardware.camera.autofocus"/>
<uses-feature android:glEsVersion="0x00020000" android:required="true"/>
    
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## Authentication

### Init Qiscus

Init Qiscus at your application

Parameters:
* context: context
* app_id: String
* app_secret: String

```java
public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        QiscusRTC.init(this, app_id, app_secret);
    }
}
```
To get your `app_id` and `app_secret`, please [contact us](https://www.qiscus.com/contactus).

### Init with custom host

Qiscus also provides on-premise package, so you can host signaling server on your own network. Please [contact us](https://www.qiscus.com/contactus) to get further information.

Parameters:
* context: context
* app_id: String
* app_secret: String
* host: String

```java
public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        QiscusRTC.init(this, app_id, app_secret, host);
    }
}
```

## Method

### Register User

Before user can start call each other, they must register the user to our server

Parameters:
* username: String
* displayName: String
* avatarUrl: String

```java
QiscusRTC.register(username, displayName, avatarUrl);
```

Start call object:
* roomId: String
* callAs: Enum QiscusRTC.CallAs.CALLER / QiscusRTC.CallAs.CALLEE
* callType: Enum QiscusRTC.CallType.VOICE / QiscusRTC.CallType.VIDEO
* callerUsername: String
* calleeUsername: String
* callerDisplayName: String
* calleeAvatarUrl: String

### Start Call

#### Start voice call

```java
QiscusRTC.CallActivityBuilder.buildCallWith(roomId)
                            .setCallAs(QiscusRTC.CallAs.CALLER)
                            .setCallType(QiscusRTC.CallType.VOICE)
                            .setCallerUsername(QiscusRTC.getUser())
                            .setCalleeUsername(calleeUsername)
                            .setCalleeDisplayName(calleeDisplayName)
                            .setCalleeDisplayAvatar(calleeAvatarUrl)
                            .show(this);
```
#### Start video call

```java
QiscusRTC.CallActivityBuilder.buildCallWith(roomId)
                            .setCallAs(QiscusRTC.CallAs.CALLER)
                            .setCallType(QiscusRTC.CallType.VIDEO)
                            .setCallerUsername(QiscusRTC.getUser())
                            .setCalleeUsername(calleeUsername)
                            .setCalleeDisplayName(calleeDisplayName)
                            .setCalleeDisplayAvatar(calleeAvatarUrl)
                            .show(this);
```

### Custom your call

You can custom your call notification, icon and callback button action with ```QiscusRTC.Call.getCallConfig()```

```java
QiscusRTC.Call.getCallConfig()
                .setBackgroundDrawble(R.drawable.bg_call)
                .setOngoingNotificationEnable(true)
                .setLargeOngoingNotifIcon(R.drawable.ic_call_white_24dp);
```

That's it! You just need 3 steps to build voice call in your apps.

### Try your call
You can try call to Android apps and Web App. Default user in our Web RTC is User3. You just click our example Web RTC Example below.

### Example
- [Basic example](https://github.com/qiscus/qiscus-rtc-sdk-android/blob/master/app/src/main/java/com/qiscus/rtc/sample/MainActivity.java)
- [Web Call Example](https://rtc.qiscus.com/chat-integration/)

### Proguard
If you want to use proguard, please read [this](app/proguard-rules.pro) file and you can add 
