package net.accellog.sefaz4j.mdfe;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;

import java.time.Duration;

public final class Sefaz4jConfig {
    private final UF uf;
    private final Ambiente ambiente;
    private final byte[] pfxBytes;
    private final String senhaPfx;
    private final String urlRecepcaoOverride;
    private String urlRetRecepcaoOverride;
    private String urlConsultaProtocoloOverride;
    private String urlRecepcaoEventoOverride;
    private Duration timeout = Duration.ofSeconds(30);
    private int maxTentativasPolling = 5;
    private Duration intervaloPolling = Duration.ofSeconds(5);

    public Sefaz4jConfig(UF uf, Ambiente ambiente, byte[] pfxBytes, String senhaPfx) {
        this(uf, ambiente, pfxBytes, senhaPfx, null);
    }

    public Sefaz4jConfig(UF uf, Ambiente ambiente, byte[] pfxBytes, String senhaPfx, String urlRecepcaoOverride) {
        this.uf = uf;
        this.ambiente = ambiente;
        this.pfxBytes = pfxBytes;
        this.senhaPfx = senhaPfx;
        this.urlRecepcaoOverride = urlRecepcaoOverride;
    }

    public UF getUf() {
        return uf;
    }

    public Ambiente getAmbiente() {
        return ambiente;
    }

    public byte[] getPfxBytes() {
        return pfxBytes;
    }

    public String getSenhaPfx() {
        return senhaPfx;
    }

    public String getUrlRecepcaoOverride() {
        return urlRecepcaoOverride;
    }

    public String getUrlRetRecepcaoOverride() {
        return urlRetRecepcaoOverride;
    }

    /**
     * Sobrescreve a URL do serviço MDFeRetRecepcao (por padrão resolvida via
     * {@code EndpointResolver}/{@code mdfe-servicos.ini} a partir de UF+Ambiente).
     */
    public Sefaz4jConfig setUrlRetRecepcaoOverride(String urlRetRecepcaoOverride) {
        this.urlRetRecepcaoOverride = urlRetRecepcaoOverride;
        return this;
    }

    public String getUrlConsultaProtocoloOverride() {
        return urlConsultaProtocoloOverride;
    }

    public Sefaz4jConfig setUrlConsultaProtocoloOverride(String urlConsultaProtocoloOverride) {
        this.urlConsultaProtocoloOverride = urlConsultaProtocoloOverride;
        return this;
    }

    public String getUrlRecepcaoEventoOverride() {
        return urlRecepcaoEventoOverride;
    }

    public Sefaz4jConfig setUrlRecepcaoEventoOverride(String urlRecepcaoEventoOverride) {
        this.urlRecepcaoEventoOverride = urlRecepcaoEventoOverride;
        return this;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Sefaz4jConfig setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public int getMaxTentativasPolling() {
        return maxTentativasPolling;
    }

    /**
     * Número máximo de tentativas de consulta em {@code ReciboPoller} enquanto
     * o lote estiver com cStat 103 ("em processamento"). Padrão: 5.
     */
    public Sefaz4jConfig setMaxTentativasPolling(int maxTentativasPolling) {
        this.maxTentativasPolling = maxTentativasPolling;
        return this;
    }

    public Duration getIntervaloPolling() {
        return intervaloPolling;
    }

    public Sefaz4jConfig setIntervaloPolling(Duration intervaloPolling) {
        this.intervaloPolling = intervaloPolling;
        return this;
    }
}
