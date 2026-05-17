package com.example.myapplication2;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiQuizActivity extends AppCompatActivity {

    public static final String EXTRA_EXERCISES = "quiz_exercises";
    public static final String EXTRA_SUBJECT   = "quiz_subject";

    private static final String TAG        = "AiQuizActivity";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    private final List<JSONObject> allExercises      = new ArrayList<>();
    private final List<JSONObject> filteredExercises = new ArrayList<>();
    private int      currentIndex   = 0;
    private volatile int evaluationSeq = 0;

    private TextView    tvProgress, tvQuestion, tvAnswer, tvEmpty, tvAiFeedback;
    private CardView    cardQuestion, cardFeedback;
    private Button      btnReveal, btnPrev, btnNext, btnCheckAnswer;
    private EditText    etUserAnswer;
    private ProgressBar pbEvaluation;

    private final ExecutorService evaluationExecutor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler        = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_quiz);

        String subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        MaterialToolbar toolbar = findViewById(R.id.quizToolbar);
        toolbar.setTitle(subject != null ? subject + " — Practice" : "Graded Practice");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvProgress     = findViewById(R.id.tv_quiz_progress);
        tvQuestion     = findViewById(R.id.tv_question);
        tvAnswer       = findViewById(R.id.tv_answer);
        tvEmpty        = findViewById(R.id.tv_quiz_empty);
        tvAiFeedback   = findViewById(R.id.tv_ai_feedback);
        cardQuestion   = findViewById(R.id.card_question);
        cardFeedback   = findViewById(R.id.card_feedback);
        btnReveal      = findViewById(R.id.btn_reveal);
        btnPrev        = findViewById(R.id.btn_prev);
        btnNext        = findViewById(R.id.btn_next);
        btnCheckAnswer = findViewById(R.id.btn_check_answer);
        etUserAnswer   = findViewById(R.id.et_user_answer);
        pbEvaluation   = findViewById(R.id.pb_evaluation);

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

        btnCheckAnswer.setOnClickListener(v -> {
            String userText = etUserAnswer.getText().toString().trim();
            if (userText.isEmpty()) {
                Toast.makeText(this, "Please type your answer first.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (filteredExercises.isEmpty()) return;
            JSONObject ex       = filteredExercises.get(currentIndex);
            String question     = ex.optString("question", "");
            String correctAnswer = ex.optString("answer", "");
            checkAnswerWithAi(question, correctAnswer, userText);
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

    private void resetEvaluationState() {
        evaluationSeq++;
        etUserAnswer.setText("");
        pbEvaluation.setVisibility(View.GONE);
        cardFeedback.setVisibility(View.GONE);
        tvAiFeedback.setText("");
        btnCheckAnswer.setEnabled(true);
    }

    private void showQuestion() {
        boolean empty = filteredExercises.isEmpty();

        resetEvaluationState();

        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        cardQuestion.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnReveal.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnPrev.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnNext.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvProgress.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnCheckAnswer.setVisibility(empty ? View.GONE : View.VISIBLE);
        etUserAnswer.setVisibility(empty ? View.GONE : View.VISIBLE);
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

    private void checkAnswerWithAi(String question, String correctAnswer, String userAnswer) {
        String apiKey = BuildConfig.GEMINI_API_KEY.trim();
        if (apiKey.isEmpty() || apiKey.startsWith("YOUR_")) {
            Toast.makeText(this, "API key not configured.", Toast.LENGTH_SHORT).show();
            return;
        }

        final int mySeq = ++evaluationSeq;

        // Dismiss keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }

        pbEvaluation.setVisibility(View.VISIBLE);
        cardFeedback.setVisibility(View.GONE);
        btnCheckAnswer.setEnabled(false);

        evaluationExecutor.execute(() -> {
            try {
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content",
                        "You are an advanced, supportive English AI Tutor. Analyze the student's " +
                        "answer against the correct answer for the given question. Your feedback " +
                        "must be strictly in English, detailed, and highly constructive. Do not " +
                        "just state the correct answer; actively contrast the student's input with " +
                        "the correct concept. Structure your response clearly using these exact " +
                        "headers:\n\n" +
                        "Verdict: (Correct / Partially Correct / Incorrect)\n\n" +
                        "Why you missed it: (Explain exactly why the student's answer is wrong or " +
                        "incomplete. For example, if they answered 'Water' for a question about " +
                        "fundamental building blocks, explain that water is a compound made of " +
                        "molecules, whereas atoms are the actual fundamental building blocks).\n\n" +
                        "Teacher's Explanation: (Provide a detailed elaboration on the correct " +
                        "concept to help them learn for next time).");

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content",
                        "Question: " + question + "\n" +
                        "Correct Answer: " + correctAnswer + "\n" +
                        "Student Answer: " + userAnswer);

                JSONArray messages = new JSONArray();
                messages.put(systemMsg);
                messages.put(userMsg);

                JSONObject bodyJson = new JSONObject();
                bodyJson.put("model", GROQ_MODEL);
                bodyJson.put("messages", messages);

                OkHttpClient client  = new OkHttpClient();
                MediaType mediaType  = MediaType.parse("application/json; charset=utf-8");
                RequestBody reqBody  = RequestBody.create(mediaType, bodyJson.toString());
                Request request = new Request.Builder()
                        .url("https://api.groq.com/openai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(reqBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Groq eval HTTP " + response.code());

                    if (evaluationSeq != mySeq) return;

                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> {
                            if (evaluationSeq != mySeq) return;
                            pbEvaluation.setVisibility(View.GONE);
                            btnCheckAnswer.setEnabled(true);
                            Toast.makeText(AiQuizActivity.this,
                                    "Evaluation failed (HTTP " + response.code() + ").",
                                    Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    JSONObject json = new JSONObject(responseBody);
                    String feedback = json
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    mainHandler.post(() -> {
                        if (evaluationSeq != mySeq) return;
                        pbEvaluation.setVisibility(View.GONE);
                        btnCheckAnswer.setEnabled(true);
                        tvAiFeedback.setText(feedback.trim());
                        cardFeedback.setVisibility(View.VISIBLE);
                    });
                }

            } catch (Throwable t) {
                Log.e(TAG, "Evaluation error", t);
                mainHandler.post(() -> {
                    if (evaluationSeq != mySeq) return;
                    pbEvaluation.setVisibility(View.GONE);
                    btnCheckAnswer.setEnabled(true);
                    Toast.makeText(AiQuizActivity.this,
                            "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
