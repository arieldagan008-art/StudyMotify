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
 *
 * <p>Fixed-time events (Goal.isFixedTime = true) are placed first at their
 * declared start/end times. Flexible sessions fill the remaining gaps.
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
     * @return weighted average minutes per unit, or 0.0 if no usable data.
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
     * Generates a daily schedule for today.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Place fixed-time events at their declared start/end times.</li>
     *   <li>Build the list of free gaps remaining in 08:00–22:00.</li>
     *   <li>Fill gaps with flexible study sessions using learning velocity.</li>
     *   <li>Sort the final schedule by start time.</li>
     * </ol>
     *
     * @param goals Active goals/events for the current user
     * @param logs  All stored progressLogs for the current user
     * @return Ordered list of ScheduleItems for today
     */
    public static List<ScheduleItem> generateDailySchedule(List<Goal>        goals,
                                                            List<ProgressLog> logs) {
        Map<String, Double> velocities    = computeAllVelocities(logs);
        List<Goal>          fixedGoals    = new ArrayList<>();
        List<Goal>          flexibleGoals = new ArrayList<>();

        // ── 1. Separate fixed events and flexible goals ───────────────────────
        for (Goal goal : goals) {
            if (goal.isCompleted) continue;
            if (goal.isFixedTime) {
                fixedGoals.add(goal);
            } else if (goal.totalUnits > 0) {
                flexibleGoals.add(goal);
            }
        }

        // ── 2. Sort fixed events by start time ────────────────────────────────
        fixedGoals.sort((a, b) -> parseToMinutes(a.startTime) - parseToMinutes(b.startTime));

        // ── 3. Build ScheduleItems for fixed events ───────────────────────────
        List<ScheduleItem> schedule = new ArrayList<>();
        for (Goal goal : fixedGoals) {
            int start = parseToMinutes(goal.startTime);
            int end   = parseToMinutes(goal.endTime);
            if (start < 0 || end <= start) continue;

            // Clamp to day window
            start = Math.max(start, DAY_START_MINUTE);
            end   = Math.min(end,   DAY_END_MINUTE);
            if (end <= start) continue;

            ScheduleItem item = new ScheduleItem();
            item.goalId          = goal.getId();
            item.goalName        = goal.getGoalName();
            item.startHour       = start / 60;
            item.startMinute     = start % 60;
            item.durationMinutes = end - start;
            item.unitsPlanned    = 0;
            item.minutesPerUnit  = 0;
            item.isFixedEvent    = true;
            schedule.add(item);
        }

        // ── 4. Build free gaps from the fixed-event list ─────────────────────
        List<int[]> gaps    = new ArrayList<>();
        int         prevEnd = DAY_START_MINUTE;

        for (ScheduleItem fixed : schedule) {
            int fixedStart = fixed.startHour * 60 + fixed.startMinute;
            int fixedEnd   = fixedStart + fixed.durationMinutes;
            if (fixedStart > prevEnd) {
                gaps.add(new int[]{prevEnd, fixedStart});
            }
            prevEnd = Math.max(prevEnd, fixedEnd);
        }
        if (prevEnd < DAY_END_MINUTE) {
            gaps.add(new int[]{prevEnd, DAY_END_MINUTE});
        }

        // ── 5. Fill gaps with flexible sessions ──────────────────────────────
        int gapIndex = 0;
        int cursor   = gaps.isEmpty() ? DAY_END_MINUTE : gaps.get(0)[0];
        int gapEnd   = gaps.isEmpty() ? DAY_END_MINUTE : gaps.get(0)[1];

        for (Goal goal : flexibleGoals) {
            if (gapIndex >= gaps.size()) break;

            int dailyUnits = SchedulerLogic.calculateDailyTarget(goal);
            if (dailyUnits <= 0) continue;

            double minsPerUnit;
            if (velocities.containsKey(goal.getId()) && velocities.get(goal.getId()) > 0) {
                minsPerUnit = velocities.get(goal.getId());
            } else if (goal.estimatedMinutesPerUnit > 0) {
                minsPerUnit = goal.estimatedMinutesPerUnit;
            } else {
                minsPerUnit = DEFAULT_MINUTES_PER_UNIT;
            }

            int rawDuration = (int) Math.ceil(dailyUnits * minsPerUnit);
            int duration    = ((rawDuration + 14) / 15) * 15; // round up to 15-min block

            // Advance to a gap that can fit the session (at least 15 min)
            while (gapIndex < gaps.size() && cursor + 15 > gapEnd) {
                gapIndex++;
                if (gapIndex < gaps.size()) {
                    cursor = gaps.get(gapIndex)[0];
                    gapEnd = gaps.get(gapIndex)[1];
                }
            }
            if (gapIndex >= gaps.size()) break;

            // Cap to remaining gap
            duration = Math.min(duration, gapEnd - cursor);
            if (duration <= 0) continue;

            ScheduleItem item = new ScheduleItem();
            item.goalId          = goal.getId();
            item.goalName        = goal.getGoalName();
            item.startHour       = cursor / 60;
            item.startMinute     = cursor % 60;
            item.durationMinutes = duration;
            item.unitsPlanned    = dailyUnits;
            item.minutesPerUnit  = minsPerUnit;
            item.isFixedEvent    = false;
            schedule.add(item);

            cursor += duration + BREAK_MINUTES;
            // Move to next gap if we've consumed this one
            if (cursor >= gapEnd) {
                gapIndex++;
                if (gapIndex < gaps.size()) {
                    cursor = gaps.get(gapIndex)[0];
                    gapEnd = gaps.get(gapIndex)[1];
                }
            }
        }

        // ── 6. Sort final schedule by start time ─────────────────────────────
        schedule.sort((a, b) -> {
            int aMin = a.startHour * 60 + a.startMinute;
            int bMin = b.startHour * 60 + b.startMinute;
            return aMin - bMin;
        });

        return schedule;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Parses "HH:MM" into minutes from midnight, or -1 on failure. */
    private static int parseToMinutes(String time) {
        if (time == null || !time.contains(":")) return -1;
        try {
            String[] parts = time.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
