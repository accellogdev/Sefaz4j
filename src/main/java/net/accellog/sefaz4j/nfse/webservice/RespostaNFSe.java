package net.accellog.sefaz4j.nfse.webservice;

public final class RespostaNFSe {
    private final boolean sucesso;
    private final String chaveAcesso;
    private final String xmlDescomprimido;
    private final String codigoErro;
    private final String mensagemErro;

    public RespostaNFSe(boolean sucesso, String chaveAcesso, String xmlDescomprimido, String codigoErro, String mensagemErro) {
        this.sucesso = sucesso;
        this.chaveAcesso = chaveAcesso;
        this.xmlDescomprimido = xmlDescomprimido;
        this.codigoErro = codigoErro;
        this.mensagemErro = mensagemErro;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public String getChaveAcesso() {
        return chaveAcesso;
    }

    public String getXmlDescomprimido() {
        return xmlDescomprimido;
    }

    public String getCodigoErro() {
        return codigoErro;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }
}
