package com.example.myapplication2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The AI Learning Brain.
 *
 * <p>Learns from real session data (progressLogs) to compute how many minutes
 * a user actually needs per unit for each goal (Learning Velocity), then uses
 * that velocity to auto-schedule today's required study blocks.
 */
public class LearningEngine {

    /** Fallback minutes-per-unit when no session data exists and goal has no estimate. */
    private static final double DEFAULT_MINUTES_PER_UNIT = 30.0;

    /** Gap between scheduled sessions (minutes). */
    private static final int BREAK_MINUTES = 15;

    /** Day starts at 08:00. */
    private static final int DAY_START_MINUTE = 8 * 60;

    /** Day ends at 22:00. */
    private static final int DAY_END_MINUTE = 22 * 60;

    // ─── Velocity computation ─────────────────────────────────────────────────

    /**
     * Computes the Learning Velocity for a single goal.
     *
     * @return weighted average minutes per unit (total minutes ÷ total units logged),
     *         or 0.0 if there is no usable data for this goal.
     */
    public static double computeVelocity(List<ProgressLog> logs, String goalId) {
        long totalMinutes = 0;
        int  totalUnits   = 0;
        for (ProgressLog log : logs) {
            if (goalId != null && goalId.equals(log.goalId)
                    && log.actualMinutes > 0 && log.amount > 0) {
                totalMinutes += log.actualMinutes;
                totalUnits   += log.amount;
            }
        }
        if (totalUnits == 0) return 0.0;
        return (double) totalMinutes / totalUnits;
    }

    /**
     * Computes Learning Velocity for every goal that appears in the logs.
     *
     * @return Map of goalId → minutes-per-unit
     */
    public static Map<String, Double> computeAllVelocities(List<ProgressLog> logs) {
        // goalId → [sumMinutes, sumUnits]
        Map<String, long[]> accumulator = new HashMap<>();

        for (ProgressLog log : logs) {
            if (log.goalId == null || log.actualMinutes <= 0 || log.amount <= 0) continue;
            if (!accumulator.containsKey(log.goalId)) {
                accumulator.put(log.goalId, new long[]{0L, 0L});
            }
            long[] bucket = accumulator.get(log.goalId);
            bucket[0] += log.actualMinutes;
            bucket[1] += log.amount;
        }

        Map<String, Double> velocities = new HashMap<>();
        for (Map.Entry<String, long[]> entry : accumulator.entrySet()) {
            long mins  = entry.getValue()[0];
            long units = entry.getValue()[1];
            if (units > 0) velocities.put(entry.getKey(), (double) mins / units);
        }
        return velocities;
    }

    // ─── Schedule generation ──────────────────────────────────────────────────

    /**
     * Generates a daily study schedule starting at 08:00.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each active goal, determine today's required units via SchedulerLogic.</li>
     *   <li>Estimate session duration = units × minutesPerUnit (from velocity or fallback).</li>
     *   <li>Round up to the nearest 15-minute block.</li>
     *   <li>Pack sessions from 08:00 with 15-min breaks. Stop at 22:00.</li>
     * </ol>
     *
     * @param goals Active goals (non-completed, with progress tracking enabled)
     * @param logs  All stored progressLogs for the current user
     * @return Ordered list of ScheduleItems for today
     */
    public static List<ScheduleItem> generateDailySchedule(List<Goal>        goals,
                                                            List<ProgressLog> logs) {
        Map<String, Double> velocities = computeAllVelocities(logs);
        List<ScheduleItem>  schedule   = new ArrayList<>();

        int cursor = DAY_START_MINUTE; // current "pen" position in minutes from midnight

        for (Goal goal : goals) {
            if (goal.isCompleted || goal.totalUnits <= 0) continue;
            if (cursor >= DAY_END_MINUTE) break;

            int dailyUnits = SchedulerLogic.calculateDailyTarget(goal);
            if (dailyUnits <= 0) continue;

            // Determine minutes-per-unit: velocity > goal estimate > default
            double minsPerUnit;
            if (velocities.containsKey(goal.getId()) && velocities.get(goal.getId()) > 0) {
                minsPerUnit = velocities.get(goal.getId());
            } else if (goal.estimatedMinutesPerUnit > 0) {
                minsPerUnit = goal.estimatedMinutesPerUnit;
            } else {
                minsPerUnit = DEFAULT_MINUTES_PER_UNIT;
            }

            int rawDuration = (int) Math.ceil(dailyUnits * minsPerUnit);
            // Round up to next 15-minute block
            int duration = ((rawDuration + 14) / 15) * 15;

            // Cap at remaining day
            int maxDuration = DAY_END_MINUTE - cursor;
            if (maxDuration <= 0) break;
            duration = Math.min(duration, maxDuration);

            ScheduleItem item = new ScheduleItem();
            item.goalId          = goal.getId();
            item.goalName        = goal.getGoalName();
            item.startHour       = cursor / 60;
            item.startMinute     = cursor % 60;
            item.durationMinutes = duration;
            item.unitsPlanned    = dailyUnits;
            item.minutesPerUnit  = minsPerUnit;
            schedule.add(item);

            cursor += duration + BREAK_MINUTES;
        }

        return schedule;
    }
}
