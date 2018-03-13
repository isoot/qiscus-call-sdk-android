package com.qiscus.rtc.sample;

import android.content.Context;

import data.ChatRoomRepository;
import data.ChatRoomRepositoryImpl;

/**
 * Created by rajapulau on 3/13/18.
 */

public class AppComponent {
    private final ChatRoomRepository chatRoomRepository;

    AppComponent(Context context){
        chatRoomRepository = new ChatRoomRepositoryImpl();
    }

    public ChatRoomRepository getChatRoomRepository() {
        return chatRoomRepository;
    }
}
