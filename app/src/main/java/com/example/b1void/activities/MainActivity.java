package com.example.b1void.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.users.FullAccount;
import com.example.b1void.R;

public class MainActivity extends AppCompatActivity {

    private Button btnLog;
    private static final String ACCESS_TOKEN = "<ACCESS TOKEN>";
    private static final int AUTH_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
        DbxClientV2 client = new DbxClientV2(config, ACCESS_TOKEN);
//
//        FullAccount account = null;
//        try {
//            account = client.users().getCurrentAccount();
//        } catch (DbxException e) {
//            throw new RuntimeException(e);
//        }
//
//        System.out.println(account.getName().getDisplayName());

        btnLog = findViewById(R.id.logBtn);
        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, NavigationApp.class);
                startActivity(intent);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
