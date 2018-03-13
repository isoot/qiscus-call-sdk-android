package com.qiscus.rtc.sample.integration;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.Toast;

import com.qiscus.rtc.sample.R;
import com.qiscus.rtc.sample.SampleApplication;
import com.qiscus.rtc.sample.adapter.ContactAdapter;
import com.qiscus.rtc.sample.adapter.OnItemClickListener;
import com.qiscus.rtc.sample.model.User;
import com.qiscus.rtc.sample.presenter.ContactPresenter;
import com.qiscus.sdk.data.model.QiscusChatRoom;

import java.util.List;

public class ListContactActivity extends AppCompatActivity implements ContactPresenter.View, OnItemClickListener {

    private RecyclerView recyclerView;
    private ContactAdapter contactAdapter;

    private ContactPresenter contactPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_contact);

        findViewById(R.id.back).setOnClickListener(v -> onBackPressed());
        recyclerView = findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        contactAdapter = new ContactAdapter(this);
        contactAdapter.setOnItemClickListener(this);
        recyclerView.setAdapter(contactAdapter);

        contactPresenter = new ContactPresenter(this,
                SampleApplication.getInstance().getComponent().getUserRepository(),
                SampleApplication.getInstance().getComponent().getChatRoomRepository());
        contactPresenter.loadContacts();
    }

    @Override
    public void onItemClick(int position) {
        contactPresenter.createRoom(contactAdapter.getData().get(position));
    }

    @Override
    public void showContacts(List<User> contacts) {
        contactAdapter.addOrUpdate(contacts);
    }

    @Override
    public void showChatRoomPage(QiscusChatRoom chatRoom) {
        startActivity(ChatActivity.generateIntent(this, chatRoom));
    }

    @Override
    public void showErrorMessage(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }
}
