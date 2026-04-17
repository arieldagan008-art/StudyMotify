package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    RecyclerView rvGoals, rvChecklist;
    Button btnAddGoal, btnReplan;
    LinearLayout layoutOverloadBanner;
    TextView tvSystemStatus;

    GoalsAdapter adapter;
    List<Goal> goalList;

    private static final String TAG = "HomeActivity";

    FirebaseAuth mAuth;
    DatabaseReference mDatabase;
    ValueEventListener goalsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.homeToolbar);
        setSupportActionBar(toolbar);

        rvGoals              = findViewById(R.id.rvGoals);
        rvChecklist          = findViewById(R.id.rvChecklist);
        btnAddGoal           = findViewById(R.id.btnAddGoal);
        btnReplan            = findViewById(R.id.btn_replan);
        layoutOverloadBanner = findViewById(R.id.layout_overload_banner);
        tvSystemStatus       = findViewById(R.id.tv_system_status);

        rvGoals.setLayoutManager(new LinearLayoutManager(this));
        goalList = new ArrayList<>();
        adapter  = new GoalsAdapter(this, goalList);
        rvGoals.setAdapter(adapter);

        rvChecklist.setLayoutManager(new LinearLayoutManager(this));

        btnAddGoal.setOnClickListener(v -> startActivity(
                new Intent(HomeActivity.this, AddGoalActivity.class)));

        btnReplan.setOnClickListener(v -> {
            List<SchedulerLogic.ReplanSuggestion> suggestions =
                    SchedulerLogic.rebalanceAll(goalList);
            if (suggestions.isEmpty()) {
                Toast.makeText(this, "No overloaded goals to re-plan.", Toast.LENGTH_SHORT).show();
            } else {
                showReplanDialog(suggestions);
            }
        });

        attachSwipeToDelete();
    }

    // ─── Toolbar menu ─────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_analytics) {
            startActivity(new Intent(this, FocusAnalyticsActivity.class));
            return true;
        }
        if (id == R.id.menu_community) {
            startActivity(new Intent(this, CommunityActivity.class));
            return true;
        }
        if (id == R.id.menu_flashcards) {
            startActivity(new Intent(this, FlashcardDeckActivity.class));
            return true;
        }
        if (id == R.id.menu_sign_out) {
            FirebaseHelper.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── Firebase lifecycle ───────────────────────────────────────────────────

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
            return;
        }

        mDatabase = FirebaseHelper.getInstance().getCurrentUserRef().child("goals");
        attachGoalsListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachGoalsListener();
    }

    // ─── Firebase listener ────────────────────────────────────────────────────

    private void attachGoalsListener() {
        if (goalsListener != null) return;
        goalsListener = new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                goalList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Goal goal = child.getValue(Goal.class);
                    if (goal != null) {
                        goalList.add(goal);
                        Log.d(TAG, "Goal: " + goal.getGoalName()
                                + " | due: " + goal.getDueDate()
                                + " | ts: " + goal.getTimestamp());
                    }
                }
                Log.d(TAG, "Total goals: " + goalList.size());
                adapter.notifyDataSetChanged();
                updateSystemStatus();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HomeActivity.this,
                        "Failed to load goals: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.addValueEventListener(goalsListener);
    }

    private void detachGoalsListener() {
        if (goalsListener != null && mDatabase != null) {
            mDatabase.removeEventListener(goalsListener);
            goalsListener = null;
        }
    }

    // ─── System status banner ─────────────────────────────────────────────────

    private void updateSystemStatus() {
        int totalDailyUnits = SchedulerLogic.checkGlobalOverload(goalList);
        if (totalDailyUnits > SchedulerLogic.DAILY_OVERLOAD_THRESHOLD) {
            tvSystemStatus.setText(
                    "⚠️ Warning: Extreme workload detected! ("
                    + totalDailyUnits + " units due today across all goals)");
            layoutOverloadBanner.setVisibility(View.VISIBLE);
        } else {
            layoutOverloadBanner.setVisibility(View.GONE);
        }
    }

    // ─── Smart Re-plan dialog ─────────────────────────────────────────────────

    private void showReplanDialog(List<SchedulerLogic.ReplanSuggestion> suggestions) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        StringBuilder sb = new StringBuilder();
        sb.append("To keep your workload sustainable (max ")
          .append(SchedulerLogic.SUSTAINABLE_DAILY_CAP)
          .append(" units/day), here are the proposed changes:\n\n");

        for (SchedulerLogic.ReplanSuggestion s : suggestions) {
            String newDateStr = sdf.format(new Date(s.newDeadlineMs));
            sb.append("• ")
              .append(s.goal.getGoalName())
              .append(": spread ")
              .append(s.goal.totalUnits - s.goal.completedUnits)
              .append(" remaining ")
              .append(s.goal.getUnitLabel())
              .append(" over the next ")
              .append((int) Math.ceil((double)(s.goal.totalUnits - s.goal.completedUnits)
                      * SchedulerLogic.getDifficultyMultiplier(s.goal.getDifficulty())
                      / SchedulerLogic.SUSTAINABLE_DAILY_CAP))
              .append(" days");
            if (s.daysExtended > 0) {
                sb.append(" (deadline extended by ")
                  .append(s.daysExtended)
                  .append(s.daysExtended == 1 ? " day" : " days")
                  .append(" → ")
                  .append(newDateStr)
                  .append(")");
            }
            sb.append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Proposed Re-plan")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Accept", (dialog, which) -> applyReplan(suggestions))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyReplan(List<SchedulerLogic.ReplanSuggestion> suggestions) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        DatabaseReference userRef = FirebaseHelper.getInstance().getCurrentUserRef();

        for (SchedulerLogic.ReplanSuggestion s : suggestions) {
            if (s.goal.getId() == null) continue;

            String newDateStr = sdf.format(new Date(s.newDeadlineMs));

            // Update local model immediately
            s.goal.deadlineDate = s.newDeadlineMs;
            s.goal.dueDate      = newDateStr;

            // Persist to Firebase
            userRef.child("goals").child(s.goal.getId())
                    .child("deadlineDate").setValue(s.newDeadlineMs);
            userRef.child("goals").child(s.goal.getId())
                    .child("dueDate").setValue(newDateStr);
        }

        Toast.makeText(this, "Re-plan applied! Deadlines updated.", Toast.LENGTH_SHORT).show();

        // Refresh adapter so cards show updated daily targets
        adapter.notifyDataSetChanged();
        updateSystemStatus();
    }

    // ─── Swipe to delete ─────────────────────────────────────────────────────

    private void attachSwipeToDelete() {
        ColorDrawable swipeBackground = new ColorDrawable(Color.parseColor("#F44336"));

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Goal deletedGoal = goalList.get(position);

                goalList.remove(position);
                adapter.notifyItemRemoved(position);

                detachGoalsListener();

                if (deletedGoal.getId() != null) {
                    mDatabase.child(deletedGoal.getId()).removeValue();
                }

                Snackbar.make(rvGoals, "Goal deleted", Snackbar.LENGTH_LONG)
                        .setDuration(3000)
                        .setAction("Undo", v -> {
                            if (deletedGoal.getId() != null) {
                                mDatabase.child(deletedGoal.getId()).setValue(deletedGoal);
                            }
                            attachGoalsListener();
                        })
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar sb, int event) {
                                if (event != DISMISS_EVENT_ACTION) {
                                    attachGoalsListener();
                                }
                            }
                        })
                        .setActionTextColor(ContextCompat.getColor(HomeActivity.this,
                                android.R.color.holo_orange_light))
                        .show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View item = viewHolder.itemView;
                if (dX > 0) {
                    swipeBackground.setBounds(item.getLeft(), item.getTop(),
                            item.getLeft() + (int) dX, item.getBottom());
                } else if (dX < 0) {
                    swipeBackground.setBounds(item.getRight() + (int) dX, item.getTop(),
                            item.getRight(), item.getBottom());
                } else {
                    swipeBackground.setBounds(0, 0, 0, 0);
                }
                swipeBackground.draw(c);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(rvGoals);
    }
}
