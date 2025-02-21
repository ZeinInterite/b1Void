package com.example.b1void.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.b1void.R;

import java.io.File;
import java.io.FileOutputStream;

public class EditImageActivity extends AppCompatActivity {

    private ImageView imageView;
    private SeekBar qualitySeekBar;
    private Button saveButton;
    private String imagePath;
    private int imageQuality = 90;
    private File currentImageFile;
    private Bitmap originalBitmap;

    private Handler handler = new Handler();
    private Runnable updateImageRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_image);

        imageView = findViewById(R.id.image_view);
        qualitySeekBar = findViewById(R.id.quality_seek_bar);
        saveButton = findViewById(R.id.save_button);

        imagePath = getIntent().getStringExtra("imagePath");
        if (imagePath == null) {
            Toast.makeText(this, "Error: Image path not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentImageFile = new File(imagePath);
        originalBitmap = BitmapFactory.decodeFile(imagePath);
        if (originalBitmap != null) {
            updateImageView(originalBitmap, imageQuality); // Initial display
        } else {
            Toast.makeText(this, "Error: Could not decode image.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        qualitySeekBar.setProgress(imageQuality);
        qualitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                imageQuality = progress;

                // Debounce
                handler.removeCallbacks(updateImageRunnable);
                updateImageRunnable = () -> new CompressImageTask().execute(imageQuality);
                handler.postDelayed(updateImageRunnable, 200); // 200ms delay
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        saveButton.setOnClickListener(v -> saveImage());
    }

    private class CompressImageTask extends AsyncTask<Integer, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Integer... params) {
            int quality = params[0];
            return compressBitmap(originalBitmap, quality);
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                imageView.setImageBitmap(result);
            } else {
                Toast.makeText(EditImageActivity.this, "Error compressing image.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private Bitmap compressBitmap(Bitmap bitmap, int quality) {
        try {
            // Compress to a byte array
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            byte[] bitmapdata = bos.toByteArray();

            // Decode the compressed byte array back to bitmap
            return BitmapFactory.decodeByteArray(bitmapdata, 0, bitmapdata.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveImage() {
        try {
            Bitmap imageBitmap = compressBitmap(originalBitmap, imageQuality);
            if (imageBitmap == null) {
                Toast.makeText(this, "Error compressing image for saving.", Toast.LENGTH_SHORT).show();
                return;
            }
            FileOutputStream out = new FileOutputStream(currentImageFile);
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, imageQuality, out);
            out.flush();
            out.close();

            Toast.makeText(this, "Image saved with quality: " + imageQuality, Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, CameraV2Activity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateImageView(Bitmap bitmap, int quality) {
        new CompressImageTask().execute(quality); // Initial image load
    }
}

