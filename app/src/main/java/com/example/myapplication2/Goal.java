package com.example.myapplication2;

public class Goal implements java.io.Serializable {
    // 1. משתני המחלקה
    public String id;
    public String goalName;
    public String category;
    public String dueDate;
    public String details;
    public boolean reminder;
    public String time;
    public long timestamp;
    public boolean isCompleted;
    public int totalUnits;
    public int completedUnits;
    public long deadlineDate;   // epoch ms — used for daily target math
    public String unitLabel;    // e.g. "pages", "chapters", "videos"
    public int difficulty;           // 1 = Easy, 2 = Medium, 3 = Hard
    public long createdAtTimestamp;  // epoch ms when the goal was first created

    // 2. בנאי ריק (Empty Constructor)
    // חובה עבור Firebase כדי שיוכל להמיר את הנתונים מהענן לאובייקט Java
    public Goal() {
    }

    // 3. בנאי מלא (Constructor)
    // משמש את AddGoalActivity בעת יצירת מטרה חדשה ושמירתה
    public Goal(String id, String goalName, String category, String dueDate, String details, boolean reminder, String time, long timestamp) {
        this.id = id;
        this.goalName = goalName;
        this.category = category;
        this.dueDate = dueDate;
        this.details = details;
        this.reminder = reminder;
        this.time = time;
        this.timestamp = timestamp;
    }

    // 4. פונקציות גישה (Getters)
    // אלו הפונקציות שה-GoalsAdapter משתמש בהן כדי להציג את הטקסט

    public String getGoalName() {
        return goalName;
    }

    public String getCategory() {
        return category;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getDetails() {
        return details;
    }

    public String getId() {
        return id;
    }

    public boolean isReminder() {
        return reminder;
    }

    public String getTime() {
        return time;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getCompletedUnits() {
        return completedUnits;
    }

    public long getDeadlineDate() {
        return deadlineDate;
    }

    public String getUnitLabel() {
        return unitLabel != null && !unitLabel.isEmpty() ? unitLabel : "units";
    }

    /** Returns difficulty clamped to 1–3; defaults to 1 (Easy) if not set. */
    public int getDifficulty() {
        return (difficulty >= 1 && difficulty <= 3) ? difficulty : 1;
    }
}