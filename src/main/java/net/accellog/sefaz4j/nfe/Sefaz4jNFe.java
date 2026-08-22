package net.accellog.sefaz4j.nfe;

import net.accellog.sefaz4j.nfe.assinatura.AssinadorXml;
import net.accellog.sefaz4j.nfe.endpoints.EndpointResolver;
import net.accellog.sefaz4j.nfe.endpoints.Servico;
import net.accellog.sefaz4j.nfe.model.TNFe;
import net.accellog.sefaz4j.nfe.validacao.ValidadorXsd;
import net.accellog.sefaz4j.nfe.webservice.ComunicacaoException;
import net.accellog.sefaz4j.nfe.webservice.ReciboPoller;
import net.accellog.sefaz4j.nfe.webservice.RespostaSefaz;
import net.accellog.sefaz4j.nfe.webservice.RespostaSefazParser;
import net.accellog.sefaz4j.nfe.webservice.SefazHttpClient;
import net.accellog.sefaz4j.nfe.webservice.SoapEnvelopeBuilder;
import net.accellog.sefaz4j.nfe.xml.EventoXmlBuilder;
import net.accellog.sefaz4j.nfe.xml.NFeXmlBuilder;
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

public final class Sefaz4jNFe {

    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 15;

    private Sefaz4jNFe() {
    }

    public static ResultadoEmissao emitir(Sefaz4jConfig config, TNFe nfe) {
        // ok=false deve significar SOMENTE "SEFAZ retornou cStat != 100" —
        // toda falha técnica (montagem/serialização do XML, assinatura,
        // validação de schema, comunicação) deve se propagar como exceção,
        // nunca virar um ResultadoEmissao(ok=false) fabricado. Os três tipos
        // nomeados pelo spec ("Tratamento de erros") — CertificadoException
        // (AssinadorXml.assinar), ValidacaoXsdException/ComunicacaoException
        // (enviarEProcessar) — já são RuntimeException e se propagam
        // naturalmente. montarDocumento()/serializarDocumento() abaixo só
        // convertem a checked Exception declarada por
        // NFeXmlBuilder/Transformer em uma unchecked equivalente — SEM
        // engolir o erro nem RuntimeExceptions já lançadas (ex.:
        // IllegalArgumentException de ChaveAcessoCalculator, NPE de campos
        // obrigatórios ausentes), que continuam se propagando intactas.
        verificarTpAmbCompativel(config, nfe);

        Document documento = montarDocumento(nfe);

        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx());

        String xmlAssinado = serializarDocumento(documento);

