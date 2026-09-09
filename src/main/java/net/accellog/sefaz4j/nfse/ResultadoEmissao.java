package net.accellog.sefaz4j.nfse;

public final class ResultadoEmissao {
    private final boolean ok;
    private final String cStat;
    private final String mensagem;
    private final String chaveAcesso;
    private final String xmlEnviado;
    private final String xmlAutorizado;

    public ResultadoEmissao(boolean ok, String cStat, String mensagem, String chaveAcesso, String xmlEnviado, String xmlAutorizado) {
        this.ok = ok;
        this.cStat = cStat;
        this.mensagem = mensagem;
        this.chaveAcesso = chaveAcesso;
        this.xmlEnviado = xmlEnviado;
        this.xmlAutorizado = xmlAutorizado;
    }

    public boolean isOk() {
        return ok;
    }

    public String getCStat() {
        return cStat;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getChaveAcesso() {
        return chaveAcesso;
    }

    /**
     * XML da DPS assinado exatamente como foi transmitido ao SEFIN Nacional (antes de
     * gzip+Base64) — populado mesmo em rejeição, para persistência/depuração (ver
     * {@code net.accellog.services.sefaz.NFSeProcessor} em bot-sefaz). Foi justamente sua
     * ausência em rejeição (antes desta mudança, {@code null} no caminho de erro) que motivou
     * este campo.
     */
    public String getXmlEnviado() {
        return xmlEnviado;
    }

    public String getXmlAutorizado() {
        return xmlAutorizado;
    }
}
