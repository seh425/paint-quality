package com.paintshop.quality.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.paintshop.quality.R;
import com.paintshop.quality.data.ApiClient;
import com.paintshop.quality.data.AreaCatalog;
import com.paintshop.quality.data.SessionManager;
import com.paintshop.quality.model.InspectionRecord;
import com.paintshop.quality.model.RoundInfo;
import com.paintshop.quality.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordsActivity extends AppCompatActivity {
    private final List<String> dates = new ArrayList<>();
    private final List<String> areas = new ArrayList<>();
    private final List<String> checkAreas = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView emptyText;
    private RecordAdapter adapter;
    private Spinner dateSpinner;
    private Spinner areaSpinner;
    private Spinner checkAreaSpinner;
    private SessionManager sessionManager;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);
        sessionManager = new SessionManager(this);
        progressBar = findViewById(R.id.recordsProgress);
        emptyText = findViewById(R.id.recordsEmptyText);
        RecyclerView list = findViewById(R.id.recordsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordAdapter();
        list.setAdapter(adapter);

        dateSpinner = findViewById(R.id.recordDateSpinner);
        areaSpinner = findViewById(R.id.recordAreaSpinner);
        checkAreaSpinner = findViewById(R.id.recordCheckAreaSpinner);
        setupDateSpinner();
        setupAreaSpinners();
    }

    private void setupDateSpinner() {
        final ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dates);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dateSpinner.setAdapter(dateAdapter);
        apiClient().getRecordDates(new ApiClient.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> recordDates) {
                binding = true;
                dates.clear();
                if (recordDates != null) {
                    dates.addAll(recordDates);
                }
                dateAdapter.notifyDataSetChanged();
                if (!dates.isEmpty()) {
                    dateSpinner.setSelection(0, false);
                }
                binding = false;
                if (dates.isEmpty()) {
                    adapter.setRecords(new ArrayList<>());
                    emptyText.setText("最近7日内暂无检查记录");
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    loadRecords();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RecordsActivity.this, "可用日期获取失败：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupAreaSpinners() {
        binding = true;
        areas.addAll(AreaCatalog.responsibilities());
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, areas);
        areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        areaSpinner.setAdapter(areaAdapter);

        User user = sessionManager.getUser();
        String initialArea = sessionManager.getLastArea();
        if (!areas.contains(initialArea)) {
            initialArea = user == null ? "" : user.area;
        }
        if (!areas.contains(initialArea)) {
            initialArea = areas.isEmpty() ? "" : areas.get(0);
        }
        int areaIndex = Math.max(0, areas.indexOf(initialArea));
        areaSpinner.setSelection(areaIndex, false);

        String initialCheckArea = sessionManager.getLastCheckArea();
        if (!AreaCatalog.checkAreas(initialArea).contains(initialCheckArea)) {
            initialCheckArea = user == null ? "" : user.checkArea;
        }
        refreshCheckAreas(initialArea, initialCheckArea);
        binding = false;

        areaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding) {
                    return;
                }
                String area = areas.get(position);
                refreshCheckAreas(area, null);
                loadRecords();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        checkAreaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding) {
                    loadRecords();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        dateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding) {
                    loadRecords();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        loadRecords();
    }

    private void refreshCheckAreas(String area, String preferredCheckArea) {
        checkAreas.clear();
        checkAreas.addAll(AreaCatalog.checkAreas(area));
        ArrayAdapter<String> checkAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, checkAreas);
        checkAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        checkAreaSpinner.setAdapter(checkAdapter);
        if (!checkAreas.isEmpty()) {
            int index = checkAreas.indexOf(preferredCheckArea);
            checkAreaSpinner.setSelection(index < 0 ? 0 : index, false);
        }
    }

    private String selectedDate() {
        Object item = dateSpinner.getSelectedItem();
        return item == null ? "" : item.toString();
    }

    private String selectedCheckArea() {
        Object item = checkAreaSpinner.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }

    private void loadRecords() {
        if (binding) {
            return;
        }
        String date = selectedDate();
        String checkArea = selectedCheckArea();
        if (date.isEmpty() || checkArea.isEmpty()) {
            adapter.setRecords(new ArrayList<>());
            emptyText.setText("请选择日期和检查区域");
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        apiClient().listRecords(date, checkArea, new ApiClient.Callback<List<InspectionRecord>>() {
            @Override
            public void onSuccess(List<InspectionRecord> records) {
                progressBar.setVisibility(View.GONE);
                List<InspectionRecord> safeRecords = records == null
                        ? new ArrayList<>() : new ArrayList<>(records);
                Collections.sort(safeRecords, new Comparator<InspectionRecord>() {
                    @Override
                    public int compare(InspectionRecord left, InspectionRecord right) {
                        int result = Integer.compare(left.roundNumber, right.roundNumber);
                        if (result == 0) result = Integer.compare(left.lineNumber, right.lineNumber);
                        if (result == 0) result = Integer.compare(left.vehiclePosition, right.vehiclePosition);
                        return result;
                    }
                });
                adapter.setRecords(safeRecords);
                emptyText.setText("该检查区域暂无共享记录");
                emptyText.setVisibility(safeRecords.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                adapter.setRecords(new ArrayList<>());
                emptyText.setText("记录加载失败，请重新选择日期");
                emptyText.setVisibility(View.VISIBLE);
                Toast.makeText(RecordsActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        loadRoundTimes(checkArea);
    }

    private void loadRoundTimes(String checkArea) {
        apiClient().getRounds(checkArea, new ApiClient.Callback<List<RoundInfo>>() {
            @Override
            public void onSuccess(List<RoundInfo> rounds) {
                Map<Integer, String> times = new HashMap<>();
                if (rounds != null) {
                    for (RoundInfo round : rounds) {
                        String time = round.timeArea == null ? "" : round.timeArea.trim();
                        times.put(round.number, time);
                    }
                }
                adapter.setRoundTimes(times);
            }

            @Override
            public void onError(String message) {
                adapter.setRoundTimes(new HashMap<>());
            }
        });
    }

    private ApiClient apiClient() {
        return ApiClient.get(this);
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private static class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.Holder> {
        private final List<InspectionRecord> records = new ArrayList<>();
        private Map<Integer, String> roundTimes = new HashMap<>();

        void setRecords(List<InspectionRecord> values) {
            records.clear();
            records.addAll(values);
            notifyDataSetChanged();
        }

        void setRoundTimes(Map<Integer, String> values) {
            roundTimes = values == null ? new HashMap<>() : values;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_record, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            InspectionRecord record = records.get(position);
            String time = roundTimes.get(record.roundNumber);
            String statusText = record.statusText == null || record.statusText.trim().isEmpty()
                    ? value(record.status) : record.statusText.trim();
            StringBuilder header = new StringBuilder("第").append(record.roundNumber).append("轮");
            if (time != null && !time.isEmpty()) {
                header.append("  ").append(time.replace("/", " ").trim());
            }
            header.append("  |  ").append(record.lineNumber).append("#线 车位")
                    .append(record.vehiclePosition)
                    .append("  |  ").append(statusText);
            holder.header.setText(header.toString());
            holder.vehicle.setText("车号：" + value(record.vehicleNumber)
                    + "    车型：" + value(record.vehicleModel));
            holder.inspector.setText("检查人：" + value(record.inspectorName));
            boolean hasIssue = statusText.toUpperCase(java.util.Locale.ROOT).contains("NOK");
            holder.issue.setVisibility(hasIssue ? View.VISIBLE : View.GONE);
            holder.issue.setText("NOK备注：" + value(record.issueNote));
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView header;
            final TextView vehicle;
            final TextView inspector;
            final TextView issue;

            Holder(View itemView) {
                super(itemView);
                header = itemView.findViewById(R.id.recordHeaderText);
                vehicle = itemView.findViewById(R.id.recordVehicleText);
                inspector = itemView.findViewById(R.id.recordInspectorText);
                issue = itemView.findViewById(R.id.recordIssueText);
            }
        }
    }
}
