package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID   = "extra_community_id";
    public static final String EXTRA_COMMUNITY_NAME = "extra_community_name";

    private String communityId;
    private String communityName;
    private String currentUid;
    private String currentDisplayName;

    // ── Chat views ────────────────────────────────────────────────────────────
    private RecyclerView    rvMessages;
    private EditText        etMessage;
    private Button          btnSend;
    private TextView        tvNoMessages;
    private LinearLayout    messageInputBar;

    private final List<Message> messageList = new ArrayList<>();
    private MessagesAdapter     messagesAdapter;
    private LinearLayoutManager layoutManager;

    private DatabaseReference messagesRef;
    private ChildEventListener messageListener;

    // ── Resources views ───────────────────────────────────────────────────────
    private RecyclerView     rvResources;
    private TextView         tvNoResources;
    private Button           btnAddResource;

    private final List<Resource> allResources      = new ArrayList<>();
    private final List<Resource> resourceList      = new ArrayList<>();
    private ResourcesAdapter     resourcesAdapter;

    private DatabaseReference    resourcesRef;
    private ValueEventListener   resourcesListener;

    // ── Filter bar ────────────────────────────────────────────────────────────
    private LinearLayout filterBarResources;
    private SearchView   svResources;
    private Spinner      spinnerGradeFilter;

    // ── Tab buttons ───────────────────────────────────────────────────────────
    private Button btnTabChat;
    private Button btnTabResources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        communityId   = getIntent().getStringExtra(EXTRA_COMMUNITY_ID);
        communityName = getIntent().getStringExtra(EXTRA_COMMUNITY_NAME);
        if (communityId == null) { finish(); return; }
        if (communityName == null) communityName = "Chat";

        // Resolve current user info
        if (FirebaseHelper.getInstance().isLoggedIn()) {
            currentUid = FirebaseHelper.getInstance().getAuth().getUid();
            String email = FirebaseHelper.getInstance().getCurrentUser().getEmail();
            currentDisplayName = email != null ? email.split("@")[0] : "Anonymous";
        } else {
            currentUid         = "anon_" + System.currentTimeMillis();
            currentDisplayName = "Anonymous";
        }

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.chatToolbar);
        toolbar.setTitle(communityName);
        toolbar.setSubtitle("Community Chat");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Tab buttons
        btnTabChat      = findViewById(R.id.btn_tab_chat);
        btnTabResources = findViewById(R.id.btn_tab_resources);

        // Chat section
        rvMessages      = findViewById(R.id.rv_messages);
        etMessage       = findViewById(R.id.et_message);
        btnSend         = findViewById(R.id.btn_send);
        tvNoMessages    = findViewById(R.id.tv_no_messages);
        messageInputBar = findViewById(R.id.message_input_bar);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        messagesAdapter = new MessagesAdapter(messageList, currentUid);
        rvMessages.setAdapter(messagesAdapter);

        btnSend.setOnClickListener(v -> sendMessage());

        // Resources section
        rvResources    = findViewById(R.id.rv_resources);
        tvNoResources  = findViewById(R.id.tv_no_resources);
        btnAddResource = findViewById(R.id.btn_add_resource);

        rvResources.setLayoutManager(new LinearLayoutManager(this));
        resourcesAdapter = new ResourcesAdapter(this, resourceList, communityId, currentUid);
        rvResources.setAdapter(resourcesAdapter);

        btnAddResource.setOnClickListener(v -> showAddResourceDialog());

        // Filter bar
        filterBarResources = findViewById(R.id.filter_bar_resources);
        svResources        = findViewById(R.id.sv_resources);
        spinnerGradeFilter = findViewById(R.id.spinner_grade_filter);

        String[] grades = {"All Grades","Seventh","Eighth","Ninth","Tenth","Eleventh","Twelfth"};
        ArrayAdapter<String> gradeFilterAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, grades);
        gradeFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGradeFilter.setAdapter(gradeFilterAdapter);

        spinnerGradeFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) { applyFilter(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        svResources.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { applyFilter(); return true; }
            @Override public boolean onQueryTextChange(String t) { applyFilter(); return true; }
        });

        // Firebase refs
        DatabaseReference communityRef = FirebaseHelper.getInstance()
                .getDatabase().getReference("communities").child(communityId);
        messagesRef  = communityRef.child("messages");
        resourcesRef = communityRef.child("resources");

        // Tab click listeners
        btnTabChat.setOnClickListener(v -> showChatTab());
        btnTabResources.setOnClickListener(v -> showResourcesTab());

        // Start on Chat tab
        showChatTab();
        attachMessageListener();
        attachResourcesListener();
    }

    // ─── Tab switching ────────────────────────────────────────────────────────

    private void showChatTab() {
        // Chat section visible
        rvMessages.setVisibility(View.VISIBLE);
        tvNoMessages.setVisibility(messageList.isEmpty() ? View.VISIBLE : View.GONE);
        messageInputBar.setVisibility(View.VISIBLE);

        // Resources section + filter bar hidden
        filterBarResources.setVisibility(View.GONE);
        rvResources.setVisibility(View.GONE);
        tvNoResources.setVisibility(View.GONE);
        btnAddResource.setVisibility(View.GONE);

        // Button active states
        btnTabChat.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF2196F3));
        btnTabChat.setTextColor(0xFFFFFFFF);
        btnTabResources.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFE3F2FD));
        btnTabResources.setTextColor(0xFF2196F3);
    }

    private void showResourcesTab() {
        // Filter bar + resources section visible
        filterBarResources.setVisibility(View.VISIBLE);
        applyFilter();
        rvResources.setVisibility(View.VISIBLE);
        btnAddResource.setVisibility(View.VISIBLE);

        // Chat section hidden
        rvMessages.setVisibility(View.GONE);
        tvNoMessages.setVisibility(View.GONE);
        messageInputBar.setVisibility(View.GONE);

        // Button active states
        btnTabResources.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF2196F3));
        btnTabResources.setTextColor(0xFFFFFFFF);
        btnTabChat.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFE3F2FD));
        btnTabChat.setTextColor(0xFF2196F3);
    }

    // ─── Toolbar menu ─────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_share_community) {
            shareCommunity();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareCommunity() {
        String shareText = "Join my study community \"" + communityName
                + "\" on StudyMotify! Use code: " + communityId;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    // ─── Real-time message listener ───────────────────────────────────────────

    private void attachMessageListener() {
        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Message msg = snapshot.getValue(Message.class);
                if (msg != null) {
                    if (msg.id == null) msg.id = snapshot.getKey();
                    messageList.add(msg);
                    messagesAdapter.notifyItemInserted(messageList.size() - 1);
                    rvMessages.scrollToPosition(messageList.size() - 1);
                    if (rvMessages.getVisibility() == View.VISIBLE) {
                        tvNoMessages.setVisibility(View.GONE);
                    }
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this,
                        "Chat error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists() && rvMessages.getVisibility() == View.VISIBLE) {
                    tvNoMessages.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        messagesRef.addChildEventListener(messageListener);
    }

    // ─── Resources listener ───────────────────────────────────────────────────

    private void attachResourcesListener() {
        resourcesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allResources.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Resource res = child.getValue(Resource.class);
                    if (res != null) {
                        if (res.id == null) res.id = child.getKey();
                        allResources.add(res);
                    }
                }
                Collections.sort(allResources,
                        (a, b) -> Long.compare(b.getLikesCount(), a.getLikesCount()));
                applyFilter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this,
                        "Resources error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        resourcesRef.addValueEventListener(resourcesListener);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyFilter() {
        String keyword = svResources != null
                ? svResources.getQuery().toString().trim().toLowerCase(Locale.getDefault())
                : "";
        String grade = spinnerGradeFilter != null && spinnerGradeFilter.getSelectedItem() != null
                ? spinnerGradeFilter.getSelectedItem().toString()
                : "All Grades";

        resourceList.clear();
        for (Resource res : allResources) {
            boolean matchesKeyword = keyword.isEmpty()
                    || res.getTitle().toLowerCase(Locale.getDefault()).contains(keyword);
            boolean matchesGrade = "All Grades".equals(grade)
                    || grade.equals(res.getGrade());
            if (matchesKeyword && matchesGrade) resourceList.add(res);
        }
        resourcesAdapter.notifyDataSetChanged();
        if (rvResources.getVisibility() == View.VISIBLE) {
            tvNoResources.setVisibility(resourceList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // ─── Add Resource dialog ──────────────────────────────────────────────────

    private void showAddResourceDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_resource, null);

        RadioGroup rgType  = dialogView.findViewById(R.id.rg_resource_type);
        EditText   etTitle = dialogView.findViewById(R.id.et_resource_title);
        EditText   etUrl   = dialogView.findViewById(R.id.et_resource_url);
        Spinner    spGrade = dialogView.findViewById(R.id.spinner_grade);

        String[] gradeOptions = {"Seventh","Eighth","Ninth","Tenth","Eleventh","Twelfth"};
        ArrayAdapter<String> gradeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, gradeOptions);
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGrade.setAdapter(gradeAdapter);

        new AlertDialog.Builder(this)
                .setTitle("Add Resource")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String url   = etUrl.getText().toString().trim();
                    String type  = (rgType.getCheckedRadioButtonId() == R.id.rb_exam)
                            ? "Exam" : "Summary";
                    String grade = spGrade.getSelectedItem().toString();

                    if (TextUtils.isEmpty(title)) {
                        Toast.makeText(this, "Please enter a title.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(url)) {
                        Toast.makeText(this, "Please enter a URL.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    pushResource(title, type, url, grade);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pushResource(String title, String type, String url, String grade) {
        DatabaseReference newRef = resourcesRef.push();
        Resource resource = new Resource(
                newRef.getKey(), title, type, url, currentDisplayName, currentUid);
        resource.grade = grade;
        newRef.setValue(resource)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Resource added!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to add resource.", Toast.LENGTH_SHORT).show());
    }

    // ─── Send a message ───────────────────────────────────────────────────────

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        DatabaseReference msgRef = messagesRef.push();
        Message msg = new Message(msgRef.getKey(), currentUid, currentDisplayName, text);

        etMessage.setText("");
        msgRef.setValue(msg).addOnFailureListener(e ->
                Toast.makeText(this, "Send failed. Try again.", Toast.LENGTH_SHORT).show());
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesRef != null && messageListener != null) {
            messagesRef.removeEventListener(messageListener);
        }
        if (resourcesRef != null && resourcesListener != null) {
            resourcesRef.removeEventListener(resourcesListener);
        }
    }
}
