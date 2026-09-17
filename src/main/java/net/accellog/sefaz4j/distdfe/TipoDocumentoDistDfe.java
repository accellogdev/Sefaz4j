package net.accellog.sefaz4j.distdfe;

public enum TipoDocumentoDistDfe {
    NFE(
        "http://www.portalfiscal.inf.br/nfe",
        "http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe",
        "nfeDistDFeInteresse",
        "/endpoints/nfe-servicos.ini",
        "NFE_",
        "NFeDistribuicaoDFe_1.01"
    ),
    CTE(
        "http://www.portalfiscal.inf.br/cte",
        "http://www.portalfiscal.inf.br/cte/wsdl/CTeDistribuicaoDFe",
        "cteDistDFeInteresse",
        "/endpoints/cte-servicos.ini",
        "CTE_",
        "CTeDistribuicaoDFe_1.00"
    );

    private final String namespaceDocumento;
    private final String namespaceWsdl;
    private final String elementoBodySoap;
    private final String arquivoIni;
    private final String prefixoSecaoIni;
    private final String chaveServicoIni;

    TipoDocumentoDistDfe(String namespaceDocumento, String namespaceWsdl, String elementoBodySoap,
                          String arquivoIni, String prefixoSecaoIni, String chaveServicoIni) {
        this.namespaceDocumento = namespaceDocumento;
        this.namespaceWsdl = namespaceWsdl;
        this.elementoBodySoap = elementoBodySoap;
        this.arquivoIni = arquivoIni;
        this.prefixoSecaoIni = prefixoSecaoIni;
        this.chaveServicoIni = chaveServicoIni;
    }

    public String getNamespaceDocumento() { return namespaceDocumento; }
    public String getNamespaceWsdl() { return namespaceWsdl; }
    public String getElementoBodySoap() { return elementoBodySoap; }
    public String getArquivoIni() { return arquivoIni; }
    public String getPrefixoSecaoIni() { return prefixoSecaoIni; }
    public String getChaveServicoIni() { return chaveServicoIni; }
}
