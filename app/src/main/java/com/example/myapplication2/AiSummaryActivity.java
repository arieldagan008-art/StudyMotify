package com.example.myapplication2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class AiSummaryActivity extends AppCompatActivity {

    public static final String EXTRA_SUMMARY = "summary_text";
    public static final String EXTRA_SUBJECT = "summary_subject";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_summary);

        String subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        MaterialToolbar toolbar = findViewById(R.id.summaryToolbar);
        toolbar.setTitle(subject != null ? subject + " — Summary" : "Summary");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String summary = getIntent().getStringExtra(EXTRA_SUMMARY);
        if (summary == null) summary = "";

        TextView tvContent = findViewById(R.id.tv_summary_content);
        tvContent.setText(summary);

        final String finalSummary = summary;
        Button btnCopy = findViewById(R.id.btn_copy_summary);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("AI Summary", finalSummary));
            Toast.makeText(this, "Summary copied to clipboard.", Toast.LENGTH_SHORT).show();
        });
    }
}
