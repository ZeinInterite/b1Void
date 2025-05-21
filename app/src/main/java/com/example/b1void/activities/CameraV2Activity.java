
package com.example.b1void.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

import yuku.ambilwarna.AmbilWarnaDialog;

public class CameraV2Activity extends AppCompatActivity {

    private static final String PREFS_NAME = "CameraSettings";
    private static final String KEY_IMAGE_FORMAT = "imageFormat";
    private static final String KEY_ORIENTATION_LOCK = "orientationLock";
    private static final String KEY_WHITE_BALANCE = "whiteBalance";
    private static final String KEY_HDR = "hdr";
    private static final String KEY_AUDIO = "audio";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String KEY_EXPOSURE_COMPENSATION = "exposureCompensation";
    private static final String KEY_RESOLUTION_SPINNER_POSITION = "resolution_spinner_position";

    // ЮАЙ элементы камеры.
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
    private String customSavePath = null;
    private FrameLayout cameraContainer;

    private FrameLayout stampContainer;
    private TextView currentStampView;
    private boolean isStampLocked = false;
    private Paint stampPaint = new Paint();
    private float initialStampSize = 60f;
    private float stampScaleFactor = 1f;
    private int currentStampColor = Color.RED;
    private EditText stampEditText;
    private LinearLayout stampControlsLayout;
    private boolean isStampModeActive = false;
    private ScaleGestureDetector scaleGestureDetector;
    private static final float MIN_STAMP_SCALE = 0.5f;
    private static final float MAX_STAMP_SCALE = 3.0f;
    private static final float INITIAL_STAMP_SIZE_DP = 14f;
    private PointF lastTouchPosition = new PointF();
    private boolean isScaling = false;
    private float initialDistance = 0f;
    private float initialStampScale = 1f;
    private SeekBar stampSizeProgressBar;
    private TextView stampSizeLabel;
    private String currentStampText = "";

