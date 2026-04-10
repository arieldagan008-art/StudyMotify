package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    RecyclerView rvGoals, rvChecklist;
    Button btnAddGoal, btnSignOut;
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

        rvGoals        = findViewById(R.id.rvGoals);
        rvChecklist    = findViewById(R.id.rvChecklist);
        btnAddGoal     = findViewById(R.id.btnAddGoal);
        btnSignOut     = findViewById(R.id.btnSignOut);
        tvSystemStatus = findViewById(R.id.tv_system_status);

        btnSignOut.setOnClickListener(v -> {
            FirebaseHelper.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        rvGoals.setLayoutManager(new LinearLayoutManager(this));
        goalList = new ArrayList<>();
        adapter  = new GoalsAdapter(this, goalList);
        rvGoals.setAdapter(adapter);

        rvChecklist.setLayoutManager(new LinearLayoutManager(this));

        btnAddGoal.setOnClickListener(v -> startActivity(
                new Intent(HomeActivity.this, AddGoalActivity.class)));

        attachSwipeToDelete();
    }

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
        if (goalsListener != null) return; // already attached
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
            tvSystemStatus.setVisibility(View.VISIBLE);
        } else {
            tvSystemStatus.setVisibility(View.GONE);
        }
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
                return false; // drag-and-drop not used
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Goal deletedGoal = goalList.get(position);

                // 1. Remove from local list and notify adapter immediately
                goalList.remove(position);
                adapter.notifyItemRemoved(position);

                // 2. Detach Firebase listener so the deletion event doesn't
                //    re-populate the list while the Snackbar is visible
                detachGoalsListener();

                // 3. Delete from Firebase
                if (deletedGoal.getId() != null) {
                    mDatabase.child(deletedGoal.getId()).removeValue();
                }

                // 4. Show Undo Snackbar for 3 seconds
                Snackbar.make(rvGoals, "Goal deleted", Snackbar.LENGTH_LONG)
                        .setDuration(3000)
                        .setAction("Undo", v -> {
                            // Restore to Firebase — the listener reattach will repopulate the list
                            if (deletedGoal.getId() != null) {
                                mDatabase.child(deletedGoal.getId()).setValue(deletedGoal);
                            }
                            attachGoalsListener();
                        })
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar sb, int event) {
                                // Reattach listener after Snackbar closes (any reason other than Undo)
                                if (event != DISMISS_EVENT_ACTION) {
                                    attachGoalsListener();
                                }
                            }
                        })
                        .setActionTextColor(ContextCompat.getColor(HomeActivity.this,
                                android.R.color.holo_orange_light))
                        .show();
            }

            // Draw red background while the card is being swiped
            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View item = viewHolder.itemView;
                if (dX > 0) { // swiping right
                    swipeBackground.setBounds(item.getLeft(), item.getTop(),
                            item.getLeft() + (int) dX, item.getBottom());
                } else if (dX < 0) { // swiping left
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
