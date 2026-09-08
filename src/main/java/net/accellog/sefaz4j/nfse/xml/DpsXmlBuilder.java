package net.accellog.sefaz4j.nfse.xml;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import net.accellog.sefaz4j.nfse.chave.DpsIdCalculator;
import net.accellog.sefaz4j.nfse.model.ObjectFactory;
import net.accellog.sefaz4j.nfse.model.TCDPS;
import net.accellog.sefaz4j.nfse.model.TCInfDPS;
import net.accellog.sefaz4j.nfse.model.TCInfoPrestador;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Monta o DOM {@link Document} de uma DPS a partir de um {@link TCDPS} já
 * populado, injetando o atributo {@code Id} de {@code infDPS} quando ainda
 * ausente.
 *
 * <p>Espelha {@link net.accellog.sefaz4j.cte.xml.CTeXmlBuilder}. Não assina o
 * documento nem valida contra o XSD — apenas serializa o objeto JAXB para
 * DOM.</p>
 */
public final class DpsXmlBuilder {

    private static final String NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";

    private DpsXmlBuilder() {
    }

    public static Document marcarIdEMontarDocumento(TCDPS dps) throws Exception {
        injetarIdSeAusente(dps);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TCDPS.class);
        Marshaller marshaller = contexto.createMarshaller();
        // Sem mapper, o JAXB marshalla com um prefixo gerado (ex.: ns2:DPS xmlns:ns2="...") em vez de
        // declarar o namespace como default — o SEFIN Nacional rejeita isso com E1228 ("Xml declarado
        // com prefixo de namespace"). O evento (EventoNFSeXmlBuilder) não precisa deste mapper porque
        // é montado como string/DOM manual, já com xmlns="..." sem prefixo.
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                return NFSE_NAMESPACE.equals(namespaceUri) ? "" : suggestion;
            }
        });
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TCDPS> elementoRaiz = fabrica.createDPS(dps);
        marshaller.marshal(elementoRaiz, documento);

        removerDeclaracaoNamespaceOrfaDoXmldsig(documento);

        return documento;
    }

    /**
     * {@code TCDPS.signature} ({@code ds:Signature}, minOccurs="0") fica sempre nulo aqui — a
     * assinatura de verdade é inserida depois, via DOM, por {@code AssinadorXml} (com seu próprio
     * {@code xmlns:ds} local no elemento {@code ds:Signature}). Mesmo assim, o JAXB RI pré-declara o
     * namespace do xmldsig (usado em outras classes do mesmo {@code JAXBContext}) como
     * {@code xmlns:ns2} órfão na raiz do {@code DPS}, e o SEFIN Nacional rejeita qualquer prefixo de
     * namespace com E1228 ("Xml declarado com prefixo de namespace") — daí a remoção explícita.
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

    private static void injetarIdSeAusente(TCDPS dps) {
        TCInfDPS infDPS = dps.getInfDPS();
        String id = infDPS.getId();
        if (id != null && !id.isEmpty()) {
            return;
        }

        TCInfoPrestador prest = infDPS.getPrest();
        String tipoInscricaoFederal;
        String inscricaoFederal;
        // Mapeamento do código numérico do tipo de inscrição federal (CNPJ=2, CPF=1, NIF=3,
        // cNaoNIF=9) e a convenção de preenchimento com zeros à esquerda até 14 dígitos NÃO
        // foram confirmados contra nenhum manual oficial do NFS-e nem contra o ACBr — é um
        // placeholder de melhor esforço, isolado a este método, pendente de verificação futura.
        if (prest.getCNPJ() != null) {
            tipoInscricaoFederal = "2";
            inscricaoFederal = prest.getCNPJ();
        } else if (prest.getCPF() != null) {
            tipoInscricaoFederal = "1";
            inscricaoFederal = padEsquerda14(prest.getCPF());
        } else if (prest.getNIF() != null) {
            tipoInscricaoFederal = "3";
            inscricaoFederal = padEsquerda14(prest.getNIF());
        } else {
            tipoInscricaoFederal = "9";
            inscricaoFederal = padEsquerda14(prest.getCNaoNIF());
        }

        String novoId = DpsIdCalculator.calcular(
            infDPS.getCLocEmi(),
            tipoInscricaoFederal,
            inscricaoFederal,
            infDPS.getSerie(),
            infDPS.getNDPS()
        );
        infDPS.setId(novoId);
    }

    private static String padEsquerda14(String valor) {
        StringBuilder sb = new StringBuilder();
        for (int i = valor.length(); i < 14; i++) {
            sb.append('0');
        }
        sb.append(valor);
        return sb.toString();
    }
}
