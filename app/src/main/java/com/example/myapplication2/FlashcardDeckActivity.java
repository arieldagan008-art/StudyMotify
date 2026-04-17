package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlashcardDeckActivity extends AppCompatActivity {

    // Preset subject tiles with emoji icons
    private static final String[][] PRESET_SUBJECTS = {
        {"📖", "Biology"},
        {"🏛", "History"},
        {"⚗", "Chemistry"},
        {"🧠", "Psychology"},
        {"📐", "Math"},
        {"🗂", "My Cards"},
    };

    private RecyclerView     rvSubjects;
    private DeckAdapter      deckAdapter;

    // subject → card count (LinkedHashMap preserves insertion order)
    private final Map<String, Integer> subjectMap = new LinkedHashMap<>();

    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_deck);

        MaterialToolbar toolbar = findViewById(R.id.deckToolbar);
        toolbar.setTitle("Flashcard Decks");
        toolbar.setSubtitle("Tap a subject to study");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvSubjects = findViewById(R.id.rv_subjects);
        rvSubjects.setLayoutManager(new GridLayoutManager(this, 2));
        deckAdapter = new DeckAdapter(this, subjectMap, subject -> {
            Intent intent = new Intent(this, StudyActivity.class);
            intent.putExtra(StudyActivity.EXTRA_SUBJECT, subject);
            startActivity(intent);
        });
        rvSubjects.setAdapter(deckAdapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_card);
        fab.setOnClickListener(v -> showAddCardDialog());

        if (FirebaseHelper.getInstance().isLoggedIn()) {
            currentUid = FirebaseHelper.getInstance().getAuth().getUid();
            loadDecks();
        } else {
            loadPresets(null);
        }
    }

    // ─── Load card counts from Firebase ──────────────────────────────────────

    private void loadDecks() {
        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .addValueEventListener(new ValueEventListener() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Map<String, Integer> firebaseCounts = new LinkedHashMap<>();
                        for (DataSnapshot subjectSnap : snapshot.getChildren()) {
                            String subject = subjectSnap.getKey();
                            if (subject != null) {
                                firebaseCounts.put(subject, (int) subjectSnap.getChildrenCount());
                            }
                        }
                        loadPresets(firebaseCounts);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadPresets(null);
                    }
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadPresets(Map<String, Integer> firebaseCounts) {
        subjectMap.clear();
        for (String[] preset : PRESET_SUBJECTS) {
            String subject = preset[1];
            int count = (firebaseCounts != null && firebaseCounts.containsKey(subject))
                    ? firebaseCounts.get(subject) : 0;
            subjectMap.put(subject, count);
        }
        // Append any custom subjects not in presets
        if (firebaseCounts != null) {
            for (Map.Entry<String, Integer> entry : firebaseCounts.entrySet()) {
                if (!subjectMap.containsKey(entry.getKey())) {
                    subjectMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        deckAdapter.refreshData();
    }

    // ─── Add Card dialog ──────────────────────────────────────────────────────

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
        DatabaseReference deckRef = FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .child(subject);

        DatabaseReference cardRef = deckRef.push();
        Flashcard card = new Flashcard(cardRef.getKey(), question, answer, subject, currentUid);

        cardRef.setValue(card)
                .addOnSuccessListener(v ->
                        Toast.makeText(this, "Card added to " + subject + "!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to add card.", Toast.LENGTH_SHORT).show());
    }

    // ─── Deck grid adapter ────────────────────────────────────────────────────

    interface OnSubjectClickListener {
        void onSubjectClick(String subject);
    }

    static class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.DeckVH> {

        private final Context               context;
        private final Map<String, Integer>  subjectMap;
        private final OnSubjectClickListener listener;
        private final List<String>          subjects;

        // Icon mapping — emoji keyed by subject name
        private static final Map<String, String> ICONS = new LinkedHashMap<>();
        static {
            ICONS.put("Biology",    "📖");
            ICONS.put("History",    "🏛");
            ICONS.put("Chemistry",  "⚗");
            ICONS.put("Psychology", "🧠");
            ICONS.put("Math",       "📐");
            ICONS.put("My Cards",   "🗂");
        }

        DeckAdapter(Context context, Map<String, Integer> subjectMap,
                    OnSubjectClickListener listener) {
            this.context    = context;
            this.subjectMap = subjectMap;
            this.listener   = listener;
            this.subjects   = new ArrayList<>(subjectMap.keySet());
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

            holder.itemView.setOnClickListener(v -> listener.onSubjectClick(subject));
        }

        @Override
        public int getItemCount() { return subjects.size(); }

        static class DeckVH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvName, tvCount;
            DeckVH(@NonNull View itemView) {
                super(itemView);
                tvIcon  = itemView.findViewById(R.id.tv_subject_icon);
                tvName  = itemView.findViewById(R.id.tv_subject_name);
                tvCount = itemView.findViewById(R.id.tv_card_count);
            }
        }
    }
}
