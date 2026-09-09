package net.accellog.sefaz4j.cte.xml;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import net.accellog.sefaz4j.chave.ChaveAcessoCalculator;
import net.accellog.sefaz4j.cte.model.ObjectFactory;
import net.accellog.sefaz4j.cte.model.TCTe;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Monta o DOM {@link Document} de um CTe a partir de um {@link TCTe} já
 * populado, injetando a chave de acesso e o atributo {@code Id} de
 * {@code infCte} quando ainda ausentes.
 *
 * <p>Espelha {@link net.accellog.sefaz4j.nfe.xml.NFeXmlBuilder}, usando os
 * nomes de campo do CTe. Não assina o documento nem valida contra o XSD —
 * apenas serializa o objeto JAXB para DOM.</p>
 */
public final class CTeXmlBuilder {

    private static final String MODELO_CTE = "57";
    private static final String CTE_NAMESPACE = "http://www.portalfiscal.inf.br/cte";

    private CTeXmlBuilder() {
    }

    public static Document marcarChaveEMontarDocumento(TCTe cte) throws Exception {
        injetarChaveEIdSeAusente(cte);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TCTe.class);
        Marshaller marshaller = contexto.createMarshaller();
        // Sem mapper, o JAXB marshalla com um prefixo gerado (ex.: ns2:CTe xmlns:ns2="...") em vez de
        // declarar o namespace do CT-e como default -- a SEFAZ rejeita isso com cStat 598 ("Usar
        // somente o namespace padrao do CTe"). Mesmo padrão já usado em DpsXmlBuilder (NFS-e).
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                return CTE_NAMESPACE.equals(namespaceUri) ? "" : suggestion;
            }
        });
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TCTe> elementoRaiz = fabrica.createCTe(cte);
        marshaller.marshal(elementoRaiz, documento);

        removerDeclaracaoNamespaceOrfaDoXmldsig(documento);

        return documento;
    }

    /**
     * {@code TCTe.InfCte.signature} (ds:Signature) fica sempre nulo aqui -- a assinatura real é
     * inserida depois via DOM por {@code AssinadorXml}. Mesmo assim o JAXB RI pré-declara o
     * namespace do xmldsig (usado em outras classes do mesmo {@code JAXBContext}) como
     * {@code xmlns:ns2} órfão na raiz do CTe, o que também viola a exigência de "somente o
     * namespace padrão do CTe" -- mesmo padrão já usado em DpsXmlBuilder (NFS-e).
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

    private static void injetarChaveEIdSeAusente(TCTe cte) {
        TCTe.InfCte infCte = cte.getInfCte();
        String idAtual = infCte.getId();
        if (idAtual != null && !idAtual.isEmpty()) {
            return;
        }

        TCTe.InfCte.Ide ide = infCte.getIde();
        String dhEmi = ide.getDhEmi();
        String chave = ChaveAcessoCalculator.calcular(
            ide.getCUF(),
            dhEmi.substring(2, 4) + dhEmi.substring(5, 7),
            infCte.getEmit().getCNPJ(),
            MODELO_CTE,
            ide.getSerie(),
            ide.getNCT(),
            ide.getTpEmis(),
            ide.getCCT()
        );
        infCte.setId("CTe" + chave);

        String cDVAtual = ide.getCDV();
        if (cDVAtual == null || cDVAtual.isEmpty()) {
            ide.setCDV(chave.substring(chave.length() - 1));
        }
    }
}
