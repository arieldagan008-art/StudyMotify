package com.example.myapplication2;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DatabaseReference;

public class AddFlashcardActivity extends AppCompatActivity {

    private EditText etSubject, etQuestion, etAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_flashcard);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etSubject  = findViewById(R.id.et_subject);
        etQuestion = findViewById(R.id.et_question);
        etAnswer   = findViewById(R.id.et_answer);

        // Pre-fill subject if launched from a subject card
        String preSubject = getIntent().getStringExtra(CardViewActivity.EXTRA_SUBJECT);
        if (!TextUtils.isEmpty(preSubject)) {
            etSubject.setText(preSubject);
        }

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        Button btnSave = findViewById(R.id.btn_save_card);
        btnSave.setOnClickListener(v -> saveCard());
    }

    private void saveCard() {
        String subject  = etSubject.getText().toString().trim();
        String question = etQuestion.getText().toString().trim();
        String answer   = etAnswer.getText().toString().trim();

        if (TextUtils.isEmpty(subject)) {
            etSubject.setError("Subject is required");
            return;
        }
        if (TextUtils.isEmpty(question)) {
            etQuestion.setError("Question is required");
            return;
        }
        if (TextUtils.isEmpty(answer)) {
            etAnswer.setError("Answer is required");
            return;
        }

        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseHelper.getInstance().getAuth().getUid();
        DatabaseReference ref = FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("flashcards")
                .push();

        Flashcard card = new Flashcard(ref.getKey(), question, answer, subject, uid);

        ref.setValue(card).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Flashcard saved!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
