
package com.example.b1void.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.users.FullAccount;
import com.example.b1void.R;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final String APP_KEY = "pozl63episyy7so"; // Ключ Dropbox
    private DbxClientV2 dropboxClient; // Клиент Dropbox
    private ProgressBar progressBar; // Прогресс-бар

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.splash_progress); // Находим прогресс-бар

        if (hasAccessToken()) {
            showProgressBar();
            initializeDropboxClientAsync();
        } else {
            authenticateDropbox(); // Запускаем аутентификацию
        }
    }

    private boolean hasAccessToken() {
        SharedPreferences prefs = getSharedPreferences("tokensPrefs", MODE_PRIVATE);
        String token = prefs.getString("accessToken", null);
        return token != null && !token.isEmpty();
    }

    private String getAccessTokenFromPreferences() {
        SharedPreferences prefs = getSharedPreferences("tokensPrefs", MODE_PRIVATE);
        return prefs.getString("accessToken", null);
    }

    private void authenticateDropbox() {
        Auth.startOAuth2Authentication(this, APP_KEY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обрабатываем результат аутентификации
        if (Auth.getOAuth2Token() != null) {
            String accessToken = Auth.getOAuth2Token();
            Log.d(TAG, "Dropbox Access Token: " + accessToken);

            saveAccessTokenToPreferences(accessToken);
            showProgressBar();
            initializeDropboxClientAsync();
        }
    }

    private void saveAccessTokenToPreferences(String accessToken) {
        SharedPreferences prefs = getSharedPreferences("tokensPrefs", MODE_PRIVATE);
        prefs.edit().putString("accessToken", accessToken).apply();
    }

    private void initializeDropboxClientAsync() {
        new Thread(() -> {
            initializeDropboxClient();
            if(dropboxClient != null) {
                getUserAccount();
            } else {
                runOnUiThread(this::hideProgressBar);
                Log.e(TAG, "Ошибка инициализации Dropbox client.");
            }

        }).start();
    }



    private void initializeDropboxClient() {
        SharedPreferences prefs = getSharedPreferences("tokensPrefs", MODE_PRIVATE);
        String token = prefs.getString("accessToken", null);
        if (token == null) {
            Log.e(TAG, "Access token не найден");
            dropboxClient = null;
            return;
        }
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/DiplomGazprom").build();
        dropboxClient = new DbxClientV2(config, token);
        testDropboxToken();
    }

    private void testDropboxToken() {
        new Thread(() -> {
            try {
                dropboxClient.users().getCurrentAccount();
                getUserAccount();
            } catch (DbxException e) {
                Log.e(TAG, "Ошибка тестирования токена Dropbox: " + e.getMessage());
                dropboxClient = null;
                SharedPreferences prefs = getSharedPreferences("tokensPrefs", MODE_PRIVATE);
                prefs.edit().remove("accessToken").apply();
                runOnUiThread(() -> {
                    hideProgressBar();
                    authenticateDropbox();
                });
            }
        }).start();
    }


    private void getUserAccount() {
        new Thread(() -> {
            try {
                if (dropboxClient == null) {
                    Log.e(TAG, "Dropbox client не инициализирован");
                    runOnUiThread(this::hideProgressBar);
                    return;
                }
                FullAccount account = dropboxClient.users().getCurrentAccount();
                Log.d(TAG, "Dropbox Account Name: " + account.getName().getDisplayName());
                runOnUiThread(this::navigateToFileManager);
            } catch (DbxException e) {
                Log.e(TAG, "Ошибка получения данных аккаунта: " + e.getMessage());
                runOnUiThread(() -> {
                    hideProgressBar();
                    getSharedPreferences("tokensPrefs", MODE_PRIVATE).edit().remove("accessToken").apply();
                    authenticateDropbox();
                });
            }
        }).start();
    }

    private void navigateToFileManager() {
        startActivity(new Intent(this, FileManagerActivity.class));
        finish();
    }

    private void showProgressBar() {
        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
    }

    private void hideProgressBar() {
        runOnUiThread(() -> progressBar.setVisibility(View.INVISIBLE));
    }
}
