package com.example.myapplication2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;

public class AiResultsActivity extends AppCompatActivity {

    public static final String EXTRA_SUMMARY         = "ai_summary";
    public static final String EXTRA_EXERCISES       = "ai_exercises";
    public static final String EXTRA_SUBJECT         = "ai_subject";
    public static final String EXTRA_FLASHCARD_COUNT = "ai_flashcard_count";
    public static final String EXTRA_WANT_SUMMARY    = "ai_want_summary";
    public static final String EXTRA_WANT_EXERCISES  = "ai_want_exercises";
    public static final String EXTRA_WANT_FLASHCARDS = "ai_want_flashcards";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_results);

        MaterialToolbar toolbar = findViewById(R.id.resultsToolbar);
        toolbar.setTitle("AI Results");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String  summary        = getIntent().getStringExtra(EXTRA_SUMMARY);
        String  exercisesJson  = getIntent().getStringExtra(EXTRA_EXERCISES);
        String  subject        = getIntent().getStringExtra(EXTRA_SUBJECT);
        int     flashcardCount = getIntent().getIntExtra(EXTRA_FLASHCARD_COUNT, 0);
        boolean wantSummary    = getIntent().getBooleanExtra(EXTRA_WANT_SUMMARY, false);
        boolean wantExercises  = getIntent().getBooleanExtra(EXTRA_WANT_EXERCISES, false);
        boolean wantFlashcards = getIntent().getBooleanExtra(EXTRA_WANT_FLASHCARDS, false);

        CardView cardSummary  = findViewById(R.id.card_summary);
        CardView cardPractice = findViewById(R.id.card_practice);
        TextView tvFlashcard  = findViewById(R.id.tv_flashcard_notice);

        boolean summaryAvailable = wantSummary && summary != null && !summary.isEmpty();
        if (!summaryAvailable) {
            cardSummary.setAlpha(0.4f);
            cardSummary.setClickable(false);
        } else {
            cardSummary.setOnClickListener(v -> {
                Intent intent = new Intent(this, AiSummaryActivity.class);
                intent.putExtra(AiSummaryActivity.EXTRA_SUMMARY, summary);
                intent.putExtra(AiSummaryActivity.EXTRA_SUBJECT, subject);
                startActivity(intent);
            });
        }

        boolean exercisesAvailable = wantExercises && exercisesJson != null && !exercisesJson.isEmpty();
        if (!exercisesAvailable) {
            cardPractice.setAlpha(0.4f);
            cardPractice.setClickable(false);
        } else {
            cardPractice.setOnClickListener(v -> {
                Intent intent = new Intent(this, AiQuizActivity.class);
                intent.putExtra(AiQuizActivity.EXTRA_EXERCISES, exercisesJson);
                intent.putExtra(AiQuizActivity.EXTRA_SUBJECT, subject);
                startActivity(intent);
            });
        }

        if (wantFlashcards && flashcardCount > 0) {
            tvFlashcard.setVisibility(View.VISIBLE);
            tvFlashcard.setText("✅ " + flashcardCount + " flashcard"
                    + (flashcardCount == 1 ? "" : "s")
                    + " added to \"" + subject + "\" deck.");
        }
    }
}
