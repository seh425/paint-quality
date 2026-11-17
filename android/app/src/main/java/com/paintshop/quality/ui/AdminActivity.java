package com.paintshop.quality.ui;

import android.app.DownloadManager;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
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
    private EditText feishuInput;
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
        apiClient = ApiClient.get(this);
        ((TextView) findViewById(R.id.adminRoleText)).setText(
                currentUser.isPrimaryAdmin() ? "当前角色：一级管理员" : "当前角色：二级管理员");
        feishuInput = findViewById(R.id.adminFeishuInput);
        feishuInput.setText(nullToEmpty(currentUser.feishuOpenId));
        newModelCodeInput = findViewById(R.id.newModelCodeInput);
        newModelNameInput = findViewById(R.id.newModelNameInput);
        selectedTemplateText = findViewById(R.id.selectedTemplateText);
        uploadTemplateButton = findViewById(R.id.uploadTemplateButton);
        findViewById(R.id.primarySection).setVisibility(
                currentUser.isPrimaryAdmin() ? View.VISIBLE : View.GONE);
        setupDates();
        setupLists();
        setupActions();
        loadExcelDates();
        if (currentUser.isPrimaryAdmin()) {
            loadUsers();
            loadModels();
        }
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
        findViewById(R.id.saveFeishuButton).setOnClickListener(view -> saveFeishu());
        findViewById(R.id.downloadExcelButton).setOnClickListener(view -> downloadExcel());
        findViewById(R.id.sendFeishuButton).setOnClickListener(view -> sendFeishu());
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

    private void saveFeishu() {
        String openId = feishuInput.getText().toString().trim();
        Button button = findViewById(R.id.saveFeishuButton);
        button.setEnabled(false);
        apiClient.saveFeishu(openId, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                button.setEnabled(true);
                currentUser.feishuOpenId = openId;
                sessionManager.updateUser(currentUser);
                Toast.makeText(AdminActivity.this, "我的飞书Open ID已保存", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                button.setEnabled(true);
                Toast.makeText(AdminActivity.this, "保存失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadExcel() {
        if (selectedDate == null) {
            return;
        }
        try {
            apiClient.downloadExcel(selectedDate);
            Toast.makeText(this, "已加入系统下载，保存到应用专属Download目录", Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法启动下载：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendFeishu() {
        if (selectedDate == null) {
            return;
        }
        String input = feishuInput.getText().toString().trim();
        if (input.isEmpty()) {
            feishuInput.setError("请先填写并保存我的飞书Open ID");
            return;
        }
        if (!input.equals(nullToEmpty(currentUser.feishuOpenId))) {
            Toast.makeText(this, "Open ID已修改，请先保存再发送", Toast.LENGTH_LONG).show();
            return;
        }
        Button button = findViewById(R.id.sendFeishuButton);
        button.setEnabled(false);
        apiClient.sendFeishu(selectedDate, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                button.setEnabled(true);
                Toast.makeText(AdminActivity.this, "已请求发送到我的飞书", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                button.setEnabled(true);
                Toast.makeText(AdminActivity.this, "飞书发送失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void uploadTemplate() {
        if (selectedTemplate == null) {
            return;
        }
        uploadTemplateButton.setEnabled(false);
        apiClient.uploadTemplate(selectedTemplate, selectedTemplateName, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                uploadTemplateButton.setEnabled(true);
                Toast.makeText(AdminActivity.this, "xlsx模板上传成功", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                uploadTemplateButton.setEnabled(true);
                Toast.makeText(AdminActivity.this, "模板上传失败：" + message, Toast.LENGTH_LONG).show();
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
        EditText areaInput = view.findViewById(R.id.editUserAreaInput);
        EditText feishuOpenIdInput = view.findViewById(R.id.editUserFeishuInput);
        Spinner roleSpinner = view.findViewById(R.id.editUserRoleSpinner);
        CheckBox enabledCheck = view.findViewById(R.id.editUserEnabledCheck);
        nameInput.setText(nullToEmpty(original.inspectorName));
        areaInput.setText(nullToEmpty(original.area));
        feishuOpenIdInput.setText(nullToEmpty(original.feishuOpenId));
        enabledCheck.setChecked(original.enabled);

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
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String name = nameInput.getText().toString().trim();
                    String area = areaInput.getText().toString().trim();
                    if (name.isEmpty() || area.isEmpty()) {
                        Toast.makeText(this, "姓名和责任区域不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    User updated = copyUser(original);
                    updated.inspectorName = name;
                    updated.area = area;
                    updated.feishuOpenId = feishuOpenIdInput.getText().toString().trim();
                    updated.enabled = enabledCheck.isChecked();
                    updated.role = roles.get(roleSpinner.getSelectedItemPosition());
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
                }));
        dialog.show();
    }

    private User copyUser(User source) {
        User copy = new User();
        copy.id = source.id;
        copy.inspectorName = source.inspectorName;
        copy.area = source.area;
        copy.role = source.role;
        copy.enabled = source.enabled;
        copy.feishuOpenId = source.feishuOpenId;
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
            holder.details.setText("区域：" + nullToEmpty(user.area) + "  |  角色：" + nullToEmpty(user.role)
                    + "\n状态：" + (user.enabled ? "启用" : "禁用"));
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
