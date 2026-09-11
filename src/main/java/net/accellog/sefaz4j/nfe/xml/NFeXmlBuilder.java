package net.accellog.sefaz4j.nfe.xml;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import net.accellog.sefaz4j.chave.ChaveAcessoCalculator;
import net.accellog.sefaz4j.nfe.model.ObjectFactory;
import net.accellog.sefaz4j.nfe.model.TNFe;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Monta o DOM {@link Document} de uma NFe a partir de um {@link TNFe} já
 * populado, injetando a chave de acesso e o atributo {@code Id} de
 * {@code infNFe} quando ainda ausentes.
 *
 * <p>Não assina o documento (isso é responsabilidade do Task 5) nem valida
 * contra o XSD (Task 6) — apenas serializa o objeto JAXB para DOM.</p>
 */
public final class NFeXmlBuilder {

    private static final String NFE_NAMESPACE = "http://www.portalfiscal.inf.br/nfe";

    private NFeXmlBuilder() {
    }

    public static Document marcarChaveEMontarDocumento(TNFe nfe) throws Exception {
        injetarChaveEIdSeAusente(nfe);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TNFe.class);
        Marshaller marshaller = contexto.createMarshaller();
        // Sem mapper, o JAXB marshalla com um prefixo gerado (ex.: ns2:NFe xmlns:ns2="...") em vez
        // de declarar o namespace da NFe como default -- a SEFAZ rejeita isso com cStat 587 ("Usar
        // somente o namespace padrao da NF-e"). Mesmo padrão já usado em CTeXmlBuilder/MDFeXmlBuilder/
        // DpsXmlBuilder.
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                return NFE_NAMESPACE.equals(namespaceUri) ? "" : suggestion;
            }
        });
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TNFe> elementoRaiz = fabrica.createNFe(nfe);
        marshaller.marshal(elementoRaiz, documento);

        removerDeclaracaoNamespaceOrfaDoXmldsig(documento);

        return documento;
    }

    /**
     * {@code TNFe.InfNFe.signature} (ds:Signature) fica sempre nulo aqui -- a assinatura real é
     * inserida depois via DOM por {@code AssinadorXml}. Mesmo assim o JAXB RI pré-declara o
     * namespace do xmldsig (usado em outras classes do mesmo {@code JAXBContext}) como
     * {@code xmlns:ns2} órfão na raiz da NFe, o que também viola a exigência de "somente o
     * namespace padrão da NF-e" -- mesmo padrão já usado em CTeXmlBuilder (CTe).
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

    private static void injetarChaveEIdSeAusente(TNFe nfe) {
        TNFe.InfNFe infNFe = nfe.getInfNFe();
        String idAtual = infNFe.getId();
        if (idAtual != null && !idAtual.isEmpty()) {
            return;
        }

        TNFe.InfNFe.Ide ide = infNFe.getIde();
        String dhEmi = ide.getDhEmi();
        String chave = ChaveAcessoCalculator.calcular(
            ide.getCUF(),
            dhEmi.substring(2, 4) + dhEmi.substring(5, 7),
            infNFe.getEmit().getCNPJ(),
            ide.getMod(),
            ide.getSerie(),
            ide.getNNF(),
            ide.getTpEmis(),
            ide.getCNF()
        );
        infNFe.setId("NFe" + chave);

        // O dígito verificador (último caractere dos 44 da chave) também é
        // exposto como campo próprio em ide/cDV — preenche a partir da
        // chave já calculada, só quando ainda ausente (mesmo padrão usado
        // para Id acima).
        String cDVAtual = ide.getCDV();
        if (cDVAtual == null || cDVAtual.isEmpty()) {
            ide.setCDV(chave.substring(chave.length() - 1));
        }
    }
}
