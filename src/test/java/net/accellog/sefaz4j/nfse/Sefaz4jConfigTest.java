package net.accellog.sefaz4j.nfse;

import net.accellog.sefaz4j.endpoints.Ambiente;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Sefaz4jConfigTest {

    @Test
    public void construtorSemOverrideDeixaUrlNula() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, new byte[0], "senha");

        assertEquals(Ambiente.HOMOLOGACAO, config.getAmbiente());
        assertNull(config.getUrlOverride());
        assertEquals(Duration.ofSeconds(30), config.getTimeout());
    }

    @Test
    public void setUrlOverrideEhFluenteEEfetivo() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, new byte[0], "senha");

        Sefaz4jConfig retorno = config.setUrlOverride("https://exemplo.invalido");

        assertEquals(config, retorno);
        assertEquals("https://exemplo.invalido", config.getUrlOverride());
    }

    @Test
    public void setTimeoutEhFluenteEEfetivo() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, new byte[0], "senha");

        config.setTimeout(Duration.ofSeconds(5));

        assertEquals(Duration.ofSeconds(5), config.getTimeout());
    }
}
