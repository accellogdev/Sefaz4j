package net.accellog.sefaz4j.nfse;

import net.accellog.sefaz4j.endpoints.Ambiente;

import java.time.Duration;

public final class Sefaz4jConfig {
    private final Ambiente ambiente;
    private final byte[] pfxBytes;
    private final String senhaPfx;
    private String urlOverride;
    private Duration timeout = Duration.ofSeconds(30);

    public Sefaz4jConfig(Ambiente ambiente, byte[] pfxBytes, String senhaPfx) {
        this.ambiente = ambiente;
        this.pfxBytes = pfxBytes;
        this.senhaPfx = senhaPfx;
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

    public String getUrlOverride() {
        return urlOverride;
    }

    public Sefaz4jConfig setUrlOverride(String urlOverride) {
        this.urlOverride = urlOverride;
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
