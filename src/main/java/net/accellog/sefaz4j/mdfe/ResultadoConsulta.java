package net.accellog.sefaz4j.mdfe;

public final class ResultadoConsulta {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String chMDFe;
    private final String protocoloXml;

    public ResultadoConsulta(boolean ok, String cStat, String xMotivo, String chMDFe, String protocoloXml) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.chMDFe = chMDFe;
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

    public String getChMDFe() {
        return chMDFe;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
