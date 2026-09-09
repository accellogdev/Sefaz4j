package net.accellog.sefaz4j.cte;

import net.accellog.sefaz4j.assinatura.AssinadorXml;
import net.accellog.sefaz4j.cte.endpoints.Servico;
import net.accellog.sefaz4j.cte.model.TCTe;
import net.accellog.sefaz4j.cte.webservice.SoapEnvelopeBuilder;
import net.accellog.sefaz4j.cte.xml.CTeXmlBuilder;
import net.accellog.sefaz4j.cte.xml.EventoCTeXmlBuilder;
import net.accellog.sefaz4j.endpoints.EndpointResolver;
import net.accellog.sefaz4j.validacao.ValidadorXsd;
import net.accellog.sefaz4j.webservice.GzipBase64;
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
import java.util.List;

public final class Sefaz4jCTe {

    private static final String CTE_NAMESPACE = "http://www.portalfiscal.inf.br/cte";
    private static final String CTE_XSD_RAIZ = "/schemas/cte/cte_v4.00.xsd";
    private static final String CTE_SERVICOS_INI = "/endpoints/cte-servicos.ini";
    private static final String PREFIXO_SECAO_CTE = "CTE_";
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 15;
    private static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 255;

    private static final String CTE_VERSAO = "4.00";
    private static final String CONS_SIT_CTE_XSD = "/schemas/cte/consSitCTe_v4.00.xsd";
    private static final String EVENTO_CTE_XSD = "/schemas/cte/eventoCTe_v4.00.xsd";
    private static final String EV_CANC_CTE_XSD = "/schemas/cte/evCancCTe_v4.00.xsd";
    private static final String EV_CCE_CTE_XSD = "/schemas/cte/evCCeCTe_v4.00.xsd";
    private static final String CONTENT_TYPE_CONSULTA = "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeConsultaV4/cteConsultaCT\"";
    private static final String CONTENT_TYPE_RECEPCAO_EVENTO = "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoEventoV4/cteRecepcaoEvento\"";

    private static final String X_COND_USO_CCE_CTE =
        "A Carta de Correção é disciplinada pelo Art. 58-B do CONVÊNIO/SINIEF 06/89: Fica permitida a " +
        "utilização de carta de correção, para regularização de erro ocorrido na emissão de documentos " +
        "fiscais relativos à prestação de serviço de transporte, desde que o erro não esteja relacionado " +
        "com: I - as variáveis que determinam o valor do imposto tais como: base de cálculo, alíquota, " +
        "diferença de preço, quantidade, valor da prestação;II - a correção de dados cadastrais que " +
        "implique mudança do emitente, tomador, remetente ou do destinatário;III - a data de emissão ou " +
        "de saída.";

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

