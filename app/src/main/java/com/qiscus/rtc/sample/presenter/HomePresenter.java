package com.qiscus.rtc.sample.presenter;

import com.qiscus.sdk.data.model.QiscusChatRoom;

import java.util.List;

import data.ChatRoomRepository;

/**
 * Created by rajapulau on 3/13/18.
 */

public class HomePresenter {
    private View view;
    private ChatRoomRepository chatRoomRepository;

    public HomePresenter(View view, ChatRoomRepository chatRoomRepository) {
        this.view = view;
        this.chatRoomRepository = chatRoomRepository;
    }

    public void loadChatRooms() {
        chatRoomRepository.getChatRooms(chatRooms -> view.showChatRooms(chatRooms),
                throwable -> view.showErrorMessage(throwable.getMessage()));
    }

    public void createChatRoom() {
        view.showContactPage();
    }

    public void openChatRoom(QiscusChatRoom chatRoom) {
        view.showChatRoomPage(chatRoom);
    }

    public interface View {
        void showChatRooms(List<QiscusChatRoom> chatRooms);

        void showChatRoomPage(QiscusChatRoom chatRoom);

        void showContactPage();

        void showErrorMessage(String errorMessage);
    }
}
