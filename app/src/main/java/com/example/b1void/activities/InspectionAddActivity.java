package com.example.b1void.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import com.example.b1void.R;

public class InspectionAddActivity extends AppCompatActivity {

    private ImageButton takePhotoBtn;
    private Button addDataBtn;
    private Button saveDataBtn;
    private String currentDirectoryPath; // Для хранения полученного пути

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspection_add);

        takePhotoBtn = findViewById(R.id.takePhotoBtn);
        addDataBtn = findViewById(R.id.addBtn);
        saveDataBtn = findViewById(R.id.saveBtn);

        // Получаем путь из intent
        currentDirectoryPath = getIntent().getStringExtra("current_directory");

        takePhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(InspectionAddActivity.this, CameraV2Activity.class);
                intent.putExtra("current_directory", currentDirectoryPath); // Передаем в CameraActivity
                startActivity(intent);
            }
        });

        addDataBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Обработка добавления данных
            }
        });

        saveDataBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Обработка сохранения данных
            }
        });
    }
}
