package com.example.myapplication2;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DatabaseReference;

public class CreateCommunityActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {
            "Biology", "History", "Chemistry", "Psychology", "Math",
            "Physics", "Computer Science", "Literature", "Languages", "General"
    };

    private EditText             etName;
    private AutoCompleteTextView actvCategory;
    private Button               btnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_community);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        etName      = findViewById(R.id.et_community_name);
        actvCategory = findViewById(R.id.actv_category);
        btnCreate   = findViewById(R.id.btn_create);

        actvCategory.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, CATEGORIES));

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        btnCreate.setOnClickListener(v -> createCommunity());
    }

    private void createCommunity() {
        String name     = etName.getText().toString().trim();
        String category = actvCategory.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Community name is required");
            return;
        }
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseHelper.getInstance().getAuth().getUid();

        // Push a new community node
        DatabaseReference commRef = FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .push();
        String communityId = commRef.getKey();

        Community community = new Community(communityId, name,
                category.isEmpty() ? "General" : category, uid);

        btnCreate.setEnabled(false);

        commRef.setValue(community).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                btnCreate.setEnabled(true);
                Toast.makeText(this, "Failed to create community.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add creator as a member: communities/{id}/members/{uid} = true
            commRef.child("members").child(uid).setValue(true);

            // Record the community in the user's profile: users/{uid}/communities/{id} = name
            FirebaseHelper.getInstance()
                    .getCurrentUserRef()
                    .child("communities")
                    .child(communityId)
                    .setValue(name);

            Toast.makeText(this,
                    "Community \"" + name + "\" created! Code: " + communityId,
                    Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        });
    }
}
