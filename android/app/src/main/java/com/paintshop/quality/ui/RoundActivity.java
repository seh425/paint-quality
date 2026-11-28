package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.AreaCatalog;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.RoundInfo;
import com.paintshop.quality.model.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoundActivity extends AppCompatActivity {
    private SessionManager sessionManager;
    private TextView userSummary;
    private TextView workDate;
    private TextView roundsHint;
    private TextView areasStatus;
    private LinearLayout roundsContainer;
    private Spinner areaSpinner;
    private Spinner checkAreaSpinner;
    private Button adminButton;
    private Button logoutButton;
    private String today;
    private boolean suppressAreaCallbacks;
    private boolean areasSaved;
    private boolean saveInFlight;
    private String savingArea = "";
    private String savingCheckArea = "";

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
        areasStatus = findViewById(R.id.areasStatusText);
        roundsContainer = findViewById(R.id.roundsContainer);
        areaSpinner = findViewById(R.id.areaSpinner);
        checkAreaSpinner = findViewById(R.id.checkAreaSpinner);
        adminButton = findViewById(R.id.adminButton);
        logoutButton = findViewById(R.id.logoutButton);
        today = LocalDate.now().toString();
        workDate.setText("检查日期：" + today);
        updateUser(sessionManager.getUser());
        setupAreaSpinners();

        findViewById(R.id.sharedRecordsButton).setOnClickListener(view ->
                startActivity(new Intent(this, RecordsActivity.class)));
        findViewById(R.id.refreshRoundsButton).setOnClickListener(view -> {
            areasSaved = false;
            saveAreasAndLoadRounds();
        });
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
        if (areasSaved) {
            loadRounds(selectedCheckArea());
        } else {
            saveAreasAndLoadRounds();
        }
    }

    private void setupAreaSpinners() {
        List<String> responsibilities = new ArrayList<>(AreaCatalog.responsibilities());
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, responsibilities);
        areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        areaSpinner.setAdapter(areaAdapter);

        User user = sessionManager.getUser();
        String savedArea = sessionManager.getLastArea();
        if (!responsibilities.contains(savedArea)) {
            savedArea = user == null ? "" : user.area;
        }
        if (!responsibilities.contains(savedArea)) {
            savedArea = responsibilities.isEmpty() ? "" : responsibilities.get(0);
        }
        int areaIndex = responsibilities.indexOf(savedArea);
        if (areaIndex >= 0) {
            areaSpinner.setSelection(areaIndex, false);
        }

        String desiredCheckArea = sessionManager.getLastCheckArea();
        if (desiredCheckArea == null || desiredCheckArea.isEmpty()) {
            desiredCheckArea = user == null ? "" : user.checkArea;
        }
        populateCheckAreas(savedArea, desiredCheckArea);

        areaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressAreaCallbacks) {
                    return;
                }
                String area = selectedArea();
                sessionManager.saveLastArea(area);
                populateCheckAreas(area, "");
                areasSaved = false;
                saveAreasAndLoadRounds();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        checkAreaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressAreaCallbacks) {
                    return;
                }
                sessionManager.saveLastCheckArea(selectedCheckArea());
                areasSaved = false;
                saveAreasAndLoadRounds();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void populateCheckAreas(String area, String desired) {
        List<String> checkAreas = new ArrayList<>(AreaCatalog.checkAreas(area));
        suppressAreaCallbacks = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, checkAreas);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        checkAreaSpinner.setAdapter(adapter);
        int index = checkAreas.indexOf(desired);
        if (index < 0) {
            index = 0;
        }
        if (index >= 0) {
            checkAreaSpinner.setSelection(index, false);
        }
        suppressAreaCallbacks = false;
    }

    private String selectedArea() {
        Object item = areaSpinner.getSelectedItem();
        return item == null ? "" : item.toString();
    }

    private String selectedCheckArea() {
        Object item = checkAreaSpinner.getSelectedItem();
        return item == null ? "" : item.toString();
    }

    private void saveAreasAndLoadRounds() {
        final String area = selectedArea();
        final String checkArea = selectedCheckArea();
        if (area.isEmpty() || checkArea.isEmpty()) {
            roundsHint.setText("请选择责任区域和检查区域");
            return;
        }
        if (saveInFlight) {
            areasSaved = false;
            return;
        }
        saveInFlight = true;
        savingArea = area;
        savingCheckArea = checkArea;
        areasStatus.setText("正在保存区域选择…");
        ApiClient.get(this).updateMyAreas(area, checkArea, new ApiClient.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                saveInFlight = false;
                if (!area.equals(selectedArea()) || !checkArea.equals(selectedCheckArea())) {
                    areasSaved = false;
                    saveAreasAndLoadRounds();
                    return;
                }
                areasSaved = true;
                sessionManager.saveLastArea(area);
                sessionManager.saveLastCheckArea(checkArea);
                if (user != null) {
                    sessionManager.updateUser(user);
                    updateUser(user);
                }
                areasStatus.setText("区域已保存，下方显示该检查区域的检查轮次");
                loadRounds(checkArea);
            }

            @Override
            public void onError(String message) {
                saveInFlight = false;
                areasSaved = false;
                areasStatus.setText("区域保存失败，请检查网络后重试");
                roundsContainer.removeAllViews();
                roundsHint.setText("区域保存失败：" + message);
            }
        });
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
        userSummary.setText("检查人：" + user.inspectorName);
        adminButton.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);
    }

    private void loadRounds(String checkArea) {
        roundsHint.setText("正在读取检查轮次…");
        roundsContainer.removeAllViews();
        ApiClient.get(this).getRounds(checkArea, new ApiClient.Callback<List<RoundInfo>>() {
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
            roundsHint.setText("管理员尚未上传该检查区域的检查模板，上传后自动显示检查轮次");
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
            button.setOnClickListener(view ->
                    openInspection(round.number, round.label(), round.availableLines()));
            roundsContainer.addView(button);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openInspection(int round, String label, List<Integer> lines) {
        Intent intent = new Intent(this, InspectionActivity.class);
        intent.putExtra(InspectionActivity.EXTRA_ROUND, round);
        intent.putExtra(InspectionActivity.EXTRA_ROUND_LABEL, label);
        intent.putExtra(InspectionActivity.EXTRA_DATE, today);
        intent.putIntegerArrayListExtra(InspectionActivity.EXTRA_LINES,
                new ArrayList<>(lines == null || lines.isEmpty() ? Collections.singletonList(1) : lines));
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
