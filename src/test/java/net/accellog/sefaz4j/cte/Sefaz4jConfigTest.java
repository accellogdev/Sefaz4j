package net.accellog.sefaz4j.cte;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Sefaz4jConfigTest {

    @Test
    public void construtorSemOverrideDeixaUrlAutorizacaoNula() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, new byte[0], "senha");

        assertEquals(UF.SP, config.getUf());
        assertEquals(Ambiente.HOMOLOGACAO, config.getAmbiente());
        assertNull(config.getUrlAutorizacaoOverride());
        assertEquals(Duration.ofSeconds(30), config.getTimeout());
    }

    @Test
    public void setUrlAutorizacaoOverrideEhFluenteEEfetivo() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, new byte[0], "senha");

        Sefaz4jConfig retorno = config.setUrlAutorizacaoOverride("https://exemplo.invalido/cte");

        assertEquals(config, retorno);
        assertEquals("https://exemplo.invalido/cte", config.getUrlAutorizacaoOverride());
    }

    @Test
    public void setTimeoutEhFluenteEEfetivo() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, new byte[0], "senha");

        config.setTimeout(Duration.ofSeconds(5));

        assertEquals(Duration.ofSeconds(5), config.getTimeout());
    }
}
