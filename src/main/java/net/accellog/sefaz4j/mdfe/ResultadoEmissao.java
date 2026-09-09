package net.accellog.sefaz4j.mdfe;

public final class ResultadoEmissao {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String chMDFe;
    private final String xmlEnviado;
    private final String xmlAutorizado;

    public ResultadoEmissao(boolean ok, String cStat, String xMotivo, String chMDFe, String xmlEnviado, String xmlAutorizado) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.chMDFe = chMDFe;
        this.xmlEnviado = xmlEnviado;
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

    public String getChMDFe() {
        return chMDFe;
    }

    /**
     * XML do MDF-e assinado exatamente como foi transmitido à SEFAZ (antes de qualquer
     * compactação de transporte) — para persistência/depuração (ver
     * {@code net.accellog.services.sefaz.MDFeProcessor} em bot-sefaz).
     */
    public String getXmlEnviado() {
        return xmlEnviado;
    }

    public String getXmlAutorizado() {
        return xmlAutorizado;
    }
}
