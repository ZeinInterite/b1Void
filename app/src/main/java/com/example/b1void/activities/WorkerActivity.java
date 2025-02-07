package com.example.b1void.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.example.b1void.R;
import com.example.b1void.adapters.InspectionAdapter;
import com.example.b1void.models.Inspection;


import java.util.ArrayList;
import java.util.List;

public class WorkerActivity extends AppCompatActivity {

    private ListView inspectionList;
    private List<String> inspections = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker);



        List<Inspection> inspections = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            inspections.add(new Inspection(
                    "Наименование товара " + (i + 1),
                    "Поставщик " + (i + 1),
                    "2023-03-" + (i + 8)
            ));
        }

        ListView inspectionList = findViewById(R.id.inspectionList);
        InspectionAdapter adapter = new InspectionAdapter(this, inspections);
        inspectionList.setAdapter(adapter);;

        inspectionList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(WorkerActivity.this, "Вы выбрали: " + inspections.get(position), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
