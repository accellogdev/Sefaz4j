package net.accellog.sefaz4j.nfe.webservice;

public final class RespostaSefaz {
    private final String cStat;
    private final String xMotivo;
    private final String nRec;
    private final String chNFe;
    private final String protocoloXml;

    public RespostaSefaz(String cStat, String xMotivo, String nRec, String chNFe, String protocoloXml) {
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.nRec = nRec;
        this.chNFe = chNFe;
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

    public String getChNFe() {
        return chNFe;
    }

    public String getProtocoloXml() {
        return protocoloXml;
    }
}
