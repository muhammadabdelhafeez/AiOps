package org.aiopsanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class AiOpsAnalysisApplicationCompatibilityTest {

    @Test
    void shouldKeepLegacyIntellijRunConfigurationMainClassAvailable()
            throws ClassNotFoundException, NoSuchMethodException {
        var launcherClass = Class.forName("org.aiopsanalysis.AiOpsAnalysisApplication");
        var main = launcherClass.getDeclaredMethod("main", String[].class);

        assertNotNull(main);
        assertEquals("org.aiopsanalysis", launcherClass.getPackageName());
        assertEquals(void.class, main.getReturnType());
        assertTrue(Modifier.isStatic(main.getModifiers()));
        assertFalse(launcherClass.isAnnotationPresent(SpringBootApplication.class));
    }
}
