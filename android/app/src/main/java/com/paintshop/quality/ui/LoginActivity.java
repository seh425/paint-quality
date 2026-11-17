package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.SessionManager;

public class LoginActivity extends AppCompatActivity {
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private EditText serverAddressInput;
    private EditText inspectorNameInput;
    private EditText areaInput;
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
        serverAddressInput = findViewById(R.id.serverAddressInput);
        inspectorNameInput = findViewById(R.id.inspectorNameInput);
        areaInput = findViewById(R.id.areaInput);
        submitButton = findViewById(R.id.loginSubmitButton);
        modeButton = findViewById(R.id.loginModeButton);
        progressBar = findViewById(R.id.loginProgress);
        subtitle = findViewById(R.id.loginSubtitle);
        serverAddressInput.setText(sessionManager.getBaseUrl());

        submitButton.setOnClickListener(view -> submit());
        modeButton.setOnClickListener(view -> {
            registerMode = !registerMode;
            updateMode();
        });
    }

    private void updateMode() {
        submitButton.setText(registerMode ? R.string.register : R.string.login);
        subtitle.setText(registerMode ? "首次注册" : "检查人登录");
        modeButton.setText(registerMode ? "已有账号？切换到登录" : "首次使用？切换到注册");
    }

    private void submit() {
        if (loading) {
            return;
        }
        String baseUrl = SessionManager.normalizeBaseUrl(serverAddressInput.getText().toString());
        String name = inspectorNameInput.getText().toString().trim();
        String area = areaInput.getText().toString().trim();
        if (!isValidServerAddress(baseUrl)) {
            serverAddressInput.setError("请输入有效地址，例如 http://192.168.1.10:8080");
            return;
        }
        if (name.isEmpty()) {
            inspectorNameInput.setError("请输入检查人");
            return;
        }
        if (area.isEmpty()) {
            areaInput.setError("请输入责任区域");
            return;
        }
        serverAddressInput.setText(baseUrl);
        sessionManager.saveBaseUrl(baseUrl);
        setLoading(true);
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
                    Toast.makeText(LoginActivity.this, "账号已被禁用，请联系管理员", Toast.LENGTH_LONG).show();
                    return;
                }
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
            apiClient.register(name, area, callback);
        } else {
            apiClient.login(name, area, callback);
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
    }

    private void openRounds() {
        Intent intent = new Intent(this, RoundActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
