package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID   = "extra_community_id";
    public static final String EXTRA_COMMUNITY_NAME = "extra_community_name";

    private String communityId;
    private String communityName;
    private String currentUid;
    private String currentDisplayName;

    private RecyclerView    rvMessages;
    private EditText        etMessage;
    private Button          btnSend;
    private TextView        tvNoMessages;

    private final List<Message> messageList = new ArrayList<>();
    private MessagesAdapter adapter;
    private LinearLayoutManager layoutManager;

    private DatabaseReference messagesRef;
    private ChildEventListener messageListener;

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
            currentDisplayName = email != null
                    ? email.split("@")[0]   // use local part of email as display name
                    : "Anonymous";
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

        rvMessages   = findViewById(R.id.rv_messages);
        etMessage    = findViewById(R.id.et_message);
        btnSend      = findViewById(R.id.btn_send);
        tvNoMessages = findViewById(R.id.tv_no_messages);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);   // newest messages at the bottom
        rvMessages.setLayoutManager(layoutManager);
        adapter = new MessagesAdapter(messageList, currentUid);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        messagesRef = FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .child(communityId)
                .child("messages");

        attachMessageListener();
    }

    // ─── Toolbar menu (Share) ─────────────────────────────────────────────────

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
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot,
                                     String previousChildName) {
                Message msg = snapshot.getValue(Message.class);
                if (msg != null) {
                    if (msg.id == null) msg.id = snapshot.getKey();
                    messageList.add(msg);
                    adapter.notifyItemInserted(messageList.size() - 1);
                    rvMessages.scrollToPosition(messageList.size() - 1);
                    tvNoMessages.setVisibility(View.GONE);
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

        // Show "No messages" text until first message arrives
        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) tvNoMessages.setVisibility(View.VISIBLE);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        messagesRef.addChildEventListener(messageListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesRef != null && messageListener != null) {
            messagesRef.removeEventListener(messageListener);
        }
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
}
