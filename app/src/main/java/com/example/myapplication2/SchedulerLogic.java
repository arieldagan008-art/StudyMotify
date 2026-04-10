package com.example.myapplication2;

import java.util.Calendar;
import java.util.List;

public class SchedulerLogic {

    /** If total daily units across all goals exceeds this, show the overload warning. */
    public static final int DAILY_OVERLOAD_THRESHOLD = 20;

    // ─── Difficulty multipliers ───────────────────────────────────────────────

    public static double getDifficultyMultiplier(int difficulty) {
        switch (difficulty) {
            case 3:  return 1.5;
            case 2:  return 1.25;
            default: return 1.0;
        }
    }

    // ─── Daily target ─────────────────────────────────────────────────────────

    public static int calculateDailyTarget(Goal goal) {
        if (goal.totalUnits <= 0 || goal.deadlineDate <= 0) return 0;

        int remaining = goal.totalUnits - goal.completedUnits;
        if (remaining <= 0) return 0;

        long daysLeft = daysUntilDeadline(goal.deadlineDate);
        double multiplier = getDifficultyMultiplier(goal.getDifficulty());
        double effectiveRemaining = remaining * multiplier;

        if (daysLeft <= 0) return (int) Math.ceil(effectiveRemaining);
        return (int) Math.ceil(effectiveRemaining / daysLeft);
    }

    public static String getDailyTargetMessage(Goal goal) {
        if (goal.totalUnits <= 0 || goal.deadlineDate <= 0) return null;

        String label = goal.getUnitLabel();
        int remaining = goal.totalUnits - goal.completedUnits;
        if (remaining <= 0) return "All done! Great work.";

        long daysLeft = daysUntilDeadline(goal.deadlineDate);
        if (daysLeft < 0) return "Deadline passed — " + remaining + " " + label + " still remaining";

        int daily = calculateDailyTarget(goal);
        String bufferNote = difficultyBufferNote(goal.getDifficulty());

        if (daysLeft == 0) return "Last day! Do all " + remaining + " " + label + " today" + bufferNote;
        return "Do " + daily + " " + label + " today to stay on track" + bufferNote;
    }

    private static String difficultyBufferNote(int difficulty) {
        switch (difficulty) {
            case 3: return " (Hard — 1.5× buffer)";
            case 2: return " (Medium — 1.25× buffer)";
            default: return "";
        }
    }

    // ─── Velocity & projected finish ─────────────────────────────────────────

    /**
     * Predicts the finish date (epoch ms) based on the user's current pace.
     * Returns -1 if there is not enough data to make a prediction.
     *
     * Formula: velocity = completedUnits / daysElapsed
     *          daysToFinish = remainingUnits / velocity
     *          projectedFinish = now + daysToFinish
     */
    public static long calculateProjectedFinish(Goal goal) {
        if (goal.totalUnits <= 0 || goal.completedUnits <= 0) return -1;

        // Use createdAtTimestamp if available, fall back to timestamp
        long startMs = goal.createdAtTimestamp > 0 ? goal.createdAtTimestamp : goal.timestamp;
        if (startMs <= 0) return -1;

        long now = System.currentTimeMillis();
        long msPerDay = 24 * 60 * 60 * 1000L;
        double daysElapsed = (double)(now - startMs) / msPerDay;

        // Need at least a few hours of elapsed time to avoid absurd extrapolations
        if (daysElapsed < 0.1) return -1;

        double velocity = goal.completedUnits / daysElapsed;      // units per day
        if (velocity <= 0) return -1;

        int remaining = goal.totalUnits - goal.completedUnits;
        if (remaining <= 0) return now;                            // already done

        double daysToFinish = remaining / velocity;
        return now + (long)(daysToFinish * msPerDay);
    }

    /**
     * Returns true if the goal's projected finish date is past its deadline,
     * meaning the user is falling behind at their current pace.
     */
    public static boolean isBehindSchedule(Goal goal) {
        if (goal.deadlineDate <= 0) return false;
        long projected = calculateProjectedFinish(goal);
        return projected > 0 && projected > goal.deadlineDate;
    }

    // ─── Global overload detection ────────────────────────────────────────────

    /**
     * Sums the daily target units of every active (non-completed) goal.
     * If the total exceeds DAILY_OVERLOAD_THRESHOLD, the user is overloaded.
     */
    public static int checkGlobalOverload(List<Goal> goals) {
        int total = 0;
        for (Goal goal : goals) {
            if (!goal.isCompleted && goal.totalUnits > 0) {
                total += calculateDailyTarget(goal);
            }
        }
        return total;
    }

    // ─── Date math ───────────────────────────────────────────────────────────

    public static long daysUntilDeadline(long deadlineEpochMs) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar deadline = Calendar.getInstance();
        deadline.setTimeInMillis(deadlineEpochMs);
        deadline.set(Calendar.HOUR_OF_DAY, 0);
        deadline.set(Calendar.MINUTE, 0);
        deadline.set(Calendar.SECOND, 0);
        deadline.set(Calendar.MILLISECOND, 0);

        long diffMs = deadline.getTimeInMillis() - today.getTimeInMillis();
        return diffMs / (24 * 60 * 60 * 1000L);
    }
}
