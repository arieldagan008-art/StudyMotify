package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT = "extra_subject";

    private String subject;
    private String currentUid;

    private TextView     tvCounter;
    private CardView     cvFlashcard;
    private LinearLayout layoutQuestion;
    private LinearLayout layoutAnswer;
    private TextView     tvQuestion;
    private TextView     tvAnswer;
    private Button       btnFlip;
    private Button       btnPrev;
    private Button       btnNext;
    private TextView     tvEmpty;

    private final List<Flashcard> cards = new ArrayList<>();
    private int     currentIndex = 0;
    private boolean showingAnswer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        if (subject == null) subject = "General";

        MaterialToolbar toolbar = findViewById(R.id.studyToolbar);
        toolbar.setTitle(subject);
        toolbar.setSubtitle("Study Mode");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvCounter     = findViewById(R.id.tv_study_counter);
        cvFlashcard   = findViewById(R.id.cv_flashcard);
        layoutQuestion = findViewById(R.id.layout_question_side);
        layoutAnswer   = findViewById(R.id.layout_answer_side);
        tvQuestion    = findViewById(R.id.tv_study_question);
        tvAnswer      = findViewById(R.id.tv_study_answer);
        btnFlip       = findViewById(R.id.btn_flip);
        btnPrev       = findViewById(R.id.btn_prev_card);
        btnNext       = findViewById(R.id.btn_next_card);
        tvEmpty       = findViewById(R.id.tv_study_empty);

        cvFlashcard.setOnClickListener(v -> flipCard());
        btnFlip.setOnClickListener(v -> flipCard());

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                showCard(currentIndex);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentIndex < cards.size() - 1) {
                currentIndex++;
                showCard(currentIndex);
            } else {
                Toast.makeText(this, "You've reached the last card!", Toast.LENGTH_SHORT).show();
            }
        });

        if (FirebaseHelper.getInstance().isLoggedIn()) {
            currentUid = FirebaseHelper.getInstance().getAuth().getUid();
        }

        loadCards();
    }

    // ─── Toolbar menu (+Add) ──────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.study_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.study_menu_add) {
            showAddCardDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── Load from Firebase (decks/{subject}) ─────────────────────────────────

    private void loadCards() {
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            onCardsLoaded();
            return;
        }

        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .child(subject)
                .addValueEventListener(new ValueEventListener() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        cards.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Flashcard card = child.getValue(Flashcard.class);
                            if (card != null) {
                                if (card.id == null) card.id = child.getKey();
                                cards.add(card);
                            }
                        }
                        onCardsLoaded();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudyActivity.this,
                                "Could not load cards: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        onCardsLoaded();
                    }
                });
    }

    private void onCardsLoaded() {
        if (cards.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            cvFlashcard.setVisibility(View.GONE);
            btnFlip.setVisibility(View.GONE);
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
            findViewById(R.id.tv_tap_hint).setVisibility(View.GONE);
            tvCounter.setText("0 / 0");
        } else {
            tvEmpty.setVisibility(View.GONE);
            cvFlashcard.setVisibility(View.VISIBLE);
            btnFlip.setVisibility(View.VISIBLE);
            btnPrev.setVisibility(View.VISIBLE);
            btnNext.setVisibility(View.VISIBLE);
            findViewById(R.id.tv_tap_hint).setVisibility(View.VISIBLE);
            currentIndex = 0;
            showCard(0);
        }
    }

    // ─── Card display ─────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private void showCard(int index) {
        if (index < 0 || index >= cards.size()) return;

        Flashcard card = cards.get(index);
        tvQuestion.setText(card.getQuestion());
        tvAnswer.setText(card.getAnswer());

        // Always start on question side
        showingAnswer = false;
        layoutQuestion.setVisibility(View.VISIBLE);
        layoutAnswer.setVisibility(View.GONE);
        btnFlip.setText("Flip");

        tvCounter.setText((index + 1) + " / " + cards.size());

        btnPrev.setAlpha(index == 0 ? 0.4f : 1.0f);
        btnNext.setAlpha(index == cards.size() - 1 ? 0.4f : 1.0f);
    }

    private void flipCard() {
        if (cards.isEmpty()) return;

        // Fade out current side
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(120);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                // Toggle sides
                showingAnswer = !showingAnswer;
                layoutQuestion.setVisibility(showingAnswer ? View.GONE  : View.VISIBLE);
                layoutAnswer.setVisibility(showingAnswer   ? View.VISIBLE : View.GONE);
                btnFlip.setText(showingAnswer ? "Flip Back" : "Flip");

                // Fade in new side
                AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
                fadeIn.setDuration(120);
                cvFlashcard.startAnimation(fadeIn);
            }
        });
        cvFlashcard.startAnimation(fadeOut);
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

        // Pre-fill current subject
        etSubject.setText(subject);

        new AlertDialog.Builder(this)
                .setTitle("Add Flashcard")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String sub      = etSubject.getText().toString().trim();
                    String question = etQuestion.getText().toString().trim();
                    String answer   = etAnswer.getText().toString().trim();

                    if (TextUtils.isEmpty(sub)) {
                        Toast.makeText(this, "Subject is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(question) || TextUtils.isEmpty(answer)) {
                        Toast.makeText(this, "Question and answer are required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pushCard(sub, question, answer);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pushCard(String sub, String question, String answer) {
        DatabaseReference deckRef = FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("decks")
                .child(sub);

        DatabaseReference cardRef = deckRef.push();
        Flashcard card = new Flashcard(cardRef.getKey(), question, answer, sub, currentUid);

        cardRef.setValue(card)
                .addOnSuccessListener(v ->
                        Toast.makeText(this, "Card added!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to add card.", Toast.LENGTH_SHORT).show());
    }
}
