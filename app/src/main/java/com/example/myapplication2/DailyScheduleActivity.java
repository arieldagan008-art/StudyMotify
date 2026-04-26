package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DailyScheduleActivity extends AppCompatActivity {

    private Button      btnGenerate;
    private ProgressBar pbLoading;
    private RecyclerView rvSchedule;
    private TextView    tvEmpty;
    private CardView    cvVelocitySummary;
    private TextView    tvVelocitySummary;

    private final List<Goal>        goals    = new ArrayList<>();
    private final List<ProgressLog> logs     = new ArrayList<>();
    private final List<ScheduleItem> schedule = new ArrayList<>();

    private ScheduleAdapter scheduleAdapter;

    // Track whether each Firebase load is done before generating
    private boolean goalsLoaded = false;
    private boolean logsLoaded  = false;
    private boolean generatePending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_schedule);

        MaterialToolbar toolbar = findViewById(R.id.scheduleToolbar);
        toolbar.setTitle("Daily Schedule");
        toolbar.setSubtitle("AI-powered study planner");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnGenerate       = findViewById(R.id.btn_generate_schedule);
        pbLoading         = findViewById(R.id.pb_schedule_loading);
        rvSchedule        = findViewById(R.id.rv_schedule);
        tvEmpty           = findViewById(R.id.tv_schedule_empty);
        cvVelocitySummary = findViewById(R.id.cv_velocity_summary);
        tvVelocitySummary = findViewById(R.id.tv_velocity_summary);

        rvSchedule.setLayoutManager(new LinearLayoutManager(this));
        scheduleAdapter = new ScheduleAdapter(this, schedule, this::onSlotClicked);
        rvSchedule.setAdapter(scheduleAdapter);

        btnGenerate.setOnClickListener(v -> {
            if (!goalsLoaded || !logsLoaded) {
                // Data not ready yet — set flag and show spinner
                generatePending = true;
                showLoading(true);
                Toast.makeText(this, "Loading data…", Toast.LENGTH_SHORT).show();
            } else {
                buildSchedule();
            }
        });

        loadData();
    }

    // ─── Firebase loading ─────────────────────────────────────────────────────

    private void loadData() {
        showLoading(true);
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            showLoading(false);
            Toast.makeText(this, "Please sign in to use the scheduler.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load goals
        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("goals")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        goals.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Goal g = child.getValue(Goal.class);
                            if (g != null) {
                                if (g.id == null) g.id = child.getKey();
                                goals.add(g);
                            }
                        }
                        goalsLoaded = true;
                        onDataReady();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        goalsLoaded = true;
                        onDataReady();
                    }
                });

        // Load progressLogs
        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("progressLogs")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        logs.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            ProgressLog log = child.getValue(ProgressLog.class);
                            if (log != null) logs.add(log);
                        }
                        logsLoaded = true;
                        onDataReady();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        logsLoaded = true;
                        onDataReady();
                    }
                });
    }

    private void onDataReady() {
        if (!goalsLoaded || !logsLoaded) return;
        showLoading(false);
        if (generatePending) {
            generatePending = false;
            buildSchedule();
        }
    }

    // ─── Schedule generation ──────────────────────────────────────────────────

    @SuppressLint("NotifyDataSetChanged")
    private void buildSchedule() {
        List<ScheduleItem> generated = LearningEngine.generateDailySchedule(goals, logs);

        schedule.clear();
        schedule.addAll(generated);
        scheduleAdapter.notifyDataSetChanged();

        if (schedule.isEmpty()) {
            tvEmpty.setText("No active goals found.\nAdd goals in the Home screen first.");
            tvEmpty.setVisibility(View.VISIBLE);
            rvSchedule.setVisibility(View.GONE);
            cvVelocitySummary.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvSchedule.setVisibility(View.VISIBLE);

            // Build velocity summary message
            Map<String, Double> velocities = LearningEngine.computeAllVelocities(logs);
            int learnedCount = 0;
            for (ScheduleItem item : schedule) {
                if (velocities.containsKey(item.goalId)) learnedCount++;
            }

            String summaryText;
            if (learnedCount == 0) {
                summaryText = "⏱ No session history yet — using default estimates.\n"
                        + "Log time in 'Log Progress' to improve accuracy!";
            } else {
                summaryText = "🧠 AI learned from your history: "
                        + learnedCount + " of " + schedule.size()
                        + " sessions use your real pace.";
            }
            tvVelocitySummary.setText(summaryText);
            cvVelocitySummary.setVisibility(View.VISIBLE);
        }
    }

    // ─── Slot click → time picker ─────────────────────────────────────────────

    private void onSlotClicked(int position) {
        if (position < 0 || position >= schedule.size()) return;
        ScheduleItem item = schedule.get(position);

        new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    // Clamp to 08:00 – 22:00
                    int clamped = Math.max(8 * 60, Math.min(22 * 60, hourOfDay * 60 + minute));
                    item.startHour   = clamped / 60;
                    item.startMinute = clamped % 60;
                    scheduleAdapter.notifyItemChanged(position);
                    Toast.makeText(this,
                            item.goalName + " moved to "
                                    + String.format(Locale.getDefault(),
                                    "%02d:%02d", item.startHour, item.startMinute),
                            Toast.LENGTH_SHORT).show();
                },
                item.startHour, item.startMinute, true /* 24-hour */)
                .show();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        pbLoading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ─── Inner adapter ────────────────────────────────────────────────────────

    interface OnSlotClickListener {
        void onSlotClick(int position);
    }

    static class ScheduleAdapter
            extends RecyclerView.Adapter<ScheduleAdapter.SlotVH> {

        private final Context            context;
        private final List<ScheduleItem> items;
        private final OnSlotClickListener listener;

        ScheduleAdapter(Context context,
                        List<ScheduleItem> items,
                        OnSlotClickListener listener) {
            this.context  = context;
            this.items    = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public SlotVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context)
                    .inflate(R.layout.item_schedule_slot, parent, false);
            return new SlotVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SlotVH holder, int position) {
            ScheduleItem item = items.get(position);

            holder.tvTime.setText(item.getStartTimeStr());
            holder.tvGoalName.setText(item.goalName);
            holder.tvTimeRange.setText(item.getStartTimeStr() + " – " + item.getEndTimeStr());
            holder.tvUnits.setText(item.unitsPlanned + " unit"
                    + (item.unitsPlanned == 1 ? "" : "s"));
            holder.tvDuration.setText("~" + item.getDurationLabel());

            // Show whether velocity came from real data or estimate
            boolean hasRealVelocity = item.minutesPerUnit != 30.0
                    && item.minutesPerUnit != item.minutesPerUnit; // always show tag
            // Show tag based on context: green "AI pace" or grey "estimate"
            // We distinguish: if minutesPerUnit is a round number multiple of default (30), likely default
            boolean isDefault = (Math.abs(item.minutesPerUnit - 30.0) < 0.01);
            holder.tvVelocityTag.setText(isDefault ? "default est." : "AI pace ✓");
            holder.tvVelocityTag.setTextColor(isDefault ? 0xFF888888 : 0xFF4CAF50);

            holder.cvCard.setOnClickListener(v ->
                    listener.onSlotClick(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class SlotVH extends RecyclerView.ViewHolder {
            TextView tvTime, tvGoalName, tvTimeRange,
                     tvUnits, tvDuration, tvVelocityTag;
            CardView cvCard;

            SlotVH(@NonNull View itemView) {
                super(itemView);
                tvTime        = itemView.findViewById(R.id.tv_slot_time);
                tvGoalName    = itemView.findViewById(R.id.tv_slot_goal_name);
                tvTimeRange   = itemView.findViewById(R.id.tv_slot_time_range);
                tvUnits       = itemView.findViewById(R.id.tv_slot_units);
                tvDuration    = itemView.findViewById(R.id.tv_slot_duration);
                tvVelocityTag = itemView.findViewById(R.id.tv_slot_velocity_tag);
                cvCard        = itemView.findViewById(R.id.cv_schedule_item);
            }
        }
    }
}
