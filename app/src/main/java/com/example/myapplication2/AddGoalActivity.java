package com.example.myapplication2;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
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

    private static final String[] CATEGORIES =
            {"Study", "Test", "Work", "Workout", "Task", "Other"};

    // UI Components
    private TextView    tvScreenTitle;
    private ImageButton btnBack;
    private EditText    etGoalName, etDetails, etCategoryOther, etTotalUnits, etUnitLabel;
    private Spinner     spinnerCategory;
    private TextView    tvDueDate, tvReminderTime, tvStartTime, tvEndTime;
    private Switch      switchReminder, switchFixedTime;
    private LinearLayout layoutFixedTimes, layoutProgressTracking;
    private Button      btnSaveGoal;
    private RadioGroup  rgDifficulty;

    private Calendar calendar;
    private long     selectedDeadlineMs = 0;
    private String   selectedStartTime  = "08:00";
    private String   selectedEndTime    = "09:00";
    private FirebaseHelper firebaseHelper;

    private Goal editingGoal = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_goal);

        firebaseHelper = FirebaseHelper.getInstance();
        calendar       = Calendar.getInstance();

        // Bind views
        tvScreenTitle         = findViewById(R.id.tv_screen_title);
        btnBack               = findViewById(R.id.btn_back);
        etGoalName            = findViewById(R.id.et_goal_name);
        tvDueDate             = findViewById(R.id.tv_due_date);
        etDetails             = findViewById(R.id.et_details);
        spinnerCategory       = findViewById(R.id.spinner_category);
        etCategoryOther       = findViewById(R.id.et_category_other);
        rgDifficulty          = findViewById(R.id.rg_difficulty);
        switchFixedTime       = findViewById(R.id.switch_fixed_time);
        layoutFixedTimes      = findViewById(R.id.layout_fixed_times);
        tvStartTime           = findViewById(R.id.tv_start_time);
        tvEndTime             = findViewById(R.id.tv_end_time);
        layoutProgressTracking= findViewById(R.id.layout_progress_tracking);
        etTotalUnits          = findViewById(R.id.et_total_units);
        etUnitLabel           = findViewById(R.id.et_unit_label);
        switchReminder        = findViewById(R.id.switch_reminder);
        tvReminderTime        = findViewById(R.id.tv_reminder_time);
        btnSaveGoal           = findViewById(R.id.btn_save_goal);

        // Category spinner setup
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CATEGORIES);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                etCategoryOther.setVisibility(
                        CATEGORIES[position].equals("Other") ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Fixed event toggle
        switchFixedTime.setOnCheckedChangeListener((btn, isChecked) -> {
            layoutFixedTimes.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            layoutProgressTracking.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });

        // Edit mode
        if (getIntent().hasExtra(EXTRA_GOAL)) {
            editingGoal = (Goal) getIntent().getSerializableExtra(EXTRA_GOAL);
            prefillFields(editingGoal);
            tvScreenTitle.setText("Edit Goal / Event");
            btnSaveGoal.setText("Update");
        }

        btnBack.setOnClickListener(v -> finish());
        tvDueDate.setOnClickListener(v -> showDatePicker());
        tvReminderTime.setOnClickListener(v -> showReminderTimePicker());
        tvStartTime.setOnClickListener(v -> showFixedTimePicker(true));
        tvEndTime.setOnClickListener(v -> showFixedTimePicker(false));
        btnSaveGoal.setOnClickListener(v -> saveGoalToFirebase());
    }

    private void prefillFields(Goal goal) {
        etGoalName.setText(goal.goalName);
        etDetails.setText(goal.details);

        // Spinner: find matching category
        String cat = goal.category != null ? goal.category : "";
        boolean found = false;
        for (int i = 0; i < CATEGORIES.length - 1; i++) {
            if (CATEGORIES[i].equalsIgnoreCase(cat)) {
                spinnerCategory.setSelection(i);
                found = true;
                break;
            }
        }
        if (!found && !cat.isEmpty()) {
            // Select "Other" and fill custom field
            spinnerCategory.setSelection(CATEGORIES.length - 1);
            etCategoryOther.setText(cat);
            etCategoryOther.setVisibility(View.VISIBLE);
        }

        if (!TextUtils.isEmpty(goal.dueDate) && !goal.dueDate.equals("DD/MM/YYYY")) {
            tvDueDate.setText(goal.dueDate);
        }
        if (goal.deadlineDate > 0) selectedDeadlineMs = goal.deadlineDate;

        if (goal.totalUnits > 0) etTotalUnits.setText(String.valueOf(goal.totalUnits));
        etUnitLabel.setText(goal.unitLabel);
        switchReminder.setChecked(goal.reminder);
        if (!TextUtils.isEmpty(goal.time)) tvReminderTime.setText(goal.time);

        // Fixed event
        if (goal.isFixedTime) {
            switchFixedTime.setChecked(true);
            layoutFixedTimes.setVisibility(View.VISIBLE);
            layoutProgressTracking.setVisibility(View.GONE);
            if (!TextUtils.isEmpty(goal.startTime)) {
                selectedStartTime = goal.startTime;
                tvStartTime.setText(goal.startTime);
            }
            if (!TextUtils.isEmpty(goal.endTime)) {
                selectedEndTime = goal.endTime;
                tvEndTime.setText(goal.endTime);
            }
        }

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
        return 1;
    }

    private String getSelectedCategory() {
        int pos = spinnerCategory.getSelectedItemPosition();
        if (pos == CATEGORIES.length - 1) {
            // "Other" — use custom text
            String custom = etCategoryOther.getText().toString().trim();
            return custom.isEmpty() ? "Other" : custom;
        }
        return CATEGORIES[pos];
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

    private void showReminderTimePicker() {
        new TimePickerDialog(
                this,
                (TimePicker view, int hourOfDay, int minute) -> {
                    String t = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    tvReminderTime.setText(t);
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true).show();
    }

    private void showFixedTimePicker(boolean isStart) {
        int[] parts = parseTime(isStart ? selectedStartTime : selectedEndTime);
        new TimePickerDialog(
                this,
                (TimePicker view, int hourOfDay, int minute) -> {
                    String t = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    if (isStart) {
                        selectedStartTime = t;
                        tvStartTime.setText(t);
                    } else {
                        selectedEndTime = t;
                        tvEndTime.setText(t);
                    }
                },
                parts[0], parts[1], true).show();
    }

    private int[] parseTime(String time) {
        try {
            String[] p = time.split(":");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (Exception e) {
            return new int[]{8, 0};
        }
    }

    private void saveGoalToFirebase() {
        String name      = etGoalName.getText().toString().trim();
        String category  = getSelectedCategory();
        String date      = tvDueDate.getText().toString().trim();
        String details   = etDetails.getText().toString().trim();
        boolean isReminder = switchReminder.isChecked();
        String time      = tvReminderTime.getText().toString().trim();
        boolean isFixed  = switchFixedTime.isChecked();

        if (TextUtils.isEmpty(name)) {
            etGoalName.setError("Name is required");
            return;
        }
        if (!firebaseHelper.isLoggedIn()) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        final Goal goal;
        final DatabaseReference ref;

        if (editingGoal != null) {
            goal = editingGoal;
            goal.goalName     = name;
            goal.category     = category;
            goal.dueDate      = date;
            goal.details      = details;
            goal.reminder     = isReminder;
            goal.time         = time;
            goal.deadlineDate = selectedDeadlineMs > 0 ? selectedDeadlineMs : editingGoal.deadlineDate;
            goal.difficulty   = getSelectedDifficulty();
            goal.isFixedTime  = isFixed;
            goal.startTime    = isFixed ? selectedStartTime : null;
            goal.endTime      = isFixed ? selectedEndTime : null;

            if (!isFixed) {
                String totalUnitsStr = etTotalUnits.getText().toString().trim();
                goal.totalUnits = TextUtils.isEmpty(totalUnitsStr) ? 0 : Integer.parseInt(totalUnitsStr);
                goal.unitLabel  = etUnitLabel.getText().toString().trim();
            }

            ref = firebaseHelper.getCurrentUserRef()
                    .child("goals")
                    .child(goal.getId());
        } else {
            DatabaseReference newRef = firebaseHelper.getCurrentUserRef().child("goals").push();
            goal = new Goal(newRef.getKey(), name, category, date, details,
                    isReminder, time, System.currentTimeMillis());

            goal.isFixedTime = isFixed;
            goal.startTime   = isFixed ? selectedStartTime : null;
            goal.endTime     = isFixed ? selectedEndTime : null;
            goal.difficulty  = getSelectedDifficulty();
            goal.deadlineDate = selectedDeadlineMs;
            goal.completedUnits = 0;
            goal.createdAtTimestamp = goal.timestamp;

            if (!isFixed) {
                String totalUnitsStr = etTotalUnits.getText().toString().trim();
                if (!TextUtils.isEmpty(totalUnitsStr)) {
                    goal.totalUnits = Integer.parseInt(totalUnitsStr);
                }
                goal.unitLabel = etUnitLabel.getText().toString().trim();
            }

            ref = newRef;
        }

        ref.setValue(goal).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this,
                        editingGoal != null ? "Updated!" : "Saved!",
                        Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Failed to save.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
