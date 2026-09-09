package net.accellog.sefaz4j.cte;

public final class ResultadoEmissao {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String chCTe;
    private final String xmlAutorizado;

    public ResultadoEmissao(boolean ok, String cStat, String xMotivo, String chCTe, String xmlAutorizado) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.chCTe = chCTe;
        this.xmlAutorizado = xmlAutorizado;
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

    public String getXmlAutorizado() {
        return xmlAutorizado;
    }
}
