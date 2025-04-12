
package com.example.b1void.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.b1void.R;
import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.VideoResult;
import com.otaliastudios.cameraview.controls.Audio;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.controls.Flash;
import com.otaliastudios.cameraview.controls.Hdr;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.controls.WhiteBalance;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;
import com.otaliastudios.cameraview.size.SizeSelector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraV2Activity extends AppCompatActivity {

    private static final String PREFS_NAME = "CameraSettings";
    private static final String KEY_IMAGE_FORMAT = "imageFormat";
    private static final String KEY_ORIENTATION_LOCK = "orientationLock";
    private static final String KEY_WHITE_BALANCE = "whiteBalance";
    private static final String KEY_HDR = "hdr";
    private static final String KEY_AUDIO = "audio";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String KEY_EXPOSURE_COMPENSATION = "exposureCompensation";
    private CameraView cameraView;
    private ImageButton captureButton;
    private ImageButton videoCaptureButton;
    private ImageButton flipButton;
    private TextView timeTextView;
    private Spinner resolutionSpinner;
    private ImageButton flashButton;
    private ImageButton exposureButton;
    private LinearLayout exposureControlsLayout;
    private SeekBar exposureSeekBar;
    private TextView exposureValueTextView;
    private ImageButton settingsButton;
    private LinearLayout settingsControlsLayout;
    private SharedPreferences sharedPreferences;
    private String currentImageFormat = "JPEG";
    private boolean isOrientationLocked = false;
    private boolean isRecordingVideo = false;
    private int currentFlash = 0;
    private boolean isExposureControlsVisible = false;
    private boolean isSettingsControlsVisible = false;
    private List<Size> supportedResolutions;
    private File currentImageFile;
    private String customSavePath = null; // Переменная для хранения пути сохранения

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_v2);

        // Получаем путь из интента, если он был передан
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("save_path")) {
            customSavePath = intent.getStringExtra("save_path");
            Log.d("CameraV2Activity", "Custom save path received: " + customSavePath);
        }

        currentFlash = 0;

        flashButton = findViewById(R.id.flash_button);


        initViews();
        initListeners();
        checkPermissions();
        setupCameraListener();
        setupTimeThread();
        setupCameraGestures();
        loadSavedSettings();
        updateLastImagePreview();
        setupExposureControls(); // Инициализация элементов управления экспозицией
    }

    private void initViews() {
        cameraView = findViewById(R.id.camera);
        cameraView.setLifecycleOwner(this);
        captureButton = findViewById(R.id.capture_button);
        videoCaptureButton = findViewById(R.id.video_capture);
        flipButton = findViewById(R.id.flip_camera);
        timeTextView = findViewById(R.id.time_text_view);
        resolutionSpinner = findViewById(R.id.resolution_spinner);
        settingsButton = findViewById(R.id.settings_button);
        settingsControlsLayout = findViewById(R.id.settings_controls_layout);
        flashButton = findViewById(R.id.flash_button);
        exposureButton = findViewById(R.id.exposure_button);
        exposureControlsLayout = findViewById(R.id.exposure_controls_layout);
        exposureSeekBar = findViewById(R.id.exposure_seek_bar);
        exposureValueTextView = findViewById(R.id.exposure_value_text_view);

        cameraView.setFlash(Flash.OFF);
        exposureControlsLayout.setVisibility(View.GONE);
        settingsControlsLayout.setVisibility(View.GONE);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void initListeners() {
        exposureButton.setOnClickListener(v -> toggleVisibility(exposureControlsLayout, isExposureControlsVisible));
        settingsButton.setOnClickListener(v -> toggleVisibility(settingsControlsLayout, isSettingsControlsVisible));
        flashButton.setOnClickListener(v -> {

            switch (currentFlash) {
                case 0:
                    flashButton.setImageResource(R.drawable.flash_on);
                    cameraView.setFlash(Flash.ON);
                    break;
                case 1:
                    cameraView.setFlash(Flash.OFF);
                    flashButton.setImageResource(R.drawable.flash_off);
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
        captureButton.setOnClickListener(v -> captureImage());
        videoCaptureButton.setOnClickListener(v -> toggleVideoCapture());
        flipButton.setOnClickListener(v -> flipCamera());
        findViewById(R.id.last_image_preview).setOnClickListener(v -> openImagePreview());
    }

    private void toggleVisibility(View view, boolean isVisible) {
        if (view.getId() == R.id.exposure_controls_layout) {
            isExposureControlsVisible = !isExposureControlsVisible;
            view.setVisibility(isExposureControlsVisible ? View.VISIBLE : View.GONE);
        } else if (view.getId() == R.id.settings_controls_layout) {
            isSettingsControlsVisible = !isSettingsControlsVisible;
            view.setVisibility(isSettingsControlsVisible ? View.VISIBLE : View.GONE);
            setupSettingsControls();
        }
    }

    private void captureImage() {
        cameraView.setMode(Mode.PICTURE);
        cameraView.takePicture();
    }

    private void toggleVideoCapture() {
        if (isRecordingVideo) {
            cameraView.stopVideo();
            cameraView.setMode(Mode.PICTURE);
            isRecordingVideo = false;
        } else {
            try {
                cameraView.setMode(Mode.VIDEO);
                File videoDir = new File(getFilesDir(), "InspectorAppFolder/saved_videos");
                if (!videoDir.exists()) {
                    videoDir.mkdirs();
                }
                File file = new File(videoDir, "video_" + System.currentTimeMillis() + ".mp4");
                cameraView.takeVideo(file);
                isRecordingVideo = true;
            } catch (Exception e) {
                Log.e("CameraError", "Error starting video recording: " + e.getMessage());
                Toast.makeText(CameraV2Activity.this, "Failed to start video recording.", Toast.LENGTH_SHORT).show();
                cameraView.setMode(Mode.PICTURE);
                isRecordingVideo = false;
            }
        }
        updateVideoCaptureButtonIcon();
    }

    private void flipCamera() {
        cameraView.setFacing(cameraView.getFacing() == Facing.BACK ? Facing.FRONT : Facing.BACK);
    }

    private void openImagePreview() {
        File directory = new File(getFilesDir(), "InspectorAppFolder/saved_images");
        ArrayList<String> imagePaths = getImagePaths(directory);

        if (!imagePaths.isEmpty()) {
            Intent intent = new Intent(CameraV2Activity.this, ImagePreviewActivity.class);
            intent.putStringArrayListExtra("image_paths", imagePaths);
            intent.putExtra("current_image_index", 0);
            startActivity(intent);
        } else {
            Toast.makeText(CameraV2Activity.this, "No images captured yet.", Toast.LENGTH_SHORT).show();
        }
    }

    private ArrayList<String> getImagePaths(File directory) {
        ArrayList<String> imagePaths = new ArrayList<>();
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File file : files) {
                    imagePaths.add(file.getAbsolutePath());
                }
            }
        }
        return imagePaths;
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void setupCameraListener() {
        cameraView.addCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(@NonNull PictureResult result) {
                result.toBitmap(bitmap -> {
                    if (bitmap != null) {
                        Bitmap stampedBitmap = addStampAndSignature(bitmap);
                        currentImageFile = saveImage(stampedBitmap);
                        if (currentImageFile != null) {
                            updateLastImagePreview();
                            Toast.makeText(CameraV2Activity.this, "Image saved to: " + currentImageFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
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
                File videoFile = result.getFile();
                Toast.makeText(CameraV2Activity.this, "Video saved to: " + videoFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                isRecordingVideo = false;
                updateVideoCaptureButtonIcon();
            }

            @Override
            public void onCameraOpened(@NonNull CameraOptions options) {
                super.onCameraOpened(options);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setupResolutionSpinner(options);
                }
                setupExposureControls(options);
            }

            @Override
            public void onCameraError(@NonNull CameraException error) {
                super.onCameraError(error);
                Log.e("CameraError", "Camera error: " + error.getMessage());
                Toast.makeText(CameraV2Activity.this, "Camera error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAutoFocusEnd(boolean successful, @NonNull PointF point) {
                super.onAutoFocusEnd(successful, point);
            }
        });
    }

    private void setupExposureControls() {
        exposureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float exposureValue = (progress - 50) / 50f; // Преобразование progress в диапазон -1.0 до 1.0
                    cameraView.setExposureCorrection(exposureValue);
                    exposureValueTextView.setText(String.format(Locale.getDefault(), "%.2f", exposureValue));

                    // Сохранение значения в SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putFloat(KEY_EXPOSURE_COMPENSATION, exposureValue);
                    editor.apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Загрузка сохраненного значения при инициализации
        float savedExposure = sharedPreferences.getFloat(KEY_EXPOSURE_COMPENSATION, 0f);
        int progress = (int) (savedExposure * 50 + 50); // Преобразование значения в progress SeekBar
        exposureSeekBar.setProgress(progress);
        exposureValueTextView.setText(String.format(Locale.getDefault(), "%.2f", savedExposure));
        cameraView.setExposureCorrection(savedExposure);
    }

    private void setupExposureControls(CameraOptions options) {
        if (options != null && options.isExposureCorrectionSupported()) {
            float minExposure = options.getExposureCorrectionMinValue();
            float maxExposure = options.getExposureCorrectionMaxValue();

            exposureSeekBar.setMax(100); // Устанавливаем максимальное значение SeekBar
            exposureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // Преобразуем значение progress в диапазон экспозиции
                        float exposureValue = minExposure + (maxExposure - minExposure) * (progress / 100f);
                        cameraView.setExposureCorrection(exposureValue);
                        exposureValueTextView.setText(String.format(Locale.getDefault(), "%.2f", exposureValue));

                        // Сохраняем значение в SharedPreferences
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putFloat(KEY_EXPOSURE_COMPENSATION, exposureValue);
                        editor.apply();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            // Загружаем сохраненное значение при инициализации
            float savedExposure = sharedPreferences.getFloat(KEY_EXPOSURE_COMPENSATION, 0f);
            int progress = (int) ((savedExposure - minExposure) / (maxExposure - minExposure) * 100);
            exposureSeekBar.setProgress(progress);
            exposureValueTextView.setText(String.format(Locale.getDefault(), "%.2f", savedExposure));
            cameraView.setExposureCorrection(savedExposure);

            // Делаем элементы управления экспозицией видимыми
            exposureControlsLayout.setVisibility(View.VISIBLE);
            isExposureControlsVisible = true;
        } else {
            exposureControlsLayout.setVisibility(View.GONE);
            isExposureControlsVisible = false;
            Toast.makeText(this, "Exposure correction not supported.", Toast.LENGTH_SHORT).show();
        }
    }


    private void setupTimeThread() {
        updateTimeDisplay();
        new Thread(() -> {
            while (!isFinishing()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(this::updateTimeDisplay);
            }
        }).start();
    }

    private void setupCameraGestures() {
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        cameraView.mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS);
    }

    private void setupSettingsControls() {
        Spinner imageFormatSpinner = findViewById(R.id.image_format_spinner);
        ArrayAdapter<CharSequence> imageFormatAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new CharSequence[]{"JPEG", "PNG"});
        imageFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageFormatSpinner.setAdapter(imageFormatAdapter);

        String currentImageFormat = sharedPreferences.getString(KEY_IMAGE_FORMAT, "JPEG");
        int imageFormatPosition = currentImageFormat.equals("JPEG") ? 0 : 1;
        imageFormatSpinner.setSelection(imageFormatPosition);

        imageFormatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedImageFormat = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_IMAGE_FORMAT, selectedImageFormat);
                editor.apply();
                CameraV2Activity.this.currentImageFormat = selectedImageFormat;
                Toast.makeText(CameraV2Activity.this, "Image Format saved: " + selectedImageFormat, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Switch orientationLockSwitch = findViewById(R.id.orientation_lock_switch);
        boolean isOrientationLocked = sharedPreferences.getBoolean(KEY_ORIENTATION_LOCK, false);
        orientationLockSwitch.setChecked(isOrientationLocked);

        orientationLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_ORIENTATION_LOCK, isChecked);
            editor.apply();
            this.isOrientationLocked = isChecked;
            cameraView.setRotation(this.isOrientationLocked ? 0 : -1);
            Toast.makeText(CameraV2Activity.this, "Orientation Lock saved: " + isChecked, Toast.LENGTH_SHORT).show();
        });

        Spinner whiteBalanceSpinner = findViewById(R.id.white_balance_spinner);
        ArrayAdapter<CharSequence> whiteBalanceAdapter = ArrayAdapter.createFromResource(
                this, R.array.white_balance_options, android.R.layout.simple_spinner_item);
        whiteBalanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        whiteBalanceSpinner.setAdapter(whiteBalanceAdapter);

        String currentWhiteBalance = sharedPreferences.getString(KEY_WHITE_BALANCE, "AUTO");
        int whiteBalancePosition = getWhiteBalancePosition(currentWhiteBalance);
        whiteBalanceSpinner.setSelection(whiteBalancePosition);

        whiteBalanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedWhiteBalance = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_WHITE_BALANCE, selectedWhiteBalance);
                editor.apply();
                setWhiteBalance(selectedWhiteBalance);
                Toast.makeText(CameraV2Activity.this, "White Balance saved: " + selectedWhiteBalance, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner hdrSpinner = findViewById(R.id.hdr_spinner);
        ArrayAdapter<CharSequence> hdrAdapter = ArrayAdapter.createFromResource(
                this, R.array.hdr_options, android.R.layout.simple_spinner_item);
        hdrAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hdrSpinner.setAdapter(hdrAdapter);

        String currentHDR = sharedPreferences.getString(KEY_HDR, "OFF");
        int hdrPosition = currentHDR.equals("OFF") ? 0 : 1;
        hdrSpinner.setSelection(hdrPosition);

        hdrSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedHDR = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_HDR, selectedHDR);
                editor.apply();
                cameraView.setHdr(selectedHDR.equals("OFF") ? Hdr.OFF : Hdr.ON);
                Toast.makeText(CameraV2Activity.this, "HDR saved: " + selectedHDR, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner audioSpinner = findViewById(R.id.audio_spinner);
        ArrayAdapter<CharSequence> audioAdapter = ArrayAdapter.createFromResource(
                this, R.array.audio_options, android.R.layout.simple_spinner_item);
        audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioSpinner.setAdapter(audioAdapter);

        String currentAudio = sharedPreferences.getString(KEY_AUDIO, "OFF");
        int audioPosition = getAudioPosition(currentAudio);
        audioSpinner.setSelection(audioPosition);

        audioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedAudio = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_AUDIO, selectedAudio);
                editor.apply();
                setAudio(selectedAudio);
                Toast.makeText(CameraV2Activity.this, "Audio saved: " + selectedAudio, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private int getWhiteBalancePosition(String whiteBalance) {
        switch (whiteBalance) {
            case "INCANDESCENT":
                return 1;
            case "FLUORESCENT":
                return 2;
            case "DAYLIGHT":
                return 3;
            case "CLOUDY":
                return 4;
            default:
                return 0;
        }
    }

    private void setWhiteBalance(String whiteBalance) {
        switch (whiteBalance) {
            case "INCANDESCENT":
                cameraView.setWhiteBalance(WhiteBalance.INCANDESCENT);
                break;
            case "FLUORESCENT":
                cameraView.setWhiteBalance(WhiteBalance.FLUORESCENT);
                break;
            case "DAYLIGHT":
                cameraView.setWhiteBalance(WhiteBalance.DAYLIGHT);
                break;
            case "CLOUDY":
                cameraView.setWhiteBalance(WhiteBalance.CLOUDY);
                break;
            default:
                cameraView.setWhiteBalance(WhiteBalance.AUTO);
                break;
        }
    }

    private int getAudioPosition(String audio) {
        switch (audio) {
            case "ON":
                return 1;
            case "MONO":
                return 2;
            case "STEREO":
                return 3;
            default:
                return 0;
        }
    }

    private void setAudio(String audio) {
        switch (audio) {
            case "ON":
                cameraView.setAudio(Audio.ON);
                break;
            case "MONO":
                cameraView.setAudio(Audio.MONO);
                break;
            case "STEREO":
                cameraView.setAudio(Audio.STEREO);
                break;
            default:
                cameraView.setAudio(Audio.OFF);
                break;
        }
    }

    private void loadSavedSettings() {
        currentImageFormat = sharedPreferences.getString(KEY_IMAGE_FORMAT, "JPEG");
        isOrientationLocked = sharedPreferences.getBoolean(KEY_ORIENTATION_LOCK, false);
        cameraView.setRotation(isOrientationLocked ? 0 : -1);
        setWhiteBalance(sharedPreferences.getString(KEY_WHITE_BALANCE, "AUTO"));
        cameraView.setHdr(sharedPreferences.getString(KEY_HDR, "OFF").equals("OFF") ? Hdr.OFF : Hdr.ON);
        setAudio(sharedPreferences.getString(KEY_AUDIO, "OFF"));

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

        String dateFormat = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
        String timestamp = sdf.format(new Date());
        canvas.drawText(timestamp, 20, 150, paint);

        return mutableBitmap;
    }

    private File saveImage(Bitmap finalBitmap) {
        File file = null; // Initialize file to null
        try {
            if (customSavePath != null && !customSavePath.isEmpty()) {
                // Используем переданный путь для сохранения
                File customDir = new File(customSavePath);
                // Check if the custom path is a directory
                if (customDir.isDirectory()) {
                    // If it is a directory, create a file inside it with a unique name
                    String fileName = "Image-" + System.currentTimeMillis() + (currentImageFormat.equals("PNG") ? ".png" : ".jpg");
                    file = new File(customDir, fileName); // Save inside the directory
                } else {
                    // If the custom path is a file, use it directly
                    file = customDir;
                }

                File parentDir = file.getParentFile();

                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        Log.e("CameraError", "Failed to create directory: " + parentDir.getAbsolutePath());
                        Toast.makeText(this, "Failed to create directory.", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                }


            } else {
                // Используем стандартный путь сохранения
                File myDir = new File(getFilesDir(), "InspectorAppFolder/saved_images");
                if (!myDir.exists()) {
                    if (!myDir.mkdirs()) {
                        Log.e("CameraError", "Failed to create directory: " + myDir.getAbsolutePath());
                        Toast.makeText(this, "Failed to create directory.", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                }

                String fname = "Image-" + System.currentTimeMillis();
                Bitmap.CompressFormat compressFormat;
                String fileExtension;

                if (currentImageFormat.equals("PNG")) {
                    compressFormat = Bitmap.CompressFormat.PNG;
                    fileExtension = ".png";
                } else {
                    compressFormat = Bitmap.CompressFormat.JPEG;
                    fileExtension = ".jpg";
                }

                fname += fileExtension;
                file = new File(myDir, fname);
            }


            FileOutputStream out = new FileOutputStream(file);
            Bitmap.CompressFormat compressFormat = currentImageFormat.equals("PNG") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            finalBitmap.compress(compressFormat, 100, out);
            out.flush();
            out.close();
            return file;

        } catch (IOException e) {
            Log.e("CameraError", "Error saving image: " + e.getMessage() + " Path: " + (file != null ? file.getAbsolutePath() : "null"));
            Toast.makeText(this, "Error saving image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
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

        resolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Size selectedSize = supportedResolutions.get(position);
                cameraView.setPictureSize(new SizeSelector() {
                    @NonNull
                    @Override
                    public List<com.otaliastudios.cameraview.size.Size> select(@NonNull List<com.otaliastudios.cameraview.size.Size> source) {
                        List<com.otaliastudios.cameraview.size.Size> result = new ArrayList<>();
                        for (com.otaliastudios.cameraview.size.Size size : source) {
                            if (size.getWidth() == selectedSize.getWidth() && size.getHeight() == selectedSize.getHeight()) {
                                result.add(size);
                                break;
                            }
                        }
                        return result;
                    }

                });

                updateCameraViewSize(selectedSize.getWidth(), selectedSize.getHeight());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateCameraViewSize(int cameraWidth, int cameraHeight) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        float cameraRatio = (float) cameraWidth / cameraHeight;
        float screenRatio = (float) screenWidth / screenHeight;

        int newWidth;
        int newHeight;

        if (cameraRatio > screenRatio) {
            newWidth = screenWidth;
            newHeight = (int) (screenWidth / cameraRatio);
        } else {
            newHeight = screenHeight;
            newWidth = (int) (screenHeight * cameraRatio);
        }

        ViewGroup.LayoutParams layoutParams = cameraView.getLayoutParams();
        layoutParams.width = newWidth;
        layoutParams.height = newHeight;
        cameraView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
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

    private void updateLastImagePreview() {
        ImageButton lastImagePreview = findViewById(R.id.last_image_preview);
        File directory = new File(getFilesDir(), "InspectorAppFolder/saved_images");
        File lastFile = null;

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                lastFile = files[0];
            }
        }

        if (lastFile != null && lastFile.exists()) {
            Bitmap myBitmap = BitmapFactory.decodeFile(lastFile.getAbsolutePath());
            lastImagePreview.setImageBitmap(myBitmap);
        } else {
            lastImagePreview.setImageResource(R.drawable.def_insp_img);
        }
    }

    private void updateVideoCaptureButtonIcon() {
        videoCaptureButton.setImageResource(isRecordingVideo ? R.drawable.ic_stop_video : R.drawable.ic_videocam);
    }
}
