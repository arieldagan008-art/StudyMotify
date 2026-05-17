package com.example.myapplication2;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AiQuizActivity extends AppCompatActivity {

    public static final String EXTRA_EXERCISES = "quiz_exercises";
    public static final String EXTRA_SUBJECT   = "quiz_subject";

    private final List<JSONObject> allExercises      = new ArrayList<>();
    private final List<JSONObject> filteredExercises = new ArrayList<>();
    private int currentIndex = 0;

    private TextView tvProgress, tvQuestion, tvAnswer, tvEmpty;
    private CardView cardQuestion;
    private Button   btnReveal, btnPrev, btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_quiz);

        String subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        MaterialToolbar toolbar = findViewById(R.id.quizToolbar);
        toolbar.setTitle(subject != null ? subject + " — Practice" : "Graded Practice");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvProgress   = findViewById(R.id.tv_quiz_progress);
        tvQuestion   = findViewById(R.id.tv_question);
        tvAnswer     = findViewById(R.id.tv_answer);
        tvEmpty      = findViewById(R.id.tv_quiz_empty);
        cardQuestion = findViewById(R.id.card_question);
        btnReveal    = findViewById(R.id.btn_reveal);
        btnPrev      = findViewById(R.id.btn_prev);
        btnNext      = findViewById(R.id.btn_next);

        String exercisesJson = getIntent().getStringExtra(EXTRA_EXERCISES);
        if (exercisesJson != null) {
            try {
                JSONArray arr = new JSONArray(exercisesJson);
                for (int i = 0; i < arr.length(); i++) {
                    allExercises.add(arr.getJSONObject(i));
                }
            } catch (Exception ignored) {}
        }

        Spinner spinner = findViewById(R.id.spinner_level);
        String[] levels = {"All Levels", "Easy", "Medium", "Hard"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, levels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                applyFilter(levels[pos]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnReveal.setOnClickListener(v -> {
            if (tvAnswer.getVisibility() == View.VISIBLE) {
                tvAnswer.setVisibility(View.GONE);
                btnReveal.setText("Reveal Answer");
            } else {
                tvAnswer.setVisibility(View.VISIBLE);
                btnReveal.setText("Hide Answer");
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                showQuestion();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentIndex < filteredExercises.size() - 1) {
                currentIndex++;
                showQuestion();
            }
        });

        applyFilter("All Levels");
    }

    private void applyFilter(String level) {
        filteredExercises.clear();
        for (JSONObject ex : allExercises) {
            String exLevel = ex.optString("level", "").trim();
            if ("All Levels".equals(level) || level.equalsIgnoreCase(exLevel)) {
                filteredExercises.add(ex);
            }
        }
        currentIndex = 0;
        showQuestion();
    }

    private void showQuestion() {
        boolean empty = filteredExercises.isEmpty();

        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        cardQuestion.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnReveal.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnPrev.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnNext.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvProgress.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvAnswer.setVisibility(View.GONE);
        btnReveal.setText("Reveal Answer");

        if (empty) return;

        JSONObject ex = filteredExercises.get(currentIndex);
        tvQuestion.setText(ex.optString("question", ""));
        tvAnswer.setText(ex.optString("answer", ""));
        tvProgress.setText("Question " + (currentIndex + 1) + " of " + filteredExercises.size());

        btnPrev.setEnabled(currentIndex > 0);
        btnPrev.setAlpha(currentIndex > 0 ? 1f : 0.4f);
        btnNext.setEnabled(currentIndex < filteredExercises.size() - 1);
        btnNext.setAlpha(currentIndex < filteredExercises.size() - 1 ? 1f : 0.4f);
    }
}
