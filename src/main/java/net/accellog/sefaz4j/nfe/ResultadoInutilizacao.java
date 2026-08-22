package net.accellog.sefaz4j.nfe;

public final class ResultadoInutilizacao {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String protocoloXml;

    public ResultadoInutilizacao(boolean ok, String cStat, String xMotivo, String protocoloXml) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.protocoloXml = protocoloXml;
    }

    public boolean isOk() {
        return ok;
    }

    public String getCStat() {
        return cStat;
    }

    public String getXMotivo() {
        return xMotivo;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
