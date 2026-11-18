package net.accellog.sefaz4j.nfse;

import net.accellog.sefaz4j.endpoints.Ambiente;

import java.time.Duration;
import java.time.Instant;

public final class Sefaz4jConfig {
    private final Ambiente ambiente;
    private final byte[] pfxBytes;
    private final String senhaPfx;
    private String urlOverride;
    private Duration timeout = Duration.ofSeconds(30);
    private LeiauteNFSe leiaute;

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

    /**
     * Leiaute da DPS: o fixado por {@link #setLeiaute}, ou, sem ele, o vigente agora no ambiente
     * ({@link LeiauteNFSe#vigente}).
     */
    public LeiauteNFSe getLeiaute() {
        return leiaute != null ? leiaute : LeiauteNFSe.vigente(ambiente, Instant.now());
    }

    /** Fixa o leiaute da DPS em vez de escolhê-lo pela data; {@code null} volta à escolha automática. */
    public Sefaz4jConfig setLeiaute(LeiauteNFSe leiaute) {
        this.leiaute = leiaute;
        return this;
    }
}
