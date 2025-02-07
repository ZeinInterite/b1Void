package com.example.b1void.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.dropbox.core.android.Auth;
import com.example.b1void.R;

public class MainActivity extends AppCompatActivity {

    private Button btnLog;
    private static final String ACCESS_TOKEN_KEY = "access-token";
    private static final int AUTH_REQUEST_CODE = 1001; // Произвольный requestCode

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnLog = findViewById(R.id.logBtn);
        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, FileManagerActivity.class);
                startActivity(intent);
            }
        });

        checkAccessToken();
    }

    private void checkAccessToken() {
        SharedPreferences prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        String accessToken = prefs.getString(ACCESS_TOKEN_KEY, null);

        if (accessToken != null) {
            navigateToFileManagerActivity();
        } else {
            Auth.startOAuth2Authentication(this, getString(R.string.APP_KEY));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void navigateToFileManagerActivity() {
        startActivity(new Intent(this, FileManagerActivity.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == AUTH_REQUEST_CODE) {
            String accessToken = Auth.getOAuth2Token();
            if (accessToken != null) {
                SharedPreferences prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);
                prefs.edit().putString(ACCESS_TOKEN_KEY, accessToken).apply();
                navigateToFileManagerActivity();
            } else {
                Toast.makeText(this, "Авторизация не удалась", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
