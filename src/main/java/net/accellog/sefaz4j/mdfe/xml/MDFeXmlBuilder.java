package net.accellog.sefaz4j.mdfe.xml;

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

    private MDFeXmlBuilder() {
    }

    public static Document marcarChaveEMontarDocumento(TMDFe mdfe) throws Exception {
        injetarChaveEIdSeAusente(mdfe);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TMDFe.class);
        Marshaller marshaller = contexto.createMarshaller();
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TMDFe> elementoRaiz = fabrica.createMDFe(mdfe);
        marshaller.marshal(elementoRaiz, documento);

        return documento;
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
