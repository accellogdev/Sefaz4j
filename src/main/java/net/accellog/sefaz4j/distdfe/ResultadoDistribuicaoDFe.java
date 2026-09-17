package net.accellog.sefaz4j.distdfe;

import java.util.List;

public final class ResultadoDistribuicaoDFe {
    private final boolean ok;
    private final String cStat;
    private final String xMotivo;
    private final String ultNSU;
    private final String maxNSU;
    private final List<DocumentoDistDfe> documentos;

    public ResultadoDistribuicaoDFe(boolean ok, String cStat, String xMotivo, String ultNSU, String maxNSU, List<DocumentoDistDfe> documentos) {
        this.ok = ok;
        this.cStat = cStat;
        this.xMotivo = xMotivo;
        this.ultNSU = ultNSU;
        this.maxNSU = maxNSU;
        this.documentos = documentos;
    }

    public boolean isOk() { return ok; }
    public String getCStat() { return cStat; }
    public String getXMotivo() { return xMotivo; }
    public String getUltNSU() { return ultNSU; }
    public String getMaxNSU() { return maxNSU; }
    public List<DocumentoDistDfe> getDocumentos() { return documentos; }
    public boolean temMaisDocumentos() { return "138".equals(cStat); }
    public boolean nadaMaisADistribuir() { return "137".equals(cStat); }
    public boolean consumoIndevido() { return "656".equals(cStat); }
}
