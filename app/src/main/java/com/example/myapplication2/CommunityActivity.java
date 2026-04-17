package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class CommunityActivity extends AppCompatActivity {

    private static final String[] SUGGESTED_COMMUNITIES = {
            "Biology", "History", "Chemistry", "Psychology", "Math",
            "Physics", "Computer Science", "Literature", "General"
    };

    private AutoCompleteTextView actvBrowse, actvPostCommunity;
    private EditText   etResourceName, etLinkUrl;
    private Button     btnBrowse, btnPost;
    private RecyclerView rvLinks;
    private TextView   tvLinksLabel, tvEmpty;

    private final List<SharedLink> linkList = new ArrayList<>();
    private SharedLinksAdapter adapter;

    private String currentCommunity = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_community);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        actvBrowse       = findViewById(R.id.actv_browse_community);
        actvPostCommunity = findViewById(R.id.actv_post_community);
        etResourceName   = findViewById(R.id.et_resource_name);
        etLinkUrl        = findViewById(R.id.et_link_url);
        btnBrowse        = findViewById(R.id.btn_browse);
        btnPost          = findViewById(R.id.btn_post_link);
        rvLinks          = findViewById(R.id.rv_shared_links);
        tvLinksLabel     = findViewById(R.id.tv_links_label);
        tvEmpty          = findViewById(R.id.tv_empty_links);

        // Autocomplete suggestions for community name
        ArrayAdapter<String> suggestions = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, SUGGESTED_COMMUNITIES);
        actvBrowse.setAdapter(suggestions);
        actvPostCommunity.setAdapter(suggestions);

        // RecyclerView
        rvLinks.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SharedLinksAdapter(this, linkList);
        rvLinks.setAdapter(adapter);

        btnBrowse.setOnClickListener(v -> {
            String community = actvBrowse.getText().toString().trim();
            if (TextUtils.isEmpty(community)) {
                actvBrowse.setError("Enter a community name");
                return;
            }
            // Mirror community name into the post field for convenience
            actvPostCommunity.setText(community);
            loadLinks(community);
        });

        btnPost.setOnClickListener(v -> postLink());

        // Default: load General community
        actvBrowse.setText("General");
        actvPostCommunity.setText("General");
        loadLinks("General");
    }

    // ─── Load links from Firebase ─────────────────────────────────────────────

    private void loadLinks(String communityName) {
        currentCommunity = communityName;
        String key = communityKey(communityName);

        tvLinksLabel.setText("Links in "" + communityName + """);
        tvEmpty.setVisibility(View.GONE);
        linkList.clear();
        adapter.notifyDataSetChanged();

        DatabaseReference ref = FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .child(key)
                .child("sharedLinks");

        ref.orderByChild("timestamp")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        linkList.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            SharedLink link = child.getValue(SharedLink.class);
                            if (link != null) {
                                if (link.id == null) link.id = child.getKey();
                                linkList.add(0, link); // newest first
                            }
                        }
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(linkList.isEmpty() ? View.VISIBLE : View.GONE);
                        tvLinksLabel.setText(linkList.size() + " link"
                                + (linkList.size() == 1 ? "" : "s")
                                + " in "" + communityName + """);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(CommunityActivity.this,
                                "Failed to load links: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─── Post a new link ──────────────────────────────────────────────────────

    private void postLink() {
        String community    = actvPostCommunity.getText().toString().trim();
        String resourceName = etResourceName.getText().toString().trim();
        String url          = etLinkUrl.getText().toString().trim();

        if (TextUtils.isEmpty(community)) {
            actvPostCommunity.setError("Community name is required");
            return;
        }
        if (TextUtils.isEmpty(resourceName)) {
            etResourceName.setError("Resource name is required");
            return;
        }
        if (TextUtils.isEmpty(url)) {
            etLinkUrl.setError("Link URL is required");
            return;
        }
        if (!url.contains(".")) {
            etLinkUrl.setError("Enter a valid URL");
            return;
        }

        String authorUid   = "";
        String authorEmail = "Anonymous";
        if (FirebaseHelper.getInstance().isLoggedIn()) {
            authorUid   = FirebaseHelper.getInstance().getAuth().getUid();
            String email = FirebaseHelper.getInstance().getCurrentUser().getEmail();
            if (email != null) authorEmail = email;
        }

        DatabaseReference ref = FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .child(communityKey(community))
                .child("sharedLinks")
                .push();

        SharedLink link = new SharedLink(
                ref.getKey(), resourceName, url, authorUid, authorEmail, community);

        btnPost.setEnabled(false);
        ref.setValue(link).addOnCompleteListener(task -> {
            btnPost.setEnabled(true);
            if (task.isSuccessful()) {
                Toast.makeText(this,
                        "Posted to "" + community + ""!", Toast.LENGTH_SHORT).show();
                etResourceName.setText("");
                etLinkUrl.setText("");
                // Reload the browse list for this community
                actvBrowse.setText(community);
                loadLinks(community);
            } else {
                Toast.makeText(this,
                        "Post failed. Check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Converts a display community name to a safe Firebase key. */
    private String communityKey(String name) {
        return name.toLowerCase(java.util.Locale.getDefault())
                   .replaceAll("[^a-z0-9_]", "_");
    }
}
