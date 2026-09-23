package net.accellog.sefaz4j.distdfe;

import net.accellog.sefaz4j.endpoints.Ambiente;

import java.time.Duration;

/**
 * Configuracao para os webservices de Distribuicao de DFe (NFe e CTe).
 * Diferente dos demais Sefaz4jConfig (nfe, cte, mdfe, nfse), nao recebe UF:
 * o endpoint de Distribuicao de DFe e sempre o Ambiente Nacional (UF.AN),
 * nunca resolvido pelo estado do emitente/interessado.
 */
public final class Sefaz4jConfig {
    private final Ambiente ambiente;
    private final byte[] pfxBytes;
    private final String senhaPfx;
    private String urlDistribuicaoDFeOverride;
    private Duration timeout = Duration.ofSeconds(30);

    public Sefaz4jConfig(Ambiente ambiente, byte[] pfxBytes, String senhaPfx) {
        this.ambiente = ambiente;
        this.pfxBytes = pfxBytes;
        this.senhaPfx = senhaPfx;
    }

    public Ambiente getAmbiente() { return ambiente; }
    public byte[] getPfxBytes() { return pfxBytes; }
    public String getSenhaPfx() { return senhaPfx; }
    public String getUrlDistribuicaoDFeOverride() { return urlDistribuicaoDFeOverride; }

    public Sefaz4jConfig setUrlDistribuicaoDFeOverride(String urlDistribuicaoDFeOverride) {
        this.urlDistribuicaoDFeOverride = urlDistribuicaoDFeOverride;
        return this;
    }

    public Duration getTimeout() { return timeout; }

    public Sefaz4jConfig setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
}
