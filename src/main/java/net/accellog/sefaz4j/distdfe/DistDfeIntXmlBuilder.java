package net.accellog.sefaz4j.distdfe;

public final class DistDfeIntXmlBuilder {

    private DistDfeIntXmlBuilder() {
    }

    public static String porUltNsu(TipoDocumentoDistDfe tipo, int tpAmb, Integer cUFAutor, String cnpjCpf, String ultNsu) {
        return montar(tipo, tpAmb, cUFAutor, cnpjCpf, "<distNSU><ultNSU>" + zeroPad15(ultNsu) + "</ultNSU></distNSU>");
    }

    public static String porNsu(TipoDocumentoDistDfe tipo, int tpAmb, Integer cUFAutor, String cnpjCpf, String nsu) {
        return montar(tipo, tpAmb, cUFAutor, cnpjCpf, "<consNSU><NSU>" + zeroPad15(nsu) + "</NSU></consNSU>");
    }

    public static String porChave(TipoDocumentoDistDfe tipo, int tpAmb, Integer cUFAutor, String cnpjCpf, String chave) {
        return montar(tipo, tpAmb, cUFAutor, cnpjCpf, "<consChNFe><chNFe>" + chave + "</chNFe></consChNFe>");
    }

    private static String montar(TipoDocumentoDistDfe tipo, int tpAmb, Integer cUFAutor, String cnpjCpf, String grupoConsulta) {
        String cUFAutorXml = cUFAutor != null ? "<cUFAutor>" + cUFAutor + "</cUFAutor>" : "";
        String documentoIdentificador = cnpjCpf.length() == 14
            ? "<CNPJ>" + cnpjCpf + "</CNPJ>"
            : "<CPF>" + cnpjCpf + "</CPF>";

        return "<distDFeInt versao=\"1.01\" xmlns=\"" + tipo.getNamespaceDocumento() + "\">"
            + "<tpAmb>" + tpAmb + "</tpAmb>"
            + cUFAutorXml
            + documentoIdentificador
            + grupoConsulta
            + "</distDFeInt>";
    }

    private static String zeroPad15(String nsu) {
        String limpo = nsu == null || nsu.isBlank() ? "0" : nsu.trim();
        return "0".repeat(Math.max(0, 15 - limpo.length())) + limpo;
    }
}
