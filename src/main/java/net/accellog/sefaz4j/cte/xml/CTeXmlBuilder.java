package net.accellog.sefaz4j.cte.xml;

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

    private CTeXmlBuilder() {
    }

    public static Document marcarChaveEMontarDocumento(TCTe cte) throws Exception {
        injetarChaveEIdSeAusente(cte);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TCTe.class);
        Marshaller marshaller = contexto.createMarshaller();
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TCTe> elementoRaiz = fabrica.createCTe(cte);
        marshaller.marshal(elementoRaiz, documento);

        return documento;
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
