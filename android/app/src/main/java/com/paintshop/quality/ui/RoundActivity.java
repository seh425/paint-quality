package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.User;

import java.time.LocalDate;

public class RoundActivity extends AppCompatActivity {
    private SessionManager sessionManager;
    private TextView userSummary;
    private TextView workDate;
    private Button adminButton;
    private Button logoutButton;
    private String today;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(this);
        if (!sessionManager.hasSession()) {
            openLogin();
            return;
        }
        setContentView(R.layout.activity_round);
        userSummary = findViewById(R.id.userSummaryText);
        workDate = findViewById(R.id.workDateText);
        adminButton = findViewById(R.id.adminButton);
        logoutButton = findViewById(R.id.logoutButton);
        today = LocalDate.now().toString();
        workDate.setText("检查日期：" + today);
        updateUser(sessionManager.getUser());

        int[] buttonIds = {R.id.round1Button, R.id.round2Button, R.id.round3Button,
                R.id.round4Button, R.id.round5Button};
        for (int i = 0; i < buttonIds.length; i++) {
            int round = i + 1;
            findViewById(buttonIds[i]).setOnClickListener(view -> openInspection(round));
        }
        findViewById(R.id.sharedRecordsButton).setOnClickListener(view ->
                startActivity(new Intent(this, RecordsActivity.class)));
        adminButton.setOnClickListener(view -> startActivity(new Intent(this, AdminActivity.class)));
        logoutButton.setOnClickListener(view -> logout());
        validateSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (workDate != null) {
            today = LocalDate.now().toString();
            workDate.setText("检查日期：" + today);
        }
    }

    private void validateSession() {
        ApiClient.get(this).me(new ApiClient.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user == null || !user.enabled) {
                    Toast.makeText(RoundActivity.this, "账号已失效或被禁用", Toast.LENGTH_LONG).show();
                    sessionManager.clear();
                    openLogin();
                    return;
                }
                sessionManager.updateUser(user);
                updateUser(user);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RoundActivity.this,
                        "登录状态暂时无法核验：" + message + "；本机草稿仍可编辑",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateUser(User user) {
        if (user == null) {
            return;
        }
        userSummary.setText("检查人：" + user.inspectorName + "    责任区域：" + user.area);
        adminButton.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);
    }

    private void openInspection(int round) {
        Intent intent = new Intent(this, InspectionActivity.class);
        intent.putExtra(InspectionActivity.EXTRA_ROUND, round);
        intent.putExtra(InspectionActivity.EXTRA_DATE, today);
        startActivity(intent);
    }

    private void logout() {
        logoutButton.setEnabled(false);
        ApiClient.get(this).logout(new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                sessionManager.clear();
                openLogin();
            }

            @Override
            public void onError(String message) {
                sessionManager.clear();
                Toast.makeText(RoundActivity.this,
                        "服务器退出失败，已退出本机：" + message, Toast.LENGTH_LONG).show();
                openLogin();
            }
        });
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
