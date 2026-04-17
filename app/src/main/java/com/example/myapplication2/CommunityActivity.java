package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class CommunityActivity extends AppCompatActivity {

    private RecyclerView         rvMine, rvDiscover;
    private TextView             tvEmptyMine, tvEmptyDiscover;
    private Button               btnCreate, btnJoin;

    private final List<Community> myCommunities      = new ArrayList<>();
    private final List<Community> discoverCommunities = new ArrayList<>();

    private CommunitiesAdapter myAdapter, discoverAdapter;

    // Re-load when returning from CreateCommunityActivity
    private final ActivityResultLauncher<Intent> createLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> { if (result.getResultCode() == RESULT_OK) loadMyCommunities(); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_community);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        rvMine         = findViewById(R.id.rv_my_communities);
        rvDiscover     = findViewById(R.id.rv_discover);
        tvEmptyMine    = findViewById(R.id.tv_empty_mine);
        tvEmptyDiscover = findViewById(R.id.tv_empty_discover);
        btnCreate      = findViewById(R.id.btn_create_community);
        btnJoin        = findViewById(R.id.btn_join_community);

        rvMine.setLayoutManager(new LinearLayoutManager(this));
        myAdapter = new CommunitiesAdapter(myCommunities, this::openChat);
        rvMine.setAdapter(myAdapter);

        rvDiscover.setLayoutManager(new LinearLayoutManager(this));
        discoverAdapter = new CommunitiesAdapter(discoverCommunities, this::joinAndOpenChat);
        rvDiscover.setAdapter(discoverAdapter);

        btnCreate.setOnClickListener(v ->
                createLauncher.launch(new Intent(this, CreateCommunityActivity.class)));

        btnJoin.setOnClickListener(v -> showJoinDialog());

        loadMyCommunities();
        loadDiscoverCommunities();
    }

    // ─── Load user's communities ──────────────────────────────────────────────

    private void loadMyCommunities() {
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            tvEmptyMine.setVisibility(View.VISIBLE);
            return;
        }

        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("communities")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> ids = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) ids.add(child.getKey());
                        if (ids.isEmpty()) {
                            showEmptyMine();
                            return;
                        }
                        fetchCommunityDetails(ids);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) { showEmptyMine(); }
                });
    }

    private void fetchCommunityDetails(List<String> ids) {
        myCommunities.clear();
        // Counter to know when all lookups are done
        final int[] remaining = {ids.size()};

        for (String id : ids) {
            FirebaseHelper.getInstance()
                    .getDatabase()
                    .getReference("communities")
                    .child(id)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @SuppressLint("NotifyDataSetChanged")
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Community c = snapshot.getValue(Community.class);
                            if (c != null) {
                                if (c.id == null) c.id = snapshot.getKey();
                                myCommunities.add(c);
                            }
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                myAdapter.notifyDataSetChanged();
                                tvEmptyMine.setVisibility(
                                        myCommunities.isEmpty() ? View.VISIBLE : View.GONE);
                            }
                        }

                        @Override public void onCancelled(@NonNull DatabaseError e) {
                            remaining[0]--;
                            if (remaining[0] == 0 && myCommunities.isEmpty()) showEmptyMine();
                        }
                    });
        }
    }

    // ─── Discover public communities ──────────────────────────────────────────

    private void loadDiscoverCommunities() {
        FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .orderByChild("createdAt")
                .limitToLast(20)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        discoverCommunities.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Community c = child.getValue(Community.class);
                            if (c != null) {
                                if (c.id == null) c.id = child.getKey();
                                discoverCommunities.add(0, c); // newest first
                            }
                        }
                        discoverAdapter.notifyDataSetChanged();
                        tvEmptyDiscover.setVisibility(
                                discoverCommunities.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        tvEmptyDiscover.setVisibility(View.VISIBLE);
                    }
                });
    }

    // ─── Join with code dialog ────────────────────────────────────────────────

    private void showJoinDialog() {
        EditText input = new EditText(this);
        input.setHint("Paste community code here");

        new AlertDialog.Builder(this)
                .setTitle("Join a Community")
                .setMessage("Enter the community code shared by a friend:")
                .setView(input)
                .setPositiveButton("Join", (dialog, which) -> {
                    String code = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(code)) joinCommunityById(code);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void joinCommunityById(String communityId) {
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .child(communityId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Toast.makeText(CommunityActivity.this,
                                    "Community not found. Check the code.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Community c = snapshot.getValue(Community.class);
                        if (c == null) return;
                        if (c.id == null) c.id = snapshot.getKey();

                        String uid = FirebaseHelper.getInstance().getAuth().getUid();

                        // Add user as member
                        snapshot.getRef().child("members").child(uid).setValue(true);

                        // Record in user's profile
                        FirebaseHelper.getInstance()
                                .getCurrentUserRef()
                                .child("communities")
                                .child(c.id)
                                .setValue(c.getName());

                        Toast.makeText(CommunityActivity.this,
                                "Joined \"" + c.getName() + "\"!",
                                Toast.LENGTH_SHORT).show();

                        // Refresh and open the chat
                        loadMyCommunities();
                        openChat(c);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        Toast.makeText(CommunityActivity.this,
                                "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private void openChat(Community c) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra(ChatActivity.EXTRA_COMMUNITY_ID,   c.getId());
        i.putExtra(ChatActivity.EXTRA_COMMUNITY_NAME, c.getName());
        startActivity(i);
    }

    /** Tapping a Discover card: join first, then open chat. */
    private void joinAndOpenChat(Community c) {
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            openChat(c);
            return;
        }
        String uid = FirebaseHelper.getInstance().getAuth().getUid();
        FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .child(c.getId())
                .child("members")
                .child(uid)
                .setValue(true);
        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("communities")
                .child(c.getId())
                .setValue(c.getName());
        openChat(c);
    }

    private void showEmptyMine() {
        myCommunities.clear();
        myAdapter.notifyDataSetChanged();
        tvEmptyMine.setVisibility(View.VISIBLE);
    }
}
