package com.example.b1void.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.b1void.R;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.splash_progress);

        // Navigate immediately; no artificial blocking work on startup
        handler.post(() -> {
            if (!isFinishing()) {
                progressBar.setVisibility(View.INVISIBLE);
                Intent intent = new Intent(SplashActivity.this, FileManagerActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}

