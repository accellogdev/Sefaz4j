package net.accellog.sefaz4j.cte;

import net.accellog.sefaz4j.assinatura.AssinadorXml;
import net.accellog.sefaz4j.cte.endpoints.Servico;
import net.accellog.sefaz4j.cte.model.TCTe;
import net.accellog.sefaz4j.cte.webservice.SoapEnvelopeBuilder;
import net.accellog.sefaz4j.cte.xml.CTeXmlBuilder;
import net.accellog.sefaz4j.endpoints.EndpointResolver;
import net.accellog.sefaz4j.validacao.ValidadorXsd;
import net.accellog.sefaz4j.webservice.RespostaSefaz;
import net.accellog.sefaz4j.webservice.RespostaSefazParser;
import net.accellog.sefaz4j.webservice.SefazHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class Sefaz4jCTe {

    private static final String CTE_NAMESPACE = "http://www.portalfiscal.inf.br/cte";
    private static final String CTE_XSD_RAIZ = "/schemas/cte/cte_v4.00.xsd";
    private static final String CTE_SERVICOS_INI = "/endpoints/cte-servicos.ini";
    private static final String PREFIXO_SECAO_CTE = "CTE_";
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 15;
    private static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 255;

    private Sefaz4jCTe() {
    }

    public static ResultadoEmissao emitir(Sefaz4jConfig config, TCTe cte) {
        // Mesma proteção de Sefaz4jNFe.emitir: emitir um documento cujo
        // ide/tpAmb diverge do Ambiente configurado é um erro de uso grave
        // (um CT-e de homologação enviado ao endpoint de produção, ou o
        // inverso), então falha cedo — antes de montar, assinar ou transmitir
        // qualquer coisa.
        verificarTpAmbCompativel(config, cte);

        Document documento = montarDocumento(cte);

        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), CTE_NAMESPACE, "infCte");

        String xmlAssinado = serializarDocumento(documento);

        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoEmissao enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado) {
        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoConsulta consultarSituacao(Sefaz4jConfig config, String chaveAcesso) {
        exigirChaveAcessoValida(chaveAcesso);

        String xmlConsulta = "<consSitCTe xmlns=\"" + CTE_NAMESPACE + "\" versao=\"4.00\">" +
            "<tpAmb>" + config.getAmbiente().getTpAmb() + "</tpAmb>" +
            "<xServ>CONSULTAR</xServ>" +
            "<chCTe>" + chaveAcesso + "</chCTe>" +
            "</consSitCTe>";

        ValidadorXsd.validar(xmlConsulta, "/schemas/cte/consSitCTe_v4.00.xsd");

        String url = config.getUrlConsultaProtocoloOverride() != null
            ? config.getUrlConsultaProtocoloOverride()
            : EndpointResolver.resolver(CTE_SERVICOS_INI, PREFIXO_SECAO_CTE, config.getUf(), config.getAmbiente(), Servico.CTE_CONSULTA_PROTOCOLO.getChaveIni());

        String envelope = SoapEnvelopeBuilder.envelopeConsultaSituacao(xmlConsulta);
        String respostaBruta = SefazHttpClient.postar(
            url,
            "http://www.portalfiscal.inf.br/cte/wsdl/CTeConsultaV4/cteConsultaCT",
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "protCTe", "chCTe");

        return new ResultadoConsulta(
            "100".equals(resposta.getCStat()),
            resposta.getCStat(),
            resposta.getXMotivo(),
            resposta.getChaveDocumento(),
            resposta.getProtocoloXml()
        );
    }

    private static void verificarTpAmbCompativel(Sefaz4jConfig config, TCTe cte) {
        if (cte.getInfCte() == null || cte.getInfCte().getIde() == null) {
            return;
        }
        String tpAmbIde = cte.getInfCte().getIde().getTpAmb();
        if (tpAmbIde == null || tpAmbIde.isEmpty()) {
            return;
        }
        String tpAmbEsperado = String.valueOf(config.getAmbiente().getTpAmb());
        if (!tpAmbEsperado.equals(tpAmbIde)) {
            throw new IllegalArgumentException(
                "ide.tpAmb (" + tpAmbIde + ") não corresponde ao Ambiente configurado ("
                    + config.getAmbiente() + ", tpAmb=" + tpAmbEsperado + ")"
            );
        }
    }

    private static Document montarDocumento(TCTe cte) {
        try {
            return CTeXmlBuilder.marcarChaveEMontarDocumento(cte);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML do CT-e", e);
        }
    }

    private static String serializarDocumento(Document documento) {
        try {
            return serializar(documento);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o XML do CT-e", e);
        }
    }

    private static ResultadoEmissao enviarEProcessar(Sefaz4jConfig config, String xmlAssinado) {
        ValidadorXsd.validar(xmlAssinado, CTE_XSD_RAIZ);

        String url = config.getUrlAutorizacaoOverride() != null
            ? config.getUrlAutorizacaoOverride()
            : EndpointResolver.resolver(CTE_SERVICOS_INI, PREFIXO_SECAO_CTE, config.getUf(), config.getAmbiente(), Servico.CTE_RECEPCAO_SINC.getChaveIni());

        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoSinc(xmlAssinado);
        String respostaBruta = SefazHttpClient.postar(
            url,
            "http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSincV4/cteRecepcao",
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "protCTe", "chCTe");

        // Mesmo padrão de fallback já usado por Sefaz4jNFe.enviarEProcessar: quando há protocolo
        // (protCTe/infProt), o cStat/xMotivo que importa é o do CT-e individual dentro dele; sem
        // protocolo (rejeição sem chegar a ser protocolada), usamos o cStat/xMotivo de nível
        // superior do próprio retCTe.
        String protocoloXml = resposta.getProtocoloXml();
        String cStatFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "cStat") : resposta.getCStat();
        String xMotivoFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "xMotivo") : resposta.getXMotivo();

        boolean autorizado = "100".equals(cStatFinal);
        String xmlFinal = protocoloXml != null
            ? "<cteProc versao=\"4.00\" xmlns=\"" + CTE_NAMESPACE + "\">" + xmlAssinado + protocoloXml + "</cteProc>"
            : xmlAssinado;

        return new ResultadoEmissao(autorizado, cStatFinal, xMotivoFinal, resposta.getChaveDocumento(), xmlFinal);
    }

    private static String extrairTextoDoElemento(String xmlFragmento, String nomeLocalElemento) {
        try {
            Document documento = criarDocumentBuilderSeguro().parse(
                new ByteArrayInputStream(xmlFragmento.getBytes(StandardCharsets.UTF_8))
            );
            NodeList lista = documento.getElementsByTagNameNS("*", nomeLocalElemento);
            return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static javax.xml.parsers.DocumentBuilder criarDocumentBuilderSeguro() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder();
    }

    private static String serializar(Document documento) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(documento), new StreamResult(writer));
        return writer.toString();
    }

    private static void exigirTamanho(String texto, int tamanhoMinimo, int tamanhoMaximo, String nomeCampo) {
        if (texto == null || texto.length() < tamanhoMinimo || texto.length() > tamanhoMaximo) {
            throw new IllegalArgumentException(
                "O campo '" + nomeCampo + "' deve ter entre " + tamanhoMinimo + " e " + tamanhoMaximo + " caracteres"
            );
        }
    }

    private static void exigirChaveAcessoValida(String chaveAcesso) {
        if (chaveAcesso == null || !chaveAcesso.matches("[0-9]{44}")) {
            throw new IllegalArgumentException(
                "O campo 'chaveAcesso' deve ter exatamente 44 dígitos numéricos, obteve: '" + chaveAcesso + "'"
            );
        }
    }

    private static String escaparTextoXml(String texto) {
        return texto
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
