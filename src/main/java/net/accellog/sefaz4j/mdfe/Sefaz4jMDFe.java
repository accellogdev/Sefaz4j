package net.accellog.sefaz4j.mdfe;

import net.accellog.sefaz4j.assinatura.AssinadorXml;
import net.accellog.sefaz4j.endpoints.EndpointResolver;
import net.accellog.sefaz4j.mdfe.endpoints.Servico;
import net.accellog.sefaz4j.mdfe.model.TMDFe;
import net.accellog.sefaz4j.mdfe.webservice.ReciboPoller;
import net.accellog.sefaz4j.mdfe.webservice.SoapEnvelopeBuilder;
import net.accellog.sefaz4j.mdfe.xml.EventoMDFeXmlBuilder;
import net.accellog.sefaz4j.mdfe.xml.MDFeXmlBuilder;
import net.accellog.sefaz4j.validacao.ValidadorXsd;
import net.accellog.sefaz4j.webservice.ComunicacaoException;
import net.accellog.sefaz4j.webservice.RespostaSefaz;
import net.accellog.sefaz4j.webservice.RespostaSefazParser;
import net.accellog.sefaz4j.webservice.SefazHttpClient;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.signature.XMLSignature;
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

/**
 * Facade de emissão do MDF-e (Manifesto Eletrônico de Documentos Fiscais), mesmo papel de
 * {@code net.accellog.sefaz4j.nfe.Sefaz4jNFe}/{@code net.accellog.sefaz4j.cte.Sefaz4jCTe}: monta,
 * assina, valida contra XSD e transmite um {@link TMDFe} (ou um XML já assinado), incluindo o
 * polling de lote (cStat 103) igual ao fluxo de NFe.
 */
public final class Sefaz4jMDFe {

    private static final String MDFE_NAMESPACE = "http://www.portalfiscal.inf.br/mdfe";
    private static final String MDFE_XSD_RAIZ = "/schemas/mdfe/mdfe_v3.00.xsd";
    private static final String MDFE_SERVICOS_INI = "/endpoints/mdfe-servicos.ini";
    private static final String PREFIXO_SECAO_MDFE = "MDFE_";
    private static final String MDFE_VERSAO = "3.00";
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 15;
    private static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 255;

    private static final String CONS_SIT_MDFE_XSD = "/schemas/mdfe/consSitMDFe_v3.00.xsd";
    private static final String EVENTO_MDFE_XSD = "/schemas/mdfe/eventoMDFe_v3.00.xsd";
    private static final String EV_CANC_MDFE_XSD = "/schemas/mdfe/evCancMDFe_v3.00.xsd";
    private static final String EV_ENC_MDFE_XSD = "/schemas/mdfe/evEncMDFe_v3.00.xsd";
    private static final String EV_INC_CONDUTOR_MDFE_XSD = "/schemas/mdfe/evIncCondutorMDFe_v3.00.xsd";
    private static final String CONTENT_TYPE_RECEPCAO = "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcao/mdfeRecepcaoLote\"";
    private static final String CONTENT_TYPE_CONSULTA = "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeConsulta/mdfeConsultaMDF\"";
    private static final String CONTENT_TYPE_RECEPCAO_EVENTO = "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento/mdfeRecepcaoEvento\"";

    private Sefaz4jMDFe() {
    }

