package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;

import com.example.myapplication2.BuildConfig;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlashcardDeckActivity extends AppCompatActivity {

    private static final String[][] PRESET_SUBJECTS = {
        {"📖", "Biology"},
        {"🏛",  "History"},
        {"⚗",  "Chemistry"},
        {"🧠",  "Psychology"},
        {"📐",  "Math"},
        {"🗂",  "My Cards"},
    };

    private static final String TAG            = "FlashcardDeckActivity";
    private static final String GROQ_MODEL     = "llama-3.1-8b-instant";

    private DeckAdapter             deckAdapter;
    private final Map<String, Integer> subjectMap = new LinkedHashMap<>();
    private String                  currentUid;

    private final ExecutorService aiExecutor  = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_deck);

        MaterialToolbar toolbar = findViewById(R.id.deckToolbar);
        toolbar.setTitle("Flashcard Decks");
        toolbar.setSubtitle("Tap a subject to study");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        RecyclerView rvSubjects = findViewById(R.id.rv_subjects);
        rvSubjects.setLayoutManager(new GridLayoutManager(this, 2));

        deckAdapter = new DeckAdapter(
                this,
                subjectMap,
                subject -> {
                    // Open study mode
                    Intent intent = new Intent(this, StudyActivity.class);
                    intent.putExtra(StudyActivity.EXTRA_SUBJECT, subject);
                    startActivity(intent);
                },
                this::confirmDeleteDeck,
                this::showShareDeckDialog
        );
        rvSubjects.setAdapter(deckAdapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_card);
        fab.setOnClickListener(v -> showAddCardDialog());

        ExtendedFloatingActionButton fabAi = findViewById(R.id.fab_generate_ai);
        fabAi.setOnClickListener(v -> showGenerateWithAiDialog());

        if (FirebaseHelper.getInstance().isLoggedIn()) {
            currentUid = FirebaseHelper.getInstance().getAuth().getUid();
            loadDecks();
        } else {
            loadPresets(null);
        }
    }

    // ─── Firebase: load deck counts ───────────────────────────────────────────

    private void loadDecks() {
        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Map<String, Integer> counts = new LinkedHashMap<>();
                        for (DataSnapshot subjectSnap : snapshot.getChildren()) {
                            String key = subjectSnap.getKey();
                            if (key != null) {
                                counts.put(key, (int) subjectSnap.getChildrenCount());
                            }
                        }
                        loadPresets(counts);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadPresets(null);
                    }
                });
    }

    private void loadPresets(Map<String, Integer> firebaseCounts) {
        subjectMap.clear();
        for (String[] preset : PRESET_SUBJECTS) {
            String subject = preset[1];
            int count = (firebaseCounts != null && firebaseCounts.containsKey(subject))
                    ? firebaseCounts.get(subject) : 0;
            subjectMap.put(subject, count);
        }
        if (firebaseCounts != null) {
            for (Map.Entry<String, Integer> entry : firebaseCounts.entrySet()) {
                if (!subjectMap.containsKey(entry.getKey())) {
                    subjectMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        deckAdapter.refreshData();
    }

    // ─── Delete deck ──────────────────────────────────────────────────────────

    private void confirmDeleteDeck(String subject) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Deck")
                .setMessage("Are you sure you want to delete the \"" + subject
                        + "\" deck? All cards will be permanently removed.")
                .setPositiveButton("Delete", (dialog, which) -> deleteDeck(subject))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteDeck(String subject) {
        if (currentUid == null) return;
        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .child(subject)
                .removeValue()
                .addOnSuccessListener(v ->
                        Toast.makeText(this, "\"" + subject + "\" deck deleted.",
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Delete failed. Try again.",
                                Toast.LENGTH_SHORT).show());
        // loadDecks() ValueEventListener will automatically refresh the grid
    }

    // ─── Share deck to community ──────────────────────────────────────────────

    private void showShareDeckDialog(String subject) {
        if (currentUid == null) {
            Toast.makeText(this, "Please sign in to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load the user's communities, then show the picker
        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("communities")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> communityIds   = new ArrayList<>();
                        List<String> communityNames = new ArrayList<>();

                        for (DataSnapshot child : snapshot.getChildren()) {
                            String id   = child.getKey();
                            String name = child.getValue(String.class);
                            if (id != null) {
                                communityIds.add(id);
                                communityNames.add(name != null ? name : id);
                            }
                        }

                        if (communityIds.isEmpty()) {
                            Toast.makeText(FlashcardDeckActivity.this,
                                    "You haven't joined any communities yet.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String[] namesArray = communityNames.toArray(new String[0]);
                        new AlertDialog.Builder(FlashcardDeckActivity.this)
                                .setTitle("Share \"" + subject + "\" deck to...")
                                .setItems(namesArray, (dialog, which) -> {
                                    String communityId   = communityIds.get(which);
                                    String communityName = communityNames.get(which);
                                    copyDeckToCommunity(subject, communityId, communityName);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(FlashcardDeckActivity.this,
                                "Could not load communities.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void copyDeckToCommunity(String subject, String communityId, String communityName) {
        DatabaseReference sourceRef = FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .child(subject);

        sourceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(FlashcardDeckActivity.this,
                            "No cards to share.", Toast.LENGTH_SHORT).show();
                    return;
                }

                DatabaseReference destRef = FirebaseHelper.getInstance()
                        .getDatabase()
                        .getReference("communities")
                        .child(communityId)
                        .child("sharedDecks")
                        .child(subject);

                for (DataSnapshot cardSnap : snapshot.getChildren()) {
                    Flashcard card = cardSnap.getValue(Flashcard.class);
                    if (card != null) {
                        String pushKey = destRef.push().getKey();
                        if (pushKey != null) destRef.child(pushKey).setValue(card);
                    }
                }

                Toast.makeText(FlashcardDeckActivity.this,
                        "\"" + subject + "\" deck shared to " + communityName + "!",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(FlashcardDeckActivity.this,
                        "Share failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─── Add Card dialog (FAB) ────────────────────────────────────────────────

    private void showAddCardDialog() {
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            Toast.makeText(this, "Please sign in to add cards.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_flashcard, null);
        EditText etSubject  = dialogView.findViewById(R.id.et_flashcard_subject);
        EditText etQuestion = dialogView.findViewById(R.id.et_flashcard_question);
        EditText etAnswer   = dialogView.findViewById(R.id.et_flashcard_answer);

        new AlertDialog.Builder(this)
                .setTitle("Add Flashcard")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String subject  = etSubject.getText().toString().trim();
                    String question = etQuestion.getText().toString().trim();
                    String answer   = etAnswer.getText().toString().trim();
                    if (TextUtils.isEmpty(subject)) {
                        Toast.makeText(this, "Subject is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(question) || TextUtils.isEmpty(answer)) {
                        Toast.makeText(this, "Question and answer are required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pushCard(subject, question, answer);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pushCard(String subject, String question, String answer) {
        DatabaseReference cardRef = FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .child(subject)
                .push();

        Flashcard card = new Flashcard(cardRef.getKey(), question, answer, subject, currentUid);
        cardRef.setValue(card)
                .addOnSuccessListener(v ->
                        Toast.makeText(this, "Card added to " + subject + "!",
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to add card.", Toast.LENGTH_SHORT).show());
    }

    // ─── Generate with AI ────────────────────────────────────────────────────

    private void showGenerateWithAiDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_generate_flashcards, null);
        EditText etSubject    = dialogView.findViewById(R.id.et_ai_subject);
        EditText etText       = dialogView.findViewById(R.id.et_ai_text);
        CheckBox cbSummary    = dialogView.findViewById(R.id.cb_summary);
        CheckBox cbExercises  = dialogView.findViewById(R.id.cb_exercises);
        CheckBox cbFlashcards = dialogView.findViewById(R.id.cb_flashcards);

        new AlertDialog.Builder(this)
                .setTitle("AI Learning Suite")
                .setView(dialogView)
                .setPositiveButton("Generate", (dialog, which) -> {
                    String subject = etSubject.getText().toString().trim();
                    String text    = etText.getText().toString().trim();

                    if (TextUtils.isEmpty(subject)) {
                        Toast.makeText(this, "Subject is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(text)) {
                        Toast.makeText(this, "Please paste some study text.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean wantSummary    = cbSummary.isChecked();
                    boolean wantExercises  = cbExercises.isChecked();
                    boolean wantFlashcards = cbFlashcards.isChecked();
                    if (!wantSummary && !wantExercises && !wantFlashcards) {
                        Toast.makeText(this, "Please select at least one output type.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    generateWithAi(subject, text, wantSummary, wantExercises, wantFlashcards);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generateWithAi(String subject, String studyText,
                                 boolean wantSummary, boolean wantExercises, boolean wantFlashcards) {
        String apiKey = BuildConfig.GEMINI_API_KEY.trim();
        if (apiKey.isEmpty()
                || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")
                || apiKey.startsWith("YOUR_")) {
            Toast.makeText(this, "Please add your API key in local.properties.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage("✨ Generating with AI…")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Analyze the following study text and produce a JSON object with ONLY these keys:\n");
        if (wantSummary) {
            promptBuilder.append("- \"summary\": \"<concise paragraph summarizing the key points>\"\n");
        }
        if (wantExercises) {
            promptBuilder.append("- \"exercises\": array of 3-5 objects, each with keys: " +
                    "\"question\" (string), \"level\" (\"Easy\", \"Medium\", or \"Hard\"), \"answer\" (string)\n");
        }
        if (wantFlashcards) {
            promptBuilder.append("- \"flashcards\": array of 3-5 objects, each with keys: " +
                    "\"front\" (string), \"back\" (string)\n");
        }
        promptBuilder.append("\nReturn ONLY a raw JSON object — no markdown, no code fences, no explanation.\n\n");
        promptBuilder.append("Study text:\n").append(studyText);

        String prompt = promptBuilder.toString();

        aiExecutor.execute(() -> {
            try {
                String url = "https://api.groq.com/openai/v1/chat/completions";

                JSONObject messageObj = new JSONObject();
                messageObj.put("role", "user");
                messageObj.put("content", prompt);

                JSONArray messages = new JSONArray();
                messages.put(messageObj);

                JSONObject requestBodyJson = new JSONObject();
                requestBodyJson.put("model", GROQ_MODEL);
                requestBodyJson.put("messages", messages);

                Log.d(TAG, "Groq REST POST → " + url + " model=" + GROQ_MODEL);

                OkHttpClient client = new OkHttpClient();
                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(mediaType, requestBodyJson.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Groq HTTP " + response.code() + ": " + responseBody);

                    if (!response.isSuccessful()) {
                        String errorMsg = "HTTP " + response.code() + "\n\n" + responseBody;
                        mainHandler.post(() -> {
                            loadingDialog.dismiss();
                            new AlertDialog.Builder(FlashcardDeckActivity.this)
                                    .setTitle("AI Error Details")
                                    .setMessage(errorMsg)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                        return;
                    }

                    JSONObject json = new JSONObject(responseBody);
                    String rawContent = json
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    Log.d(TAG, "Groq raw content: " + rawContent);

                    String cleaned = rawContent.trim();
                    if (cleaned.startsWith("```")) {
                        cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "")
                                         .replaceAll("```$", "")
                                         .trim();
                    }

                    JSONObject result = new JSONObject(cleaned);

                    int flashcardCount = 0;
                    if (wantFlashcards && result.has("flashcards")) {
                        List<Flashcard> cards = parseFlashcardsFromJson(
                                result.getJSONArray("flashcards"), subject);
                        flashcardCount = cards.size();
                        for (Flashcard card : cards) {
                            DatabaseReference ref = FirebaseHelper.getInstance()
                                    .getCurrentUserRef()
                                    .child("decks").child(subject).push();
                            card.id = ref.getKey();
                            ref.setValue(card);
                        }
                    }

                    final int finalCount = flashcardCount;
                    final JSONObject finalResult = result;
                    mainHandler.post(() -> {
                        loadingDialog.dismiss();
                        Intent intent = new Intent(
                                FlashcardDeckActivity.this, AiResultsActivity.class);
                        intent.putExtra(AiResultsActivity.EXTRA_SUBJECT, subject);
                        intent.putExtra(AiResultsActivity.EXTRA_WANT_SUMMARY,    wantSummary);
                        intent.putExtra(AiResultsActivity.EXTRA_WANT_EXERCISES,  wantExercises);
                        intent.putExtra(AiResultsActivity.EXTRA_WANT_FLASHCARDS, wantFlashcards);
                        intent.putExtra(AiResultsActivity.EXTRA_FLASHCARD_COUNT, finalCount);
                        if (wantSummary && finalResult.has("summary")) {
                            intent.putExtra(AiResultsActivity.EXTRA_SUMMARY,
                                    finalResult.optString("summary", ""));
                        }
                        if (wantExercises && finalResult.has("exercises")) {
                            JSONArray ex = finalResult.optJSONArray("exercises");
                            intent.putExtra(AiResultsActivity.EXTRA_EXERCISES,
                                    ex != null ? ex.toString() : "");
                        }
                        startActivity(intent);
                    });
                }

            } catch (Throwable t) {
                t.printStackTrace();
                Log.e(TAG, "AI generation error", t);
                mainHandler.post(() -> {
                    loadingDialog.dismiss();
                    new AlertDialog.Builder(FlashcardDeckActivity.this)
                            .setTitle("AI Error Details")
                            .setMessage(t.getClass().getSimpleName() + "\n\n" + t.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private List<Flashcard> parseFlashcardsFromJson(JSONArray arr, String subject) {
        List<Flashcard> result = new ArrayList<>();
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj   = arr.getJSONObject(i);
                String     front = obj.optString("front",    "").trim();
                String     back  = obj.optString("back",     "").trim();
                if (front.isEmpty()) front = obj.optString("question", "").trim();
                if (back.isEmpty())  back  = obj.optString("answer",   "").trim();
                if (!front.isEmpty() && !back.isEmpty()) {
                    result.add(new Flashcard(null, front, back, subject, currentUid));
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    // ─── Inner adapter ────────────────────────────────────────────────────────

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    interface OnSubjectClickListener  { void onSubjectClick(String subject); }
    interface OnSubjectDeleteListener { void onSubjectDelete(String subject); }
    interface OnSubjectShareListener  { void onSubjectShare(String subject); }

    static class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.DeckVH> {

        private final Context                context;
        private final Map<String, Integer>   subjectMap;
        private final OnSubjectClickListener  clickListener;
        private final OnSubjectDeleteListener deleteListener;
        private final OnSubjectShareListener  shareListener;
        private final List<String>            subjects = new ArrayList<>();

        private static final Map<String, String> ICONS = new LinkedHashMap<>();
        static {
            ICONS.put("Biology",    "📖");
            ICONS.put("History",    "🏛");
            ICONS.put("Chemistry",  "⚗");
            ICONS.put("Psychology", "🧠");
            ICONS.put("Math",       "📐");
            ICONS.put("My Cards",   "🗂");
        }

        DeckAdapter(Context context,
                    Map<String, Integer>   subjectMap,
                    OnSubjectClickListener  clickListener,
                    OnSubjectDeleteListener deleteListener,
                    OnSubjectShareListener  shareListener) {
            this.context        = context;
            this.subjectMap     = subjectMap;
            this.clickListener  = clickListener;
            this.deleteListener = deleteListener;
            this.shareListener  = shareListener;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void refreshData() {
            subjects.clear();
            subjects.addAll(subjectMap.keySet());
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DeckVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context)
                    .inflate(R.layout.item_subject_tile, parent, false);
            return new DeckVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull DeckVH holder, int position) {
            String subject = subjects.get(position);
            int    count   = subjectMap.getOrDefault(subject, 0);
            String icon    = ICONS.getOrDefault(subject, "📚");

            holder.tvIcon.setText(icon);
            holder.tvName.setText(subject);
            holder.tvCount.setText(count == 1 ? "1 card" : count + " cards");

            holder.itemView.setOnClickListener(v -> clickListener.onSubjectClick(subject));
            holder.btnDelete.setOnClickListener(v -> deleteListener.onSubjectDelete(subject));
            holder.btnShare.setOnClickListener(v -> shareListener.onSubjectShare(subject));
        }

        @Override
        public int getItemCount() { return subjects.size(); }

        static class DeckVH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvName, tvCount;
            android.widget.Button btnDelete, btnShare;

            DeckVH(@NonNull View itemView) {
                super(itemView);
                tvIcon    = itemView.findViewById(R.id.tv_subject_icon);
                tvName    = itemView.findViewById(R.id.tv_subject_name);
                tvCount   = itemView.findViewById(R.id.tv_card_count);
                btnDelete = itemView.findViewById(R.id.btn_delete_deck);
                btnShare  = itemView.findViewById(R.id.btn_share_deck);
            }
        }
    }
}
