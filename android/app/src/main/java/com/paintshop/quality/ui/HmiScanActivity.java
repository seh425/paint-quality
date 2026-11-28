package com.paintshop.quality.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.paintshop.quality.R;
import com.paintshop.quality.util.HmiParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class HmiScanActivity extends AppCompatActivity {

    public static final String RESULT_VEHICLE_NUMBERS = "hmiVehicleNumbers";
    public static final String RESULT_MODEL_VALUES = "hmiModelValues";

    private ImageView imageView;
    private CropOverlayView cropOverlay;
    private android.widget.TextView statusText;
    private Button recognizeButton;
    private Button applyButton;
    private Bitmap bitmap;
    private List<HmiParser.Row> recognizedRows = new ArrayList<>();
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hmi_scan);
        imageView = findViewById(R.id.hmiImageView);
        cropOverlay = findViewById(R.id.hmiCropOverlay);
        statusText = findViewById(R.id.hmiStatusText);
        recognizeButton = findViewById(R.id.recognizeHmiButton);
        applyButton = findViewById(R.id.applyHmiButton);

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                loadImage(uri);
            }
        });

        findViewById(R.id.chooseHmiImageButton).setOnClickListener(view ->
                pickImageLauncher.launch("image/*"));
        recognizeButton.setOnClickListener(view -> recognize());
        applyButton.setOnClickListener(view -> applyResult());
    }

    private void loadImage(Uri uri) {
        try {
            bitmap = decodeSampledBitmap(uri, 2400);
            if (bitmap == null) {
                Toast.makeText(this, "无法读取照片", Toast.LENGTH_LONG).show();
                return;
            }
            imageView.setImageBitmap(bitmap);
            recognizedRows.clear();
            applyButton.setEnabled(false);
            statusText.setText("拖动白框只框住 5 辆车的车号和车型两列，再点识别");
            imageView.post(() -> updateOverlayRect());
            recognizeButton.setEnabled(true);
        } catch (Exception error) {
            Toast.makeText(this, "读取照片失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateOverlayRect() {
        if (bitmap == null || imageView.getWidth() == 0 || imageView.getHeight() == 0) {
            return;
        }
        float scale = Math.min(
                imageView.getWidth() / (float) bitmap.getWidth(),
                imageView.getHeight() / (float) bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = (imageView.getWidth() - width) / 2f;
        float top = (imageView.getHeight() - height) / 2f;
        cropOverlay.setImageRect(new RectF(left, top, left + width, top + height));
    }

    private void recognize() {
        if (bitmap == null) {
            Toast.makeText(this, "请先选择HMI照片", Toast.LENGTH_SHORT).show();
            return;
        }
        RectF crop = cropOverlay.getCropRect();
        RectF imageRect = cropOverlay.getImageRect();
        if (crop.isEmpty() || imageRect.isEmpty()) {
            Toast.makeText(this, "请先调整选择框", Toast.LENGTH_SHORT).show();
            return;
        }
        int left = (int) Math.max(0, (crop.left - imageRect.left) / imageRect.width() * bitmap.getWidth());
        int top = (int) Math.max(0, (crop.top - imageRect.top) / imageRect.height() * bitmap.getHeight());
        int right = (int) Math.min(bitmap.getWidth(),
                (crop.right - imageRect.left) / imageRect.width() * bitmap.getWidth());
        int bottom = (int) Math.min(bitmap.getHeight(),
                (crop.bottom - imageRect.top) / imageRect.height() * bitmap.getHeight());
        if (right <= left || bottom <= top) {
            Toast.makeText(this, "选择框太小，请重新调整", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        recognizeButton.setEnabled(false);
        applyButton.setEnabled(false);
        statusText.setText("正在识别HMI屏幕…");
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(cropped, 0))
                .addOnSuccessListener(text -> {
                    recognizedRows = HmiParser.parse(text.getText());
                    recognizeButton.setEnabled(true);
                    if (recognizedRows.isEmpty()) {
                        statusText.setText("没有识别到车号。请把选择框缩小到 5 行数据区域后重试。");
                        return;
                    }
                    StringBuilder builder = new StringBuilder("识别到 ")
                            .append(recognizedRows.size()).append(" 辆：");
                    for (int i = 0; i < recognizedRows.size(); i++) {
                        HmiParser.Row row = recognizedRows.get(i);
                        if (i > 0) {
                            builder.append("；");
                        }
                        builder.append(row.carNumber).append(" / ").append(row.modelValue);
                    }
                    if (recognizedRows.size() < 5) {
                        builder.append("。不足5辆，可调整框后重试，或先填入再手工补。");
                    }
                    statusText.setText(builder.toString());
                    applyButton.setEnabled(true);
                })
                .addOnFailureListener(error -> {
                    recognizeButton.setEnabled(true);
                    statusText.setText("识别失败：" + error.getMessage());
                });
    }

    private void applyResult() {
        if (recognizedRows.isEmpty()) {
            Toast.makeText(this, "请先识别", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> numbers = new ArrayList<>();
        ArrayList<String> models = new ArrayList<>();
        for (HmiParser.Row row : recognizedRows) {
            numbers.add(row.carNumber);
            models.add(row.modelValue);
        }
        android.content.Intent result = new android.content.Intent();
        result.putStringArrayListExtra(RESULT_VEHICLE_NUMBERS, numbers);
        result.putStringArrayListExtra(RESULT_MODEL_VALUES, models);
        setResult(RESULT_OK, result);
        finish();
    }

    private Bitmap decodeSampledBitmap(Uri uri, int maxDimension) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxDimension) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }
}