        // prefixoAssinatura="" (em vez do "ds:" default do Apache Santuario): confirmado
        // empiricamente contra a SEFAZ-PR (homologação) que a mesma regra da NFS-e/SEFIN
        // Nacional (E1228) também vale aqui -- sem isso, a SEFAZ rejeita com cStat 598 ("Usar
        // somente o namespace padrao do CTe"), mesmo com o CTe/infCte já sem prefixo.
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), CTE_NAMESPACE, "infCte", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1, "");

        String xmlAssinado = serializarDocumento(documento);

        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoEmissao enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado) {
        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoConsulta consultarSituacao(Sefaz4jConfig config, String chaveAcesso) {
        exigirChaveAcessoValida(chaveAcesso);

        String xmlConsulta = "<consSitCTe xmlns=\"" + CTE_NAMESPACE + "\" versao=\"" + CTE_VERSAO + "\">" +
            "<tpAmb>" + config.getAmbiente().getTpAmb() + "</tpAmb>" +
            "<xServ>CONSULTAR</xServ>" +
            "<chCTe>" + chaveAcesso + "</chCTe>" +
            "</consSitCTe>";

        ValidadorXsd.validar(xmlConsulta, CONS_SIT_CTE_XSD);

        String url = config.getUrlConsultaProtocoloOverride() != null
            ? config.getUrlConsultaProtocoloOverride()
            : EndpointResolver.resolver(CTE_SERVICOS_INI, PREFIXO_SECAO_CTE, config.getUf(), config.getAmbiente(), Servico.CTE_CONSULTA_PROTOCOLO.getChaveIni());

        String envelope = SoapEnvelopeBuilder.envelopeConsultaSituacao(xmlConsulta);
        String respostaBruta = SefazHttpClient.postar(
            url,
            CONTENT_TYPE_CONSULTA,
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

    public static ResultadoEvento cancelar(Sefaz4jConfig config, String chaveAcesso, String nProt, String justificativa) {
        exigirChaveAcessoValida(chaveAcesso);
        exigirTamanho(justificativa, TAMANHO_MINIMO_JUSTIFICATIVA, TAMANHO_MAXIMO_JUSTIFICATIVA, "justificativa do cancelamento");
        if (nProt == null || !nProt.matches("[0-9]{15}")) {
            throw new IllegalArgumentException("O campo 'nProt' deve ter exatamente 15 dígitos numéricos, obteve: '" + nProt + "'");
        }

        String cUF = chaveAcesso.substring(0, 2);
        String cnpj = chaveAcesso.substring(6, 20);

        String evCancCTeFragmento = "<evCancCTe xmlns=\"" + CTE_NAMESPACE + "\">" +
            "<descEvento>Cancelamento</descEvento>" +
            "<nProt>" + nProt + "</nProt>" +
            "<xJust>" + escaparTextoXml(justificativa) + "</xJust>" +
            "</evCancCTe>";
        ValidadorXsd.validar(evCancCTeFragmento, EV_CANC_CTE_XSD);

        String detEvento = "<detEvento versaoEvento=\"" + CTE_VERSAO + "\">" + evCancCTeFragmento + "</detEvento>";

        Document documento = EventoCTeXmlBuilder.montar(
            cUF, String.valueOf(config.getAmbiente().getTpAmb()), cnpj, chaveAcesso, "110111", 1, CTE_VERSAO, detEvento
        );
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), CTE_NAMESPACE, "infEvento", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);
        String xmlEventoAssinado = serializarDocumento(documento);

        return enviarEProcessarEvento(config, xmlEventoAssinado);
    }

    public static ResultadoEvento corrigirCartaDeCorrecao(Sefaz4jConfig config, String chaveAcesso, List<InfCorrecao> correcoes) {
        return corrigirCartaDeCorrecao(config, chaveAcesso, correcoes, 1);
    }

    public static ResultadoEvento corrigirCartaDeCorrecao(Sefaz4jConfig config, String chaveAcesso, List<InfCorrecao> correcoes, int nSeqEvento) {
        exigirChaveAcessoValida(chaveAcesso);
        if (correcoes == null || correcoes.isEmpty()) {
            throw new IllegalArgumentException("A lista 'correcoes' deve ter ao menos um item");
        }
        for (InfCorrecao correcao : correcoes) {
            exigirTamanho(correcao.getGrupoAlterado(), 1, 20, "grupoAlterado");
            exigirTamanho(correcao.getCampoAlterado(), 1, 20, "campoAlterado");
            exigirTamanho(correcao.getValorAlterado(), 1, 500, "valorAlterado");
        }

        String cUF = chaveAcesso.substring(0, 2);
        String cnpj = chaveAcesso.substring(6, 20);

        StringBuilder infCorrecoesXml = new StringBuilder();
        for (InfCorrecao correcao : correcoes) {
            infCorrecoesXml.append("<infCorrecao>")
                .append("<grupoAlterado>").append(escaparTextoXml(correcao.getGrupoAlterado())).append("</grupoAlterado>")
                .append("<campoAlterado>").append(escaparTextoXml(correcao.getCampoAlterado())).append("</campoAlterado>")
                .append("<valorAlterado>").append(escaparTextoXml(correcao.getValorAlterado())).append("</valorAlterado>");
            if (correcao.getNroItemAlterado() != null) {
                infCorrecoesXml.append("<nroItemAlterado>").append(correcao.getNroItemAlterado()).append("</nroItemAlterado>");
            }
            infCorrecoesXml.append("</infCorrecao>");
        }

        String evCCeCTeFragmento = "<evCCeCTe xmlns=\"" + CTE_NAMESPACE + "\">" +
            "<descEvento>Carta de Correção</descEvento>" +
            infCorrecoesXml +
            "<xCondUso>" + escaparTextoXml(X_COND_USO_CCE_CTE) + "</xCondUso>" +
            "</evCCeCTe>";
        ValidadorXsd.validar(evCCeCTeFragmento, EV_CCE_CTE_XSD);

        String detEvento = "<detEvento versaoEvento=\"" + CTE_VERSAO + "\">" + evCCeCTeFragmento + "</detEvento>";

        Document documento = EventoCTeXmlBuilder.montar(
            cUF, String.valueOf(config.getAmbiente().getTpAmb()), cnpj, chaveAcesso, "110110", nSeqEvento, CTE_VERSAO, detEvento
        );
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), CTE_NAMESPACE, "infEvento", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);
        String xmlEventoAssinado = serializarDocumento(documento);

        return enviarEProcessarEvento(config, xmlEventoAssinado);
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

        // O MOC do CT-e exige que o conteúdo de cteDadosMsg na recepção síncrona venha
        // compactado em gzip e codificado em Base64 (diferente das demais operações, que
        // trafegam XML puro) -- sem isso a SEFAZ responde cStat 244 "Falha na descompactação
        // da área de dados", mesmo com o XML assinado e válido contra a XSD.
        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoSinc(GzipBase64.comprimirECodificar(xmlAssinado));
        String respostaBruta = SefazHttpClient.postar(
            url,
            "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSincV4/cteRecepcao\"",
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

        return new ResultadoEmissao(autorizado, cStatFinal, xMotivoFinal, resposta.getChaveDocumento(), xmlAssinado, xmlFinal);
    }

    private static ResultadoEvento enviarEProcessarEvento(Sefaz4jConfig config, String xmlEventoAssinado) {
        ValidadorXsd.validar(xmlEventoAssinado, EVENTO_CTE_XSD);

        String url = config.getUrlRecepcaoEventoOverride() != null
            ? config.getUrlRecepcaoEventoOverride()
            : EndpointResolver.resolver(CTE_SERVICOS_INI, PREFIXO_SECAO_CTE, config.getUf(), config.getAmbiente(), Servico.CTE_RECEPCAO_EVENTO.getChaveIni());

        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoEvento(xmlEventoAssinado);
        String respostaBruta = SefazHttpClient.postar(
            url,
            CONTENT_TYPE_RECEPCAO_EVENTO,
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "retEventoCTe");

        // protocoloXml (quando presente) É o retEventoCTe/infEvento completo — CTe não tem um nível de
        // lote separado como o NFe (TRetEvento não tem cStat/xMotivo próprios fora de infEvento). Sem
        // protocolo (rejeição antes de qualquer infEvento existir), caímos no cStat/xMotivo de nível
        // superior do próprio retEventoCTe como único fallback disponível.
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

    private static String escaparTextoXml(String texto) {
        return texto
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
