package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CardViewActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT = "extra_subject";

    private TextView    tvSubject, tvCounter, tvQuestion, tvAnswer, tvEmpty;
    private CardView    cvAnswerContainer;
    private Button      btnShowAnswer, btnPrev, btnNext, btnShare;

    private final List<Flashcard> cards = new ArrayList<>();
    private int currentIndex = 0;
    private String subject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_card_view);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        if (subject == null) subject = "General";

        tvSubject        = findViewById(R.id.tv_card_subject);
        tvCounter        = findViewById(R.id.tv_card_counter);
        tvQuestion       = findViewById(R.id.tv_card_question);
        tvAnswer         = findViewById(R.id.tv_card_answer);
        tvEmpty          = findViewById(R.id.tv_empty_cards);
        cvAnswerContainer = findViewById(R.id.cv_answer_container);
        btnShowAnswer    = findViewById(R.id.btn_show_answer);
        btnPrev          = findViewById(R.id.btn_prev_card);
        btnNext          = findViewById(R.id.btn_next_card);
        btnShare         = findViewById(R.id.btn_share_community);

        tvSubject.setText(subject);

        findViewById(R.id.iv_back_arrow).setOnClickListener(v -> finish());

        btnShowAnswer.setOnClickListener(v -> toggleAnswer());

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

        btnShare.setOnClickListener(v -> shareCurrentCard());

        loadCards();
    }

    // ─── Load from Firebase + seed defaults ──────────────────────────────────

    private void loadCards() {
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            // Fall back to seed data if not logged in
            cards.addAll(getSeedCards(subject));
            onCardsLoaded();
            return;
        }

        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("flashcards")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        cards.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Flashcard card = child.getValue(Flashcard.class);
                            if (card != null && subject.equalsIgnoreCase(card.getSubject())) {
                                cards.add(card);
                            }
                        }
                        // If no personal cards exist for this subject, use seed data
                        if (cards.isEmpty()) {
                            cards.addAll(getSeedCards(subject));
                        }
                        onCardsLoaded();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(CardViewActivity.this,
                                "Could not load cards: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        cards.addAll(getSeedCards(subject));
                        onCardsLoaded();
                    }
                });
    }

    @SuppressLint("SetTextI18n")
    private void onCardsLoaded() {
        if (cards.isEmpty()) {
            // Show empty state
            tvEmpty.setVisibility(View.VISIBLE);
            tvQuestion.setVisibility(View.GONE);
            cvAnswerContainer.setVisibility(View.GONE);
            btnShowAnswer.setVisibility(View.GONE);
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
            btnShare.setVisibility(View.GONE);
            tvCounter.setText("0 / 0");
        } else {
            tvEmpty.setVisibility(View.GONE);
            tvQuestion.setVisibility(View.VISIBLE);
            btnShowAnswer.setVisibility(View.VISIBLE);
            btnPrev.setVisibility(View.VISIBLE);
            btnNext.setVisibility(View.VISIBLE);
            btnShare.setVisibility(View.VISIBLE);
            currentIndex = 0;
            showCard(0);
        }
    }

    // ─── Practice mode ────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private void showCard(int index) {
        if (index < 0 || index >= cards.size()) return;

        Flashcard card = cards.get(index);
        tvQuestion.setText(card.getQuestion());
        tvAnswer.setText(card.getAnswer());

        // Always hide answer when a new card is shown
        cvAnswerContainer.setVisibility(View.GONE);
        btnShowAnswer.setText("Show Answer");

        tvCounter.setText((index + 1) + " / " + cards.size());

        // Disable Prev/Next at boundaries
        btnPrev.setAlpha(index == 0 ? 0.4f : 1.0f);
        btnNext.setAlpha(index == cards.size() - 1 ? 0.4f : 1.0f);
    }

    private void toggleAnswer() {
        if (cvAnswerContainer.getVisibility() == View.VISIBLE) {
            cvAnswerContainer.setVisibility(View.GONE);
            btnShowAnswer.setText("Show Answer");
        } else {
            cvAnswerContainer.setVisibility(View.VISIBLE);
            btnShowAnswer.setText("Hide Answer");
        }
    }

    // ─── Community sharing ────────────────────────────────────────────────────

    private void shareCurrentCard() {
        if (cards.isEmpty()) return;

        Flashcard card = cards.get(currentIndex);
        String communityKey = subject.toLowerCase().replace(" ", "_");

        Map<String, Object> sharedCard = new HashMap<>();
        sharedCard.put("question",   card.getQuestion());
        sharedCard.put("answer",     card.getAnswer());
        sharedCard.put("subject",    card.getSubject());
        sharedCard.put("sharedBy",   FirebaseHelper.getInstance().isLoggedIn()
                ? FirebaseHelper.getInstance().getAuth().getUid()
                : "anonymous");
        sharedCard.put("timestamp",  System.currentTimeMillis());

        FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .child(communityKey)
                .child("sharedCards")
                .push()
                .setValue(sharedCard)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Card shared to the " + subject + " community!",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Share failed. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─── Seed / default flashcards ────────────────────────────────────────────

    private List<Flashcard> getSeedCards(String subject) {
        List<Flashcard> seeds = new ArrayList<>();
        switch (subject) {
            case "Biology":
                seeds.addAll(Arrays.asList(
                    new Flashcard(null, "What is the process by which plants make food?",
                            "Photosynthesis", "Biology", null),
                    new Flashcard(null, "What is the basic unit of life?",
                            "The cell", "Biology", null),
                    new Flashcard(null, "What molecule carries genetic information?",
                            "DNA (Deoxyribonucleic Acid)", "Biology", null),
                    new Flashcard(null, "What process do cells use to release energy from glucose?",
                            "Cellular respiration", "Biology", null),
                    new Flashcard(null, "What is natural selection?",
                            "The process where organisms with favorable traits survive and reproduce more",
                            "Biology", null)
                ));
                break;
            case "History":
                seeds.addAll(Arrays.asList(
                    new Flashcard(null, "When did World War II end?",
                            "1945", "History", null),
                    new Flashcard(null, "Who was the first President of the United States?",
                            "George Washington", "History", null),
                    new Flashcard(null, "What year did the French Revolution begin?",
                            "1789", "History", null),
                    new Flashcard(null, "What empire was ruled by Julius Caesar?",
                            "The Roman Empire", "History", null),
                    new Flashcard(null, "What event triggered World War I?",
                            "The assassination of Archduke Franz Ferdinand in 1914",
                            "History", null)
                ));
                break;
            case "Chemistry":
                seeds.addAll(Arrays.asList(
                    new Flashcard(null, "What is the chemical symbol for water?",
                            "H₂O", "Chemistry", null),
                    new Flashcard(null, "What is the atomic number of Carbon?",
                            "6", "Chemistry", null),
                    new Flashcard(null, "What is the pH of a neutral solution?",
                            "7", "Chemistry", null),
                    new Flashcard(null, "What is the most abundant gas in Earth's atmosphere?",
                            "Nitrogen (N₂), about 78%", "Chemistry", null),
                    new Flashcard(null, "What type of bond involves the sharing of electrons?",
                            "Covalent bond", "Chemistry", null)
                ));
                break;
            case "Psychology":
                seeds.addAll(Arrays.asList(
                    new Flashcard(null, "Who is considered the father of psychoanalysis?",
                            "Sigmund Freud", "Psychology", null),
                    new Flashcard(null, "What is classical conditioning?",
                            "Learning through association between a stimulus and a response (Pavlov)",
                            "Psychology", null),
                    new Flashcard(null, "What is Maslow's Hierarchy of Needs?",
                            "A motivational theory with 5 levels: Physiological, Safety, Love, Esteem, Self-Actualization",
                            "Psychology", null),
                    new Flashcard(null, "What is the difference between classical and operant conditioning?",
                            "Classical: association between stimuli. Operant: behavior shaped by rewards/punishments",
                            "Psychology", null),
                    new Flashcard(null, "What is cognitive dissonance?",
                            "The discomfort felt when holding two conflicting beliefs or behaviors",
                            "Psychology", null)
                ));
                break;
            case "Math":
                seeds.addAll(Arrays.asList(
                    new Flashcard(null, "What is the Pythagorean theorem?",
                            "a² + b² = c², where c is the hypotenuse of a right triangle",
                            "Math", null),
                    new Flashcard(null, "What is the derivative of x²?",
                            "2x", "Math", null),
                    new Flashcard(null, "What is the sum of angles in a triangle?",
                            "180 degrees", "Math", null),
                    new Flashcard(null, "What does π (pi) approximately equal?",
                            "3.14159…", "Math", null),
                    new Flashcard(null, "What is a prime number?",
                            "A number greater than 1 that has no divisors other than 1 and itself",
                            "Math", null)
                ));
                break;
            default:
                // My Cards or unknown — return empty so user is prompted to create their own
                break;
        }
        return seeds;
    }
}
