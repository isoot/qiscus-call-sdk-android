package com.qiscus.rtc.sample.integration;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import com.qiscus.rtc.sample.R;
import com.qiscus.sdk.Qiscus;
import com.qiscus.sdk.data.model.QiscusChatRoom;

import java.io.IOException;

import retrofit2.HttpException;

public class ContactActivity extends AppCompatActivity {
    private static final String TAG = ContactActivity.class.getSimpleName();

    private View user1;
    private View user2;
    private View user4;
    private View user5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);

        user1 = (RelativeLayout) findViewById(R.id.user1);
        user2 = (RelativeLayout) findViewById(R.id.user2);
        user4 = (RelativeLayout) findViewById(R.id.user4);
        user5 = (RelativeLayout) findViewById(R.id.user5);

        switch (Qiscus.getQiscusAccount().getEmail()) {
            case "user1_sample_call@example.com":
                user1.setVisibility(View.GONE);
                break;
            case "user2_sample_call@example.com":
                user2.setVisibility(View.GONE);
                break;
            case "user4_sample_call@example.com":
                user4.setVisibility(View.GONE);
                break;
            case "user5_sample_call@example.com":
                user5.setVisibility(View.GONE);
                break;
            default:
                break;
        }

        user1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Qiscus.buildChatRoomWith("user1_sample_call@example.com")
                        .build(new Qiscus.ChatBuilderListener() {
                            @Override
                            public void onSuccess(QiscusChatRoom qiscusChatRoom) {
                                startActivity(ChatActivity.generateIntent(ContactActivity.this, qiscusChatRoom));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                if (throwable instanceof HttpException) {
                                    HttpException e = (HttpException) throwable;

                                    try {
                                        String errorMessage = e.response().errorBody().string();
                                        Log.e(TAG, errorMessage);
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                    }
                                } else if (throwable instanceof IOException) {
                                    Log.e(TAG, "Can not connect to qiscus server.");
                                } else {
                                    Log.e(TAG, "Unexpected error.");
                                }
                            }
                        });
            }
        });
        user2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Qiscus.buildChatRoomWith("user2_sample_call@example.com")
                        .build(new Qiscus.ChatBuilderListener() {
                            @Override
                            public void onSuccess(QiscusChatRoom qiscusChatRoom) {
                                startActivity(ChatActivity.generateIntent(ContactActivity.this, qiscusChatRoom));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                if (throwable instanceof HttpException) {
                                    HttpException e = (HttpException) throwable;

                                    try {
                                        String errorMessage = e.response().errorBody().string();
                                        Log.e(TAG, errorMessage);
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                    }
                                } else if (throwable instanceof IOException) {
                                    Log.e(TAG, "Can not connect to qiscus server.");
                                } else {
                                    Log.e(TAG, "Unexpected error.");
                                }
                            }
                        });
            }
        });
        user4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Qiscus.buildChatRoomWith("user4_sample_call@example.com")
                        .build(new Qiscus.ChatBuilderListener() {
                            @Override
                            public void onSuccess(QiscusChatRoom qiscusChatRoom) {
                                startActivity(ChatActivity.generateIntent(ContactActivity.this, qiscusChatRoom));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                if (throwable instanceof HttpException) {
                                    HttpException e = (HttpException) throwable;

                                    try {
                                        String errorMessage = e.response().errorBody().string();
                                        Log.e(TAG, errorMessage);
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                    }
                                } else if (throwable instanceof IOException) {
                                    Log.e(TAG, "Can not connect to qiscus server.");
                                } else {
                                    Log.e(TAG, "Unexpected error.");
                                }
                            }
                        });
            }
        });
        user5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Qiscus.buildChatRoomWith("user5_sample_call@example.com")
                        .build(new Qiscus.ChatBuilderListener() {
                            @Override
                            public void onSuccess(QiscusChatRoom qiscusChatRoom) {
                                startActivity(ChatActivity.generateIntent(ContactActivity.this, qiscusChatRoom));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                if (throwable instanceof HttpException) {
                                    HttpException e = (HttpException) throwable;

                                    try {
                                        String errorMessage = e.response().errorBody().string();
                                        Log.e(TAG, errorMessage);
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                    }
                                } else if (throwable instanceof IOException) {
                                    Log.e(TAG, "Can not connect to qiscus server.");
                                } else {
                                    Log.e(TAG, "Unexpected error.");
                                }
                            }
                        });
            }
        });
    }
}
