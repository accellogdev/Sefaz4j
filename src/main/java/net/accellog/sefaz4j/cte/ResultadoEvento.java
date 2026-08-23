package net.accellog.sefaz4j.cte;

public final class ResultadoEvento {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String nProt;
    private final String protocoloXml;

    public ResultadoEvento(boolean ok, String cStat, String xMotivo, String nProt, String protocoloXml) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.nProt = nProt;
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

    public String getNProt() {
        return nProt;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
