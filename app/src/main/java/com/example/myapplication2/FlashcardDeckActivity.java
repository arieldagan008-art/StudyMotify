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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlashcardDeckActivity extends AppCompatActivity {

    private static final String[][] PRESET_SUBJECTS = {
        {"📖", "Biology"},
        {"🏛",  "History"},
        {"⚗",  "Chemistry"},
        {"🧠",  "Psychology"},
        {"📐",  "Math"},
        {"🗂",  "My Cards"},
    };

    private DeckAdapter             deckAdapter;
    private final Map<String, Integer> subjectMap = new LinkedHashMap<>();
    private String                  currentUid;

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
