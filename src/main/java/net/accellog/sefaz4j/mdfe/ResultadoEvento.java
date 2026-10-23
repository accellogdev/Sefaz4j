package net.accellog.sefaz4j.mdfe;

public final class ResultadoEvento {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String nProt;
    private final String protocoloXml;
    private final String xmlEvento;

    public ResultadoEvento(boolean ok, String cStat, String xMotivo, String nProt, String protocoloXml, String xmlEvento) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.nProt = nProt;
        this.protocoloXml = protocoloXml;
        this.xmlEvento = xmlEvento;
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

    // procEvento (evento assinado + retEvento) quando a SEFAZ devolveu o retEvento; sem ele, só o
    // evento assinado -- mesmo critério de ResultadoEmissao.getXmlAutorizado. É o XML que se guarda e
    // se distribui (DACCE, download do XML do evento), não o protocoloXml sozinho.
    public String getXmlEvento() {
        return xmlEvento;
    }
}
