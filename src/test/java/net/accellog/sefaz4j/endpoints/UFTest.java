package net.accellog.sefaz4j.endpoints;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UFTest {
    @Test
    public void deveTerValorParaAmbienteNacional() {
        assertTrue(java.util.Arrays.asList(UF.values()).contains(UF.AN));
        assertEquals("AN", UF.AN.name());
    }
}