    private static final String KEY_RESOLUTION_WIDTH = "resolution_width";
    private static final String KEY_RESOLUTION_HEIGHT = "resolution_height";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_v2);

        // Ловим путь сохранения, если его передали. Если нет - похеру, сохраняем в дефолтную папку.
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("save_path")) {
            customSavePath = intent.getStringExtra("save_path");
            Log.d("CameraV2Activity", "Custom save path received: " + customSavePath);
        }

        currentFlash = 0;
        initViews(); // Инициализируем всю хуйню с View
        initListeners(); // Цепляем обработчики нажатий
        checkPermissions(); // Чекаем, есть ли у дрочилы права на камеру и память
        setupCameraListener(); // Вешаем слушателя на камеру, чтобы ловить фотки/видео
        setupTimeThread(); // Запускаем поток, который обновляет время
        setupCameraGestures(); // Настраиваем жесты камеры (зум, фокус)
        loadSavedSettings(); // Загружаем сохраненные настройки камеры (формат, ориентация и т.д.)
        updateLastImagePreview(); // Обновляем превью последней фотки
        setupExposureControls(); // Настраиваем ползунок экспозиции

        stampSizeProgressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                stampScaleFactor = (float) progress / 100f; // Преобразуем progress в scaleFactor, который будет менять размер штампа
                stampScaleFactor = Math.max(MIN_STAMP_SCALE, Math.min(stampScaleFactor, MAX_STAMP_SCALE));

                if (currentStampView != null) {
                    currentStampView.setTextSize(TypedValue.COMPLEX_UNIT_PX, initialStampSize * stampScaleFactor);
                }

                // Обновляем текст label
                stampSizeLabel.setText("Размер: " + String.format("%.2f", stampScaleFactor));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        stampSizeLabel.setText("Размер: " + String.format("%.2f", stampScaleFactor));

        initialStampSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                INITIAL_STAMP_SIZE_DP,
                getResources().getDisplayMetrics()
        );

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                stampScaleFactor *= scaleFactor;
                stampScaleFactor = Math.max(MIN_STAMP_SCALE, Math.min(stampScaleFactor, MAX_STAMP_SCALE));

                if (currentStampView != null) {
                    currentStampView.setTextSize(TypedValue.COMPLEX_UNIT_PX, initialStampSize * stampScaleFactor);
                }
                return true;
            }
        });
    }

    // Инициализируем View-элементы
    private void initViews() {
        stampSizeProgressBar = findViewById(R.id.stamp_size_progress);
        stampSizeLabel = findViewById(R.id.stamp_size_label);

        stampSizeProgressBar.setProgress((int) (stampScaleFactor * 100));

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
        cameraContainer = findViewById(R.id.camera_container);

        stampContainer = findViewById(R.id.stamp_container);
        stampControlsLayout = findViewById(R.id.stamp_controls_layout);
        stampEditText = findViewById(R.id.stamp_edit_text);
        ImageButton stampButton = findViewById(R.id.stamp_button);
        ImageButton stampColorButton = findViewById(R.id.stamp_color_button);
        ImageButton stampLockButton = findViewById(R.id.stamp_lock_button);

        cameraView.setFlash(Flash.OFF);
        exposureControlsLayout.setVisibility(View.GONE);
        settingsControlsLayout.setVisibility(View.GONE);
        stampControlsLayout.setVisibility(View.GONE);
        stampEditText.setVisibility(View.GONE);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        stampPaint.setColor(currentStampColor);
        stampPaint.setTextSize(initialStampSize);
        stampPaint.setAntiAlias(true);
        stampPaint.setStyle(Paint.Style.FILL);
    }

    // Цепляем слушателей ко всем кнопкам и прочей хуйне
    private void initListeners() {
        exposureButton.setOnClickListener(v -> toggleVisibility(exposureControlsLayout, isExposureControlsVisible));
        settingsButton.setOnClickListener(v -> toggleVisibility(settingsControlsLayout, isSettingsControlsVisible));

        // Обработчик нажатия на кнопку "Вспышка"
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
                    cameraView.setFlash(Flash.AUTO);
                    flashButton.setImageResource(R.drawable.flash_auto);
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

        findViewById(R.id.stamp_button).setOnClickListener(v -> toggleStampMode());
        findViewById(R.id.stamp_color_button).setOnClickListener(v -> showColorPicker());
        findViewById(R.id.stamp_lock_button).setOnClickListener(v -> toggleStampLock());

        stampEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String stampText = stampEditText.getText().toString().trim();
                if (!stampText.isEmpty()) {
                    currentStampText = stampText; // Сохраняем текст
                    createStamp(currentStampText);
                    // Убираем очистку текста и скрытие поля
                    hideKeyboard();
                    return true;
                }
            }
            return false;
        });

        // хуйня, которая не пригодилась, но удалять лень
        stampEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentStampText = s.toString();
                if (currentStampView != null) {
                    currentStampView.setText(currentStampText);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


    }

    // Переключаем режим штампа: включаем/выключаем редактирование и отображение контролов
    private void toggleStampMode() {
        isStampModeActive = !isStampModeActive;
        stampControlsLayout.setVisibility(isStampModeActive ? View.VISIBLE : View.GONE);

        if (isStampModeActive) {
            stampEditText.setVisibility(View.VISIBLE);
            stampEditText.requestFocus();
            showKeyboard();

            // Создаем штамп с запомненным текстом или пустой штамп, если текста нет
            createStamp(currentStampText);
        } else {
            hideKeyboard();
            stampEditText.setVisibility(View.GONE);

            // Удаляем штамп, если текст не введен (пустой штамп)
            if (currentStampView != null && currentStampView.getText().toString().isEmpty()) {
                stampContainer.removeView(currentStampView);
                currentStampView = null;
            }
        }
    }

    private void createStamp(String text) {
        if (currentStampView != null) {
            stampContainer.removeView(currentStampView);
        }
        exposureControlsLayout.setVisibility(View.GONE);
        currentStampView = new androidx.appcompat.widget.AppCompatTextView(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                // Гарантируем что текст будет виден при изменении размера
                setBackgroundColor(Color.TRANSPARENT);
            }
        };

        currentStampView.setText(text);
        currentStampView.setTextColor(currentStampColor);
        currentStampView.setTextSize(TypedValue.COMPLEX_UNIT_PX, initialStampSize * stampScaleFactor);
        currentStampView.setGravity(Gravity.CENTER);
        currentStampView.setPadding(20, 10, 20, 10);
        currentStampView.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);

        currentStampView.setLayoutParams(params);
        stampContainer.addView(currentStampView);

        currentStampView.setOnTouchListener((v, event) -> {
            if (!isStampModeActive || isStampLocked) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchPosition.set(event.getX(), event.getY());
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - lastTouchPosition.x;
                    float dy = event.getY() - lastTouchPosition.y;

                    currentStampView.setX(currentStampView.getX() + dx);
                    currentStampView.setY(currentStampView.getY() + dy);

                    lastTouchPosition.set(event.getX(), event.getY());
                    return true;

                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        });
    }

    // Блокируем/разблокируем штамп, чтобы его нельзя было двигать
    private void toggleStampLock() {
        isStampLocked = !isStampLocked;
        ImageButton lockButton = findViewById(R.id.stamp_lock_button);
        lockButton.setImageResource(isStampLocked ? R.drawable.ic_lock : R.drawable.ic_unlock);

        if (currentStampView != null) {
            if (isStampLocked) {
                currentStampView.setTextColor(Color.RED); // Красный текст при блокировке
                currentStampView.setBackground(null); // Убираем фон полностью
            } else {
                currentStampView.setTextColor(currentStampColor); // Возвращаем выбранный цвет
            }
        }
    }

    // Показываем диалог выбора цвета для штампа
    private void showColorPicker() {
        AmbilWarnaDialog colorPicker = new AmbilWarnaDialog(this, currentStampColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                currentStampColor = color;
                stampPaint.setColor(color);
                if (currentStampView != null) {
                    currentStampView.setTextColor(color);
                }
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
                // нихуя не делаем, логично же
            }
        });
        colorPicker.show();
    }
    // показываем клаву для ввода текста дрочилы
    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(stampEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }
    // Скрываем клавиатуру(ахуеть!)
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(stampEditText.getWindowToken(), 0);
        }
    }
    // Объединяем фотку с камеры и штамп
    private Bitmap combineCameraAndStamp(Bitmap cameraBitmap) {
        if (currentStampView == null || currentStampView.getVisibility() != View.VISIBLE) {
            return addDateToBitmap(cameraBitmap);
        }


        Bitmap combinedBitmap = Bitmap.createBitmap(
                cameraBitmap.getWidth(),
                cameraBitmap.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawBitmap(cameraBitmap, 0, 0, null);

        currentStampView.setDrawingCacheEnabled(true);
        currentStampView.buildDrawingCache();
        Bitmap stampBitmap = Bitmap.createBitmap(currentStampView.getDrawingCache());
        currentStampView.setDrawingCacheEnabled(false);

        if (stampBitmap != null) {
            int[] stampLocation = new int[2];
            currentStampView.getLocationInWindow(stampLocation);

            int[] cameraLocation = new int[2];
            cameraContainer.getLocationInWindow(cameraLocation);

            float x = stampLocation[0] - cameraLocation[0];
            float y = stampLocation[1] - cameraLocation[1];

            float scaleX = (float) cameraBitmap.getWidth() / cameraContainer.getWidth();
            float scaleY = (float) cameraBitmap.getHeight() / cameraContainer.getHeight();

            Matrix matrix = new Matrix();
            matrix.postScale(scaleX, scaleY);
            matrix.postTranslate(x * scaleX, y * scaleY);

            canvas.drawBitmap(stampBitmap, matrix, null);
        }

        return addDateToBitmap(combinedBitmap);
    }

    // Добавляем дату и время на фотку
    private Bitmap addDateToBitmap(Bitmap cameraBitmap) {
        Bitmap mutableBitmap = cameraBitmap.copy(Bitmap.Config.ARGB_8888, true);
        //       Canvas canvas = new Canvas(mutableBitmap);

//        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
//
//        Paint paint = new Paint();
//        paint.setColor(Color.RED);
//
//        //  Размер текста
//        float textSize = cameraBitmap.getWidth() / 25f;
//        paint.setTextSize(textSize);
//
//        paint.setAntiAlias(true);
//        paint.setShadowLayer(5f, 0f, 0f, Color.BLACK);
//
//        //  Позиция текста
//        float x = cameraBitmap.getWidth() / 50f;
//        float y = cameraBitmap.getHeight() / 20f;
//        canvas.drawText(dateTime, x, y, paint);

        return mutableBitmap;
    }

    // Делаем дикпик
    private void captureImage() {
        if (stampControlsLayout.getVisibility() == View.VISIBLE) {
            stampControlsLayout.setVisibility(View.INVISIBLE);
        }

        cameraView.setMode(Mode.PICTURE);
        cameraView.takePicture();

        new Handler().postDelayed(() -> {
            if (isStampModeActive) {
                stampControlsLayout.setVisibility(View.VISIBLE);
            }
        }, 500);
    }

    // Переключаем видимость контролов (экспозиция, настройки)
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

    // Включаем/выключаем запись видео
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

    // Открываем экран с превьюшек фоток
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
    // Получаем список путей к фоткам в папке
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
    // здесь, епта, спрашиваем у дрочилы разрешения октрыть камеру
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

    // Настраиваем слушателя камеры, чтобы ловить события (сделали фотку, сняли видео, ошибка и т.д.)
    private void setupCameraListener() {
        cameraView.addCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(@NonNull PictureResult result) {
                result.toBitmap(bitmap -> {
                    if (bitmap != null) {
                        Bitmap stampedBitmap = combineCameraAndStamp(bitmap); // Используйте combineCameraAndStamp
                        currentImageFile = saveImage(stampedBitmap);
                        if (currentImageFile != null) {
                            updateLastImagePreview();
                            Toast.makeText(CameraV2Activity.this, "Image saved to: " + currentImageFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        } else {
                            Log.e("CameraError", "Failed to save image.");
                            Toast.makeText(CameraV2Activity.this, "Наебнулось при создании снимка.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e("CameraError", "Failed to decode Bitmap from PictureResult");
                        Toast.makeText(CameraV2Activity.this, "Наебнулось при декодировани.", Toast.LENGTH_SHORT).show();
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

    // Настраиваем контролы экспозиции (ползунок)
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

    // Настраиваем контролы экспозиции с учетом поддерживаемых значений
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
            exposureControlsLayout.setVisibility(View.GONE);
            isExposureControlsVisible = true;
        } else {
            exposureControlsLayout.setVisibility(View.GONE);
            isExposureControlsVisible = false;
            Toast.makeText(this, "Exposure correction not supported.", Toast.LENGTH_SHORT).show();
        }
    }

    // Запускаем поток, который каждую секунду обновляет время на экране
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

    // Настраиваем жесты камеры (зум, фокус)
    private void setupCameraGestures() {
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        cameraView.mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS);
    }

    // Настраиваем контролы настроек (формат изображения, ориентация и т.д.)
    private void setupSettingsControls() {
        // Спинер для выбора формата изображения (JPEG, PNG)
        Spinner imageFormatSpinner = findViewById(R.id.image_format_spinner);
        ArrayAdapter<CharSequence> imageFormatAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new CharSequence[]{"JPEG", "PNG"});
        imageFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageFormatSpinner.setAdapter(imageFormatAdapter);

        // Подгружаем текущий формат изображения из настроек, епты
        String currentImageFormat = sharedPreferences.getString(KEY_IMAGE_FORMAT, "JPEG");
        int imageFormatPosition = currentImageFormat.equals("JPEG") ? 0 : 1;
        imageFormatSpinner.setSelection(imageFormatPosition);

        // Вешаем слушателя на спиннер формата изображения, чтобы сохранять выбор
        imageFormatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedImageFormat = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_IMAGE_FORMAT, selectedImageFormat);
                editor.apply();
                CameraV2Activity.this.currentImageFormat = selectedImageFormat;
            }

            // Нихуя не делаем
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Свитч для блокировки ориентации экрана
        Switch orientationLockSwitch = findViewById(R.id.orientation_lock_switch);
        boolean isOrientationLocked = sharedPreferences.getBoolean(KEY_ORIENTATION_LOCK, false);
        orientationLockSwitch.setChecked(isOrientationLocked);

        orientationLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_ORIENTATION_LOCK, isChecked);
            editor.apply();
            this.isOrientationLocked = isChecked;
            cameraView.setRotation(this.isOrientationLocked ? 0 : -1);
        });

        // Спинер для выбора баланса белого
        Spinner whiteBalanceSpinner = findViewById(R.id.white_balance_spinner);
        ArrayAdapter<CharSequence> whiteBalanceAdapter = ArrayAdapter.createFromResource(
                this, R.array.white_balance_options, android.R.layout.simple_spinner_item);
        whiteBalanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        whiteBalanceSpinner.setAdapter(whiteBalanceAdapter);

        // Подгружаем текущий баланс белого из настроек
        String currentWhiteBalance = sharedPreferences.getString(KEY_WHITE_BALANCE, "AUTO");
        int whiteBalancePosition = getWhiteBalancePosition(currentWhiteBalance);
        whiteBalanceSpinner.setSelection(whiteBalancePosition);

        // Вешаем слушателя на спиннер баланса белого, чтобы сохранять выбор
        whiteBalanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedWhiteBalance = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_WHITE_BALANCE, selectedWhiteBalance);
                editor.apply();

                setWhiteBalance(selectedWhiteBalance);
            }

            // Опять же, нихуя не делаем
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // и снова хуйня, которую жалко удалять
            }
        });

        // Спинер для выбора режима HDR (High Dynamic Range - типа улучшает качество фоток, хуйня)
        Spinner hdrSpinner = findViewById(R.id.hdr_spinner);
        ArrayAdapter<CharSequence> hdrAdapter = ArrayAdapter.createFromResource(
                this, R.array.hdr_options, android.R.layout.simple_spinner_item);
        hdrAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hdrSpinner.setAdapter(hdrAdapter);

        // Подгружаем текущий режим HDR из настроек
        String currentHDR = sharedPreferences.getString(KEY_HDR, "OFF");
        int hdrPosition = currentHDR.equals("OFF") ? 0 : 1;
        hdrSpinner.setSelection(hdrPosition);

        // Вешаем слушателя на спиннер HDR, чтобы сохранять выбор
        hdrSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedHDR = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_HDR, selectedHDR);
                editor.apply();
                cameraView.setHdr(selectedHDR.equals("OFF") ? Hdr.OFF : Hdr.ON);
            }

            // Ну ты понял...
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // ...все та же хуйня
            }
        });

        // Спинер для выбора аудиорежима
        Spinner audioSpinner = findViewById(R.id.audio_spinner);
        ArrayAdapter<CharSequence> audioAdapter = ArrayAdapter.createFromResource(
                this, R.array.audio_options, android.R.layout.simple_spinner_item);
        audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioSpinner.setAdapter(audioAdapter);

        // Подгружаем текущий аудиорежим из настроек
        String currentAudio = sharedPreferences.getString(KEY_AUDIO, "OFF");
        int audioPosition = getAudioPosition(currentAudio);
        audioSpinner.setSelection(audioPosition);

        // вешаем бла бла бла...
        audioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedAudio = parent.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_AUDIO, selectedAudio);
                editor.apply();
                setAudio(selectedAudio);
            }

            // Догадайся, что тут
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // ...ага, она
            }
        });

    }

    // Получаем позицию баланса белого в спиннере по его значению
    private int getWhiteBalancePosition(String whiteBalance) {
        switch (whiteBalance) {
            case "INCANDESCENT": // Лампы накаливания
                return 1;
            case "FLUORESCENT": // Люминесцентные лампы
                return 2;
            case "DAYLIGHT": // Дневной свет
                return 3;
            case "CLOUDY": // Облачно
                return 4;
            default:
                return 0; // Автоматически
        }
    }

    // Устанавливаем баланс белого
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

    // Получаем позицию аудиорежима в спиннере по его значению
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

    // Устанавливаем аудиорежим
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

    // Загружаем сохраненные настройки камеры
    private void loadSavedSettings() {
        currentImageFormat = sharedPreferences.getString(KEY_IMAGE_FORMAT, "JPEG");
        isOrientationLocked = sharedPreferences.getBoolean(KEY_ORIENTATION_LOCK, false);
        setWhiteBalance(sharedPreferences.getString(KEY_WHITE_BALANCE, "AUTO"));
        cameraView.setHdr(sharedPreferences.getString(KEY_HDR, "OFF").equals("OFF") ? Hdr.OFF : Hdr.ON);
        setAudio(sharedPreferences.getString(KEY_AUDIO, "OFF"));

        // Загружаем сохраненную позицию Spinner
        int savedSpinnerPosition = sharedPreferences.getInt(KEY_RESOLUTION_SPINNER_POSITION, 0);

        int savedWidth = sharedPreferences.getInt(KEY_RESOLUTION_WIDTH, -1); // -1 - значение по умолчанию, если ничего не сохранено
        int savedHeight = sharedPreferences.getInt(KEY_RESOLUTION_HEIGHT, -1);

        if (savedWidth != -1 && savedHeight != -1) {
            // Создаем Size из сохраненных значений
            Size savedSize = new Size(savedWidth, savedHeight);
            // Устанавливаем его для cameraView
            cameraView.setPictureSize(new SizeSelector() {
                @NonNull
                @Override
                public List<com.otaliastudios.cameraview.size.Size> select(@NonNull List<com.otaliastudios.cameraview.size.Size> source) {
                    List<com.otaliastudios.cameraview.size.Size> result = new ArrayList<>();
                    for (com.otaliastudios.cameraview.size.Size size : source) {
                        if (size.getWidth() == savedSize.getWidth() && size.getHeight() == savedSize.getHeight()) {
                            result.add(size);
                            break;
                        }
                    }
                    return result;
                }

            });
            updateCameraViewSize(savedWidth,savedHeight);

            if (supportedResolutions != null && savedSpinnerPosition < supportedResolutions.size()) {
                resolutionSpinner.setSelection(savedSpinnerPosition);
            }
        }

    }

    // Обрабатываем результат запроса разрешений (дали или не дали доступ к камере и т.д.)
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

    // Обновляем время на экране
    private void updateTimeDisplay() {
        String timeFormat = "HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat, Locale.getDefault());
        String currentTime = sdf.format(new Date());
        timeTextView.setText(currentTime);
        timeTextView.setTextSize(20);
    }

    // Сохраняем изображение на диск
    private File saveImage(Bitmap finalBitmap) {
        File file = null; // Инициализируем file в null, а то IDE ругается
        try {
            if (customSavePath != null && !customSavePath.isEmpty()) {
                // Используем переданный путь для сохранения
                File customDir = new File(customSavePath);
                if (customDir.isDirectory()) {
                    String fileName = "Image-" + System.currentTimeMillis() + (currentImageFormat.equals("PNG") ? ".png" : ".jpg");
                    file = new File(customDir, fileName);
                } else {
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

    // Настраиваем спиннер с разрешением изображения (соотношение сторон фотки)
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

        // Загружаем сохраненную позицию Spinner и устанавливаем ее
        int savedSpinnerPosition = sharedPreferences.getInt(KEY_RESOLUTION_SPINNER_POSITION, 0);
        if (savedSpinnerPosition < resolutionLabels.size()) {
            resolutionSpinner.setSelection(savedSpinnerPosition);
        }

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
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(KEY_RESOLUTION_WIDTH, selectedSize.getWidth());
                editor.putInt(KEY_RESOLUTION_HEIGHT, selectedSize.getHeight());

                // Сохраняем позицию Spinner
                editor.putInt(KEY_RESOLUTION_SPINNER_POSITION, position);
                editor.apply();

                updateCameraViewSize(selectedSize.getWidth(), selectedSize.getHeight());
            }

            // И тут, как всегда, нихуя
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // Обновляем размер CameraView, чтобы он соответствовал выбранному разрешению
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

    // Вызывается при возобновлении работы Activity (когда возвращаемся к приложению)
    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            cameraView.open(); // Открываем камеру, если есть все разрешения
        }
    }

    // Вызывается при приостановке работы Activity (когда сворачиваем приложение)
    @Override
    protected void onPause() {
        super.onPause();
        cameraView.close(); // Закрываем камеру, чтобы не жрала батарею
    }

    // Вызывается при уничтожении Activity (когда закрываем приложение)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraView.destroy(); // Уничтожаем CameraView, чтобы освободить ресурсы
    }

    // Обновляем превью последней фотки в углу экрана
    private void updateLastImagePreview() {
        ImageButton lastImagePreview = findViewById(R.id.last_image_preview); // Находим кнопку превью
        File directory = new File(getFilesDir(), "InspectorAppFolder/saved_images"); // Папка с фотками
        File lastFile = null; // Файл последней фотки (инициализируем в null)

        if (directory.exists() && directory.isDirectory()) { // Если папка существует и это папка
            File[] files = directory.listFiles(); // Получаем список файлов в папке
            if (files != null && files.length > 0) { // Сортируем файлы по дате изменения (последние - в начале)
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); // Берем первый файл из списка (последний измененный)
                lastFile = files[0];
            }
        }

        if (lastFile != null && lastFile.exists()) { // Если файл последней фотки существует
            Bitmap myBitmap = BitmapFactory.decodeFile(lastFile.getAbsolutePath()); // Декодируем файл в Bitmap
            lastImagePreview.setImageBitmap(myBitmap); // Устанавливаем Bitmap в ImageButton
        } else {
            lastImagePreview.setImageResource(R.drawable.def_insp_img);  // Если нет фотки - ставим дефолтную картинку
        }
    }

    // Обновляем иконку кнопки записи видео (пауза/стоп)
    private void updateVideoCaptureButtonIcon() {
        videoCaptureButton.setImageResource(isRecordingVideo ? R.drawable.ic_stop_video : R.drawable.ic_videocam);
    }
}
