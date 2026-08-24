package net.accellog.sefaz4j.nfse;

import net.accellog.sefaz4j.assinatura.AssinadorXml;
import net.accellog.sefaz4j.nfse.model.TCDPS;
import net.accellog.sefaz4j.nfse.webservice.PayloadCompactado;
import net.accellog.sefaz4j.nfse.webservice.RespostaNFSe;
import net.accellog.sefaz4j.nfse.webservice.RespostaNFSeParser;
import net.accellog.sefaz4j.nfse.xml.DpsXmlBuilder;
import net.accellog.sefaz4j.validacao.ValidadorXsd;
import net.accellog.sefaz4j.webservice.SefazHttpClient;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.signature.XMLSignature;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Fachada da NFS-e (Padrão Nacional / SEFIN Nacional).
 *
 * <p>Diferente de {@link net.accellog.sefaz4j.nfe.Sefaz4jNFe} e
 * {@link net.accellog.sefaz4j.cte.Sefaz4jCTe}, o transporte aqui é REST/JSON (não SOAP): o XML da DPS
 * assinado é comprimido em gzip, codificado em Base64 e enviado dentro de um objeto JSON; a resposta
 * traz o XML da NFS-e pelo mesmo caminho inverso.</p>
 */
public final class Sefaz4jNFSe {

    private static final String NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";
    private static final String DPS_XSD_RAIZ = "/schemas/nfse/DPS_v1.01.xsd";
    // ATENÇÃO: algoritmo NÃO confirmado contra o Manual de Integração do SEFIN Nacional nem
    // testado empiricamente contra Homologação. RSA-SHA256/SHA-256 é o palpite mais provável
    // (padrão federal mais recente), não um fato verificado. Se a Homologação real rejeitar a
    // assinatura, troque só estas duas constantes.
    private static final String ALGORITMO_ASSINATURA = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256;
    private static final String ALGORITMO_DIGEST = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256;
    private static final String URL_PRODUCAO = "https://sefin.nfse.gov.br/sefinnacional/nfse";
    private static final String URL_HOMOLOGACAO = "https://sefin.producaorestrita.nfse.gov.br/SefinNacional/nfse";

    private Sefaz4jNFSe() {
    }

    public static ResultadoEmissao emitir(Sefaz4jConfig config, TCDPS dps) {
        Document documento = montarDocumento(dps);

        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), NFSE_NAMESPACE, "infDPS", ALGORITMO_ASSINATURA, ALGORITMO_DIGEST);

        String xmlAssinado = serializarDocumento(documento);

        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoEmissao enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado) {
        return enviarEProcessar(config, xmlAssinado);
    }

    private static Document montarDocumento(TCDPS dps) {
        try {
            return DpsXmlBuilder.marcarIdEMontarDocumento(dps);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML da DPS", e);
        }
    }

    private static ResultadoEmissao enviarEProcessar(Sefaz4jConfig config, String xmlAssinado) {
        ValidadorXsd.validar(xmlAssinado, DPS_XSD_RAIZ);

        String url = config.getUrlOverride() != null
            ? config.getUrlOverride()
            : baseUrlEmissao(config);

        String corpoJson = PayloadCompactado.montarRequisicaoJson("dpsXmlGZipB64", xmlAssinado);
        String respostaBruta = SefazHttpClient.postar(
            url,
            "application/json",
            corpoJson,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaNFSe resposta = RespostaNFSeParser.parsear(respostaBruta, "nfseXmlGZipB64");

        if (!resposta.isSucesso()) {
            return new ResultadoEmissao(false, resposta.getCodigoErro(), resposta.getMensagemErro(), null, null);
        }

        String cStat = extrairTextoDoElemento(resposta.getXmlDescomprimido(), "cStat");
        return new ResultadoEmissao("100".equals(cStat), cStat, null, resposta.getChaveAcesso(), resposta.getXmlDescomprimido());
    }

    static String baseUrlEmissao(Sefaz4jConfig config) {
        return config.getAmbiente() == net.accellog.sefaz4j.endpoints.Ambiente.PRODUCAO ? URL_PRODUCAO : URL_HOMOLOGACAO;
    }

    private static String extrairTextoDoElemento(String xml, String nomeLocalElemento) {
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document documento = dbf.newDocumentBuilder().parse(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
            org.w3c.dom.NodeList lista = documento.getElementsByTagNameNS("*", nomeLocalElemento);
            return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String serializarDocumento(Document documento) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(documento), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o XML da DPS", e);
        }
    }
}
