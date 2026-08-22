package net.accellog.sefaz4j.webservice;

public final class RespostaSefaz {
    private final String cStat;
    private final String xMotivo;
    private final String nRec;
    private final String chaveDocumento;
    private final String protocoloXml;

    public RespostaSefaz(String cStat, String xMotivo, String nRec, String chaveDocumento, String protocoloXml) {
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.nRec = nRec;
        this.chaveDocumento = chaveDocumento;
        this.protocoloXml = protocoloXml;
    }

    public String getCStat() {
        return cStat;
    }

    public String getXMotivo() {
        return xMotivo;
    }

    public String getNRec() {
        return nRec;
    }

    public String getChaveDocumento() {
        return chaveDocumento;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
