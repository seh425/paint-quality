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
    private EditText passwordInput;
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
        passwordInput = findViewById(R.id.loginPasswordInput);
        submitButton = findViewById(R.id.loginSubmitButton);
        modeButton = findViewById(R.id.loginModeButton);
        progressBar = findViewById(R.id.loginProgress);
        subtitle = findViewById(R.id.loginSubtitle);
        serverAddressInput.setText(sessionManager.getBaseUrl());
        inspectorNameInput.setText(sessionManager.getLastName());
        passwordInput.setText(sessionManager.getLastPassword());

        submitButton.setOnClickListener(view -> submit());
        modeButton.setOnClickListener(view -> {
            registerMode = !registerMode;
            updateMode();
        });
        updateMode();
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
        String name = inspectorNameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (name.isEmpty()) {
            inspectorNameInput.setError("请输入检查人");
            return;
        }
        if (password.length() < 6) {
            passwordInput.setError("登录密码至少6位");
            return;
        }
        sessionManager.saveLastName(name);
        sessionManager.saveLastPassword(password);
        setLoading(true);
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
                sessionManager.saveAuth(response.token, response.user);
                openAreaSelection();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this,
                        (submittedRegisterMode ? "注册失败：" : "登录失败：") + message,
                        Toast.LENGTH_LONG).show();
            }
        };
        if (registerMode) {
            apiClient.register(name, password, callback);
        } else {
            apiClient.login(name, password, callback);
        }
    }

    private void setLoading(boolean value) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!value);
        modeButton.setEnabled(!value);
        inspectorNameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
    }

    private void openAreaSelection() {
        Intent intent = new Intent(this, AreaSelectionActivity.class);
        startActivity(intent);
        finish();
    }

    private void openRounds() {
        Intent intent = new Intent(this, RoundActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
