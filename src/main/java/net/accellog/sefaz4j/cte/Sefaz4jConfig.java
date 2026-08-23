package net.accellog.sefaz4j.cte;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;

import java.time.Duration;

public final class Sefaz4jConfig {
    private final UF uf;
    private final Ambiente ambiente;
    private final byte[] pfxBytes;
    private final String senhaPfx;
    private String urlAutorizacaoOverride;
    private String urlConsultaProtocoloOverride;
    private String urlRecepcaoEventoOverride;
    private Duration timeout = Duration.ofSeconds(30);

    public Sefaz4jConfig(UF uf, Ambiente ambiente, byte[] pfxBytes, String senhaPfx) {
        this(uf, ambiente, pfxBytes, senhaPfx, null);
    }

    public Sefaz4jConfig(UF uf, Ambiente ambiente, byte[] pfxBytes, String senhaPfx, String urlAutorizacaoOverride) {
        this.uf = uf;
        this.ambiente = ambiente;
        this.pfxBytes = pfxBytes;
        this.senhaPfx = senhaPfx;
        this.urlAutorizacaoOverride = urlAutorizacaoOverride;
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

    public String getUrlAutorizacaoOverride() {
        return urlAutorizacaoOverride;
    }

    public Sefaz4jConfig setUrlAutorizacaoOverride(String urlAutorizacaoOverride) {
        this.urlAutorizacaoOverride = urlAutorizacaoOverride;
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
}
