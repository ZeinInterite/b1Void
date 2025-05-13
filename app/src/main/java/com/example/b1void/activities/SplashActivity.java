package com.example.b1void.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.b1void.R;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar; // Крутилка прогресса, шоб юзер не ахуел, что ниче не происходит
    private int progressStatus = 0; // На каком мы этапе загрузки, типа 0-100
    private Handler handler = new Handler(); // Штука, шоб можно было из потока в UI лезть, а то будет ругаться

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.splash_progress); // Нашли эту хрень в разметке, можно с ней работать. Важно, чтоб в activity_splash.xml эта крутилка была и с id splash_progress,
        // а то код не взъебет где искать

        // Запускаем долгую задачу в отдельном потоке, а то UI зависнет нахуй
        new Thread(new Runnable() {
            public void run() {
                while (progressStatus < 100) {
                    progressStatus += 1; // Типа грузим че-то, на самом деле просто циферки крутим для вида

                    // Обновляем эту крутилку, шоб показывала прогресс. Не забываем про Handler, а то пизда
                    handler.post(new Runnable() {
                        public void run() {
                            progressBar.setProgress(progressStatus);
                        }
                    });
                    try {
                        // Ждем немного, а то слишком быстро все пролетит и не интересно будет
                        Thread.sleep(20); // Время в миллисекундах, подбери как нравится
                    } catch (InterruptedException e) {
                        e.printStackTrace(); // Ну это если поток сломался, всякое бывает
                    }
                }

                // Когда типа все загрузилось (progressStatus == 100)
                handler.post(new Runnable() {
                    public void run() {
                        // Тут можно прятать крутилку, делать че угодно.
                        progressBar.setVisibility(View.INVISIBLE); // Прячем эту хрень с глаз долой, из сердца вон

                        // intent хуйня для перехода в другое активити.
                        Intent intent = new Intent(SplashActivity.this, FileManagerActivity.class);
                        startActivity(intent);

                        finish(); // Закрываем SplashActivity, чтоб не висела в фоне и .зер не мог вернуться кнопкой "Назад"
                    }
                });
            }
        }).start(); // Погнали запускать поток, шоб все заработало
    }
}