    public static ResultadoEmissao emitir(Sefaz4jConfig config, TMDFe mdfe) {
        // Mesma proteção de Sefaz4jCTe.emitir/Sefaz4jNFe.emitir: emitir um documento cujo
        // ide/tpAmb diverge do Ambiente configurado é um erro de uso grave (um MDF-e de
        // homologação enviado ao endpoint de produção, ou o inverso), então falha cedo — antes
        // de montar, assinar ou transmitir qualquer coisa.
        verificarTpAmbCompativel(config, mdfe);

        Document documento = montarDocumento(mdfe);

        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), MDFE_NAMESPACE, "infMDFe", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);

        String xmlAssinado = serializarDocumento(documento);

        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoEmissao enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado) {
        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoConsulta consultarSituacao(Sefaz4jConfig config, String chaveAcesso) {
        exigirChaveAcessoValida(chaveAcesso);

        String xmlConsulta = "<consSitMDFe xmlns=\"" + MDFE_NAMESPACE + "\" versao=\"" + MDFE_VERSAO + "\">" +
            "<tpAmb>" + config.getAmbiente().getTpAmb() + "</tpAmb>" +
            "<xServ>CONSULTAR</xServ>" +
            "<chMDFe>" + chaveAcesso + "</chMDFe>" +
            "</consSitMDFe>";

        ValidadorXsd.validar(xmlConsulta, CONS_SIT_MDFE_XSD);

        String url = config.getUrlConsultaProtocoloOverride() != null
            ? config.getUrlConsultaProtocoloOverride()
            : EndpointResolver.resolver(MDFE_SERVICOS_INI, PREFIXO_SECAO_MDFE, config.getUf(), config.getAmbiente(), Servico.MDFE_CONSULTA_PROTOCOLO.getChaveIni());

        String envelope = SoapEnvelopeBuilder.envelopeConsultaSituacao(xmlConsulta);
        String respostaBruta = SefazHttpClient.postar(
            url,
            CONTENT_TYPE_CONSULTA,
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "protMDFe", "chMDFe");

        return new ResultadoConsulta(
            "100".equals(resposta.getCStat()),
            resposta.getCStat(),
            resposta.getXMotivo(),
            resposta.getChaveDocumento(),
            resposta.getProtocoloXml()
        );
    }

    public static ResultadoEvento cancelar(Sefaz4jConfig config, String chaveAcesso, String nProt, String justificativa) {
        exigirChaveAcessoValida(chaveAcesso);
        exigirTamanho(justificativa, TAMANHO_MINIMO_JUSTIFICATIVA, TAMANHO_MAXIMO_JUSTIFICATIVA, "justificativa do cancelamento");
        exigirNProtValido(nProt);

        String cUF = chaveAcesso.substring(0, 2);
        String cnpj = chaveAcesso.substring(6, 20);

        String evCancMDFeFragmento = "<evCancMDFe xmlns=\"" + MDFE_NAMESPACE + "\">" +
            "<descEvento>Cancelamento</descEvento>" +
            "<nProt>" + nProt + "</nProt>" +
            "<xJust>" + escaparTextoXml(justificativa) + "</xJust>" +
            "</evCancMDFe>";
        ValidadorXsd.validar(evCancMDFeFragmento, EV_CANC_MDFE_XSD);

        String detEvento = "<detEvento versaoEvento=\"" + MDFE_VERSAO + "\">" + evCancMDFeFragmento + "</detEvento>";

        Document documento = EventoMDFeXmlBuilder.montar(cUF, String.valueOf(config.getAmbiente().getTpAmb()), cnpj, chaveAcesso, "110111", 1, MDFE_VERSAO, detEvento);
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), MDFE_NAMESPACE, "infEvento", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);
        String xmlEventoAssinado = serializarDocumento(documento);

        return enviarEProcessarEvento(config, xmlEventoAssinado);
    }

    public static ResultadoEvento encerrar(Sefaz4jConfig config, String chaveAcesso, String nProt, String cUFEnc, String cMunEnc, String dtEnc) {
        exigirChaveAcessoValida(chaveAcesso);
        exigirNProtValido(nProt);

        String cUF = chaveAcesso.substring(0, 2);
        String cnpj = chaveAcesso.substring(6, 20);

        String evEncMDFeFragmento = "<evEncMDFe xmlns=\"" + MDFE_NAMESPACE + "\">" +
            "<descEvento>Encerramento</descEvento>" +
            "<nProt>" + nProt + "</nProt>" +
            "<dtEnc>" + dtEnc + "</dtEnc>" +
            "<cUF>" + cUFEnc + "</cUF>" +
            "<cMun>" + cMunEnc + "</cMun>" +
            "</evEncMDFe>";
        ValidadorXsd.validar(evEncMDFeFragmento, EV_ENC_MDFE_XSD);

        String detEvento = "<detEvento versaoEvento=\"" + MDFE_VERSAO + "\">" + evEncMDFeFragmento + "</detEvento>";

        Document documento = EventoMDFeXmlBuilder.montar(cUF, String.valueOf(config.getAmbiente().getTpAmb()), cnpj, chaveAcesso, "110112", 1, MDFE_VERSAO, detEvento);
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), MDFE_NAMESPACE, "infEvento", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);
        String xmlEventoAssinado = serializarDocumento(documento);

        return enviarEProcessarEvento(config, xmlEventoAssinado);
    }

    public static ResultadoEvento incluirCondutor(Sefaz4jConfig config, String chaveAcesso, String xNome, String cpf) {
        return incluirCondutor(config, chaveAcesso, xNome, cpf, 1);
    }

    public static ResultadoEvento incluirCondutor(Sefaz4jConfig config, String chaveAcesso, String xNome, String cpf, int nSeqEvento) {
        exigirChaveAcessoValida(chaveAcesso);

        String cUF = chaveAcesso.substring(0, 2);
        String cnpj = chaveAcesso.substring(6, 20);

        String evIncCondutorMDFeFragmento = "<evIncCondutorMDFe xmlns=\"" + MDFE_NAMESPACE + "\">" +
            "<descEvento>Inclusao Condutor</descEvento>" +
            "<condutor>" +
            "<xNome>" + escaparTextoXml(xNome) + "</xNome>" +
            "<CPF>" + cpf + "</CPF>" +
            "</condutor>" +
            "</evIncCondutorMDFe>";
        ValidadorXsd.validar(evIncCondutorMDFeFragmento, EV_INC_CONDUTOR_MDFE_XSD);

        String detEvento = "<detEvento versaoEvento=\"" + MDFE_VERSAO + "\">" + evIncCondutorMDFeFragmento + "</detEvento>";

        Document documento = EventoMDFeXmlBuilder.montar(cUF, String.valueOf(config.getAmbiente().getTpAmb()), cnpj, chaveAcesso, "110114", nSeqEvento, MDFE_VERSAO, detEvento);
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), MDFE_NAMESPACE, "infEvento", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);
        String xmlEventoAssinado = serializarDocumento(documento);

        return enviarEProcessarEvento(config, xmlEventoAssinado);
    }

    private static void verificarTpAmbCompativel(Sefaz4jConfig config, TMDFe mdfe) {
        if (mdfe.getInfMDFe() == null || mdfe.getInfMDFe().getIde() == null) {
            return;
        }
        String tpAmbIde = mdfe.getInfMDFe().getIde().getTpAmb();
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

    private static Document montarDocumento(TMDFe mdfe) {
        try {
            return MDFeXmlBuilder.marcarChaveEMontarDocumento(mdfe);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML do MDF-e", e);
        }
    }

    private static String serializarDocumento(Document documento) {
        try {
            return serializar(documento);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o XML do MDF-e", e);
        }
    }

    private static ResultadoEmissao enviarEProcessar(Sefaz4jConfig config, String xmlAssinado) {
        ValidadorXsd.validar(xmlAssinado, MDFE_XSD_RAIZ);

        String urlRecepcao = config.getUrlRecepcaoOverride() != null
            ? config.getUrlRecepcaoOverride()
            : EndpointResolver.resolver(MDFE_SERVICOS_INI, PREFIXO_SECAO_MDFE, config.getUf(), config.getAmbiente(), Servico.MDFE_RECEPCAO.getChaveIni());

        String envelope = SoapEnvelopeBuilder.envelopeRecepcao(xmlAssinado, 1L);
        String respostaBruta = SefazHttpClient.postar(
            urlRecepcao,
            CONTENT_TYPE_RECEPCAO,
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "protMDFe", "chMDFe");

        if ("103".equals(resposta.getCStat())) {
            String urlRetRecepcao = config.getUrlRetRecepcaoOverride() != null
                ? config.getUrlRetRecepcaoOverride()
                : EndpointResolver.resolver(MDFE_SERVICOS_INI, PREFIXO_SECAO_MDFE, config.getUf(), config.getAmbiente(), Servico.MDFE_RET_RECEPCAO.getChaveIni());
            resposta = ReciboPoller.aguardarProtocolo(
                resposta.getNRec(),
                config.getAmbiente().getTpAmb(),
                urlRetRecepcao,
                config.getPfxBytes(),
                config.getSenhaPfx(),
                config.getMaxTentativasPolling(),
                config.getIntervaloPolling(),
                config.getTimeout()
            );

            if ("103".equals(resposta.getCStat())) {
                throw new ComunicacaoException(
                    "Lote ainda em processamento (cStat 103) após esgotar as tentativas de polling; "
                        + "não é possível determinar se o MDF-e foi autorizado ou rejeitado",
                    null
                );
            }
        }

        // Mesmo padrão de fallback já usado por Sefaz4jNFe.enviarEProcessar/Sefaz4jCTe.enviarEProcessar:
        // quando há protocolo (protMDFe/infProt), o cStat/xMotivo que importa é o do MDF-e individual
        // dentro dele — o cStat de nível de lote (104, "lote processado") só confirma que o lote
        // terminou de processar, não o resultado do documento; sem protocolo (rejeição sem chegar a
        // ser protocolada), usamos o cStat/xMotivo de nível superior do próprio retEnviMDFe.
        String protocoloXml = resposta.getProtocoloXml();
        String cStatFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "cStat") : resposta.getCStat();
        String xMotivoFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "xMotivo") : resposta.getXMotivo();

        boolean autorizado = "100".equals(cStatFinal);
        String xmlFinal = protocoloXml != null
            ? "<mdfeProc versao=\"" + MDFE_VERSAO + "\" xmlns=\"" + MDFE_NAMESPACE + "\">" + xmlAssinado + protocoloXml + "</mdfeProc>"
            : xmlAssinado;

        return new ResultadoEmissao(autorizado, cStatFinal, xMotivoFinal, resposta.getChaveDocumento(), xmlFinal);
    }

    private static ResultadoEvento enviarEProcessarEvento(Sefaz4jConfig config, String xmlEventoAssinado) {
        ValidadorXsd.validar(xmlEventoAssinado, EVENTO_MDFE_XSD);

        String url = config.getUrlRecepcaoEventoOverride() != null
            ? config.getUrlRecepcaoEventoOverride()
            : EndpointResolver.resolver(MDFE_SERVICOS_INI, PREFIXO_SECAO_MDFE, config.getUf(), config.getAmbiente(), Servico.RECEPCAO_EVENTO.getChaveIni());

        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoEvento(xmlEventoAssinado);
        String respostaBruta = SefazHttpClient.postar(
            url,
            CONTENT_TYPE_RECEPCAO_EVENTO,
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "retEventoMDFe");

        // Mesmo padrão de fallback já usado por Sefaz4jCTe.enviarEProcessarEvento: protocoloXml
        // (quando presente) É o retEventoMDFe/infEvento completo — MDFe não tem um nível de lote
        // separado para eventos, igual ao CTe. Sem protocolo (rejeição antes de qualquer infEvento
        // existir), caímos no cStat/xMotivo de nível superior do próprio retEventoMDFe como único
        // fallback disponível.
        String protocoloXml = resposta.getProtocoloXml();
        String cStatFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "cStat") : resposta.getCStat();
        String xMotivoFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "xMotivo") : resposta.getXMotivo();
        String nProtFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "nProt") : null;

        return new ResultadoEvento("135".equals(cStatFinal), cStatFinal, xMotivoFinal, nProtFinal, protocoloXml);
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

    private static void exigirNProtValido(String nProt) {
        if (nProt == null || !nProt.matches("[0-9]{15}")) {
            throw new IllegalArgumentException("O campo 'nProt' deve ter exatamente 15 dígitos numéricos, obteve: '" + nProt + "'");
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
