package io.agora.jkzy_demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import io.agora.jkzy_demo.fragment.JoinChannelVideo;
import io.agora.jkzy_demo.fragment.LiveStreaming;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button btnSetting, btnEnter;
    private Button btnBroadcast;
    private FragmentManager fragmentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();

        btnSetting = findViewById(R.id.btn_setting);
        btnSetting.setOnClickListener(this);

        btnEnter = findViewById(R.id.btn_enter);
        btnEnter.setOnClickListener(this);

        btnBroadcast = findViewById(R.id.btn_broadcast);
        btnBroadcast.setOnClickListener(this);

    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (btnEnter.getVisibility() == View.GONE) {
            btnEnter.setVisibility(View.VISIBLE);
            btnSetting.setVisibility(View.VISIBLE);
            btnBroadcast.setVisibility(View.VISIBLE);
            fragmentManager.popBackStackImmediate();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_enter:
                btnEnter.setVisibility(View.GONE);
                btnSetting.setVisibility(View.GONE);
                btnBroadcast.setVisibility(View.GONE);
                Fragment joinVideo = new JoinChannelVideo();
                fragmentManager.beginTransaction()
                        .add(R.id.fragment_container, joinVideo, "joinVideo")
                        .addToBackStack(null)
                        .commit();
                break;
            case R.id.btn_setting:
                startActivity(new Intent(MainActivity.this, SettingActivity.class));
                break;
            case R.id.btn_broadcast:
                btnEnter.setVisibility(View.GONE);
                btnSetting.setVisibility(View.GONE);
                btnBroadcast.setVisibility(View.GONE);
                Fragment broadCast = new LiveStreaming();
                fragmentManager.beginTransaction()
                        .add(R.id.fragment_container, broadCast, "joinVideo")
                        .addToBackStack(null)
                        .commit();
                break;
        }
    }
}
