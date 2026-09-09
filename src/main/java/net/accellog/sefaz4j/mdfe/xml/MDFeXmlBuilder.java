package net.accellog.sefaz4j.mdfe.xml;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import net.accellog.sefaz4j.chave.ChaveAcessoCalculator;
import net.accellog.sefaz4j.mdfe.model.ObjectFactory;
import net.accellog.sefaz4j.mdfe.model.TMDFe;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Monta o DOM {@link Document} de um MDF-e a partir de um {@link TMDFe} já
 * populado, injetando a chave de acesso e o atributo {@code Id} de
 * {@code infMDFe} quando ainda ausentes. Mesmo papel de
 * {@code net.accellog.sefaz4j.nfe.xml.NFeXmlBuilder}/{@code CTeXmlBuilder}.
 * Não assina nem valida contra XSD.
 */
public final class MDFeXmlBuilder {

    private static final String MDFE_NAMESPACE = "http://www.portalfiscal.inf.br/mdfe";

    private MDFeXmlBuilder() {
    }

    public static Document marcarChaveEMontarDocumento(TMDFe mdfe) throws Exception {
        injetarChaveEIdSeAusente(mdfe);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TMDFe.class);
        Marshaller marshaller = contexto.createMarshaller();
        // Mesmo fix de CTeXmlBuilder (confirmado empiricamente contra a SEFAZ real: cStat 598
        // "Usar somente o namespace padrao do CTe" era o mesmo problema para o CT-e) -- sem
        // mapper, o JAXB marshalla com prefixo gerado (ns2:) em vez de default namespace.
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                return MDFE_NAMESPACE.equals(namespaceUri) ? "" : suggestion;
            }
        });
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TMDFe> elementoRaiz = fabrica.createMDFe(mdfe);
        marshaller.marshal(elementoRaiz, documento);

        removerDeclaracaoNamespaceOrfaDoXmldsig(documento);

        return documento;
    }

    /**
     * {@code TMDFe.InfMDFe.signature} (ds:Signature) fica sempre nulo aqui -- a assinatura real
     * é inserida depois via DOM por {@code AssinadorXml}. Mesmo assim o JAXB RI pré-declara o
     * namespace do xmldsig como {@code xmlns:ns2} órfão na raiz do MDFe -- mesmo padrão de
     * {@code CTeXmlBuilder}/{@code DpsXmlBuilder}.
     */
    private static void removerDeclaracaoNamespaceOrfaDoXmldsig(Document documento) {
        org.w3c.dom.Element raiz = documento.getDocumentElement();
        org.w3c.dom.NamedNodeMap atributos = raiz.getAttributes();
        for (int i = atributos.getLength() - 1; i >= 0; i--) {
            org.w3c.dom.Attr atributo = (org.w3c.dom.Attr) atributos.item(i);
            if (javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(atributo.getNamespaceURI())
                && "http://www.w3.org/2000/09/xmldsig#".equals(atributo.getValue())) {
                raiz.removeAttributeNode(atributo);
            }
        }
    }

    private static void injetarChaveEIdSeAusente(TMDFe mdfe) {
        TMDFe.InfMDFe infMDFe = mdfe.getInfMDFe();
        String idAtual = infMDFe.getId();
        if (idAtual != null && !idAtual.isEmpty()) {
            return;
        }

        TMDFe.InfMDFe.Ide ide = infMDFe.getIde();
        String dhEmi = ide.getDhEmi();
        String chave = ChaveAcessoCalculator.calcular(
            ide.getCUF(),
            dhEmi.substring(2, 4) + dhEmi.substring(5, 7),
            infMDFe.getEmit().getCNPJ(),
            ide.getMod(),
            ide.getSerie(),
            ide.getNMDF(),
            ide.getTpEmis(),
            ide.getCMDF()
        );
        infMDFe.setId("MDFe" + chave);

        String cDVAtual = ide.getCDV();
        if (cDVAtual == null || cDVAtual.isEmpty()) {
            ide.setCDV(chave.substring(chave.length() - 1));
        }
    }
}
