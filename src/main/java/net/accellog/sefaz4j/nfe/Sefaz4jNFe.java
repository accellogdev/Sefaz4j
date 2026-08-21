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
