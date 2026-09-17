package net.accellog.sefaz4j.mdfe;

/**
 * Um documento (NF-e) a incluir num MDF-e de carga tardia, via o evento
 * {@code evIncDFeMDFe} (tpEvento 110115) — mesmo papel de
 * {@code net.accellog.sefaz4j.cte.InfCorrecao} para a carta de correção do CT-e. A XSD oficial
 * ({@code evInclusaoDFeMDFe_v3.00.xsd}) só aceita {@code chNFe} dentro de {@code infDoc}, não
 * {@code chCTe} — diferente do {@code infDoc} da emissão original do MDF-e, que aceita ambos.
 */
public final class InfDocInclusao {
    private final String cMunDescarga;
    private final String xMunDescarga;
    private final String chNFe;

    public InfDocInclusao(String cMunDescarga, String xMunDescarga, String chNFe) {
        this.cMunDescarga = cMunDescarga;
        this.xMunDescarga = xMunDescarga;
        this.chNFe = chNFe;
    }

    public String getCMunDescarga() {
        return cMunDescarga;
    }

    public String getXMunDescarga() {
        return xMunDescarga;
    }

    public String getChNFe() {
        return chNFe;
    }
}
