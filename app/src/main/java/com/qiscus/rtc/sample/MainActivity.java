package com.qiscus.rtc.sample;

import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.qiscus.rtc.sample.integration.ChatActivity;
import com.qiscus.rtc.sample.integration.ContactActivity;
import com.qiscus.rtc.sample.simple.LoginActivity;
import com.qiscus.sdk.Qiscus;
import com.qiscus.sdk.data.model.QiscusAccount;
import com.qiscus.sdk.data.model.QiscusChatRoom;

import java.io.IOException;

import retrofit2.HttpException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Button simple;
    private Button integration;
    private Button logout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        simple = findViewById(R.id.btn_simple);
        integration = findViewById(R.id.btn_chat_integration);
        logout = findViewById(R.id.btn_logout);

        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                logout.setVisibility(View.GONE);
                Toast.makeText(getApplicationContext(), "Success Logout", Toast.LENGTH_SHORT).show();
                Qiscus.clearUser();
            }
        });
        simple.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });
        integration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                final View dialog = inflater.inflate(R.layout.dialog_login, null);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                alertDialogBuilder.setView(dialog);
                alertDialogBuilder.setCancelable(false);

                final AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();

                dialog.findViewById(R.id.login_user1).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Qiscus.setUser("user1_sample_call@example.com", "123")
                                .withUsername("User 1 Sample Call")
                                .save(new Qiscus.SetUserListener() {
                                    @Override
                                    public void onSuccess(QiscusAccount qiscusAccount) {
                                        Log.i(TAG, "Login chat with account: " + qiscusAccount);
                                        alertDialog.cancel();

                                        startActivity(new Intent(MainActivity.this, ContactActivity.class));
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        if (throwable instanceof HttpException) {
                                            HttpException e = (HttpException) throwable;

                                            try {
                                                String errorMessage = e.response().errorBody().string();
                                                Log.e(TAG, errorMessage);
                                            } catch (IOException er) {
                                                er.printStackTrace();
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
                dialog.findViewById(R.id.login_user2).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Qiscus.setUser("user2_sample_call@example.com", "123")
                                .withUsername("User 2 Sample Call")
                                .save(new Qiscus.SetUserListener() {
                                    @Override
                                    public void onSuccess(QiscusAccount qiscusAccount) {
                                        Log.i(TAG, "Login chat with account: " + qiscusAccount);
                                        alertDialog.cancel();

                                        startActivity(new Intent(MainActivity.this, ContactActivity.class));
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        if (throwable instanceof HttpException) {
                                            HttpException e = (HttpException) throwable;

                                            try {
                                                String errorMessage = e.response().errorBody().string();
                                                Log.e(TAG, errorMessage);
                                            } catch (IOException er) {
                                                er.printStackTrace();
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
                dialog.findViewById(R.id.login_user4).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Qiscus.setUser("user4_sample_call@example.com", "123")
                                .withUsername("User 4 Sample Call")
                                .save(new Qiscus.SetUserListener() {
                                    @Override
                                    public void onSuccess(QiscusAccount qiscusAccount) {
                                        Log.i(TAG, "Login chat with account: " + qiscusAccount);
                                        alertDialog.cancel();

                                        startActivity(new Intent(MainActivity.this, ContactActivity.class));
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        if (throwable instanceof HttpException) {
                                            HttpException e = (HttpException) throwable;

                                            try {
                                                String errorMessage = e.response().errorBody().string();
                                                Log.e(TAG, errorMessage);
                                            } catch (IOException er) {
                                                er.printStackTrace();
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
                dialog.findViewById(R.id.login_user5).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Qiscus.setUser("user5_sample_call@example.com", "123")
                                .withUsername("User 5 Sample Call")
                                .save(new Qiscus.SetUserListener() {
                                    @Override
                                    public void onSuccess(QiscusAccount qiscusAccount) {
                                        Log.i(TAG, "Login chat with account: " + qiscusAccount);
                                        alertDialog.cancel();

                                        startActivity(new Intent(MainActivity.this, ContactActivity.class));
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        if (throwable instanceof HttpException) {
                                            HttpException e = (HttpException) throwable;

                                            try {
                                                String errorMessage = e.response().errorBody().string();
                                                Log.e(TAG, errorMessage);
                                            } catch (IOException er) {
                                                er.printStackTrace();
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
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResumeChat: " + Qiscus.hasSetupUser());
        if (Qiscus.hasSetupUser()) {
            logout.setVisibility(View.VISIBLE);
            logout.setText("Logout as "+Qiscus.getQiscusAccount().getUsername());
        } else {
            logout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
