package com.example.myapplication2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class CardsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cards);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.iv_back_arrow).setOnClickListener(v -> finish());

        // Subject grid — each card launches practice mode for that subject
        wireSubjectCard(R.id.cv_subject_history,    "History");
        wireSubjectCard(R.id.cv_subject_biology,    "Biology");
        wireSubjectCard(R.id.cv_subject_chemistry,  "Chemistry");
        wireSubjectCard(R.id.cv_subject_psychology, "Psychology");
        wireSubjectCard(R.id.cv_subject_math,       "Math");
        wireSubjectCard(R.id.cv_subject_custom,     "My Cards");

        Button btnAdd = findViewById(R.id.btn_add_card);
        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddFlashcardActivity.class)));
    }

    private void wireSubjectCard(int cardViewId, String subject) {
        CardView card = findViewById(cardViewId);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, CardViewActivity.class);
            intent.putExtra(CardViewActivity.EXTRA_SUBJECT, subject);
            startActivity(intent);
        });
    }
}
