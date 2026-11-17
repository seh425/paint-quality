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
import com.paintshop.quality.model.InspectionRecord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecordsActivity extends AppCompatActivity {
    private final List<String> dates = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView emptyText;
    private RecordAdapter adapter;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);
        progressBar = findViewById(R.id.recordsProgress);
        emptyText = findViewById(R.id.recordsEmptyText);
        RecyclerView list = findViewById(R.id.recordsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordAdapter();
        list.setAdapter(adapter);

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 3; i++) {
            dates.add(today.minusDays(i).toString());
        }
        Spinner spinner = findViewById(R.id.recordDateSpinner);
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dates);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dateAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDate = dates.get(position);
                loadRecords();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void loadRecords() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        ApiClient.get(this).listRecords(selectedDate, new ApiClient.Callback<List<InspectionRecord>>() {
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
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private static class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.Holder> {
        private final List<InspectionRecord> records = new ArrayList<>();

        void setRecords(List<InspectionRecord> values) {
            records.clear();
            records.addAll(values);
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
            holder.header.setText("第" + record.roundNumber + "轮  |  " + record.lineNumber
                    + "#线  |  车位" + record.vehiclePosition + "  |  " + value(record.status));
            holder.vehicle.setText("车号：" + value(record.vehicleNumber)
                    + "    车型：" + value(record.modelCode) + " / " + value(record.vehicleModel)
                    + "\nVIN：" + value(record.vin));
            holder.inspector.setText("检查人：" + value(record.inspectorName)
                    + "    区域：" + value(record.area));
            boolean hasIssue = "NOK".equals(record.status);
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
