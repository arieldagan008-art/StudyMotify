package com.example.myapplication2;

import java.util.Locale;

/** Represents one block of study time in a generated daily schedule. */
public class ScheduleItem {

    public String goalId;
    public String goalName;
    public int    startHour;         // 8 – 22
    public int    startMinute;       // 0 or 15 or 30 or 45
    public int    durationMinutes;   // length of the session
    public int    unitsPlanned;      // units the user is expected to complete
    public double minutesPerUnit;    // velocity used to estimate duration
    public boolean isFixedEvent;     // true = placed from Goal.isFixedTime; no units/velocity

    public ScheduleItem() {}

    public String getStartTimeStr() {
        return String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute);
    }

    public String getEndTimeStr() {
        int totalEnd = startHour * 60 + startMinute + durationMinutes;
        int h = totalEnd / 60;
        int m = totalEnd % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    public String getDurationLabel() {
        if (durationMinutes < 60) return durationMinutes + " min";
        int h = durationMinutes / 60;
        int m = durationMinutes % 60;
        return m == 0
                ? h + " hr"
                : h + " hr " + m + " min";
    }
}
