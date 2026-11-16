
package com.vida_cruz.triage_service.domain.usecase.rules;

import com.vida_cruz.triage_service.domain.model.VitalSigns;

public class VitalSignsTriageRules {

    public static final int MINIMAL_HEART_RATE_TO_CRITICAL = 40;
    public static final int MAX_HEART_RATE_TO_CRITICAL = 120;
    public static final int MAX_SPO_TO_CRITICAL = 90;
    public static final int MAX_SYSTOLIC_BP_TO_CRITICAL = 90;
    public static final int MAX_GCS_TO_CRITICAL = 8;
    public static final int MAX_SPO_TO_URGENT = 93;
    public static final int MAX_SYSTOLIC_BP_TO_URGENT = 100;
    public static final double MIN_VITAL_TEMPERATURE_TO_URGENT = 38.5;
    public static final int MIN_GCS_TO_URGENT = 9;
    public static final int MAX_GCS_TO_URGENT = 13;
    public static final int MIN_HEART_RATE_TO_SEMI_URGENT = 90;
    public static final double MIN_TEMPERATURE_TO_SEMI_URGENT = 38.0;
    public static final double MAX_TEMPERATURE_TO_URGENT = 38.5;
    public static final int MIN_PAIN_RATE_TO_SEMI_URGENT = 7;

    public boolean isCritical(VitalSigns vitals) {
        return getCriticalHeartRate(vitals) || validOtherCriticalFigures(vitals);
    }

    private static boolean validOtherCriticalFigures(VitalSigns vitals) {
        return vitals.spo2() < MAX_SPO_TO_CRITICAL
                || vitals.systolicBp() < MAX_SYSTOLIC_BP_TO_CRITICAL
                || vitals.gcs() <= MAX_GCS_TO_CRITICAL;
    }

    private static boolean getCriticalHeartRate(VitalSigns vitals) {
        return vitals.heartRate() < MINIMAL_HEART_RATE_TO_CRITICAL
                || vitals.heartRate() > MAX_HEART_RATE_TO_CRITICAL;
    }

    public boolean isUrgent(VitalSigns vitals) {
        return between(vitals.spo2(), MAX_SPO_TO_CRITICAL, MAX_SPO_TO_URGENT)
                || between(vitals.systolicBp(), MAX_SYSTOLIC_BP_TO_CRITICAL, MAX_SYSTOLIC_BP_TO_URGENT)
                || vitals.temperature() >= MIN_VITAL_TEMPERATURE_TO_URGENT
                || between(vitals.gcs(), MIN_GCS_TO_URGENT, MAX_GCS_TO_URGENT);
    }

    public boolean isSemiUrgent(VitalSigns vitals) {
        return between(vitals.heartRate(), MIN_HEART_RATE_TO_SEMI_URGENT, MAX_HEART_RATE_TO_CRITICAL)
                || getSemiUrgentTemperature(vitals)
                || vitals.pain() >= MIN_PAIN_RATE_TO_SEMI_URGENT;
    }

    private static boolean getSemiUrgentTemperature(VitalSigns vitals) {
        return vitals.temperature() >= MIN_TEMPERATURE_TO_SEMI_URGENT
                && vitals.temperature() < MAX_TEMPERATURE_TO_URGENT;
    }

    private boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }
}
