package com.paintshop.quality.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ScanActivity extends AppCompatActivity {
    public static final String EXTRA_MODEL_CODES = "modelCodes";
    public static final String EXTRA_BATCH = "batchMode";
    public static final String RESULT_VIN = "vin";
    public static final String RESULT_VEHICLE_NUMBER = "vehicleNumber";
    public static final String RESULT_MODEL_CODE = "modelCode";
    public static final String RESULT_VINS = "vins";
    public static final String RESULT_VEHICLE_NUMBERS = "vehicleNumbers";
    public static final String RESULT_MODEL_CODE_LIST = "modelCodeList";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
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

    private void startCamera() {
        statusText.setText("正在启动相机…");
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
        statusText.setText("正在设备端识别条码和文本…");
        InputImage image;
        try {
            image = InputImage.fromFilePath(this, Uri.fromFile(photo));
        } catch (IOException | RuntimeException error) {
            photo.delete();
            setRecognizing(false);
            statusText.setText("照片读取失败，请重试或返回手输");
            return;
        }
        BarcodeScanner barcodeScanner = BarcodeScanning.getClient();
        TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<List<Barcode>> barcodeTask = barcodeScanner.process(image);
        Task<Text> textTask = textRecognizer.process(image);
        Tasks.whenAllComplete(barcodeTask, textTask).addOnCompleteListener(combined -> {
            List<String> barcodeValues = new ArrayList<>();
            if (barcodeTask.isSuccessful() && barcodeTask.getResult() != null) {
                for (Barcode barcode : barcodeTask.getResult()) {
                    if (barcode.getRawValue() != null) {
                        barcodeValues.add(barcode.getRawValue());
                    }
                }
            }
            String text = textTask.isSuccessful() && textTask.getResult() != null
                    ? textTask.getResult().getText() : "";
            String barcode = RecognitionParser.barcodeCandidate(
                    barcodeValues.toArray(new String[0]));
            if (barcode.isEmpty()) {
                barcode = RecognitionParser.barcodeCandidate(text);
            }
            String vin = RecognitionParser.extractVin(text, barcode);
            String vehicleNumber = RecognitionParser.vehicleNumberFromBarcode(barcode);
            String modelCode = RecognitionParser.modelCodeFromBarcode(barcode);
            barcodeScanner.close();
            textRecognizer.close();
            photo.delete();

            if (!barcodeTask.isSuccessful() && !textTask.isSuccessful()) {
                setRecognizing(false);
                statusText.setText("识别服务失败，请重试或返回手输");
                Toast.makeText(this, "条码和文本识别均失败", Toast.LENGTH_LONG).show();
                return;
            }
            if (vehicleNumber.isEmpty() && modelCode.isEmpty() && vin.isEmpty()) {
                setRecognizing(false);
                statusText.setText("未识别到条码车号、车型或VIN，请重拍或返回手输");
                Toast.makeText(this, "未可靠识别，不会自动编造结果", Toast.LENGTH_LONG).show();
                return;
            }
            deliverResult(vin, vehicleNumber, modelCode);
        });
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
        InputImage image;
        try {
            image = InputImage.fromFilePath(this, uris.get(index));
        } catch (IOException | RuntimeException error) {
            processBatchImage(uris, index + 1, vins, numbers, codes);
            return;
        }
        BarcodeScanner barcodeScanner = BarcodeScanning.getClient();
        TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<List<Barcode>> barcodeTask = barcodeScanner.process(image);
        Task<Text> textTask = textRecognizer.process(image);
        Tasks.whenAllComplete(barcodeTask, textTask).addOnCompleteListener(combined -> {
            List<String> barcodeValues = new ArrayList<>();
            if (barcodeTask.isSuccessful() && barcodeTask.getResult() != null) {
                for (Barcode barcode : barcodeTask.getResult()) {
                    if (barcode.getRawValue() != null) {
                        barcodeValues.add(barcode.getRawValue());
                    }
                }
            }
            String text = textTask.isSuccessful() && textTask.getResult() != null
                    ? textTask.getResult().getText() : "";
            String barcode = RecognitionParser.barcodeCandidate(
                    barcodeValues.toArray(new String[0]));
            if (barcode.isEmpty()) {
                barcode = RecognitionParser.barcodeCandidate(text);
            }
            String vin = RecognitionParser.extractVin(text, barcode);
            String vehicleNumber = RecognitionParser.vehicleNumberFromBarcode(barcode);
            String modelCode = RecognitionParser.modelCodeFromBarcode(barcode);
            barcodeScanner.close();
            textRecognizer.close();
            if (!vehicleNumber.isEmpty() || !modelCode.isEmpty()) {
                numbers.add(vehicleNumber);
                codes.add(modelCode);
                vins.add(vin);
            }
            processBatchImage(uris, index + 1, vins, numbers, codes);
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
        captureButton.setEnabled(!value && imageCapture != null);
    }
}
