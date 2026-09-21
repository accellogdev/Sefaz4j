package net.accellog.sefaz4j.distdfe;

import java.util.List;

public final class ResultadoDistribuicaoDFe {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String ultNSU;
    private final String maxNSU;
    private final List<DocumentoDistDfe> documentos;
    private final String xmlEnvio;
    private final String xmlRetorno;

    public ResultadoDistribuicaoDFe(boolean ok, String cStat, String xMotivo, String ultNSU, String maxNSU, List<DocumentoDistDfe> documentos) {
        this(ok, cStat, xMotivo, ultNSU, maxNSU, documentos, null, null);
    }

    // xmlEnvio/xmlRetorno: XML bruto de request/response, usado so' para auditoria/log (ver
    // sefaz.distdfe_log no bot-dist-dfe) - nunca lido para decisao de negocio (cStat/ultNSU/etc.
    // acima ja cobrem isso). Construtor de 6 args acima mantido para nao quebrar quem monta este
    // DTO diretamente (ex.: RetDistDFeIntParser, testes) sem ter os XMLs brutos em maos.
    public ResultadoDistribuicaoDFe(boolean ok, String cStat, String xMotivo, String ultNSU, String maxNSU, List<DocumentoDistDfe> documentos, String xmlEnvio, String xmlRetorno) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.ultNSU = ultNSU;
        this.maxNSU = maxNSU;
        this.documentos = documentos;
        this.xmlEnvio = xmlEnvio;
        this.xmlRetorno = xmlRetorno;
    }

    public boolean isOk() { return ok; }
    public String getCStat() { return cStat; }
    public String getXMotivo() { return xMotivo; }
    public String getUltNSU() { return ultNSU; }
    public String getMaxNSU() { return maxNSU; }
    public List<DocumentoDistDfe> getDocumentos() { return documentos; }
    public String getXmlEnvio() { return xmlEnvio; }
    public String getXmlRetorno() { return xmlRetorno; }
    public boolean temMaisDocumentos() { return "138".equals(cStat); }
    public boolean nadaMaisADistribuir() { return "137".equals(cStat); }
    public boolean consumoIndevido() { return "656".equals(cStat); }
}
