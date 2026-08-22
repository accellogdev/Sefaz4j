package net.accellog.sefaz4j.nfe;

public final class ResultadoConsulta {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String chNFe;
    private final String protocoloXml;

    public ResultadoConsulta(boolean ok, String cStat, String xMotivo, String chNFe, String protocoloXml) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.chNFe = chNFe;
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

    public String getChNFe() {
        return chNFe;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
