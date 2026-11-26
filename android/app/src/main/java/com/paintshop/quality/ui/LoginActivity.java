package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.SessionManager;

import java.util.LinkedHashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    private static final String[] AREAS = {"前处理电泳", "密封", "面漆"};
    private static final Map<String, String[]> CHECK_AREAS = new LinkedHashMap<>();

    static {
        CHECK_AREAS.put("前处理电泳", new String[]{"前处理", "电泳", "电泳烘房"});
        CHECK_AREAS.put("密封", new String[]{"密封线", "UBC", "Antichip", "中涂烘房出口"});
        CHECK_AREAS.put("面漆", new String[]{"中涂喷房", "中涂烘房", "面漆1线", "面漆2线",
                "面漆烘房1线", "面漆烘房2线"});
    }

    private ApiClient apiClient;
    private SessionManager sessionManager;
    private TextInputLayout serverAddressLayout;
    private TextInputLayout areaLayout;
    private TextInputLayout checkAreaLayout;
    private EditText serverAddressInput;
    private EditText inspectorNameInput;
    private MaterialAutoCompleteTextView areaInput;
    private MaterialAutoCompleteTextView checkAreaInput;
    private Button submitButton;
    private Button modeButton;
    private ProgressBar progressBar;
    private TextView subtitle;
    private boolean registerMode;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(this);
        if (sessionManager.hasSession()) {
            openRounds();
            return;
        }
        setContentView(R.layout.activity_login);
        apiClient = ApiClient.get(this);
        serverAddressLayout = findViewById(R.id.serverAddressLayout);
        areaLayout = findViewById(R.id.areaLayout);
        checkAreaLayout = findViewById(R.id.checkAreaLayout);
        serverAddressInput = findViewById(R.id.serverAddressInput);
        inspectorNameInput = findViewById(R.id.inspectorNameInput);
        areaInput = findViewById(R.id.areaInput);
        checkAreaInput = findViewById(R.id.checkAreaInput);
        submitButton = findViewById(R.id.loginSubmitButton);
        modeButton = findViewById(R.id.loginModeButton);
        progressBar = findViewById(R.id.loginProgress);
        subtitle = findViewById(R.id.loginSubtitle);
        serverAddressInput.setText(sessionManager.getBaseUrl());
        inspectorNameInput.setText(sessionManager.getLastName());
        boolean showServerAddress = !sessionManager.isBaseConfigured()
                || "PRIMARY_ADMIN".equals(sessionManager.getLastRole());
        serverAddressLayout.setVisibility(showServerAddress ? View.VISIBLE : View.GONE);

        setupAreaPickers();
        submitButton.setOnClickListener(view -> submit());
        modeButton.setOnClickListener(view -> {
            registerMode = !registerMode;
            updateMode();
        });
        updateMode();
    }

    private void setupAreaPickers() {
        areaInput.setThreshold(0);
        areaInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, AREAS));
        areaInput.setOnClickListener(view -> areaInput.showDropDown());
        areaInput.setOnItemClickListener((parent, view, position, id) -> refreshCheckAreas());
        areaInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                if (!loading) {
                    refreshCheckAreas();
                }
            }
        });
        checkAreaInput.setThreshold(0);
        checkAreaInput.setOnClickListener(view -> {
            if (checkAreaInput.getAdapter() != null && checkAreaInput.getAdapter().getCount() > 0) {
                checkAreaInput.showDropDown();
            }
        });
        refreshCheckAreas();

        String lastArea = sessionManager.getLastArea();
        if (!lastArea.isEmpty()) {
            areaInput.setText(lastArea, false);
            refreshCheckAreas();
            String lastCheckArea = sessionManager.getLastCheckArea();
            if (!lastCheckArea.isEmpty()) {
                checkAreaInput.setText(lastCheckArea, false);
            }
        }
    }

    private void refreshCheckAreas() {
        String[] items = CHECK_AREAS.get(areaInput.getText().toString().trim());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, items == null ? new String[0] : items);
        checkAreaInput.setAdapter(adapter);
        checkAreaInput.setText("", false);
    }

    private void updateMode() {
        submitButton.setText(registerMode ? R.string.register : R.string.login);
        subtitle.setText(registerMode ? "首次注册" : "检查人登录");
        modeButton.setText(registerMode ? "已有账号？切换到登录" : "首次使用？切换到注册");
        int areaVisibility = registerMode ? View.GONE : View.VISIBLE;
        areaLayout.setVisibility(areaVisibility);
        checkAreaLayout.setVisibility(areaVisibility);
    }

    private void submit() {
        if (loading) {
            return;
        }
        boolean showServerAddress = serverAddressLayout.getVisibility() == View.VISIBLE;
        String baseUrl = showServerAddress
                ? SessionManager.normalizeBaseUrl(serverAddressInput.getText().toString())
                : sessionManager.getBaseUrl();
        String name = inspectorNameInput.getText().toString().trim();
        String area = "";
        String checkArea = "";
        if (!registerMode) {
            area = areaInput.getText().toString().trim();
            checkArea = checkAreaInput.getText().toString().trim();
        }
        if (!isValidServerAddress(baseUrl)) {
            if (showServerAddress) {
                serverAddressInput.setError("请输入有效地址，例如 http://192.168.1.10:8080");
            } else {
                Toast.makeText(this, "服务器地址未配置，请联系管理员", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (name.isEmpty()) {
            inspectorNameInput.setError("请输入检查人");
            return;
        }
        if (!registerMode) {
            if (area.isEmpty()) {
                areaInput.setError("请选择责任区域");
                return;
            }
            if (CHECK_AREAS.containsKey(area) && checkArea.isEmpty()) {
                Toast.makeText(this, "请选择检查区域", Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (showServerAddress) {
            serverAddressInput.setText(baseUrl);
            sessionManager.saveBaseUrl(baseUrl);
        }
        sessionManager.saveLastName(name);
        setLoading(true);
        final String submittedArea = area;
        final String submittedCheckArea = checkArea;
        final boolean submittedRegisterMode = registerMode;
        ApiClient.Callback<ApiClient.AuthResponse> callback = new ApiClient.Callback<ApiClient.AuthResponse>() {
            @Override
            public void onSuccess(ApiClient.AuthResponse response) {
                setLoading(false);
                if (response == null || response.user == null || response.token == null
                        || response.token.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "服务器未返回有效登录信息", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!response.user.enabled) {
                    if (submittedRegisterMode) {
                        registerMode = false;
                        updateMode();
                        Toast.makeText(LoginActivity.this,
                                "注册成功，请等待管理员审核同意后再登录", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "账号尚未通过管理员审核或已被禁用", Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                sessionManager.saveLastArea(submittedArea);
                sessionManager.saveLastCheckArea(submittedCheckArea);
                sessionManager.saveAuth(response.token, response.user);
                openRounds();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this,
                        (registerMode ? "注册失败：" : "登录失败：") + message,
                        Toast.LENGTH_LONG).show();
            }
        };
        if (registerMode) {
            apiClient.register(name, callback);
        } else {
            apiClient.login(name, area, checkArea, callback);
        }
    }

    private boolean isValidServerAddress(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            boolean http = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            return http && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private void setLoading(boolean value) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!value);
        modeButton.setEnabled(!value);
        serverAddressInput.setEnabled(!value);
        inspectorNameInput.setEnabled(!value);
        areaInput.setEnabled(!value);
        checkAreaInput.setEnabled(!value);
    }

    private void openRounds() {
        Intent intent = new Intent(this, RoundActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
