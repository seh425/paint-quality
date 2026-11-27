package com.paintshop.quality.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.AreaCatalog;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.User;
import com.paintshop.quality.model.VehicleModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminActivity extends AppCompatActivity {
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private User currentUser;
    private EditText emailInput;
    private Spinner templateAreaSpinner;
    private TextView templateAreaText;
    private final List<String> templateAreas = new ArrayList<>();
    private Spinner excelDateSpinner;
    private ArrayAdapter<String> dateAdapter;
    private final List<String> excelDates = new ArrayList<>();
    private String selectedDate;
    private EditText newModelCodeInput;
    private EditText newModelNameInput;
    private TextView selectedTemplateText;
    private Button uploadTemplateButton;
    private Uri selectedTemplate;
    private String selectedTemplateName = "template.xlsx";
    private UserAdapter userAdapter;
    private ModelAdapter modelAdapter;
    private ActivityResultLauncher<String[]> templateLauncher;
    private final List<java.io.File> pendingShareFiles = new ArrayList<>();
    private boolean shareInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(this);
        currentUser = sessionManager.getUser();
        if (currentUser == null || !currentUser.isAdmin()) {
            Toast.makeText(this, "没有管理员权限", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_admin);
        applyWindowInsets();
        apiClient = ApiClient.get(this);
        ((TextView) findViewById(R.id.adminRoleText)).setText(
                currentUser.isPrimaryAdmin() ? "当前角色：一级管理员" : "当前角色：二级管理员");
        emailInput = findViewById(R.id.adminEmailInput);
        emailInput.setText(nullToEmpty(currentUser.email));
        setupServerAddress();
        setupTemplateArea();
        newModelCodeInput = findViewById(R.id.newModelCodeInput);
        newModelNameInput = findViewById(R.id.newModelNameInput);
        selectedTemplateText = findViewById(R.id.selectedTemplateText);
        uploadTemplateButton = findViewById(R.id.uploadTemplateButton);
        findViewById(R.id.primarySection).setVisibility(View.VISIBLE);
        setupDates();
        setupLists();
        setupActions();
        loadExcelDates();
        loadUsers();
        loadModels();
    }

    private void applyWindowInsets() {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void setupServerAddress() {
        View section = findViewById(R.id.serverSection);
        EditText serverInput = findViewById(R.id.adminServerAddressInput);
        if (!currentUser.isPrimaryAdmin()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        serverInput.setText(sessionManager.getBaseUrl());
        findViewById(R.id.saveServerButton).setOnClickListener(view -> {
            String value = serverInput.getText().toString().trim();
            if (!isValidServerAddress(value)) {
                serverInput.setError("请输入有效地址，例如 https://example.com/");
                return;
            }
            String normalized = SessionManager.normalizeBaseUrl(value);
            sessionManager.saveBaseUrl(normalized);
            serverInput.setText(normalized);
            Toast.makeText(this, "服务器地址已保存", Toast.LENGTH_SHORT).show();
        });
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

    private void setupTemplateArea() {
        templateAreas.clear();
        if (currentUser.isPrimaryAdmin()) {
            templateAreas.addAll(AreaCatalog.allCheckAreas());
        } else {
            templateAreas.addAll(AreaCatalog.checkAreas(currentUser.area));
            if (templateAreas.isEmpty() && !nullToEmpty(currentUser.checkArea).isEmpty()) {
                templateAreas.add(currentUser.checkArea);
            }
        }
        templateAreaSpinner = findViewById(R.id.templateAreaSpinner);
        templateAreaText = findViewById(R.id.templateAreaText);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, templateAreas);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        templateAreaSpinner.setAdapter(adapter);
        templateAreaSpinner.setVisibility(View.VISIBLE);
        templateAreaText.setVisibility(View.GONE);
        int index = templateAreas.indexOf(nullToEmpty(currentUser.checkArea));
        if (index >= 0) {
            templateAreaSpinner.setSelection(index);
        }
    }

    private String selectedTemplateArea() {
        Object selected = templateAreaSpinner.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    private void setupDates() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 3; i++) {
            excelDates.add(today.minusDays(i).toString());
        }
        selectedDate = excelDates.get(0);
        excelDateSpinner = findViewById(R.id.excelDateSpinner);
        dateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, excelDates);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        excelDateSpinner.setAdapter(dateAdapter);
        excelDateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDate = excelDates.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedDate = null;
            }
        });
    }

    private void setupLists() {
        RecyclerView usersList = findViewById(R.id.usersList);
        usersList.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new UserAdapter(this::showUserEditor);
        usersList.setAdapter(userAdapter);
        RecyclerView modelsList = findViewById(R.id.modelsList);
        modelsList.setLayoutManager(new LinearLayoutManager(this));
        modelAdapter = new ModelAdapter(new ModelAdapter.Listener() {
            @Override
            public void onEdit(VehicleModel model) {
                showModelEditor(model);
            }

            @Override
            public void onDelete(VehicleModel model) {
                confirmDeleteModel(model);
            }
        });
        modelsList.setAdapter(modelAdapter);
    }

    private void setupActions() {
        findViewById(R.id.adminBackButton).setOnClickListener(view -> finish());
        findViewById(R.id.saveEmailButton).setOnClickListener(view -> saveEmail());
        findViewById(R.id.sendEmailButton).setOnClickListener(view -> sendEmail());
        findViewById(R.id.addModelButton).setOnClickListener(view -> addModel());
        templateLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) {
                return;
            }
            String name = queryFileName(uri);
            if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                Toast.makeText(this, "请选择.xlsx格式模板", Toast.LENGTH_LONG).show();
                return;
            }
            selectedTemplate = uri;
            selectedTemplateName = name;
            selectedTemplateText.setText("已选择：" + name);
            uploadTemplateButton.setEnabled(true);
        });
        findViewById(R.id.chooseTemplateButton).setOnClickListener(view -> templateLauncher.launch(
                new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"}));
        uploadTemplateButton.setOnClickListener(view -> uploadTemplate());
    }

    private void saveEmail() {
        String email = emailInput.getText().toString().trim();
        Button button = findViewById(R.id.saveEmailButton);
        button.setEnabled(false);
        apiClient.saveEmail(email, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                button.setEnabled(true);
                currentUser.email = email;
                sessionManager.updateUser(currentUser);
                Toast.makeText(AdminActivity.this, "我的邮箱已保存", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                button.setEnabled(true);
                Toast.makeText(AdminActivity.this, "保存失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendEmail() {
        if (selectedDate == null) {
            return;
        }
        String email = nullToEmpty(currentUser.email).trim();
        if (email.isEmpty()) {
            emailInput.setError("请先填写并保存我的邮箱");
            return;
        }
        final Button button = findViewById(R.id.sendEmailButton);
        button.setEnabled(false);
        apiClient.getExcelAreas(new ApiClient.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> areas) {
                List<String> targets = areas == null || areas.isEmpty()
                        ? fallbackExcelAreas() : areas;
                downloadAll(targets, email, button);
            }

            @Override
            public void onError(String message) {
                downloadAll(fallbackExcelAreas(), email, button);
            }
        });
    }

    private List<String> fallbackExcelAreas() {
        if (currentUser.isPrimaryAdmin()) {
            return AreaCatalog.allCheckAreas();
        }
        List<String> allowed = AreaCatalog.checkAreas(currentUser.area);
        if (!allowed.isEmpty()) {
            return allowed;
        }
        List<String> single = new ArrayList<>();
        if (!nullToEmpty(currentUser.checkArea).isEmpty()) {
            single.add(currentUser.checkArea);
        }
        return single;
    }

    private void downloadAll(List<String> areas, String email, Button button) {
        deletePendingShareFiles();
        if (areas.isEmpty()) {
            button.setEnabled(true);
            showLongError("没有可导出的检查区域", "当前责任区域下没有可用检查区域");
            return;
        }
        downloadAt(areas, 0, email, button);
    }

    private void downloadAt(List<String> areas, int index, String email, Button button) {
        if (index >= areas.size()) {
            button.setEnabled(true);
            openMailClient(new ArrayList<>(pendingShareFiles), email);
            return;
        }
        String area = areas.get(index);
        java.io.File target = new java.io.File(
                new java.io.File(getCacheDir(), "email"), area + "-" + selectedDate + ".xlsx");
        apiClient.downloadExcelToFile(selectedDate, area, target,
                new ApiClient.Callback<java.io.File>() {
            @Override
            public void onSuccess(java.io.File file) {
                pendingShareFiles.add(file);
                downloadAt(areas, index + 1, email, button);
            }

            @Override
            public void onError(String message) {
                button.setEnabled(true);
                deletePendingShareFiles();
                showLongError("Excel导出失败", area + "：" + message);
            }
        });
    }

    private void openMailClient(List<java.io.File> files, String email) {
        try {
            if (files.isEmpty()) {
                throw new IllegalStateException("没有可发送的附件");
            }
            ArrayList<Uri> uris = new ArrayList<>();
            ClipData clipData = null;
            for (java.io.File file : files) {
                Uri uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", file);
                uris.add(uri);
                if (clipData == null) {
                    clipData = ClipData.newRawUri("excel", uri);
                } else {
                    clipData.addItem(new ClipData.Item(uri));
                }
            }
            Intent base = new Intent(Intent.ACTION_SEND_MULTIPLE);
            base.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            base.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            base.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
            base.putExtra(Intent.EXTRA_SUBJECT, "质量检查记录 " + selectedDate);
            base.putExtra(Intent.EXTRA_TEXT, "附件为 " + selectedDate
                    + " 质量检查记录（共" + files.size() + "个检查区域），请查收。");
            base.setClipData(clipData);
            base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            copyRecipient(email);
            shareInFlight = true;
            String mailPackage = defaultMailPackage();
            if (mailPackage != null) {
                Intent targeted = new Intent(base).setPackage(mailPackage);
                try {
                    startActivity(targeted);
                    return;
                } catch (RuntimeException ignored) {
                    // 默认邮箱APP不支持附件时，退回系统选择器
                }
            }
            startActivity(Intent.createChooser(base, "发送检查记录到邮箱"));
        } catch (RuntimeException error) {
            deletePendingShareFiles();
            showLongError("无法打开邮件客户端",
                    "请确认手机已安装并登录邮箱APP。\n" + error.getMessage());
        }
    }

    private void copyRecipient(String email) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("收件人", email));
        Toast.makeText(this, "收件人已复制：" + email
                + "\n若邮件收件人为空，请长按收件人栏粘贴", Toast.LENGTH_LONG).show();
    }

    private String defaultMailPackage() {
        try {
            Intent probe = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
            android.content.pm.ResolveInfo info = getPackageManager()
                    .resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null && info.activityInfo != null) {
                return info.activityInfo.packageName;
            }
        } catch (RuntimeException ignored) {
            // 查询失败时使用系统选择器
        }
        return null;
    }

    private void deletePendingShareFiles() {
        for (java.io.File file : pendingShareFiles) {
            if (file != null && file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        pendingShareFiles.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shareInFlight) {
            shareInFlight = false;
            deletePendingShareFiles();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deletePendingShareFiles();
    }

    private void showLongError(String title, String message) {
        TextView content = new TextView(this);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setText(message);
        content.setTextIsSelectable(true);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void uploadTemplate() {
        if (selectedTemplate == null) {
            return;
        }
        String area = selectedTemplateArea();
        if (area.trim().isEmpty()) {
            Toast.makeText(this, "请选择模板检查区域", Toast.LENGTH_LONG).show();
            return;
        }
        uploadTemplateButton.setEnabled(false);
        apiClient.uploadTemplate(selectedTemplate, selectedTemplateName, area,
                new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                uploadTemplateButton.setEnabled(true);
                Toast.makeText(AdminActivity.this,
                        "xlsx模板上传成功（" + area + "）", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                uploadTemplateButton.setEnabled(true);
                showLongError("模板上传失败", message);
            }
        });
    }

    private String queryFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the URI segment below.
        }
        String name = uri.getLastPathSegment();
        return name == null ? "template.xlsx" : name;
    }

    private void loadExcelDates() {
        apiClient.getExcelDates(new ApiClient.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> serverDates) {
                if (serverDates == null || serverDates.isEmpty()) {
                    return;
                }
                List<String> available = new ArrayList<>();
                for (String localDate : new ArrayList<>(excelDates)) {
                    if (serverDates.contains(localDate)) {
                        available.add(localDate);
                    }
                }
                if (!available.isEmpty()) {
                    excelDates.clear();
                    excelDates.addAll(available);
                    dateAdapter.notifyDataSetChanged();
                    selectedDate = excelDates.get(0);
                    excelDateSpinner.setSelection(0);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminActivity.this,
                        "可用Excel日期获取失败，仍可选择近3天：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadUsers() {
        apiClient.listUsers(new ApiClient.Callback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                userAdapter.setUsers(users);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminActivity.this, "人员列表加载失败：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showUserEditor(User original) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_user, null);
        EditText nameInput = view.findViewById(R.id.editUserNameInput);
        Spinner roleSpinner = view.findViewById(R.id.editUserRoleSpinner);
        TextView roleLabel = view.findViewById(R.id.editUserRoleLabel);
        CheckBox enabledCheck = view.findViewById(R.id.editUserEnabledCheck);
        nameInput.setText(nullToEmpty(original.inspectorName));
        enabledCheck.setChecked(original.enabled);
        boolean canSetRole = currentUser.isPrimaryAdmin();
        roleSpinner.setVisibility(canSetRole ? View.VISIBLE : View.GONE);
        roleLabel.setVisibility(canSetRole ? View.VISIBLE : View.GONE);

        List<String> roles = new ArrayList<>();
        if (original.isPrimaryAdmin()) {
            roles.add("PRIMARY_ADMIN");
        } else {
            roles.add("USER");
            roles.add("SECONDARY_ADMIN");
        }
        ArrayAdapter<String> rolesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, roles);
        rolesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(rolesAdapter);
        roleSpinner.setSelection("SECONDARY_ADMIN".equals(original.role) ? 1 : 0);
        roleSpinner.setEnabled(!original.isPrimaryAdmin());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑注册人员")
                .setView(view)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .setNeutralButton("删除", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            boolean deletable = !original.isPrimaryAdmin() && original.id != currentUser.id;
            deleteButton.setVisibility(deletable ? View.VISIBLE : View.GONE);
            deleteButton.setOnClickListener(view1 -> confirmDeleteUser(original, dialog));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "姓名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    User updated = copyUser(original);
                    updated.inspectorName = name;
                    updated.enabled = enabledCheck.isChecked();
                    updated.role = canSetRole
                            ? roles.get(roleSpinner.getSelectedItemPosition())
                            : original.role;
                    Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    saveButton.setEnabled(false);
                    apiClient.updateUser(updated, new ApiClient.Callback<User>() {
                        @Override
                        public void onSuccess(User response) {
                            if (updated.id == currentUser.id) {
                                currentUser = response == null ? updated : response;
                                sessionManager.updateUser(currentUser);
                            }
                            dialog.dismiss();
                            Toast.makeText(AdminActivity.this, "人员信息已保存", Toast.LENGTH_SHORT).show();
                            loadUsers();
                        }

                        @Override
                        public void onError(String message) {
                            saveButton.setEnabled(true);
                            Toast.makeText(AdminActivity.this, "保存失败：" + message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                });
        });
        dialog.show();
    }

    private void confirmDeleteUser(User user, AlertDialog editor) {
        new AlertDialog.Builder(this)
                .setTitle("删除人员")
                .setMessage("确定删除“" + nullToEmpty(user.inspectorName) + "”？删除后该人员将无法登录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> apiClient.deleteUser(user.id,
                        new ApiClient.Callback<Void>() {
                            @Override
                            public void onSuccess(Void ignored) {
                                editor.dismiss();
                                Toast.makeText(AdminActivity.this, "人员已删除", Toast.LENGTH_SHORT).show();
                                loadUsers();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(AdminActivity.this, "删除失败：" + message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    private User copyUser(User source) {
        User copy = new User();
        copy.id = source.id;
        copy.inspectorName = source.inspectorName;
        copy.area = source.area;
        copy.checkArea = source.checkArea;
        copy.role = source.role;
        copy.enabled = source.enabled;
        copy.email = source.email;
        return copy;
    }

    private void addModel() {
        String code = newModelCodeInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        String name = newModelNameInput.getText().toString().trim();
        if (code.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "车型代码和名称均不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Button button = findViewById(R.id.addModelButton);
        button.setEnabled(false);
        apiClient.createModel(code, name, new ApiClient.Callback<VehicleModel>() {
            @Override
            public void onSuccess(VehicleModel model) {
                button.setEnabled(true);
                newModelCodeInput.setText("");
                newModelNameInput.setText("");
                Toast.makeText(AdminActivity.this, "车型已添加", Toast.LENGTH_SHORT).show();
                loadModels();
            }

            @Override
            public void onError(String message) {
                button.setEnabled(true);
                Toast.makeText(AdminActivity.this, "添加失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadModels() {
        apiClient.listModels(new ApiClient.Callback<List<VehicleModel>>() {
            @Override
            public void onSuccess(List<VehicleModel> models) {
                modelAdapter.setModels(models);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminActivity.this, "车型列表加载失败：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showModelEditor(VehicleModel model) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_model, null);
        EditText codeInput = view.findViewById(R.id.editModelCodeInput);
        EditText nameInput = view.findViewById(R.id.editModelNameInput);
        codeInput.setText(nullToEmpty(model.code));
        nameInput.setText(nullToEmpty(model.modelName));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑车型")
                .setView(view)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String code = codeInput.getText().toString().trim().toUpperCase(Locale.ROOT);
                    String name = nameInput.getText().toString().trim();
                    if (code.isEmpty() || name.isEmpty()) {
                        Toast.makeText(this, "车型代码和名称均不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    saveButton.setEnabled(false);
                    apiClient.updateModel(model.id, code, name, new ApiClient.Callback<VehicleModel>() {
                        @Override
                        public void onSuccess(VehicleModel response) {
                            dialog.dismiss();
                            Toast.makeText(AdminActivity.this, "车型已保存", Toast.LENGTH_SHORT).show();
                            loadModels();
                        }

                        @Override
                        public void onError(String message) {
                            saveButton.setEnabled(true);
                            Toast.makeText(AdminActivity.this, "保存失败：" + message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }));
        dialog.show();
    }

    private void confirmDeleteModel(VehicleModel model) {
        new AlertDialog.Builder(this)
                .setTitle("删除车型")
                .setMessage("确定删除“" + model.code + " / " + model.modelName + "”？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> apiClient.deleteModel(model.id,
                        new ApiClient.Callback<Void>() {
                            @Override
                            public void onSuccess(Void ignored) {
                                Toast.makeText(AdminActivity.this, "车型已删除", Toast.LENGTH_SHORT).show();
                                loadModels();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(AdminActivity.this, "删除失败：" + message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static class UserAdapter extends RecyclerView.Adapter<UserAdapter.Holder> {
        interface Listener {
            void onEdit(User user);
        }

        private final Listener listener;
        private final List<User> users = new ArrayList<>();

        UserAdapter(Listener listener) {
            this.listener = listener;
        }

        void setUsers(List<User> values) {
            users.clear();
            if (values != null) users.addAll(values);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            User user = users.get(position);
            holder.name.setText(nullToEmpty(user.inspectorName));
            holder.details.setText("区域：" + nullToEmpty(user.area)
                    + "  |  检查区域：" + nullToEmpty(user.checkArea)
                    + "  |  角色：" + nullToEmpty(user.role)
                    + "\n状态：" + (user.enabled ? "已同意登录" : "待审核/已禁用"));
            holder.edit.setOnClickListener(view -> listener.onEdit(user));
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView details;
            final Button edit;

            Holder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.userNameText);
                details = itemView.findViewById(R.id.userDetailsText);
                edit = itemView.findViewById(R.id.editUserButton);
            }
        }
    }

    private static class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.Holder> {
        interface Listener {
            void onEdit(VehicleModel model);
            void onDelete(VehicleModel model);
        }

        private final Listener listener;
        private final List<VehicleModel> models = new ArrayList<>();

        ModelAdapter(Listener listener) {
            this.listener = listener;
        }

        void setModels(List<VehicleModel> values) {
            models.clear();
            if (values != null) models.addAll(values);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_model, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            VehicleModel model = models.get(position);
            holder.text.setText(nullToEmpty(model.code) + " / " + nullToEmpty(model.modelName)
                    + (model.active ? "" : "（停用）"));
            holder.edit.setOnClickListener(view -> listener.onEdit(model));
            holder.delete.setOnClickListener(view -> listener.onDelete(model));
        }

        @Override
        public int getItemCount() {
            return models.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final Button edit;
            final Button delete;

            Holder(View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.modelItemText);
                edit = itemView.findViewById(R.id.editModelButton);
                delete = itemView.findViewById(R.id.deleteModelButton);
            }
        }
    }
}
