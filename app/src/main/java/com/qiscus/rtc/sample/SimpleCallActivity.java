package com.qiscus.rtc.sample;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.qiscus.rtc.QiscusRTC;

public class SimpleCallActivity extends AppCompatActivity {
    private static final String TAG = SimpleCallActivity.class.getSimpleName();

    private EditText etTargetUsername;
    private EditText etRoomId;
    private Button btnVoiceCall;
    private Button btnVideoCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_call);

    }

    @Override
    public void onResume() {
        super.onResume();

        if (!QiscusRTC.hasSession()) {
            Intent intent = new Intent(SimpleCallActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }

        initView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public String generateRoomCall() {
        String room = "callId" + String.valueOf(System.currentTimeMillis());
        return room;
    }

    private void initView() {
        etTargetUsername = (EditText) findViewById(R.id.target_username);
        etRoomId = (EditText) findViewById(R.id.room_id);
        btnVoiceCall = (Button) findViewById(R.id.btn_voice_call);
        etRoomId.setText(generateRoomCall());
        btnVoiceCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!etTargetUsername.getText().toString().isEmpty() && !etRoomId.getText().toString().isEmpty()) {
                    QiscusRTC.buildCallWith(etRoomId.getText().toString())
                            .setCallAs(QiscusRTC.CallAs.CALLEE)
                            .setCallType(QiscusRTC.CallType.VOICE)
                            .setCalleeUsername(QiscusRTC.getUser())
                            .setCallerUsername(etTargetUsername.getText().toString())
                            .setCallerDisplayName(etTargetUsername.getText().toString())
                            .setCallerDisplayAvatar("http://dk6kcyuwrpkrj.cloudfront.net/wp-content/uploads/sites/45/2014/05/avatar-blank.jpg")
                            .show(SimpleCallActivity.this);
                } else {
                    Toast.makeText(SimpleCallActivity.this, "Target user and room id required", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnVideoCall = (Button) findViewById(R.id.btn_video_call);
        btnVideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!etTargetUsername.getText().toString().isEmpty() && !etRoomId.getText().toString().isEmpty()) {
                    QiscusRTC.buildCallWith(etRoomId.getText().toString())
                            .setCallAs(QiscusRTC.CallAs.CALLEE)
                            .setCallType(QiscusRTC.CallType.VIDEO)
                            .setCalleeUsername(QiscusRTC.getUser())
                            .setCallerUsername(etTargetUsername.getText().toString())
                            .setCallerDisplayName(etTargetUsername.getText().toString())
                            .setCallerDisplayAvatar("http://dk6kcyuwrpkrj.cloudfront.net/wp-content/uploads/sites/45/2014/05/avatar-blank.jpg")
                            .show(SimpleCallActivity.this);
                } else {
                    Toast.makeText(SimpleCallActivity.this, "Target user and room id required", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
