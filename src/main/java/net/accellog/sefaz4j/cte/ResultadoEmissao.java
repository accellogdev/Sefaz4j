package net.accellog.sefaz4j.cte;

public final class ResultadoEmissao {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String chCTe;
    private final String xmlEnviado;
    private final String xmlAutorizado;

    public ResultadoEmissao(boolean ok, String cStat, String xMotivo, String chCTe, String xmlEnviado, String xmlAutorizado) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.chCTe = chCTe;
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

    public String getChCTe() {
        return chCTe;
    }

    /**
     * XML do CT-e assinado exatamente como foi transmitido à SEFAZ (antes de qualquer
     * compactação de transporte) — para persistência/depuração (ver
     * {@code net.accellog.services.sefaz.CTeProcessor} em bot-sefaz), independente do
     * resultado (autorizado, rejeitado ou com falha de comunicação — este último não chega a
     * gerar um {@code ResultadoEmissao}, mas rejeição sim).
     */
    public String getXmlEnviado() {
        return xmlEnviado;
    }

    public String getXmlAutorizado() {
        return xmlAutorizado;
    }
}
