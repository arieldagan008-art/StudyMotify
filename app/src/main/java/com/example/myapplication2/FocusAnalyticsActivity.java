package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;

public class FocusAnalyticsActivity extends AppCompatActivity {

    private HourlyBarChartView barChartView;
    private TextView tvInsight, tvTotalLogged, tvTotalSessions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_focus_analytics);

        MaterialToolbar toolbar = findViewById(R.id.analyticsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        barChartView    = findViewById(R.id.barChartView);
        tvInsight       = findViewById(R.id.tv_insight);
        tvTotalLogged   = findViewById(R.id.tv_total_logged);
        tvTotalSessions = findViewById(R.id.tv_total_sessions);

        loadProgressLogs();
    }

    private void loadProgressLogs() {
        if (!FirebaseHelper.getInstance().isLoggedIn()) {
            tvInsight.setText("Please log in to see your analytics.");
            return;
        }

        FirebaseHelper.getInstance()
                .getCurrentUserRef()
                .child("progressLogs")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int[] hourlyUnits = new int[24];
                        int totalUnits    = 0;
                        int totalSessions = 0;

                        for (DataSnapshot entry : snapshot.getChildren()) {
                            Long tsLong  = entry.child("timestamp").getValue(Long.class);
                            Long amtLong = entry.child("amount").getValue(Long.class);
                            if (tsLong == null || amtLong == null) continue;

                            int amount = amtLong.intValue();
                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(tsLong);
                            int hour = cal.get(Calendar.HOUR_OF_DAY);

                            hourlyUnits[hour] += amount;
                            totalUnits        += amount;
                            totalSessions++;
                        }

                        barChartView.setData(hourlyUnits);
                        tvTotalLogged.setText(String.valueOf(totalUnits));
                        tvTotalSessions.setText(String.valueOf(totalSessions));
                        tvInsight.setText(buildInsight(hourlyUnits, totalUnits));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvInsight.setText("Could not load data: " + error.getMessage());
                    }
                });
    }

    /** Finds the 2-hour peak window and returns a Smart Insight string. */
    private String buildInsight(int[] hourlyUnits, int totalUnits) {
        if (totalUnits == 0) {
            return "No progress logged yet. Start logging to see your peak focus hours!";
        }

        // Find the hour with the highest units
        int peakHour = 0;
        for (int i = 1; i < 24; i++) {
            if (hourlyUnits[i] > hourlyUnits[peakHour]) peakHour = i;
        }

        // Build peak window label (e.g. "14:00 – 16:00")
        int windowEnd = Math.min(peakHour + 2, 23);
        String startLabel = String.format("%02d:00", peakHour);
        String endLabel   = String.format("%02d:00", windowEnd);

        // How much of total comes from peak + adjacent hour?
        int peakWindowUnits = hourlyUnits[peakHour];
        if (peakHour + 1 < 24) peakWindowUnits += hourlyUnits[peakHour + 1];

        // Build multiplier text
        String multiplierText = "";
        if (totalUnits > 0) {
            double fraction = (double) peakWindowUnits / totalUnits;
            if (fraction >= 0.5) {
                multiplierText = " You complete over half your work during this window!";
            } else if (fraction >= 0.3) {
                multiplierText = " You complete about " + Math.round(fraction * 100) + "% of your work during this time.";
            } else {
                multiplierText = " Your focus is spread fairly evenly throughout the day.";
            }
        }

        return "Your peak productivity is between " + startLabel + " and " + endLabel + "." + multiplierText;
    }
}
