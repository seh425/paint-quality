package com.paintshop.quality.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.paintshop.quality.R;
import com.paintshop.quality.util.RecognitionParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanActivity extends AppCompatActivity {
    public static final String EXTRA_MODEL_CODES = "modelCodes";
    public static final String EXTRA_BATCH = "batchMode";
    public static final String RESULT_VIN = "vin";
    public static final String RESULT_VEHICLE_NUMBER = "vehicleNumber";
    public static final String RESULT_MODEL_CODE = "modelCode";
    public static final String RESULT_VINS = "vins";
    public static final String RESULT_VEHICLE_NUMBERS = "vehicleNumbers";
    public static final String RESULT_MODEL_CODE_LIST = "modelCodeList";

    private static final int MAX_SIDE = 2000;
    private static final int MAX_PASS = 3;

    private PreviewView previewView;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button captureButton;
    private ImageCapture imageCapture;
    private List<String> modelCodes = new ArrayList<>();
    private boolean recognizing;
    private boolean batchMode;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String> pickLauncher;
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor();

    private interface AnalysisCallback {
        void onResult(String barcode, String text);
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.scanRoot);
        int base = Math.round(12 * getResources().getDisplayMetrics().density);
        int extra = Math.round(8 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(base, base + bars.top + extra, base, base + bars.bottom + extra);
            return insets;
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        applyWindowInsets();
        previewView = findViewById(R.id.cameraPreview);
        statusText = findViewById(R.id.scanStatusText);
        progressBar = findViewById(R.id.scanProgress);
        captureButton = findViewById(R.id.captureButton);
        List<String> codes = getIntent().getStringArrayListExtra(EXTRA_MODEL_CODES);
        if (codes != null) {
            modelCodes = codes;
        }
        batchMode = getIntent().getBooleanExtra(EXTRA_BATCH, false);
        pickLauncher = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        setResult(RESULT_CANCELED);
                        finish();
                        return;
                    }
                    recognizeBatch(uris);
                });
        if (batchMode) {
            previewView.setVisibility(View.GONE);
            captureButton.setText("重新选择照片");
            captureButton.setEnabled(true);
            captureButton.setOnClickListener(view -> openBatchPicker());
            findViewById(R.id.cancelScanButton).setOnClickListener(view -> {
                setResult(RESULT_CANCELED);
                finish();
            });
            openBatchPicker();
            return;
        }
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        statusText.setText("未授予相机权限，请返回手输或在系统设置中允许相机权限");
                        captureButton.setEnabled(false);
                        Toast.makeText(this, "需要相机权限才能拍照识别", Toast.LENGTH_LONG).show();
                    }
                });
        captureButton.setOnClickListener(view -> takePhoto());
        findViewById(R.id.cancelScanButton).setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analyzerExecutor.shutdownNow();
    }

    private void startCamera() {
        statusText.setText("正在启动相机…");
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
                captureButton.setEnabled(true);
                statusText.setText("对准标识后拍照；识别结果仍可手动修改");
            } catch (ExecutionException | RuntimeException error) {
                imageCapture = null;
                captureButton.setEnabled(false);
                statusText.setText("相机启动失败，请返回手输");
                Toast.makeText(this, "相机启动失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                captureButton.setEnabled(false);
                statusText.setText("相机启动被中断，请返回手输");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (recognizing || imageCapture == null) {
            return;
        }
        setRecognizing(true);
        statusText.setText("正在拍照…");
        File photo = new File(getCacheDir(), "scan-" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photo).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        recognize(photo);
                    }

                    @Override
                    public void onError(ImageCaptureException error) {
                        photo.delete();
                        setRecognizing(false);
                        statusText.setText("拍照失败，请重试或返回手输");
                        Toast.makeText(ScanActivity.this,
                                "拍照失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void recognize(File photo) {
        statusText.setText("正在设备端识别条码和文本，模糊照片会自动增强重试…");
        analyzeUri(Uri.fromFile(photo), new AnalysisCallback() {
            @Override
            public void onResult(String barcode, String text) {
                photo.delete();
                handleSingleResult(barcode, text);
            }
        });
    }

    private void handleSingleResult(String barcode, String text) {
        String vin = RecognitionParser.extractVin(text, barcode);
        String vehicleNumber = RecognitionParser.vehicleNumberFromBarcode(barcode);
        String modelCode = RecognitionParser.modelCodeFromBarcode(barcode);
        if (modelCode.isEmpty()) {
            modelCode = RecognitionParser.matchingModelCode(text, modelCodes);
        }
        setRecognizing(false);
        if (vehicleNumber.isEmpty() && modelCode.isEmpty() && vin.isEmpty()) {
            statusText.setText("未识别到条码车号、车型或VIN，请重拍或返回手输");
            Toast.makeText(this, "未可靠识别，不会自动编造结果", Toast.LENGTH_LONG).show();
            return;
        }
        deliverResult(vin, vehicleNumber, modelCode);
    }

    /**
     * 本地解码并做多轮识别：原图 → 灰度增强 → 增强后旋转90° → 增强后旋转270°。
     * 只有前一轮完全无结果时才继续，兼顾速度与弱光/轻微模糊场景。
     */
    private void analyzeUri(Uri uri, AnalysisCallback callback) {
        analyzerExecutor.execute(() -> {
            Bitmap bitmap = decodeSampled(uri);
            if (bitmap == null) {
                postResult(callback, "", "");
                return;
            }
            try {
                analyzePass(bitmap, 0, new ArrayList<>(), new StringBuilder(), callback);
            } catch (RuntimeException error) {
                bitmap.recycle();
                postResult(callback, "", "");
            }
        });
    }

    private void analyzePass(Bitmap base, int pass, List<String> barcodes,
                             StringBuilder texts, AnalysisCallback callback) {
        Bitmap working = base;
        int rotation = 0;
        if (pass > 0) {
            working = enhance(base);
            if (pass == 2) {
                rotation = 90;
            } else if (pass == 3) {
                rotation = 270;
            }
        }
        final Bitmap source = working;
        InputImage image = InputImage.fromBitmap(source, rotation);
        BarcodeScanner barcodeScanner = BarcodeScanning.getClient();
        TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<List<Barcode>> barcodeTask = barcodeScanner.process(image);
        Task<Text> textTask = textRecognizer.process(image);
        Tasks.whenAllComplete(barcodeTask, textTask).addOnCompleteListener(analyzerExecutor, done -> {
            try {
                if (barcodeTask.isSuccessful() && barcodeTask.getResult() != null) {
                    for (Barcode barcode : barcodeTask.getResult()) {
                        if (barcode.getRawValue() != null) {
                            barcodes.add(barcode.getRawValue());
                        }
                    }
                }
                if (textTask.isSuccessful() && textTask.getResult() != null) {
                    texts.append('\n').append(textTask.getResult().getText());
                }
            } catch (RuntimeException ignored) {
                // 单轮识别异常不影响后续轮次
            } finally {
                barcodeScanner.close();
                textRecognizer.close();
                if (source != base) {
                    source.recycle();
                }
            }

            String combinedText = texts.toString();
            if (!hasReliableCandidate(barcodes, combinedText) && pass < MAX_PASS) {
                analyzePass(base, pass + 1, barcodes, texts, callback);
                return;
            }
            String barcode = RecognitionParser.barcodeCandidate(
                    barcodes.toArray(new String[0]));
            if (barcode.isEmpty()) {
                barcode = RecognitionParser.barcodeCandidate(combinedText);
            }
            base.recycle();
            postResult(callback, barcode, combinedText);
        });
    }

    private boolean hasReliableCandidate(List<String> barcodes, String text) {
        if (!RecognitionParser.barcodeCandidate(barcodes.toArray(new String[0])).isEmpty()) {
            return true;
        }
        if (!RecognitionParser.barcodeCandidate(text).isEmpty()) {
            return true;
        }
        if (!RecognitionParser.extractVin(text).isEmpty()) {
            return true;
        }
        return !RecognitionParser.matchingModelCode(text, modelCodes).isEmpty();
    }

    private void postResult(AnalysisCallback callback, String barcode, String text) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                callback.onResult(barcode, text);
            }
        });
    }

    private Bitmap decodeSampled(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    return null;
                }
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    return null;
                }
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) {
                return null;
            }
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input != null) {
                    orientation = new ExifInterface(input)
                            .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL);
                }
            } catch (IOException ignored) {
                // 读取EXIF失败时按正常方向处理
            }
            return applyOrientation(bitmap, orientation);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private Bitmap applyOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            matrix.postRotate(90);
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            matrix.postRotate(180);
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            matrix.postRotate(270);
        } else {
            return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    private Bitmap enhance(Bitmap source) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        ColorMatrix grayscale = new ColorMatrix();
        grayscale.setSaturation(0f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.6f, 0, 0, 0, -45,
                0, 1.6f, 0, 0, -45,
                0, 0, 1.6f, 0, -45,
                0, 0, 0, 1, 0});
        grayscale.postConcat(contrast);
        paint.setColorFilter(new ColorMatrixColorFilter(grayscale));
        canvas.drawBitmap(source, 0, 0, paint);
        return result;
    }

    private void openBatchPicker() {
        statusText.setText("从相册选择最多5张条码照片");
        pickLauncher.launch("image/*");
    }

    private void recognizeBatch(List<Uri> uris) {
        List<Uri> selected = uris.size() > 5 ? new ArrayList<>(uris.subList(0, 5)) : uris;
        setRecognizing(true);
        List<String> vins = new ArrayList<>();
        List<String> numbers = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        processBatchImage(selected, 0, vins, numbers, codes);
    }

    private void processBatchImage(List<Uri> uris, int index,
                                   List<String> vins, List<String> numbers, List<String> codes) {
        if (index >= uris.size()) {
            setRecognizing(false);
            if (numbers.isEmpty() && codes.isEmpty()) {
                statusText.setText("未识别到条码，请重选清晰照片");
                Toast.makeText(this, "未识别到任何条码", Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            Intent result = new Intent();
            result.putStringArrayListExtra(RESULT_VINS, new ArrayList<>(vins));
            result.putStringArrayListExtra(RESULT_VEHICLE_NUMBERS, new ArrayList<>(numbers));
            result.putStringArrayListExtra(RESULT_MODEL_CODE_LIST, new ArrayList<>(codes));
            setResult(RESULT_OK, result);
            finish();
            return;
        }
        statusText.setText("正在识别第 " + (index + 1) + "/" + uris.size() + " 张…");
        analyzeUri(uris.get(index), new AnalysisCallback() {
            @Override
            public void onResult(String barcode, String text) {
                String vin = RecognitionParser.extractVin(text, barcode);
                String vehicleNumber = RecognitionParser.vehicleNumberFromBarcode(barcode);
                String modelCode = RecognitionParser.modelCodeFromBarcode(barcode);
                if (!vehicleNumber.isEmpty()) {
                    if (modelCode.isEmpty()) {
                        modelCode = RecognitionParser.matchingModelCode(text, modelCodes);
                    }
                    numbers.add(vehicleNumber);
                    codes.add(modelCode);
                    vins.add(vin);
                }
                processBatchImage(uris, index + 1, vins, numbers, codes);
            }
        });
    }

    private void deliverResult(String vin, String vehicleNumber, String modelCode) {
        Intent result = new Intent();
        result.putExtra(RESULT_VIN, vin);
        result.putExtra(RESULT_VEHICLE_NUMBER, vehicleNumber);
        result.putExtra(RESULT_MODEL_CODE, modelCode);
        setResult(RESULT_OK, result);
        Toast.makeText(this, "识别完成，请核对并补充后保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setRecognizing(boolean value) {
        recognizing = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        captureButton.setEnabled(!value && (batchMode || imageCapture != null));
    }
}
