package net.accellog.sefaz4j.cte;

public final class ResultadoEmissao {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String chCTe;
    private final String nProt;
    private final String xmlAutorizado;

    public ResultadoEmissao(boolean ok, String cStat, String xMotivo, String chCTe, String nProt, String xmlAutorizado) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.chCTe = chCTe;
        this.nProt = nProt;
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

    // Protocolo de autorização (nProt) -- necessário para eventos futuros sobre este mesmo
    // documento (cancelamento, carta de correção), que exigem o nProt da autorização original.
    // Ausente antes desta correção: só ResultadoEvento (emitido pelos próprios eventos) carregava
    // nProt; a emissão original nunca o expunha, forçando quem chamava a inventar outra fonte.
    public String getNProt() {
        return nProt;
    }

    public String getXmlAutorizado() {
        return xmlAutorizado;
    }
}
