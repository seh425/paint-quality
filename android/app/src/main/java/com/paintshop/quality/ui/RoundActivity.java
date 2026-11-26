package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.RoundInfo;
import com.paintshop.quality.model.User;

import java.time.LocalDate;
import java.util.List;

public class RoundActivity extends AppCompatActivity {
    private SessionManager sessionManager;
    private TextView userSummary;
    private TextView workDate;
    private TextView roundsHint;
    private LinearLayout roundsContainer;
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
        roundsHint = findViewById(R.id.roundsHint);
        roundsContainer = findViewById(R.id.roundsContainer);
        adminButton = findViewById(R.id.adminButton);
        logoutButton = findViewById(R.id.logoutButton);
        today = LocalDate.now().toString();
        workDate.setText("检查日期：" + today);
        updateUser(sessionManager.getUser());

        findViewById(R.id.sharedRecordsButton).setOnClickListener(view ->
                startActivity(new Intent(this, RecordsActivity.class)));
        findViewById(R.id.refreshRoundsButton).setOnClickListener(view -> loadRounds());
        adminButton.setOnClickListener(view -> startActivity(new Intent(this, AdminActivity.class)));
        logoutButton.setOnClickListener(view -> logout());
        validateSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (workDate == null) {
            return;
        }
        today = LocalDate.now().toString();
        workDate.setText("检查日期：" + today);
        loadRounds();
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
        String checkArea = user.checkArea == null ? "" : user.checkArea;
        userSummary.setText("检查人：" + user.inspectorName + "    责任区域：" + user.area
                + "    检查区域：" + checkArea);
        adminButton.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);
    }

    private void loadRounds() {
        roundsHint.setText("正在读取检查轮次…");
        roundsContainer.removeAllViews();
        ApiClient.get(this).getRounds(new ApiClient.Callback<List<RoundInfo>>() {
            @Override
            public void onSuccess(List<RoundInfo> rounds) {
                renderRounds(rounds);
            }

            @Override
            public void onError(String message) {
                roundsContainer.removeAllViews();
                roundsHint.setText("检查轮次读取失败：" + message + "\n请检查服务器是否已更新");
            }
        });
    }

    private void renderRounds(List<RoundInfo> rounds) {
        roundsContainer.removeAllViews();
        if (rounds == null || rounds.isEmpty()) {
            roundsHint.setText("管理员尚未上传检查模板，上传后自动显示检查轮次");
            return;
        }
        roundsHint.setText("请选择检查轮次（每轮5辆）");
        for (RoundInfo round : rounds) {
            Button button = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(8);
            button.setLayoutParams(params);
            button.setText(round.label());
            button.setOnClickListener(view -> openInspection(round.number, round.label()));
            roundsContainer.addView(button);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openInspection(int round, String label) {
        Intent intent = new Intent(this, InspectionActivity.class);
        intent.putExtra(InspectionActivity.EXTRA_ROUND, round);
        intent.putExtra(InspectionActivity.EXTRA_ROUND_LABEL, label);
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
