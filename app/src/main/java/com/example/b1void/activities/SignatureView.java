package com.example.b1void.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;

public class SignatureView extends View {

    private static final String TAG = "SignatureView";
    private String text = "Текст подписи"; // Начальный текст
    private float textSize = 50;        // Начальный размер текста
    private float x = 400;               // Начальная позиция X
    private float y = 600;               // Начальная позиция Y
    private Paint paint;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private PointF lastTouch;
    private Matrix matrix = new Matrix();
    private float scaleFactor = 1f;
    private float scaledTextSize;
    private boolean isLocked = false;


    public SignatureView(Context context) {
        super(context);
        init();
    }

    public SignatureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SignatureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);

        // Инициализация ScaleGestureDetector
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

        // Инициализация GestureDetector для обработки одинарного касания
        gestureDetector = new GestureDetector(getContext(), new GestureListener());

        lastTouch = new PointF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Применяем матрицу к канве
        canvas.save();
        canvas.setMatrix(matrix);
        canvas.drawText(text, x, y, paint);
        canvas.restore();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        if (isLocked) {
            return true; // Игнорируем касания, если надпись заблокирована
        }

        float newX = event.getX();
        float newY = event.getY();

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                Log.d(TAG, "ACTION_DOWN");
                lastTouch.set(newX, newY);
                break;

            case MotionEvent.ACTION_MOVE:
                Log.d(TAG, "ACTION_MOVE");
                if (!scaleGestureDetector.isInProgress()) {
                    float dx = newX - lastTouch.x;
                    float dy = newY - lastTouch.y;
                    matrix.postTranslate(dx, dy);
                    x += dx;
                    y += dy;
                    lastTouch.set(newX, newY);
                    invalidate();
                }
                break;
        }

        return true;
    }

    // Метод для установки текста
    public void setText(String text) {
        this.text = text;
        invalidate(); // Перерисовываем View
    }

    // Метод для установки размера текста
    public void setTextSize(float textSize) {
        this.textSize = textSize;
        paint.setTextSize(textSize);
        invalidate(); // Перерисовываем View
    }

    // Метод для установки блокировки
    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public boolean isLocked() {
        return isLocked;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f)); // Ограничиваем масштаб
            matrix.setScale(scaleFactor, scaleFactor);
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final int DOUBLE_TAP_TIME_DELTA = 300; // milliseconds
        private long lastTapTime = 0;


        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            long tapTime = System.currentTimeMillis();
            if ((tapTime - lastTapTime) < DOUBLE_TAP_TIME_DELTA) {
                // Double tap
                isLocked = !isLocked;
                setLocked(isLocked);
                Toast.makeText(getContext(), isLocked ? "Надпись заблокирована" : "Надпись разблокирована", Toast.LENGTH_SHORT).show();
                lastTapTime = 0; // Reset
                return true;
            } else {
                // Single tap
                showEditDialog();
                lastTapTime = tapTime;
                return true;
            }
        }
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Редактировать текст");

        // Создаем EditText для ввода текста
        final AppCompatEditText input = new AppCompatEditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(text);
        builder.setView(input);

        // Обрабатываем нажатие кнопки "OK"
        builder.setPositiveButton("OK", (dialog, which) -> {
            text = input.getText().toString();
            invalidate(); // Перерисовываем View
        });

        // Обрабатываем нажатие кнопки "Отмена"
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    public float getTextSize() {
        return textSize;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    public String getText() {
        return text;
    }

    public float getScaleFactor() {
        return scaleFactor;
    }

    public Paint getPaint() {
        return paint;
    }

    public float getScaledTextSize() {
        return scaledTextSize;
    }
}

