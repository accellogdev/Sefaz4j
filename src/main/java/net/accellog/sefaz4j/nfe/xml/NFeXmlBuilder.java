package net.accellog.sefaz4j.nfe.xml;

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

    private NFeXmlBuilder() {
    }

    public static Document marcarChaveEMontarDocumento(TNFe nfe) throws Exception {
        injetarChaveEIdSeAusente(nfe);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TNFe.class);
        Marshaller marshaller = contexto.createMarshaller();
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TNFe> elementoRaiz = fabrica.createNFe(nfe);
        marshaller.marshal(elementoRaiz, documento);

        return documento;
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
