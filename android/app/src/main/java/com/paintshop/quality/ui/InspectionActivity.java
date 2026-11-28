package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.DraftStore;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.DraftVehicle;
import com.paintshop.quality.model.HmiModelMapping;
import com.paintshop.quality.model.InspectionDraft;
import com.paintshop.quality.model.InspectionRecord;
import com.paintshop.quality.model.VehicleModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class InspectionActivity extends AppCompatActivity {
    public static final String EXTRA_ROUND = "roundNumber";
    public static final String EXTRA_ROUND_LABEL = "roundLabel";
    public static final String EXTRA_DATE = "workDate";
    public static final String EXTRA_LINES = "availableLines";

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
    private final List<HmiModelMapping> hmiMappings = new ArrayList<>();
    private final List<Integer> availableLines = new ArrayList<>();

    private TextView positionText;
    private TextView draftStatusText;
    private LinearLayout lineRow;
    private RadioGroup lineGroup;
    private RadioButton line1Button;
    private RadioButton line2Button;
    private Button doubleCheckButton;
    private EditText vehicleNumberInput;
    private MaterialAutoCompleteTextView modelNameDropdown;
    private TextInputLayout modelNameLayout;
    private EditText issueNoteInput;
    private TextInputLayout issueNoteLayout;
    private RadioGroup statusGroup;
    private RadioButton statusNaButton;
    private RadioButton statusNokButton;
    private Button previousButton;
    private Button nextButton;
    private Button scanButton;
    private Button batchScanButton;
    private Button hmiScanButton;
    private Button refreshModelsButton;
    private ActivityResultLauncher<Intent> scanLauncher;
    private ActivityResultLauncher<Intent> batchScanLauncher;
    private ActivityResultLauncher<Intent> hmiScanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspection);
        applyWindowInsets();
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
        ArrayList<Integer> intentLines = getIntent().getIntegerArrayListExtra(EXTRA_LINES);
        availableLines.clear();
        if (intentLines == null || intentLines.isEmpty()) {
            availableLines.add(1);
        } else {
            availableLines.addAll(intentLines);
        }
        configureLines();
        String roundLabel = getIntent().getStringExtra(EXTRA_ROUND_LABEL);
        ((TextView) findViewById(R.id.inspectionTitleText)).setText(
                roundLabel == null || roundLabel.isEmpty() ? roundTitle(roundNumber) : roundLabel);
        ((TextView) findViewById(R.id.inspectionDateText)).setText("日期：" + workDate + "（每轮恰好5辆）");
        loadDraft(availableLines.contains(1) ? 1 : availableLines.get(0));
        bindVehicle();
        installListeners();
        scanLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        applyScanResult(result.getData());
                    }
                });
        batchScanLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        applyBatchScanResult(result.getData());
                    }
                });
        hmiScanLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        applyHmiScanResult(result.getData());
                    }
                });
    }

    private void applyWindowInsets() {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadModels(false);
        loadHmiMappings();
    }

    @Override
    protected void onPause() {
        saveCurrentVehicle();
        super.onPause();
    }

    private void bindViews() {
        positionText = findViewById(R.id.vehiclePositionText);
        draftStatusText = findViewById(R.id.draftStatusText);
        lineRow = findViewById(R.id.lineRow);
        lineGroup = findViewById(R.id.lineGroup);
        line1Button = findViewById(R.id.line1Button);
        line2Button = findViewById(R.id.line2Button);
        doubleCheckButton = findViewById(R.id.doubleCheckButton);
        vehicleNumberInput = findViewById(R.id.vehicleNumberInput);
        modelNameDropdown = findViewById(R.id.modelNameDropdown);
        modelNameLayout = findViewById(R.id.modelNameLayout);
        issueNoteInput = findViewById(R.id.issueNoteInput);
        issueNoteLayout = findViewById(R.id.issueNoteLayout);
        statusGroup = findViewById(R.id.statusGroup);
        statusNaButton = findViewById(R.id.statusNaButton);
        statusNokButton = findViewById(R.id.statusNokButton);
        previousButton = findViewById(R.id.previousButton);
        nextButton = findViewById(R.id.nextButton);
        scanButton = findViewById(R.id.scanButton);
        batchScanButton = findViewById(R.id.batchScanButton);
        hmiScanButton = findViewById(R.id.hmiScanButton);
        refreshModelsButton = findViewById(R.id.refreshModelsButton);
    }

    private void configureLines() {
        boolean showLines = availableLines.size() > 1;
        lineGroup.setVisibility(showLines ? View.VISIBLE : View.GONE);
        line1Button.setEnabled(availableLines.contains(1));
        line2Button.setEnabled(availableLines.contains(2));
        if (availableLines.size() == 1) {
            lineGroup.check(availableLines.get(0) == 2 ? R.id.line2Button : R.id.line1Button);
        }
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
        vehicleNumberInput.addTextChangedListener(watcher);
        issueNoteInput.addTextChangedListener(watcher);
        modelNameDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (!binding && !uploading) {
                saveCurrentVehicle();
            }
        });

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
        doubleCheckButton.setOnClickListener(view -> {
            if (uploading) {
                return;
            }
            draft.doubleCheck = !draft.doubleCheck;
            draftStore.save(userKey, draft);
            updateDoubleCheckView();
        });
        hmiScanButton.setOnClickListener(view -> {
            if (!uploading) {
                hmiScanLauncher.launch(new Intent(this, HmiScanActivity.class));
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
        batchScanButton.setOnClickListener(view -> {
            if (uploading) {
                return;
            }
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_BATCH, true);
            ArrayList<String> codes = new ArrayList<>();
            for (VehicleModel model : models) {
                if (model.active) {
                    codes.add(model.code);
                }
            }
            intent.putStringArrayListExtra(ScanActivity.EXTRA_MODEL_CODES, codes);
            batchScanLauncher.launch(intent);
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
        updateDoubleCheckView();
    }

    private void updateDoubleCheckView() {
        if (doubleCheckButton == null || draft == null) {
            return;
        }
        doubleCheckButton.setText(draft.doubleCheck ? "双倍检查：开" : "双倍检查：关");
        doubleCheckButton.setEnabled(!uploading);
        if (!uploading) {
            nextButton.setText(currentIndex == 4
                    ? (draft.doubleCheck ? "确认追加（双倍）" : "确认上传") : "下一辆");
        }
    }

    private void bindVehicle() {
        binding = true;
        DraftVehicle vehicle = draft.vehicles.get(currentIndex);
        positionText.setText("第" + (currentIndex + 1) + "/5辆（车位" + vehicle.vehiclePosition + "）");
        vehicleNumberInput.setText(nullToEmpty(vehicle.vehicleNumber));
        modelNameDropdown.setText(nullToEmpty(vehicle.vehicleModel), false);
        issueNoteInput.setText(nullToEmpty(vehicle.issueNote));
        if ("NOK".equals(vehicle.status)) {
            statusGroup.check(R.id.statusNokButton);
        } else {
            statusGroup.check(R.id.statusNaButton);
        }
        issueNoteLayout.setVisibility("NOK".equals(vehicle.status) ? View.VISIBLE : View.GONE);
        previousButton.setEnabled(currentIndex > 0 && !uploading);
        nextButton.setText(currentIndex == 4
                ? (draft.doubleCheck ? "确认追加（双倍）" : "确认上传") : "下一辆");
        String doubleText = draft.doubleCheck ? "｜双倍检查已开启，再次上传会追加" : "";
        draftStatusText.setText(draft.lineNumber + "#线 · 本机草稿已保存（用户＋日期＋轮次＋线别）" + doubleText);
        binding = false;
    }

    private void saveCurrentVehicle() {
        if (submitted || draft == null || vehicleNumberInput == null) {
            return;
        }
        DraftVehicle vehicle = draft.vehicles.get(currentIndex);
        vehicle.vin = "";
        vehicle.vehicleNumber = vehicleNumberInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        String selectedName = modelNameDropdown.getText().toString().trim();
        vehicle.vehicleModel = selectedName;
        vehicle.modelCode = "";
        for (VehicleModel model : models) {
            if (model.active && model.modelName != null
                    && model.modelName.equalsIgnoreCase(selectedName)) {
                vehicle.modelCode = model.code;
                vehicle.vehicleModel = model.modelName;
                break;
            }
        }
        if (statusGroup.getCheckedRadioButtonId() == R.id.statusNokButton) {
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
        String vehicleNumber = nullToEmpty(vehicle.vehicleNumber).trim();
        if (vehicleNumber.isEmpty()) {
            return "请填写车号";
        }
        if (!vehicleNumber.matches("[A-Z0-9]{6}")) {
            return "车号须为6位字母或数字";
        }
        if (nullToEmpty(vehicle.vehicleModel).trim().isEmpty()) {
            return "请选择车型名称";
        }
        if (nullToEmpty(vehicle.modelCode).trim().isEmpty()) {
            return "所选车型不在车型库中，请让管理员添加后刷新";
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
        if (error.contains("车号")) {
            vehicleNumberInput.setError(error);
            vehicleNumberInput.requestFocus();
        } else if (error.contains("车型")) {
            modelNameLayout.setError(error);
            modelNameDropdown.requestFocus();
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
        apiClient.submitBatch(workDate, roundNumber, draft.lineNumber, vehicles, draft.doubleCheck,
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
        nextButton.setText(value ? "正在上传…" : (currentIndex == 4
                ? (draft.doubleCheck ? "确认追加（双倍）" : "确认上传") : "下一辆"));
        previousButton.setEnabled(!value && currentIndex > 0);
        scanButton.setEnabled(!value);
        batchScanButton.setEnabled(!value);
        hmiScanButton.setEnabled(!value);
        refreshModelsButton.setEnabled(!value);
        doubleCheckButton.setEnabled(!value);
        line1Button.setEnabled(!value && availableLines.contains(1));
        line2Button.setEnabled(!value && availableLines.contains(2));
    }

    private void applyScanResult(Intent data) {
        DraftVehicle vehicle = draft.vehicles.get(currentIndex);
        String number = nullToEmpty(data.getStringExtra(ScanActivity.RESULT_VEHICLE_NUMBER));
        String code = nullToEmpty(data.getStringExtra(ScanActivity.RESULT_MODEL_CODE));
        if (!number.isEmpty()) {
            vehicle.vehicleNumber = number;
        }
        if (!code.isEmpty()) {
            boolean resolved = false;
            for (VehicleModel model : models) {
                if (model.active && code.equalsIgnoreCase(model.code)) {
                    vehicle.modelCode = model.code;
                    vehicle.vehicleModel = model.modelName;
                    resolved = true;
                    break;
                }
            }
            if (!resolved) {
                vehicle.modelCode = "";
                vehicle.vehicleModel = "";
                Toast.makeText(this, "识别到车型代码 " + code
                        + "，但车型库没有对应车型，请让管理员添加后刷新", Toast.LENGTH_LONG).show();
            }
        }
        draftStore.save(userKey, draft);
        bindVehicle();
        Toast.makeText(this, "已按条码填充车号、车型，请核对并补充其余字段",
                Toast.LENGTH_LONG).show();
    }

    private void applyBatchScanResult(Intent data) {
        ArrayList<String> numbers = data.getStringArrayListExtra(ScanActivity.RESULT_VEHICLE_NUMBERS);
        ArrayList<String> codes = data.getStringArrayListExtra(ScanActivity.RESULT_MODEL_CODE_LIST);
        int count = numbers == null ? 0 : numbers.size();
        if (count == 0) {
            Toast.makeText(this, "没有识别到任何条码，请确认照片清晰且包含完整条码",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int filled = Math.min(5, count);
        int unresolved = 0;
        for (int i = 0; i < filled; i++) {
            DraftVehicle vehicle = draft.vehicles.get(i);
            String number = nullToEmpty(numbers.get(i));
            String code = codes != null && i < codes.size() ? nullToEmpty(codes.get(i)) : "";
            if (!number.isEmpty()) {
                vehicle.vehicleNumber = number;
            }
            vehicle.modelCode = "";
            vehicle.vehicleModel = "";
            if (!code.isEmpty()) {
                for (VehicleModel model : models) {
                    if (model.active && code.equalsIgnoreCase(model.code)) {
                        vehicle.modelCode = model.code;
                        vehicle.vehicleModel = model.modelName;
                        break;
                    }
                }
                if (vehicle.modelCode.isEmpty()) {
                    unresolved++;
                }
            }
        }
        currentIndex = 0;
        draft.currentIndex = 0;
        draftStore.save(userKey, draft);
        bindVehicle();
        String message = "已批量识别 " + filled + " 张，请逐车核对";
        if (unresolved > 0) {
            message += "；其中 " + unresolved + " 辆车型代码不在车型库中，请让管理员添加后刷新";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void applyHmiScanResult(Intent data) {
        ArrayList<String> numbers = data.getStringArrayListExtra(HmiScanActivity.RESULT_VEHICLE_NUMBERS);
        ArrayList<String> values = data.getStringArrayListExtra(HmiScanActivity.RESULT_MODEL_VALUES);
        int count = numbers == null ? 0 : numbers.size();
        if (count == 0) {
            Toast.makeText(this, "没有识别到HMI车号，请调整选择框后重试", Toast.LENGTH_LONG).show();
            return;
        }
        int filled = Math.min(5, count);
        int unresolved = 0;
        for (int i = 0; i < filled; i++) {
            DraftVehicle vehicle = draft.vehicles.get(i);
            String number = nullToEmpty(numbers.get(i));
            String rawModel = values != null && i < values.size() ? nullToEmpty(values.get(i)) : "";
            if (!number.isEmpty()) {
                vehicle.vehicleNumber = number;
            }
            vehicle.modelCode = "";
            vehicle.vehicleModel = "";
            String pviCode = resolveHmiModelCode(rawModel);
            if (!pviCode.isEmpty()) {
                for (VehicleModel model : models) {
                    if (model.active && pviCode.equalsIgnoreCase(model.code)) {
                        vehicle.modelCode = model.code;
                        vehicle.vehicleModel = model.modelName;
                        break;
                    }
                }
            }
            if (vehicle.modelCode.isEmpty()) {
                unresolved++;
            }
        }
        currentIndex = 0;
        draft.currentIndex = 0;
        draftStore.save(userKey, draft);
        bindVehicle();
        String message = "已按HMI识别 " + filled + " 辆，请逐车核对";
        if (unresolved > 0) {
            message += "；其中 " + unresolved + " 辆车型未配置HMI转换，请到管理员页完善";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String resolveHmiModelCode(String rawValue) {
        String value = nullToEmpty(rawValue).trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        for (HmiModelMapping mapping : hmiMappings) {
            if (mapping.hmiValue != null && mapping.hmiValue.trim().equalsIgnoreCase(value)) {
                return mapping.pviCode == null ? "" : mapping.pviCode.trim();
            }
        }
        for (VehicleModel model : models) {
            if (model.active && model.code != null && model.code.equalsIgnoreCase(value)) {
                return model.code;
            }
        }
        return "";
    }

    private void loadHmiMappings() {
        apiClient.listHmiMappings(new ApiClient.Callback<List<HmiModelMapping>>() {
            @Override
            public void onSuccess(List<HmiModelMapping> values) {
                hmiMappings.clear();
                if (values != null) {
                    hmiMappings.addAll(values);
                }
            }

            @Override
            public void onError(String message) {
                // HMI mappings are optional; barcode scanning and manual entry still work.
            }
        });
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
                bindModelOptions(notify);
            }

            @Override
            public void onError(String message) {
                refreshModelsButton.setEnabled(!uploading);
                Toast.makeText(InspectionActivity.this,
                        "车型列表加载失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindModelOptions(boolean notify) {
        List<String> names = new ArrayList<>();
        for (VehicleModel model : models) {
            if (model.active && model.modelName != null && !model.modelName.trim().isEmpty()) {
                names.add(model.modelName);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names);
        modelNameDropdown.setAdapter(adapter);
        if (draft != null) {
            String current = nullToEmpty(draft.vehicles.get(currentIndex).vehicleModel);
            modelNameDropdown.setText(current, false);
        }
        if (notify) {
            Toast.makeText(InspectionActivity.this,
                    "车型列表已刷新，共" + names.size() + "项", Toast.LENGTH_SHORT).show();
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
