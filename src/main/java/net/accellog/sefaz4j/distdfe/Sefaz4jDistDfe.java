package net.accellog.sefaz4j.distdfe;

import net.accellog.sefaz4j.endpoints.EndpointResolver;
import net.accellog.sefaz4j.endpoints.UF;
import net.accellog.sefaz4j.validacao.ValidadorXsd;
import net.accellog.sefaz4j.webservice.SefazHttpClient;

public final class Sefaz4jDistDfe {

    private Sefaz4jDistDfe() {
    }

    public static ResultadoDistribuicaoDFe distribuicaoPorUltNSU(TipoDocumentoDistDfe tipo, Sefaz4jConfig config, Integer cUFAutor, String cnpjCpf, String ultNSU) {
        exigirCUFAutorSeObrigatorio(tipo, cUFAutor);
        String xml = DistDfeIntXmlBuilder.porUltNsu(tipo, config.getAmbiente().getTpAmb(), cUFAutor, cnpjCpf, ultNSU);
        return executar(tipo, config, xml);
    }

    public static ResultadoDistribuicaoDFe distribuicaoPorNSU(TipoDocumentoDistDfe tipo, Sefaz4jConfig config, Integer cUFAutor, String cnpjCpf, String nsu) {
        exigirCUFAutorSeObrigatorio(tipo, cUFAutor);
        String xml = DistDfeIntXmlBuilder.porNsu(tipo, config.getAmbiente().getTpAmb(), cUFAutor, cnpjCpf, nsu);
        return executar(tipo, config, xml);
    }

    public static ResultadoDistribuicaoDFe distribuicaoPorChave(TipoDocumentoDistDfe tipo, Sefaz4jConfig config, Integer cUFAutor, String cnpjCpf, String chave) {
        exigirCUFAutorSeObrigatorio(tipo, cUFAutor);
        String xml = DistDfeIntXmlBuilder.porChave(tipo, config.getAmbiente().getTpAmb(), cUFAutor, cnpjCpf, chave);
        return executar(tipo, config, xml);
    }

    private static void exigirCUFAutorSeObrigatorio(TipoDocumentoDistDfe tipo, Integer cUFAutor) {
        if (tipo.isCUFAutorObrigatorio() && cUFAutor == null) {
            throw new IllegalArgumentException("cUFAutor e obrigatorio para Distribuicao de DFe de " + tipo);
        }
    }

    private static ResultadoDistribuicaoDFe executar(TipoDocumentoDistDfe tipo, Sefaz4jConfig config, String distDFeIntXml) {
        ValidadorXsd.validar(distDFeIntXml, tipo.getXsdRequisicao());

        String url = config.getUrlDistribuicaoDFeOverride() != null
            ? config.getUrlDistribuicaoDFeOverride()
            : EndpointResolver.resolver(tipo.getArquivoIni(), tipo.getPrefixoSecaoIni(), UF.AN, config.getAmbiente(), tipo.getChaveServicoIni());

        String envelope = montarEnvelopeSoap(tipo, distDFeIntXml);
        String contentType = "application/soap+xml; charset=utf-8; action=\"" + tipo.getNamespaceWsdl() + "/" + tipo.getElementoBodySoap() + "\"";

        String respostaBruta = SefazHttpClient.postar(url, contentType, envelope, config.getPfxBytes(), config.getSenhaPfx(), config.getTimeout());

        return RetDistDFeIntParser.parsear(respostaBruta);
    }

    static String montarEnvelopeSoap(TipoDocumentoDistDfe tipo, String distDFeIntXml) {
        String prefixoDadosMsg = tipo == TipoDocumentoDistDfe.NFE ? "nfe" : "cte";
        String elementoDadosMsg = prefixoDadosMsg + "DadosMsg";
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
            + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "
            + "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
            + "<soap12:Body>"
            + "<" + tipo.getElementoBodySoap() + " xmlns=\"" + tipo.getNamespaceWsdl() + "\">"
            + "<" + elementoDadosMsg + ">" + distDFeIntXml + "</" + elementoDadosMsg + ">"
            + "</" + tipo.getElementoBodySoap() + ">"
            + "</soap12:Body>"
            + "</soap12:Envelope>";
    }
}
