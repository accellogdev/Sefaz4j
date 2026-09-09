package net.accellog.sefaz4j.nfse;

public final class ResultadoEmissao {
    private final boolean ok;
    private final String cStat;
    private final String mensagem;
    private final String chaveAcesso;
    private final String xmlAutorizado;

    public ResultadoEmissao(boolean ok, String cStat, String mensagem, String chaveAcesso, String xmlAutorizado) {
        this.ok = ok;
        this.cStat = cStat;
        this.mensagem = mensagem;
        this.chaveAcesso = chaveAcesso;
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

    public String getXmlAutorizado() {
        return xmlAutorizado;
    }
}
