package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.DraftStore;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.DraftVehicle;
import com.paintshop.quality.model.InspectionDraft;
import com.paintshop.quality.model.InspectionRecord;
import com.paintshop.quality.model.VehicleModel;
import com.paintshop.quality.util.RecognitionParser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InspectionActivity extends AppCompatActivity {
    public static final String EXTRA_ROUND = "roundNumber";
    public static final String EXTRA_DATE = "workDate";

    private ApiClient apiClient;
    private DraftStore draftStore;
    private String userKey;
    private String workDate;
    private int roundNumber;
    private InspectionDraft draft;
    private int currentIndex;
    private boolean binding;
    private boolean uploading;
    private boolean submitted;
    private final List<VehicleModel> models = new ArrayList<>();

    private TextView positionText;
    private TextView draftStatusText;
    private RadioGroup lineGroup;
    private RadioButton line1Button;
    private RadioButton line2Button;
    private EditText vinInput;
    private EditText vehicleNumberInput;
    private EditText modelCodeInput;
    private EditText modelNameInput;
    private EditText issueNoteInput;
    private TextInputLayout issueNoteLayout;
    private RadioGroup statusGroup;
    private RadioButton statusNaButton;
    private RadioButton statusOkButton;
    private RadioButton statusNokButton;
    private Button previousButton;
    private Button nextButton;
    private Button scanButton;
    private Button refreshModelsButton;
    private ActivityResultLauncher<Intent> scanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspection);
        SessionManager sessionManager = new SessionManager(this);
        if (!sessionManager.hasSession()) {
            finish();
            return;
        }
        apiClient = ApiClient.get(this);
        draftStore = new DraftStore(this);
        userKey = sessionManager.getUserKey();
        roundNumber = getIntent().getIntExtra(EXTRA_ROUND, 1);
        workDate = getIntent().getStringExtra(EXTRA_DATE);
        if (workDate == null || workDate.isEmpty()) {
            workDate = LocalDate.now().toString();
        }
        bindViews();
        ((TextView) findViewById(R.id.inspectionTitleText)).setText(roundTitle(roundNumber));
        ((TextView) findViewById(R.id.inspectionDateText)).setText("日期：" + workDate + "（每轮恰好5辆）");
        loadDraft(1);
        bindVehicle();
        installListeners();
        scanLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        applyScanResult(result.getData());
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadModels(false);
    }

    @Override
    protected void onPause() {
        saveCurrentVehicle();
        super.onPause();
    }

    private void bindViews() {
        positionText = findViewById(R.id.vehiclePositionText);
        draftStatusText = findViewById(R.id.draftStatusText);
        lineGroup = findViewById(R.id.lineGroup);
        line1Button = findViewById(R.id.line1Button);
        line2Button = findViewById(R.id.line2Button);
        vinInput = findViewById(R.id.vinInput);
        vehicleNumberInput = findViewById(R.id.vehicleNumberInput);
        modelCodeInput = findViewById(R.id.modelCodeInput);
        modelNameInput = findViewById(R.id.modelNameInput);
        issueNoteInput = findViewById(R.id.issueNoteInput);
        issueNoteLayout = findViewById(R.id.issueNoteLayout);
        statusGroup = findViewById(R.id.statusGroup);
        statusNaButton = findViewById(R.id.statusNaButton);
        statusOkButton = findViewById(R.id.statusOkButton);
        statusNokButton = findViewById(R.id.statusNokButton);
        previousButton = findViewById(R.id.previousButton);
        nextButton = findViewById(R.id.nextButton);
        scanButton = findViewById(R.id.scanButton);
        refreshModelsButton = findViewById(R.id.refreshModelsButton);
    }

    private void installListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                if (!binding) {
                    saveCurrentVehicle();
                }
            }
        };
        vinInput.addTextChangedListener(watcher);
        vehicleNumberInput.addTextChangedListener(watcher);
        modelCodeInput.addTextChangedListener(watcher);
        modelNameInput.addTextChangedListener(watcher);
        issueNoteInput.addTextChangedListener(watcher);

        lineGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!binding && !uploading) {
                saveCurrentVehicle();
                loadDraft(checkedId == R.id.line2Button ? 2 : 1);
                bindVehicle();
            }
        });
        statusGroup.setOnCheckedChangeListener((group, checkedId) -> {
            issueNoteLayout.setVisibility(checkedId == R.id.statusNokButton ? View.VISIBLE : View.GONE);
            if (!binding) {
                saveCurrentVehicle();
            }
        });
        previousButton.setOnClickListener(view -> {
            if (currentIndex > 0 && validateCurrentVehicle()) {
                saveCurrentVehicle();
                currentIndex--;
                draft.currentIndex = currentIndex;
                draftStore.save(userKey, draft);
                bindVehicle();
            }
        });
        nextButton.setOnClickListener(view -> {
            if (currentIndex == 4) {
                submitBatch();
            } else if (validateCurrentVehicle()) {
                saveCurrentVehicle();
                currentIndex++;
                draft.currentIndex = currentIndex;
                draftStore.save(userKey, draft);
                bindVehicle();
            }
        });
        scanButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, ScanActivity.class);
            ArrayList<String> codes = new ArrayList<>();
            for (VehicleModel model : models) {
                if (model.active) {
                    codes.add(model.code);
                }
            }
            intent.putStringArrayListExtra(ScanActivity.EXTRA_MODEL_CODES, codes);
            scanLauncher.launch(intent);
        });
        refreshModelsButton.setOnClickListener(view -> loadModels(true));
    }

    private String roundTitle(int round) {
        String[] names = {"第一次 7:00-9:00", "第二次 9:00-11:00", "第三次 12:00-14:00",
                "第四次 14:00-16:00", "第五次 16:00-18:00"};
        int index = Math.max(0, Math.min(4, round - 1));
        return names[index];
    }

    private void loadDraft(int line) {
        InspectionDraft saved = draftStore.load(userKey, workDate, roundNumber, line);
        draft = saved == null ? InspectionDraft.empty(workDate, roundNumber, line) : saved;
        currentIndex = Math.max(0, Math.min(4, draft.currentIndex));
        for (int i = 0; i < 5; i++) {
            DraftVehicle vehicle = draft.vehicles.get(i);
            if (vehicle == null) {
                vehicle = new DraftVehicle(i + 1);
                draft.vehicles.set(i, vehicle);
            }
            vehicle.vehiclePosition = i + 1;
            if (vehicle.status == null || vehicle.status.isEmpty()) {
                vehicle.status = "NA";
            }
        }
        binding = true;
        lineGroup.check(line == 2 ? R.id.line2Button : R.id.line1Button);
        binding = false;
        draftStore.save(userKey, draft);
    }

    private void bindVehicle() {
        binding = true;
        DraftVehicle vehicle = draft.vehicles.get(currentIndex);
        positionText.setText("第" + (currentIndex + 1) + "/5辆（车位" + vehicle.vehiclePosition + "）");
        vinInput.setText(nullToEmpty(vehicle.vin));
        vehicleNumberInput.setText(nullToEmpty(vehicle.vehicleNumber));
        modelCodeInput.setText(nullToEmpty(vehicle.modelCode));
        modelNameInput.setText(nullToEmpty(vehicle.vehicleModel));
        issueNoteInput.setText(nullToEmpty(vehicle.issueNote));
        if ("OK".equals(vehicle.status)) {
            statusGroup.check(R.id.statusOkButton);
        } else if ("NOK".equals(vehicle.status)) {
            statusGroup.check(R.id.statusNokButton);
        } else {
            statusGroup.check(R.id.statusNaButton);
        }
        issueNoteLayout.setVisibility("NOK".equals(vehicle.status) ? View.VISIBLE : View.GONE);
        previousButton.setEnabled(currentIndex > 0 && !uploading);
        nextButton.setText(currentIndex == 4 ? R.string.upload : R.string.next);
        draftStatusText.setText(draft.lineNumber + "#线 · 本机草稿已保存（用户＋日期＋轮次＋线别）");
        binding = false;
    }

    private void saveCurrentVehicle() {
        if (submitted || draft == null || vinInput == null) {
            return;
        }
        DraftVehicle vehicle = draft.vehicles.get(currentIndex);
        vehicle.vin = vinInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        vehicle.vehicleNumber = vehicleNumberInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        vehicle.modelCode = modelCodeInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        vehicle.vehicleModel = modelNameInput.getText().toString().trim();
        if (statusGroup.getCheckedRadioButtonId() == R.id.statusOkButton) {
            vehicle.status = "OK";
        } else if (statusGroup.getCheckedRadioButtonId() == R.id.statusNokButton) {
            vehicle.status = "NOK";
        } else {
            vehicle.status = "NA";
        }
        vehicle.issueNote = "NOK".equals(vehicle.status)
                ? issueNoteInput.getText().toString().trim() : "";
        draft.currentIndex = currentIndex;
        draftStore.save(userKey, draft);
    }

    private String validationError(DraftVehicle vehicle) {
        if (!nullToEmpty(vehicle.vin).isEmpty() && !RecognitionParser.isValidVin(vehicle.vin)) {
            return "VIN须为17位，且不能包含I/O/Q";
        }
        String vehicleNumber = nullToEmpty(vehicle.vehicleNumber).trim();
        if (vehicleNumber.isEmpty()) {
            return "请填写车号";
        }
        if (!vehicleNumber.matches("[A-Z0-9]{6}")) {
            return "车号须为6位字母或数字";
        }
        if (RecognitionParser.isValidVin(vehicle.vin)
                && !RecognitionParser.vehicleNumberFromVin(vehicle.vin).equals(vehicleNumber)) {
            return "车号必须为VIN后6位";
        }
        if (nullToEmpty(vehicle.modelCode).trim().isEmpty()) {
            return "请填写车型代码";
        }
        if (nullToEmpty(vehicle.vehicleModel).trim().isEmpty()) {
            return "请填写车型名称";
        }
        if ("NOK".equals(vehicle.status) && nullToEmpty(vehicle.issueNote).trim().isEmpty()) {
            return "NOK状态必须填写备注";
        }
        return "";
    }

    private boolean validateCurrentVehicle() {
        saveCurrentVehicle();
        DraftVehicle vehicle = draft.vehicles.get(currentIndex);
        String error = validationError(vehicle);
        if (error.isEmpty()) {
            return true;
        }
        if (error.contains("VIN")) {
            vinInput.setError(error);
            vinInput.requestFocus();
        } else if (error.contains("车号")) {
            vehicleNumberInput.setError(error);
            vehicleNumberInput.requestFocus();
        } else if (error.contains("代码")) {
            modelCodeInput.setError(error);
            modelCodeInput.requestFocus();
        } else if (error.contains("名称")) {
            modelNameInput.setError(error);
            modelNameInput.requestFocus();
        } else {
            issueNoteInput.setError(error);
            issueNoteInput.requestFocus();
        }
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        return false;
    }

    private void submitBatch() {
        if (uploading || !validateCurrentVehicle()) {
            return;
        }
        saveCurrentVehicle();
        for (int i = 0; i < 5; i++) {
            String error = validationError(draft.vehicles.get(i));
            if (!error.isEmpty()) {
                currentIndex = i;
                draft.currentIndex = i;
                draftStore.save(userKey, draft);
                bindVehicle();
                Toast.makeText(this, "第" + (i + 1) + "辆：" + error, Toast.LENGTH_LONG).show();
                return;
            }
        }
        setUploading(true);
        List<DraftVehicle> vehicles = new ArrayList<>(draft.vehicles);
        apiClient.submitBatch(workDate, roundNumber, draft.lineNumber, vehicles,
                new ApiClient.Callback<List<InspectionRecord>>() {
                    @Override
                    public void onSuccess(List<InspectionRecord> records) {
                        submitted = true;
                        draftStore.clear(userKey, workDate, roundNumber, draft.lineNumber);
                        Toast.makeText(InspectionActivity.this, "本轮5辆已成功上传", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        setUploading(false);
                        saveCurrentVehicle();
                        Toast.makeText(InspectionActivity.this,
                                "上传失败，草稿已保留：" + message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setUploading(boolean value) {
        uploading = value;
        nextButton.setEnabled(!value);
        nextButton.setText(value ? "正在上传…" : (currentIndex == 4 ? "确认上传" : "下一辆"));
        previousButton.setEnabled(!value && currentIndex > 0);
        scanButton.setEnabled(!value);
        refreshModelsButton.setEnabled(!value);
        line1Button.setEnabled(!value);
        line2Button.setEnabled(!value);
    }

    private void applyScanResult(Intent data) {
        DraftVehicle vehicle = draft.vehicles.get(currentIndex);
        String vin = nullToEmpty(data.getStringExtra(ScanActivity.RESULT_VIN));
        String number = nullToEmpty(data.getStringExtra(ScanActivity.RESULT_VEHICLE_NUMBER));
        String code = nullToEmpty(data.getStringExtra(ScanActivity.RESULT_MODEL_CODE));
        if (!vin.isEmpty()) {
            vehicle.vin = vin;
        }
        if (!number.isEmpty()) {
            vehicle.vehicleNumber = number;
        }
        if (!code.isEmpty()) {
            vehicle.modelCode = code;
            for (VehicleModel model : models) {
                if (model.active && code.equalsIgnoreCase(model.code)) {
                    vehicle.vehicleModel = model.modelName;
                    break;
                }
            }
        }
        draftStore.save(userKey, draft);
        bindVehicle();
        Toast.makeText(this, "已填充可可靠识别的字段，请核对并手动补充其余字段",
                Toast.LENGTH_LONG).show();
    }

    private void loadModels(boolean notify) {
        refreshModelsButton.setEnabled(false);
        apiClient.listModels(new ApiClient.Callback<List<VehicleModel>>() {
            @Override
            public void onSuccess(List<VehicleModel> values) {
                refreshModelsButton.setEnabled(!uploading);
                models.clear();
                if (values != null) {
                    models.addAll(values);
                }
                if (notify) {
                    Toast.makeText(InspectionActivity.this,
                            "车型代码已刷新，共" + models.size() + "项", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                refreshModelsButton.setEnabled(!uploading);
                Toast.makeText(InspectionActivity.this,
                        "车型代码加载失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
