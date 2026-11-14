package com.vida_cruz.triage_service.infraestructure.configuration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityPropertiesTest {

    private static final String GOOD_KEY = "a-long-enough-key-0123456789";

    @Test
    void shouldRefuseToStartWithoutAUsableKey() {
        assertThrows(IllegalArgumentException.class, () -> new SecurityProperties(true, null));
        assertThrows(IllegalArgumentException.class, () -> new SecurityProperties(true, List.of(" ")));
        assertThrows(IllegalArgumentException.class, () -> new SecurityProperties(true, List.of("short")));
        assertThrows(IllegalArgumentException.class, () -> new SecurityProperties(true, List.of(GOOD_KEY, "short")));
    }

    @Test
    void shouldAcceptStrongKeysIgnoringBlanksAndNeedNoneWhenDisabled() {
        assertEquals(List.of(GOOD_KEY), new SecurityProperties(true, List.of("  " + GOOD_KEY + " ", " ")).apiKeys());
        assertDoesNotThrow(() -> new SecurityProperties(false, null));
    }
}