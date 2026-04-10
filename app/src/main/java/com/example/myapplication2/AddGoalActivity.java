package com.example.myapplication2;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;

import java.util.Calendar;
import java.util.Locale;

public class AddGoalActivity extends AppCompatActivity {

    public static final String EXTRA_GOAL = "extra_goal";

    // UI Components
    private TextView tvScreenTitle;
    private ImageButton btnBack;
    private EditText etGoalName, etDetails, etCategory, etTotalUnits, etUnitLabel;
    private TextView tvDueDate, tvReminderTime;
    private Switch switchReminder;
    private Button btnSaveGoal;

    private RadioGroup rgDifficulty;

    private Calendar calendar;
    private long selectedDeadlineMs = 0;
    private FirebaseHelper firebaseHelper;

    // Non-null when editing an existing goal
    private Goal editingGoal = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_goal);

        firebaseHelper = FirebaseHelper.getInstance();
        calendar = Calendar.getInstance();

        // Bind views
        tvScreenTitle  = findViewById(R.id.tv_screen_title);
        btnBack        = findViewById(R.id.btn_back);
        etGoalName     = findViewById(R.id.et_goal_name);
        tvDueDate      = findViewById(R.id.tv_due_date);
        etDetails      = findViewById(R.id.et_details);
        etCategory     = findViewById(R.id.et_category);
        etTotalUnits   = findViewById(R.id.et_total_units);
        etUnitLabel    = findViewById(R.id.et_unit_label);
        rgDifficulty   = findViewById(R.id.rg_difficulty);
        switchReminder = findViewById(R.id.switch_reminder);
        tvReminderTime = findViewById(R.id.tv_reminder_time);
        btnSaveGoal    = findViewById(R.id.btn_save_goal);

        // Check if we're editing an existing goal
        if (getIntent().hasExtra(EXTRA_GOAL)) {
            editingGoal = (Goal) getIntent().getSerializableExtra(EXTRA_GOAL);
            prefillFields(editingGoal);
            tvScreenTitle.setText("Edit Goal");
            btnSaveGoal.setText("Update Goal");
        }

        btnBack.setOnClickListener(v -> finish());

        tvDueDate.setOnClickListener(v -> showDatePicker());
        tvReminderTime.setOnClickListener(v -> showTimePicker());
        btnSaveGoal.setOnClickListener(v -> saveGoalToFirebase());
    }

    /** Pre-fills all fields with the existing goal's data. */
    private void prefillFields(Goal goal) {
        etGoalName.setText(goal.goalName);
        etDetails.setText(goal.details);
        etCategory.setText(goal.category);

        if (!TextUtils.isEmpty(goal.dueDate) && !goal.dueDate.equals("DD/MM/YYYY")) {
            tvDueDate.setText(goal.dueDate);
        }

        if (goal.deadlineDate > 0) {
            selectedDeadlineMs = goal.deadlineDate;
        }

        if (goal.totalUnits > 0) {
            etTotalUnits.setText(String.valueOf(goal.totalUnits));
        }

        etUnitLabel.setText(goal.unitLabel);
        switchReminder.setChecked(goal.reminder);

        if (!TextUtils.isEmpty(goal.time)) {
            tvReminderTime.setText(goal.time);
        }

        // Pre-select difficulty radio button
        switch (goal.getDifficulty()) {
            case 3: rgDifficulty.check(R.id.rb_hard);   break;
            case 2: rgDifficulty.check(R.id.rb_medium); break;
            default: rgDifficulty.check(R.id.rb_easy);  break;
        }
    }

    private int getSelectedDifficulty() {
        int checked = rgDifficulty.getCheckedRadioButtonId();
        if (checked == R.id.rb_hard)   return 3;
        if (checked == R.id.rb_medium) return 2;
        return 1; // default Easy
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    String selectedDate = String.format(
                            Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year);
                    tvDueDate.setText(selectedDate);

                    Calendar deadline = Calendar.getInstance();
                    deadline.set(year, month, dayOfMonth, 0, 0, 0);
                    deadline.set(Calendar.MILLISECOND, 0);
                    selectedDeadlineMs = deadline.getTimeInMillis();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (TimePicker view, int hourOfDay, int minute) -> {
                    String selectedTime = String.format(
                            Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    tvReminderTime.setText(selectedTime);
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true);
        dialog.show();
    }

    private void saveGoalToFirebase() {
        String name     = etGoalName.getText().toString().trim();
        String category = etCategory.getText().toString().trim();
        String date     = tvDueDate.getText().toString().trim();
        String details  = etDetails.getText().toString().trim();
        boolean isReminder = switchReminder.isChecked();
        String time     = tvReminderTime.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etGoalName.setError("Goal name is required");
            return;
        }

        if (!firebaseHelper.isLoggedIn()) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build the goal object
        final Goal goal;
        final DatabaseReference ref;

        if (editingGoal != null) {
            // ── EDIT MODE: reuse the existing ID and preserve non-editable fields ──
            goal = editingGoal;
            goal.goalName  = name;
            goal.category  = category;
            goal.dueDate   = date;
            goal.details   = details;
            goal.reminder  = isReminder;
            goal.time      = time;
            goal.deadlineDate = selectedDeadlineMs > 0 ? selectedDeadlineMs : editingGoal.deadlineDate;

            String totalUnitsStr = etTotalUnits.getText().toString().trim();
            goal.totalUnits = TextUtils.isEmpty(totalUnitsStr) ? 0 : Integer.parseInt(totalUnitsStr);
            goal.unitLabel  = etUnitLabel.getText().toString().trim();
            goal.difficulty = getSelectedDifficulty();
            // completedUnits and isCompleted are intentionally preserved

            ref = firebaseHelper.getCurrentUserRef()
                    .child("goals")
                    .child(goal.getId());
        } else {
            // ── CREATE MODE: push a new node ──
            DatabaseReference newRef = firebaseHelper.getCurrentUserRef().child("goals").push();
            goal = new Goal(newRef.getKey(), name, category, date, details,
                    isReminder, time, System.currentTimeMillis());

            String totalUnitsStr = etTotalUnits.getText().toString().trim();
            if (!TextUtils.isEmpty(totalUnitsStr)) {
                goal.totalUnits = Integer.parseInt(totalUnitsStr);
            }
            goal.completedUnits      = 0;
            goal.deadlineDate        = selectedDeadlineMs;
            goal.unitLabel           = etUnitLabel.getText().toString().trim();
            goal.difficulty          = getSelectedDifficulty();
            goal.createdAtTimestamp  = goal.timestamp; // same as creation time
            ref = newRef;
        }

        ref.setValue(goal).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String msg = editingGoal != null ? "Goal updated!" : "Goal saved!";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Failed to save goal.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
