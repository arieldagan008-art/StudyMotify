package com.example.myapplication2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoalsAdapter extends RecyclerView.Adapter<GoalsAdapter.GoalViewHolder> {

    private final Context context;
    private final List<Goal> goalList;

    public GoalsAdapter(Context context, List<Goal> goalList) {
        this.context = context;
        this.goalList = goalList;
    }

    @NonNull
    @Override
    public GoalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_goal, parent, false);
        return new GoalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GoalViewHolder holder, int position) {
        Goal goal = goalList.get(position);

        // ── Priority strip color ─────────────────────────────────────────────
        switch (goal.getDifficulty()) {
            case 3:  holder.priorityStrip.setBackgroundColor(0xFFF44336); break; // Hard  — Red
            case 2:  holder.priorityStrip.setBackgroundColor(0xFFFF9800); break; // Medium — Orange
            default: holder.priorityStrip.setBackgroundColor(0xFF4CAF50); break; // Easy  — Green
        }

        // ── Tap card to edit ─────────────────────────────────────────────────
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, AddGoalActivity.class);
            intent.putExtra(AddGoalActivity.EXTRA_GOAL, goal);
            context.startActivity(intent);
        });

        // ── Title ────────────────────────────────────────────────────────────
        holder.tvTitle.setText(goal.getGoalName());

        // ── Category badge ───────────────────────────────────────────────────
        if (!TextUtils.isEmpty(goal.getCategory())) {
            holder.tvCategory.setText(goal.getCategory());
            holder.tvCategory.setVisibility(View.VISIBLE);
        } else {
            holder.tvCategory.setVisibility(View.GONE);
        }

        // ── Description ──────────────────────────────────────────────────────
        if (!TextUtils.isEmpty(goal.getDetails())) {
            holder.tvDescription.setText(goal.getDetails());
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        // ── Due date ─────────────────────────────────────────────────────────
        String dueDate = goal.getDueDate();
        if (!TextUtils.isEmpty(dueDate) && !dueDate.equals("DD/MM/YYYY")) {
            holder.tvDueDate.setText("Due: " + dueDate);
            holder.tvDueDate.setVisibility(View.VISIBLE);
        } else {
            holder.tvDueDate.setVisibility(View.GONE);
        }

        // ── Completion checkbox + strike-through ─────────────────────────────
        holder.cbCompleted.setOnCheckedChangeListener(null);
        holder.cbCompleted.setChecked(goal.isCompleted());
        applyStrikeThrough(holder.tvTitle, goal.isCompleted());

        holder.cbCompleted.setOnCheckedChangeListener((btn, isChecked) -> {
            goal.isCompleted = isChecked;
            applyStrikeThrough(holder.tvTitle, isChecked);
            if (goal.getId() != null) {
                FirebaseHelper.getInstance()
                        .getCurrentUserRef()
                        .child("goals")
                        .child(goal.getId())
                        .child("completed")
                        .setValue(isChecked);
            }
        });

        // ── Progress tracking ─────────────────────────────────────────────────
        if (goal.totalUnits > 0) {
            holder.layoutProgress.setVisibility(View.VISIBLE);

            // Progress text
            String label = goal.getUnitLabel();
            holder.tvProgressText.setText(goal.completedUnits + " / " + goal.totalUnits + " " + label);

            // Progress bar (0–100)
            int percent = (int) ((goal.completedUnits / (float) goal.totalUnits) * 100);
            holder.progressBar.setProgress(Math.min(percent, 100));

            // Daily target message — red if projected finish is past deadline
            String targetMsg = SchedulerLogic.getDailyTargetMessage(goal);
            if (targetMsg != null) {
                holder.tvDailyTarget.setText(targetMsg);
                holder.tvDailyTarget.setVisibility(View.VISIBLE);
                boolean behind = SchedulerLogic.isBehindSchedule(goal);
                holder.tvDailyTarget.setTextColor(behind ? Color.RED : Color.parseColor("#1565C0"));
            } else {
                holder.tvDailyTarget.setVisibility(View.GONE);
            }

            // Log progress button
            holder.btnLogProgress.setOnClickListener(v -> showLogProgressDialog(goal, holder));
        } else {
            holder.layoutProgress.setVisibility(View.GONE);
            holder.tvDailyTarget.setVisibility(View.GONE);
        }
    }

    private void showLogProgressDialog(Goal goal, GoalViewHolder holder) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Units completed (e.g. 5)");

        int remaining = goal.totalUnits - goal.completedUnits;

        new AlertDialog.Builder(context)
                .setTitle("Log Progress")
                .setMessage("How many " + goal.getUnitLabel() + " did you complete?\n"
                        + remaining + " remaining to reach your goal.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    if (TextUtils.isEmpty(val)) return;

                    int added = Integer.parseInt(val);
                    if (added <= 0) {
                        Toast.makeText(context, "Enter a number greater than 0", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int newCompleted = Math.min(goal.completedUnits + added, goal.totalUnits);
                    goal.completedUnits = newCompleted;

                    if (goal.getId() != null) {
                        // Update completedUnits on the goal node
                        FirebaseHelper.getInstance()
                                .getCurrentUserRef()
                                .child("goals")
                                .child(goal.getId())
                                .child("completedUnits")
                                .setValue(newCompleted);

                        // Write a progress log entry for heatmap / analytics
                        Map<String, Object> logEntry = new HashMap<>();
                        logEntry.put("goalId",    goal.getId());
                        logEntry.put("goalName",  goal.getGoalName());
                        logEntry.put("amount",    added);
                        logEntry.put("timestamp", System.currentTimeMillis());

                        FirebaseHelper.getInstance()
                                .getCurrentUserRef()
                                .child("progressLogs")
                                .push()
                                .setValue(logEntry);
                    }

                    // Refresh this card immediately without waiting for Firebase
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_ID) notifyItemChanged(pos);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyStrikeThrough(TextView tv, boolean completed) {
        if (completed) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(0xFF9E9E9E);
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(0xFF000000);
        }
    }

    @Override
    public int getItemCount() {
        return goalList.size();
    }

    static class GoalViewHolder extends RecyclerView.ViewHolder {
        TextView  tvTitle, tvCategory, tvDescription, tvDueDate;
        TextView  tvDailyTarget, tvProgressText;
        CheckBox  cbCompleted;
        ProgressBar progressBar;
        LinearLayout layoutProgress;
        Button btnLogProgress;
        View priorityStrip;

        GoalViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle        = itemView.findViewById(R.id.tv_goal_title);
            tvCategory     = itemView.findViewById(R.id.tv_goal_category);
            tvDescription  = itemView.findViewById(R.id.tv_goal_description);
            tvDueDate      = itemView.findViewById(R.id.tv_goal_due_date);
            cbCompleted    = itemView.findViewById(R.id.cb_goal_completed);
            tvDailyTarget  = itemView.findViewById(R.id.tv_daily_target);
            layoutProgress = itemView.findViewById(R.id.layout_progress);
            tvProgressText = itemView.findViewById(R.id.tv_progress_text);
            progressBar    = itemView.findViewById(R.id.progress_bar);
            btnLogProgress = itemView.findViewById(R.id.btn_log_progress);
            priorityStrip  = itemView.findViewById(R.id.view_priority_strip);
        }
    }
}
