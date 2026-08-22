package net.accellog.sefaz4j.nfe;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;

import java.time.Duration;

public final class Sefaz4jConfig {
    private final UF uf;
    private final Ambiente ambiente;
    private final byte[] pfxBytes;
    private final String senhaPfx;
    private final String urlAutorizacaoOverride;
    private String urlRetAutorizacaoOverride;
    private String urlConsultaProtocoloOverride;
    private String urlRecepcaoEventoOverride;
    private String urlInutilizacaoOverride;
    private Duration timeout = Duration.ofSeconds(30);
    private int maxTentativasPolling = 5;
    private Duration intervaloPolling = Duration.ofSeconds(5);

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

    public String getUrlRetAutorizacaoOverride() {
        return urlRetAutorizacaoOverride;
    }

    /**
     * Sobrescreve a URL do serviço NFeRetAutorizacao4 (por padrão resolvida
     * via {@code EndpointResolver}/{@code nfe-servicos.ini} a partir de
     * UF+Ambiente), simetricamente ao override de autorização já existente
     * no construtor. Útil para testes e para ambientes com um proxy/gateway
     * próprio na frente da SEFAZ.
     */
    public Sefaz4jConfig setUrlRetAutorizacaoOverride(String urlRetAutorizacaoOverride) {
        this.urlRetAutorizacaoOverride = urlRetAutorizacaoOverride;
        return this;
    }

    public String getUrlConsultaProtocoloOverride() {
        return urlConsultaProtocoloOverride;
    }

    /**
     * Sobrescreve a URL do serviço NfeConsultaProtocolo4 (por padrão
     * resolvida via {@code EndpointResolver}/{@code nfe-servicos.ini} a
     * partir de UF+Ambiente), simetricamente aos demais overrides de URL já
     * existentes. Útil para testes e para ambientes com um proxy/gateway
     * próprio na frente da SEFAZ.
     */
    public Sefaz4jConfig setUrlConsultaProtocoloOverride(String urlConsultaProtocoloOverride) {
        this.urlConsultaProtocoloOverride = urlConsultaProtocoloOverride;
        return this;
    }

    public String getUrlRecepcaoEventoOverride() {
        return urlRecepcaoEventoOverride;
    }

    /**
     * Sobrescreve a URL do serviço RecepcaoEvento4 (por padrão resolvida
     * via {@code EndpointResolver}/{@code nfe-servicos.ini} a partir de
     * UF+Ambiente), simetricamente aos demais overrides de URL já
     * existentes. Útil para testes e para ambientes com um proxy/gateway
     * próprio na frente da SEFAZ.
     */
    public Sefaz4jConfig setUrlRecepcaoEventoOverride(String urlRecepcaoEventoOverride) {
        this.urlRecepcaoEventoOverride = urlRecepcaoEventoOverride;
        return this;
    }

    public String getUrlInutilizacaoOverride() {
        return urlInutilizacaoOverride;
    }

    /**
     * Sobrescreve a URL do serviço NfeInutilizacao4 (por padrão resolvida
     * via {@code EndpointResolver}/{@code nfe-servicos.ini} a partir de
     * UF+Ambiente), simetricamente aos demais overrides de URL já
     * existentes. Útil para testes e para ambientes com um proxy/gateway
     * próprio na frente da SEFAZ.
     */
    public Sefaz4jConfig setUrlInutilizacaoOverride(String urlInutilizacaoOverride) {
        this.urlInutilizacaoOverride = urlInutilizacaoOverride;
        return this;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxTentativasPolling() {
        return maxTentativasPolling;
    }

    public Duration getIntervaloPolling() {
        return intervaloPolling;
    }

    /**
     * Timeout de conexão/leitura usado nas chamadas HTTP à SEFAZ
     * (autorização e retAutorização). Padrão: 30 segundos.
     */
    public Sefaz4jConfig setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Número máximo de tentativas de consulta em {@code ReciboPoller}
     * enquanto o lote estiver com cStat 103 ("em processamento"). Padrão: 5.
     */
    public Sefaz4jConfig setMaxTentativasPolling(int maxTentativasPolling) {
        this.maxTentativasPolling = maxTentativasPolling;
        return this;
    }

    /**
     * Intervalo de espera entre tentativas de {@code ReciboPoller}.
     * Padrão: 5 segundos.
     */
    public Sefaz4jConfig setIntervaloPolling(Duration intervaloPolling) {
        this.intervaloPolling = intervaloPolling;
        return this;
    }
}
