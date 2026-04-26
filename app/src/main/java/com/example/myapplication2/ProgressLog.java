package com.example.myapplication2;

/** Firebase-serializable model for a single study session log entry. */
public class ProgressLog {

    public String goalId;
    public String goalName;
    public int    amount;        // units completed in this session
    public int    actualMinutes; // minutes the session actually took (0 = not recorded)
    public long   timestamp;

    /** Required for Firebase deserialization. */
    public ProgressLog() {}

    public ProgressLog(String goalId, String goalName, int amount,
                       int actualMinutes, long timestamp) {
        this.goalId        = goalId;
        this.goalName      = goalName;
        this.amount        = amount;
        this.actualMinutes = actualMinutes;
        this.timestamp     = timestamp;
    }
}
