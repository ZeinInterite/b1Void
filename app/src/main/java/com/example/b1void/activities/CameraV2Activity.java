
package com.example.b1void.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.b1void.R;
import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.*;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.VideoResult;
import com.otaliastudios.cameraview.controls.Audio;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.controls.Flash;
import com.otaliastudios.cameraview.controls.Hdr;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.controls.PictureFormat;
import com.otaliastudios.cameraview.controls.WhiteBalance;
import com.otaliastudios.cameraview.filter.Filters;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;
import com.otaliastudios.cameraview.size.SizeSelector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraV2Activity extends AppCompatActivity {

    private CameraView cameraView;
    private ImageButton captureButton;
    private ImageButton flipButton;
    private TextView timeTextView;
    private EditText signatureEditText;
    private int signatureTextSize = 50;
    private boolean isOrientationLocked = false;
    private boolean isManualFocus = false;
    private Spinner resolutionSpinner;
    private List<Size> supportedResolutions;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private File currentImageFile;
    private ImageButton flashButton;
    private int currentFlash;
    private ImageButton exposureButton;
    private LinearLayout exposureControlsLayout;
    private SeekBar exposureSeekBar;
    private TextView exposureValueTextView;

    private boolean isExposureControlsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_v2);

        cameraView = findViewById(R.id.camera);
        cameraView.setLifecycleOwner(this);
        captureButton = findViewById(R.id.capture_button);
        flipButton = findViewById(R.id.flip_camera);
        timeTextView = findViewById(R.id.time_text_view);
        resolutionSpinner = findViewById(R.id.resolution_spinner);
        currentFlash = 0;

        flashButton = findViewById(R.id.flash_button);
        cameraView.setFlash(Flash.OFF);

        // Инициализация новых элементов управления экспозицией
        exposureButton = findViewById(R.id.exposure_button);
        exposureControlsLayout = findViewById(R.id.exposure_controls_layout);
        exposureSeekBar = findViewById(R.id.exposure_seek_bar);
        exposureValueTextView = findViewById(R.id.exposure_value_text_view);

        // Скрываем элементы управления экспозицией при старте
        exposureControlsLayout.setVisibility(View.GONE);

        // Обработчик нажатия на кнопку экспозиции
        exposureButton.setOnClickListener(v -> {
            isExposureControlsVisible = !isExposureControlsVisible;
            exposureControlsLayout.setVisibility(isExposureControlsVisible ? View.VISIBLE : View.GONE);

            if (isExposureControlsVisible) {
                setupExposureControl();
            }
        });

        // здесь, епта, спрашиваем у дрочилы разрешения октрыть камеру
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }

        cameraView.addCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(@NonNull PictureResult result) {
                result.toBitmap(bitmap -> {
                    if (bitmap != null) {
                        Bitmap stampedBitmap = addStampAndSignature(bitmap);
                        currentImageFile = saveImage(stampedBitmap);
                        if (currentImageFile != null) {

                            Intent intent = new Intent(CameraV2Activity.this, EditImageActivity.class);
                            intent.putExtra("imagePath", currentImageFile.getAbsolutePath());
                            startActivity(intent);
                            finish();
                        } else {
                            Log.e("CameraError", "Failed to save image.");
                            Toast.makeText(CameraV2Activity.this, "Failed to capture and process image.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e("CameraError", "Failed to decode Bitmap from PictureResult");
                        Toast.makeText(CameraV2Activity.this, "Failed to capture and process image.", Toast.LENGTH_SHORT).show();
                    }
                });


            }

            @Override
            public void onVideoTaken(@NonNull VideoResult result) {
                //  Снимаем ебальничек на видео
                File videoFile = result.getFile();
                Toast.makeText(CameraV2Activity.this, "Video saved to: " + videoFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCameraOpened(@NonNull CameraOptions options) {
                // Camera was opened
                super.onCameraOpened(options);
                // Initialize the resolution Spinner after the camera is opened
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setupResolutionSpinner(options);
                }
            }

            @Override
            public void onCameraClosed() {
                // Camera was closed
                super.onCameraClosed();
            }

            @Override
            public void onCameraError(@NonNull CameraException error) {
                // Camera error occurred
                super.onCameraError(error);
                Log.e("CameraError", "Camera error: " + error.getMessage());
                Toast.makeText(CameraV2Activity.this, "Camera error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAutoFocusStart(@NonNull PointF point) {
                super.onAutoFocusStart(point);
            }

            @Override
            public void onAutoFocusEnd(boolean successful, @NonNull PointF point) {
                super.onAutoFocusEnd(successful, point);
            }
        });

        flashButton.setOnClickListener(v -> {

            switch (currentFlash) {
                case 0:
                    flashButton.setImageResource(R.drawable.flash_on);
                    cameraView.setFlash(Flash.ON);
                    break;
                case 1:
                    cameraView.setFlash(Flash.OFF);
                    flashButton.setImageResource(R.drawable.flash_off);
                    // Replace with your "flash off" icon
                    break;
                case 2:
                    flashButton.setImageResource(R.drawable.flash_auto);
                    cameraView.setFlash(Flash.AUTO);
                    break;
                case 3:
                    currentFlash = -1;
                    flashButton.setImageResource(R.drawable.flash_torch);
                    cameraView.setFlash(Flash.TORCH);

                    break;
            }
            currentFlash += 1;

        });

        // Set up capture button
        captureButton.setOnClickListener(v -> {
            if (cameraView.getMode() == Mode.PICTURE) {
                cameraView.takePicture();
            } else {
                // For video, start or stop recording
                if (cameraView.isTakingVideo()) {
                    cameraView.stopVideo();
                } else {
                    File videoDir = new File(getFilesDir(), "InspectorAppFolder/saved_videos"); // Use internal storage
                    if (!videoDir.exists()) {
                        videoDir.mkdirs();
                    }
                    File file = new File(videoDir, "video_" + System.currentTimeMillis() + ".mp4");
                    cameraView.takeVideo(file);
                }
            }
        });

        flipButton.setOnClickListener(v -> {
            if (cameraView.getFacing() == Facing.BACK) {
                cameraView.setFacing(Facing.FRONT);
            } else {
                cameraView.setFacing(Facing.BACK);
            }
        });
        // Set up time display
        updateTimeDisplay();
        Thread timeThread = new Thread(() -> {
            while (!isFinishing()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(this::updateTimeDisplay);
            }
        });
        timeThread.start();

        // Initial setup
        setupCameraGestures();
        setupManualFocus();
        configureCameraSettings();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraView.open();
            } else {
                Toast.makeText(this, "Permissions denied. Camera functionality may be limited.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void updateTimeDisplay() {
        String timeFormat = "HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat, Locale.getDefault());
        String currentTime = sdf.format(new Date());
        timeTextView.setText(currentTime);
        timeTextView.setTextSize(20);
    }


    private Bitmap addStampAndSignature(Bitmap bitmap) {
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(150);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // Add timestamp
        String dateFormat = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
        String timestamp = sdf.format(new Date());
        canvas.drawText(timestamp, 20, 150, paint);

        // Add signature
//        paint.setTextSize(signatureTextSize);
//        String signature = signatureEditText.getText().toString();
//        canvas.drawText(signature, 20, 100, paint); // Adjust position as needed

        return mutableBitmap;
    }

    private File saveImage(Bitmap finalBitmap) {
        File myDir = new File(getFilesDir(), "InspectorAppFolder/saved_images"); // Use internal storage
        if (!myDir.exists()) {
            myDir.mkdirs();
        }
        String fname = "Image-" + System.currentTimeMillis() + ".jpg";
        File file = new File(myDir, fname);
        try {
            FileOutputStream out = new FileOutputStream(file);
            int imageQuality = 100;
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, imageQuality, out);
            out.flush();
            out.close();
            Toast.makeText(this, "Image saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return file; // Возвращаем File
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving image.", Toast.LENGTH_SHORT).show();
            return null; // Возвращаем null в случае ошибки
        }
    }



    private void setupCameraGestures() {
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        cameraView.mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS);
    }


    @SuppressLint("ClickableViewAccessibility")
    private void setupManualFocus() {
        cameraView.setOnTouchListener((v, event) -> {
            if (isManualFocus) {
                float x = event.getX() / v.getWidth();
                float y = event.getY() / v.getHeight();
                cameraView.startAutoFocus(x, y);
                return true;
            }
            return false;
        });
    }


    private void configureCameraSettings() {

        // 3. Подпись фото и выбор размера шрифта для этой подписи
//        configureSignature(); Перенести!

        // 7. Выбор формата изображения
        configureImageFormat();

        // 10. Блокировка ориентации фото
        configureOrientationLock();

        // 11. Настройка экспозиции
        //configureExposure();  Убрали отсюда

        // 13. Настройка Flash
//        configureFlash();

        // 14. Настройка White Balance
//        configureWhiteBalance();

        // 15. Настройка HDR
//        configureHDR(); Перенести!

        // 16. Настройка Audio
//        configureAudio();

        // 17. Настройка Camera Mode (Picture/Video)
        configureCameraMode();
    }

    private void configureSignature() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_signature_settings, null);
        builder.setView(dialogView);

        EditText signatureEditText = dialogView.findViewById(R.id.signature_edit_text);
        SeekBar textSizeSeekBar = dialogView.findViewById(R.id.text_size_seek_bar);
        signatureEditText.setText(this.signatureEditText.getText().toString());
        textSizeSeekBar.setProgress(signatureTextSize);

        TextView textSizePreview = dialogView.findViewById(R.id.text_size_preview);
        textSizePreview.setTextSize(signatureTextSize);

        textSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                signatureTextSize = progress;
                textSizePreview.setTextSize(signatureTextSize);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        builder.setTitle("Configure Signature")
                .setPositiveButton("Save", (dialog, which) -> {
                    String newSignature = signatureEditText.getText().toString();
                    this.signatureEditText.setText(newSignature);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void configureImageFormat() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Image Format")
                .setItems(new CharSequence[]{"JPEG", "DNG"}, (dialog, which) -> {
                    switch (which){
                        case 0: cameraView.setPictureFormat(PictureFormat.JPEG); break;
                        case 1: cameraView.setPictureFormat(PictureFormat.DNG); break;
                    }
                })
                .show();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupResolutionSpinner(CameraOptions options) {
        if (options == null) {
            Toast.makeText(this, "Camera not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        Collection<com.otaliastudios.cameraview.size.Size> supportedSizesCollection = options.getSupportedPictureSizes();
        if (supportedSizesCollection == null || supportedSizesCollection.isEmpty()) {
            Toast.makeText(this, "No supported resolutions.", Toast.LENGTH_SHORT).show();
            return;
        }

        supportedResolutions = new ArrayList<>();
        List<String> resolutionLabels = new ArrayList<>();

        for (com.otaliastudios.cameraview.size.Size s : supportedSizesCollection) {
            Size size = new Size(s.getWidth(), s.getHeight());
            supportedResolutions.add(size);
            resolutionLabels.add(size.getWidth() + "x" + size.getHeight());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, resolutionLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(adapter);

        resolutionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Size selectedSize = supportedResolutions.get(position);
                cameraView.setPictureSize(new SizeSelector() {
                    @NonNull
                    @Override
                    public List<com.otaliastudios.cameraview.size.Size> select(@NonNull List<com.otaliastudios.cameraview.size.Size> source) {
                        List<com.otaliastudios.cameraview.size.Size> result = new ArrayList<>();
                        for (com.otaliastudios.cameraview.size.Size size : source) {
                            if (size.getWidth() == selectedSize.getWidth() && size.getHeight() == selectedSize.getHeight()) {
                                result.add(size);
                                // Важно! Возвращаем размер, который соответствует выбранному.
                                break; //  Размер уже найден, незачем дальше перебирать
                            }
                        }
                        return result;
                    }

                });

                //  После установки разрешения, нужно обновить размер CameraView
                updateCameraViewSize(selectedSize.getWidth(), selectedSize.getHeight());
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void updateCameraViewSize(int width, int height) {
        //  Получаем текущие параметры layout
        ViewGroup.LayoutParams layoutParams = cameraView.getLayoutParams();

        //  Устанавливаем новые размеры
        layoutParams.width = width;
        layoutParams.height = height;

        //  Применяем изменения
        cameraView.setLayoutParams(layoutParams);
    }


    private void configureOrientationLock() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Lock Orientation")
                .setItems(new CharSequence[]{"Lock", "Unlock"}, (dialog, which) -> {
                    isOrientationLocked = (which == 0);
                    cameraView.setRotation(isOrientationLocked ? 0 : -1);
                })
                .show();
    }


    private void setupExposureControl() {
        CameraOptions options = cameraView.getCameraOptions();
        if (options == null || !options.isExposureCorrectionSupported()) {
            Toast.makeText(this, "Exposure correction not supported.", Toast.LENGTH_SHORT).show();
            return;
        }

        float minExposure = options.getExposureCorrectionMinValue();
        float maxExposure = options.getExposureCorrectionMaxValue();

        int smoothnessFactor = 100;

        int progressOffset = (int) Math.abs(minExposure * smoothnessFactor);
        int maxProgress = (int) ((maxExposure - minExposure) * smoothnessFactor);
        exposureSeekBar.setMax(maxProgress);

        exposureSeekBar.setProgress(progressOffset);
        exposureValueTextView.setText(String.valueOf(0.0f));
        cameraView.setExposureCorrection(0.0f);

        exposureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float exposureValue = (float) (progress - progressOffset) / smoothnessFactor;
                cameraView.setExposureCorrection(exposureValue);
                exposureValueTextView.setText(String.format(Locale.getDefault(), "%.2f", exposureValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }


    private void configureWhiteBalance() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select White Balance")
                .setItems(new CharSequence[]{"Auto", "Incandescent", "Fluorescent", "Daylight", "Cloudy"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            cameraView.setWhiteBalance(WhiteBalance.AUTO);
                            break;
                        case 1:
                            cameraView.setWhiteBalance(WhiteBalance.INCANDESCENT);
                            break;
                        case 2:
                            cameraView.setWhiteBalance(WhiteBalance.FLUORESCENT);
                            break;
                        case 3:
                            cameraView.setWhiteBalance(WhiteBalance.DAYLIGHT);
                            break;
                        case 4:
                            cameraView.setWhiteBalance(WhiteBalance.CLOUDY);
                            break;
                    }
                })
                .show();
    }

    private void configureHDR() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enable HDR")
                .setItems(new CharSequence[]{"Off", "On"}, (dialog, which) -> {
                    if (which == 0) {
                        cameraView.setHdr(Hdr.OFF);
                    } else {
                        cameraView.setHdr(Hdr.ON);
                    }

                })
                .show();
    }


    private void configureAudio() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enable Audio")
                .setItems(new CharSequence[]{"Off", "On", "Mono", "Stereo"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            cameraView.setAudio(Audio.OFF);
                            break;
                        case 1:
                            cameraView.setAudio(Audio.ON);
                            break;
                        case 2:
                            cameraView.setAudio(Audio.MONO);
                            break;
                        case 3:
                            cameraView.setAudio(Audio.STEREO);
                            break;
                    }
                })
                .show();
    }

    private void configureCameraMode() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Camera Mode")
                .setItems(new CharSequence[]{"Picture", "Video"}, (dialog, which) -> {
                    if (which == 0) {
                        cameraView.setMode(Mode.PICTURE);

                    } else {
                        cameraView.setMode(Mode.VIDEO);
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            cameraView.open();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraView.destroy();
    }

}
