package com.paintshop.quality.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.AreaCatalog;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.User;

import java.util.ArrayList;
import java.util.List;

public class AreaSelectionActivity extends AppCompatActivity {
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private MaterialAutoCompleteTextView areaInput;
    private MaterialAutoCompleteTextView checkAreaInput;
    private Button confirmButton;
    private ProgressBar progressBar;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(this);
        User user = sessionManager.getUser();
        if (!sessionManager.hasSession() || user == null) {
            openLogin();
            return;
        }
        setContentView(R.layout.activity_area_selection);
        apiClient = ApiClient.get(this);
        ((TextView) findViewById(R.id.areaSelectionUserText))
                .setText("检查人：" + user.inspectorName);
        areaInput = findViewById(R.id.areaSelectionAreaInput);
        checkAreaInput = findViewById(R.id.areaSelectionCheckAreaInput);
        confirmButton = findViewById(R.id.areaSelectionConfirmButton);
        progressBar = findViewById(R.id.areaSelectionProgress);

        setupAreaPicker(user);
        confirmButton.setOnClickListener(view -> confirm());
    }

    private void setupAreaPicker(User user) {
        List<String> responsibilities = new ArrayList<>(AreaCatalog.responsibilities());
        areaInput.setThreshold(0);
        areaInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, responsibilities));
        areaInput.setOnClickListener(view -> areaInput.showDropDown());
        areaInput.setOnItemClickListener((parent, view, position, id) -> refreshCheckAreas());
        areaInput.setText(initialArea(user), false);

        String initialCheckArea = sessionManager.getLastCheckArea();
        if (initialCheckArea.isEmpty()
                || !AreaCatalog.checkAreas(areaInput.getText().toString()).contains(initialCheckArea)) {
            initialCheckArea = user.checkArea;
        }
        if (initialCheckArea == null || initialCheckArea.isEmpty()
                || !AreaCatalog.checkAreas(areaInput.getText().toString()).contains(initialCheckArea)) {
            initialCheckArea = AreaCatalog.firstCheckArea(areaInput.getText().toString());
        }
        refreshCheckAreas();
        checkAreaInput.setText(initialCheckArea, false);
    }

    private String initialArea(User user) {
        String lastArea = sessionManager.getLastArea();
        List<String> responsibilities = AreaCatalog.responsibilities();
        if (responsibilities.contains(lastArea)) {
            return lastArea;
        }
        if (responsibilities.contains(user.area)) {
            return user.area;
        }
        return responsibilities.isEmpty() ? "" : responsibilities.get(0);
    }

    private void refreshCheckAreas() {
        String area = areaInput.getText().toString().trim();
        List<String> areas = new ArrayList<>(AreaCatalog.checkAreas(area));
        checkAreaInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, areas));
        checkAreaInput.setThreshold(0);
        checkAreaInput.setOnClickListener(view -> {
            if (checkAreaInput.getAdapter() != null
                    && checkAreaInput.getAdapter().getCount() > 0) {
                checkAreaInput.showDropDown();
            }
        });
        if (areas.isEmpty()) {
            checkAreaInput.setText("", false);
        } else {
            String current = checkAreaInput.getText().toString().trim();
            int index = areas.indexOf(current);
            checkAreaInput.setText(areas.get(index < 0 ? 0 : index), false);
        }
    }

    private void confirm() {
        if (loading) {
            return;
        }
        String area = areaInput.getText().toString().trim();
        String checkArea = checkAreaInput.getText().toString().trim();
        if (area.isEmpty()) {
            Toast.makeText(this, "请选择责任区域", Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkArea.isEmpty()) {
            Toast.makeText(this, "请选择检查区域", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        apiClient.updateMyAreas(area, checkArea, new ApiClient.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                setLoading(false);
                sessionManager.saveLastArea(area);
                sessionManager.saveLastCheckArea(checkArea);
                if (user != null) {
                    sessionManager.updateUser(user);
                }
                startActivity(new Intent(AreaSelectionActivity.this, RoundActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(AreaSelectionActivity.this,
                        "保存检查区域失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean value) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        confirmButton.setEnabled(!value);
        areaInput.setEnabled(!value);
        checkAreaInput.setEnabled(!value);
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