        return enviarEProcessar(config, xmlAssinado);
    }

    private static void verificarTpAmbCompativel(Sefaz4jConfig config, TNFe nfe) {
        if (nfe.getInfNFe() == null || nfe.getInfNFe().getIde() == null) {
            return;
        }
        String tpAmbIde = nfe.getInfNFe().getIde().getTpAmb();
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

    private static Document montarDocumento(TNFe nfe) {
        try {
            return NFeXmlBuilder.marcarChaveEMontarDocumento(nfe);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML da NFe", e);
        }
    }

    private static String serializarDocumento(Document documento) {
        try {
            return serializar(documento);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o XML da NFe", e);
        }
    }

    public static ResultadoEmissao enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado) {
        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoConsulta consultarSituacao(Sefaz4jConfig config, String chaveAcesso) {
        String xmlConsulta = "<consSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
            "<tpAmb>" + config.getAmbiente().getTpAmb() + "</tpAmb>" +
            "<xServ>CONSULTAR</xServ>" +
            "<chNFe>" + chaveAcesso + "</chNFe>" +
            "</consSitNFe>";

        ValidadorXsd.validar(xmlConsulta, "/schemas/nfe/consSitNFe_v4.00.xsd");

        String url = config.getUrlConsultaProtocoloOverride() != null
            ? config.getUrlConsultaProtocoloOverride()
            : EndpointResolver.resolver(config.getUf(), config.getAmbiente().paraEndpoints(), Servico.NFE_CONSULTA_PROTOCOLO);

        String envelope = SoapEnvelopeBuilder.envelopeConsultaSituacao(xmlConsulta);
        String respostaBruta = SefazHttpClient.postar(
            url,
            "http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4/nfeConsultaNF",
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta);

        return new ResultadoConsulta(
            "100".equals(resposta.getCStat()),
            resposta.getCStat(),
            resposta.getXMotivo(),
            resposta.getChNFe(),
            resposta.getProtocoloXml()
        );
    }

    public static ResultadoEvento cancelar(Sefaz4jConfig config, String chaveAcesso, String nProt, String justificativa) {
        exigirTamanhoMinimo(justificativa, TAMANHO_MINIMO_JUSTIFICATIVA, "justificativa do cancelamento");

        String cUF = chaveAcesso.substring(0, 2);
        String cnpj = chaveAcesso.substring(6, 20);

        String detEvento = "<detEvento versao=\"1.00\">" +
            "<descEvento>Cancelamento</descEvento>" +
            "<nProt>" + nProt + "</nProt>" +
            "<xJust>" + escaparTextoXml(justificativa) + "</xJust>" +
            "</detEvento>";

        Document documento = EventoXmlBuilder.montar(cUF, String.valueOf(config.getAmbiente().getTpAmb()), cnpj, chaveAcesso, "110111", 1, "1.00", detEvento);
        AssinadorXml.assinarEvento(documento, config.getPfxBytes(), config.getSenhaPfx());
        String xmlEventoAssinado = serializarDocumento(documento);

        return enviarEProcessarEvento(config, xmlEventoAssinado, "eventoCancNFe_v1.00.xsd");
    }

    private static final String X_COND_USO_CCE =
        "A Carta de Correção é disciplinada pelo § 1º-A do art. 7º do Convênio S/N, de 15 de dezembro de 1970 " +
        "e pode ser utilizada para regularização de erro ocorrido na emissão de documento fiscal, desde que o " +
        "erro não esteja relacionado com: I - as variáveis que determinam o valor do imposto tais como: base " +
        "de cálculo, alíquota, diferença de preço, quantidade, valor da operação ou da prestação; II - a " +
        "correção de dados cadastrais que implique mudança do remetente ou do destinatário; III - a data de " +
        "emissão ou de saída.";

    public static ResultadoEvento corrigirCartaDeCorrecao(Sefaz4jConfig config, String chaveAcesso, String textoCorrecao) {
        return corrigirCartaDeCorrecao(config, chaveAcesso, textoCorrecao, 1);
    }

    public static ResultadoEvento corrigirCartaDeCorrecao(Sefaz4jConfig config, String chaveAcesso, String textoCorrecao, int nSeqEvento) {
        exigirTamanhoMinimo(textoCorrecao, 15, "texto de correção da CC-e");

        String cUF = chaveAcesso.substring(0, 2);
        String cnpj = chaveAcesso.substring(6, 20);

        String detEvento = "<detEvento versao=\"1.00\">" +
            "<descEvento>Carta de Correção</descEvento>" +
            "<xCorrecao>" + escaparTextoXml(textoCorrecao) + "</xCorrecao>" +
            "<xCondUso>" + escaparTextoXml(X_COND_USO_CCE) + "</xCondUso>" +
            "</detEvento>";

        Document documento = EventoXmlBuilder.montar(cUF, String.valueOf(config.getAmbiente().getTpAmb()), cnpj, chaveAcesso, "110110", nSeqEvento, "1.00", detEvento);
        AssinadorXml.assinarEvento(documento, config.getPfxBytes(), config.getSenhaPfx());
        String xmlEventoAssinado = serializarDocumento(documento);

        return enviarEProcessarEvento(config, xmlEventoAssinado, "CCe_v1.00.xsd");
    }

    public static ResultadoInutilizacao inutilizar(
        Sefaz4jConfig config,
        String cUF,
        String ano,
        String cnpj,
        String serie,
        String nNFIni,
        String nNFFin,
        String justificativa
    ) {
        exigirTamanhoMinimo(justificativa, TAMANHO_MINIMO_JUSTIFICATIVA, "justificativa da inutilização");

        String serieParaId = padEsquerdaComZeros(serie, 3);
        String nNFIniParaId = padEsquerdaComZeros(nNFIni, 9);
        String nNFFinParaId = padEsquerdaComZeros(nNFFin, 9);

        String idNumerico = cUF + ano + cnpj + "55" + serieParaId + nNFIniParaId + nNFFinParaId;
        if (idNumerico.length() != 41 || !idNumerico.matches("[0-9]{41}")) {
            throw new IllegalArgumentException(
                "A concatenação cUF+ano+CNPJ+mod+serie+nNFIni+nNFFin (serie/nNFIni/nNFFin completados "
                    + "com zeros à esquerda só para o Id) deve ter 41 dígitos numéricos (2+2+14+2+3+9+9), "
                    + "obteve " + idNumerico.length() + ": '" + idNumerico + "'"
            );
        }

        String xmlInutilizacao = "<inutNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
            "<infInut Id=\"ID" + idNumerico + "\">" +
            "<tpAmb>" + config.getAmbiente().getTpAmb() + "</tpAmb>" +
            "<xServ>INUTILIZAR</xServ>" +
            "<cUF>" + cUF + "</cUF>" +
            "<ano>" + ano + "</ano>" +
            "<CNPJ>" + cnpj + "</CNPJ>" +
            "<mod>55</mod>" +
            "<serie>" + serie + "</serie>" +
            "<nNFIni>" + nNFIni + "</nNFIni>" +
            "<nNFFin>" + nNFFin + "</nNFFin>" +
            "<xJust>" + escaparTextoXml(justificativa) + "</xJust>" +
            "</infInut>" +
            "</inutNFe>";

        Document documento = parseXmlParaDocumento(xmlInutilizacao);
        AssinadorXml.assinarInutilizacao(documento, config.getPfxBytes(), config.getSenhaPfx());
        String xmlAssinado = serializarDocumento(documento);

        ValidadorXsd.validar(xmlAssinado, "/schemas/nfe/inutNFe_v4.00.xsd");

        String url = config.getUrlInutilizacaoOverride() != null
            ? config.getUrlInutilizacaoOverride()
            : EndpointResolver.resolver(config.getUf(), config.getAmbiente().paraEndpoints(), Servico.NFE_INUTILIZACAO);

        String envelope = SoapEnvelopeBuilder.envelopeInutilizacao(xmlAssinado);
        String respostaBruta = SefazHttpClient.postar(
            url,
            "http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4/nfeInutilizacaoNF",
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "infInut");

        return new ResultadoInutilizacao(
            "102".equals(resposta.getCStat()),
            resposta.getCStat(),
            resposta.getXMotivo(),
            resposta.getProtocoloXml()
        );
    }

    // Completa com zeros à esquerda só para compor o atributo Id de infInut (que exige largura
    // fixa) — nunca usado para o conteúdo dos elementos serie/nNFIni/nNFFin em si, que o schema
    // (TSerie/TNF) proíbe ter zero à esquerda.
    private static String padEsquerdaComZeros(String valor, int tamanho) {
        StringBuilder sb = new StringBuilder(valor);
        while (sb.length() < tamanho) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    // xmlSemAssinatura é montado por concatenação de string por esta
    // própria classe (não é entrada remota) — sem necessidade do
    // hardening XXE aplicado a criarDocumentBuilderSeguro (que trata
    // resposta HTTP da SEFAZ). Mesmo padrão que EventoXmlBuilder já usa
    // internamente para o mesmo tipo de parse.
    private static Document parseXmlParaDocumento(String xmlSemAssinatura) {
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder().parse(
                new ByteArrayInputStream(xmlSemAssinatura.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML de inutilização", e);
        }
    }

    private static void exigirTamanhoMinimo(String texto, int tamanhoMinimo, String nomeCampo) {
        if (texto == null || texto.length() < tamanhoMinimo) {
            throw new IllegalArgumentException(
                "O campo '" + nomeCampo + "' deve ter ao menos " + tamanhoMinimo + " caracteres"
            );
        }
    }

    // Escapa os 5 caracteres especiais de XML — necessário porque, ao
    // contrário do fluxo de emissão (TNFe é serializado via JAXB, que já
    // escapa automaticamente), aqui o XML do evento é montado por
    // concatenação de string, e xJust/xCorrecao são texto livre do
    // chamador.
    private static String escaparTextoXml(String texto) {
        return texto
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static ResultadoEvento enviarEProcessarEvento(Sefaz4jConfig config, String xmlEventoAssinado, String xsdRaiz) {
        ValidadorXsd.validar(xmlEventoAssinado, "/schemas/nfe/" + xsdRaiz);

        String url = config.getUrlRecepcaoEventoOverride() != null
            ? config.getUrlRecepcaoEventoOverride()
            : EndpointResolver.resolver(config.getUf(), config.getAmbiente().paraEndpoints(), Servico.RECEPCAO_EVENTO);

        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoEvento(xmlEventoAssinado, 1L);
        String respostaBruta = SefazHttpClient.postar(
            url,
            "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4/nfeRecepcaoEvento",
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta, "retEvento");

        // Mesma distinção lote-vs-item já usada em enviarEProcessar (Task
        // original de emissão): cStat/xMotivo do retEnvEvento descrevem o
        // LOTE de evento; o cStat/xMotivo/nProt que realmente importa é o
        // do retEvento individual, dentro do fragmento capturado como
        // protocoloXml.
        String protocoloXml = resposta.getProtocoloXml();
        String cStatFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "cStat") : resposta.getCStat();
        String xMotivoFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "xMotivo") : resposta.getXMotivo();
        String nProtFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "nProt") : null;

        return new ResultadoEvento("135".equals(cStatFinal), cStatFinal, xMotivoFinal, nProtFinal, protocoloXml);
    }

    private static ResultadoEmissao enviarEProcessar(Sefaz4jConfig config, String xmlAssinado) {
        ValidadorXsd.validar(xmlAssinado);

        String urlAutorizacao = config.getUrlAutorizacaoOverride() != null
            ? config.getUrlAutorizacaoOverride()
            : EndpointResolver.resolver(config.getUf(), config.getAmbiente().paraEndpoints(), Servico.NFE_AUTORIZACAO);

        String envelope = SoapEnvelopeBuilder.envelopeAutorizacao(xmlAssinado, 1L);
        String respostaBruta = SefazHttpClient.postar(
            urlAutorizacao,
            "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4/nfeAutorizacaoLote",
            envelope,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaSefaz resposta = RespostaSefazParser.parsear(respostaBruta);

        if ("103".equals(resposta.getCStat())) {
            String urlRetAutorizacao = config.getUrlRetAutorizacaoOverride() != null
                ? config.getUrlRetAutorizacaoOverride()
                : EndpointResolver.resolver(config.getUf(), config.getAmbiente().paraEndpoints(), Servico.NFE_RET_AUTORIZACAO);
            resposta = ReciboPoller.aguardarProtocolo(
                resposta.getNRec(),
                config.getAmbiente().getTpAmb(),
                urlRetAutorizacao,
                config.getPfxBytes(),
                config.getSenhaPfx(),
                config.getMaxTentativasPolling(),
                config.getIntervaloPolling(),
                config.getTimeout()
            );

            // Esgotar as tentativas de polling com o lote ainda "em
            // processamento" (103) é uma situação de INCERTEZA técnica, não
            // uma rejeição de negócio — não sabemos se a NFe foi autorizada
            // ou não. Tratar isso como ResultadoEmissao(ok=false) seria
            // indistinguível de uma rejeição real da SEFAZ e, combinado com
            // o risco de retentativa às cegas já documentado no spec, pode
            // levar a emitir a mesma NFe duas vezes. Por isso lançamos
            // ComunicacaoException em vez de devolver um resultado.
            if ("103".equals(resposta.getCStat())) {
                throw new ComunicacaoException(
                    "Lote ainda em processamento (cStat 103) após esgotar as tentativas de polling; "
                        + "não é possível determinar se a NFe foi autorizada ou rejeitada",
                    null
                );
            }
        }

        // RespostaSefaz.getCStat()/getXMotivo() refletem o status do LOTE
        // (ex.: "104"/"Lote processado" ou o retorno de ReciboPoller); quando
        // há protocolo (protNFe/infProt), o status que realmente importa
        // para o consumidor da fachada é o da NF-e individual dentro dele
        // (ex.: "100"/"Autorizado o uso da NF-e"), por isso extraímos daí
        // quando presente, com o cStat/xMotivo do lote como fallback.
        String protocoloXml = resposta.getProtocoloXml();
        String cStatFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "cStat") : resposta.getCStat();
        String xMotivoFinal = protocoloXml != null ? extrairTextoDoElemento(protocoloXml, "xMotivo") : resposta.getXMotivo();

        boolean autorizado = "100".equals(cStatFinal);
        // xmlAutorizado precisa ser um único documento XML bem-formado
        // ("pronto para persistência/DANFE"), não a concatenação crua de
        // dois elementos-raiz irmãos (NFe + protNFe) — por isso, quando há
        // protocolo, envolvemos os dois no elemento de distribuição
        // canônico nfeProc. Sem protocolo (rejeição de negócio ou ainda em
        // processamento sem chegar a 103-esgotado), devolvemos o XML
        // assinado isolado, que já é bem-formado por si só.
        String xmlFinal = protocoloXml != null
            ? "<nfeProc versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">" + xmlAssinado + protocoloXml + "</nfeProc>"
            : xmlAssinado;

        return new ResultadoEmissao(autorizado, cStatFinal, xMotivoFinal, resposta.getChNFe(), xmlFinal);
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

    // xmlFragmento vem de resposta.getProtocoloXml(), que por sua vez vem da
    // resposta HTTP da SEFAZ (entrada remota) — protegido aqui contra XXE/
    // expansão de entidades, mesmo tratamento dado a RespostaSefazParser.
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
}
