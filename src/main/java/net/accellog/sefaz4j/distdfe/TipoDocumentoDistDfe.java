package net.accellog.sefaz4j.distdfe;

public enum TipoDocumentoDistDfe {
    NFE(
        "http://www.portalfiscal.inf.br/nfe",
        "1.01",
        "/schemas/distdfe/distDFeInt_v1.01.xsd",
        "http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe",
        "nfeDistDFeInteresse",
        "/endpoints/nfe-servicos.ini",
        "NFE_",
        "NFeDistribuicaoDFe_1.01",
        false
    ),
    // namespaceDocumento = "http://www.portalfiscal.inf.br/cte" (nao "nfe"): o
    // distDFeInt_v1.00.xsd que o ACBr distribui em Exemplos/ACBrDFe/Schemas/CTe/ e' uma copia
    // byte-a-byte do mesmo arquivo em Schemas/NFe/ (confirmado com diff) -- um artefato de
    // exemplo desatualizado (datado 2017), nao uma fonte curada especificamente para CTe. O
    // codigo Delphi real do ACBr que efetivamente conversa com a SEFAZ
    // (ACBrCTe.Consts.NAME_SPACE_CTE, usado em ACBrCTeWebServices.pas ao montar o
    // TDistDFeInt para CTe) usa "http://www.portalfiscal.inf.br/cte". Confirmado empiricamente:
    // com "nfe" a SEFAZ (hom1.cte.fazenda.gov.br) rejeitava com cStat=215 "Falha no esquema
    // xml" em toda chamada.
    CTE(
        "http://www.portalfiscal.inf.br/cte",
        "1.00",
        "/schemas/distdfe/distDFeInt_cte_v1.00.xsd",
        "http://www.portalfiscal.inf.br/cte/wsdl/CTeDistribuicaoDFe",
        "cteDistDFeInteresse",
        "/endpoints/cte-servicos.ini",
        "CTE_",
        "CTeDistribuicaoDFe_1.00",
        true
    );

    private final String namespaceDocumento;
    private final String versaoDocumento;
    private final String xsdRequisicao;
    private final String namespaceWsdl;
    private final String elementoBodySoap;
    private final String arquivoIni;
    private final String prefixoSecaoIni;
    private final String chaveServicoIni;
    private final boolean cUFAutorObrigatorio;

    TipoDocumentoDistDfe(String namespaceDocumento, String versaoDocumento, String xsdRequisicao,
                          String namespaceWsdl, String elementoBodySoap, String arquivoIni,
                          String prefixoSecaoIni, String chaveServicoIni, boolean cUFAutorObrigatorio) {
        this.namespaceDocumento = namespaceDocumento;
        this.versaoDocumento = versaoDocumento;
        this.xsdRequisicao = xsdRequisicao;
        this.namespaceWsdl = namespaceWsdl;
        this.elementoBodySoap = elementoBodySoap;
        this.arquivoIni = arquivoIni;
        this.prefixoSecaoIni = prefixoSecaoIni;
        this.chaveServicoIni = chaveServicoIni;
        this.cUFAutorObrigatorio = cUFAutorObrigatorio;
    }

    public String getNamespaceDocumento() { return namespaceDocumento; }
    public String getVersaoDocumento() { return versaoDocumento; }
    public String getXsdRequisicao() { return xsdRequisicao; }
    public String getNamespaceWsdl() { return namespaceWsdl; }
    public String getElementoBodySoap() { return elementoBodySoap; }
    public String getArquivoIni() { return arquivoIni; }
    public String getPrefixoSecaoIni() { return prefixoSecaoIni; }
    public String getChaveServicoIni() { return chaveServicoIni; }
    public boolean isCUFAutorObrigatorio() { return cUFAutorObrigatorio; }
}
