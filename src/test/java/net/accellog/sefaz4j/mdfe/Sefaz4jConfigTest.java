package net.accellog.sefaz4j.mdfe;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Sefaz4jConfigTest {

    @Test
    public void construtorSemOverrideDeixaUrlRecepcaoNulaEUsaDefaults() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, new byte[0], "senha");

        assertEquals(UF.SP, config.getUf());
        assertEquals(Ambiente.HOMOLOGACAO, config.getAmbiente());
        assertNull(config.getUrlRecepcaoOverride());
        assertEquals(Duration.ofSeconds(30), config.getTimeout());
        assertEquals(5, config.getMaxTentativasPolling());
        assertEquals(Duration.ofSeconds(5), config.getIntervaloPolling());
    }

    @Test
    public void construtorComOverrideDeUrlRecepcaoEEfetivo() {
        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.HOMOLOGACAO, new byte[0], "senha", "https://exemplo.invalido/mdfe"
        );

        assertEquals("https://exemplo.invalido/mdfe", config.getUrlRecepcaoOverride());
    }

    @Test
    public void settersFluentesDosDemaisOverridesSaoEfetivos() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, new byte[0], "senha");

        Sefaz4jConfig retorno = config
            .setUrlRetRecepcaoOverride("https://exemplo.invalido/retrecepcao")
            .setUrlConsultaProtocoloOverride("https://exemplo.invalido/consulta")
            .setUrlRecepcaoEventoOverride("https://exemplo.invalido/evento")
            .setTimeout(Duration.ofSeconds(10))
            .setMaxTentativasPolling(3)
            .setIntervaloPolling(Duration.ofSeconds(2));

        assertEquals(config, retorno);
        assertEquals("https://exemplo.invalido/retrecepcao", config.getUrlRetRecepcaoOverride());
        assertEquals("https://exemplo.invalido/consulta", config.getUrlConsultaProtocoloOverride());
        assertEquals("https://exemplo.invalido/evento", config.getUrlRecepcaoEventoOverride());
        assertEquals(Duration.ofSeconds(10), config.getTimeout());
        assertEquals(3, config.getMaxTentativasPolling());
        assertEquals(Duration.ofSeconds(2), config.getIntervaloPolling());
    }
}
