package net.accellog.sefaz4j.nfse;

public final class ResultadoEvento {
    private final boolean ok;
    private final String cStat;
    private final String mensagem;
    private final String protocoloXml;

    public ResultadoEvento(boolean ok, String cStat, String mensagem, String protocoloXml) {
        this.ok = ok;
        this.cStat = cStat;
        this.mensagem = mensagem;
        this.protocoloXml = protocoloXml;
    }

    public boolean isOk() {
        return ok;
    }

    public String getCStat() {
        return cStat;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
