package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.TriageLevel;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriageClassifierTest {

    private static final String NON_ADMINISTRATIVE_COMPLAINT = "Herida por cortar";

    private final TriageClassifier classifier = new TriageClassifier();

    @ParameterizedTest(name = "{0} -> {9}")
    @CsvSource(delimiter = '|', value = {
            "E1|130|180|110|97|36.8|8|15|Dolor torácico|CRITICO",
            "E2|95|150|95|91|37.0|4|15|Dificultad para respirar|URGENTE",
            "E3|88|130|85|98|38.2|6|15|Fiebre|URGENTE_2",
            "E4|72|120|80|99|36.5|2|15|Herida por cortar|NO_URGENTE",
            "E5|70|115|75|99|36.4|0|15|Renovación de recetario|NO_URGENTE_2",
            "E6|35|80|50|85|35.9|0|6|Inconsciente|CRITICO"
    })
    void shouldClassifyReferencePatients(String caseId, int heartRate, int systolic, int diastolic,
                                         int spo2, double temperature, int pain, int gcs,
                                         String complaint, TriageLevel expected) {
        VitalSigns vitals = new VitalSigns(heartRate, systolic, diastolic, spo2, temperature, pain, gcs);

        assertEquals(expected, classifier.classify(vitals, complaint));
    }

    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @CsvSource({
            "HEART_RATE,39,CRITICO",
            "HEART_RATE,40,NO_URGENTE",
            "HEART_RATE,89,NO_URGENTE",
            "HEART_RATE,90,URGENTE_2",
            "HEART_RATE,120,URGENTE_2",
            "HEART_RATE,121,CRITICO",
            "SPO2,89,CRITICO",
            "SPO2,90,URGENTE",
            "SPO2,93,URGENTE",
            "SPO2,94,NO_URGENTE",
            "SYSTOLIC,89,CRITICO",
            "SYSTOLIC,90,URGENTE",
            "SYSTOLIC,100,URGENTE",
            "SYSTOLIC,101,NO_URGENTE",
            "GCS,8,CRITICO",
            "GCS,9,URGENTE",
            "GCS,13,URGENTE",
            "GCS,14,NO_URGENTE",
            "TEMPERATURE,38.5,URGENTE",
            "TEMPERATURE,38.0,URGENTE_2",
            "TEMPERATURE,37.9,NO_URGENTE",
            "PAIN,7,URGENTE_2",
            "PAIN,6,NO_URGENTE"
    })
    void shouldApplyBoundaryValues(String field, double value, TriageLevel expected) {
        VitalSigns vitals = withField(healthy(), field, value);

        assertEquals(expected, classifier.classify(vitals, NON_ADMINISTRATIVE_COMPLAINT));
    }

    @Test
    void shouldApplyHighestSeverityWhenSeveralConditionsMatch() {
        VitalSigns vitals = healthy().toBuilder().spo2(91).heartRate(130).build();

        assertEquals(TriageLevel.CRITICO, classifier.classify(vitals, NON_ADMINISTRATIVE_COMPLAINT));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "documentos,NO_URGENTE_2",
            "Recolección de Resultados,NO_URGENTE_2",
            "  VACUNACIÓN  ,NO_URGENTE_2",
            "seguimiento,NO_URGENTE_2",
            "dolor de oído,NO_URGENTE"
    })
    void shouldDowngradeOnlyAdministrativeComplaints(String complaint, TriageLevel expected) {
        assertEquals(expected, classifier.classify(healthy(), complaint));
    }

    @Test
    void shouldNotDowngradeHigherLevelsForAdministrativeComplaint() {
        VitalSigns vitals = healthy().toBuilder().spo2(92).build();

        assertEquals(TriageLevel.URGENTE, classifier.classify(vitals, "documentos"));
    }

    @Test
    void shouldTreatNullComplaintAsNonAdministrative() {
        assertEquals(TriageLevel.NO_URGENTE, classifier.classify(healthy(), null));
    }

    private static VitalSigns healthy() {
        return new VitalSigns(72, 120, 60, 99, 36.5, 2, 15);
    }

    private static VitalSigns withField(VitalSigns base, String field, double value) {
        VitalSigns.VitalSignsBuilder builder = base.toBuilder();
        switch (field) {
            case "HEART_RATE" -> builder.heartRate((int) value);
            case "SPO2" -> builder.spo2((int) value);
            case "SYSTOLIC" -> builder.systolicBp((int) value);
            case "GCS" -> builder.gcs((int) value);
            case "TEMPERATURE" -> builder.temperature(value);
            case "PAIN" -> builder.pain((int) value);
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        }
        return builder.build();
    }
}