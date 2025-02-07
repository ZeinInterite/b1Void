package com.example.b1void.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.b1void.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;


public class AdmLogActivity extends AppCompatActivity {

    private EditText emailEdBox, passEdBox;
    private Button logBtn;
    private TextView ReturnTxt;
    private FirebaseAuth mAuth;
    private static final String TAG = "AdmLogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adm_log);

        emailEdBox = findViewById(R.id.emailEdBox);
        passEdBox = findViewById(R.id.passEdBox);
        logBtn = findViewById(R.id.logBtn);
        ReturnTxt = findViewById(R.id.ReturnTxt);

        mAuth = FirebaseAuth.getInstance();
        // Проверяем, авторизован ли пользователь
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Пользователь уже авторизован, переходим к AdmActivity
            updateUI();
        }

        logBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEdBox.getText().toString().trim();
                String password = passEdBox.getText().toString().trim();

                if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                    Toast.makeText(AdmLogActivity.this, "Введите email и пароль", Toast.LENGTH_SHORT).show();
                    return;
                }
                signInOrSignUp(email, password);
            }
        });

        ReturnTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }


    private void signInOrSignUp(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Вход успешен
                            Log.d(TAG, "signInWithEmail:success");
                            updateUI();
                        } else {
                            // Если вход не удался, пробуем зарегистрировать
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            signUp(email, password);
                        }
                    }
                });
    }

    private void signUp(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            updateUI();
                        } else {
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            String errorMessage = "Неверный логин или пароль";
                            if (task.getException() instanceof FirebaseAuthWeakPasswordException) {
                                errorMessage = "Слишком простой пароль.";
                            } else if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                errorMessage = "Неверный формат email";
                            } else if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                                errorMessage = "Пользователь с таким email уже существует";
                            }
                            Toast.makeText(AdmLogActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updateUI() {
        Intent intent = new Intent(AdmLogActivity.this, AdmActivity.class);
        startActivity(intent);
        finish();
    }
}
