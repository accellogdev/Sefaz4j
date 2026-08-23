package net.accellog.sefaz4j.cte;

public final class ResultadoConsulta {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String chCTe;
    private final String protocoloXml;

    public ResultadoConsulta(boolean ok, String cStat, String xMotivo, String chCTe, String protocoloXml) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.chCTe = chCTe;
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

    public String getChCTe() {
        return chCTe;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
