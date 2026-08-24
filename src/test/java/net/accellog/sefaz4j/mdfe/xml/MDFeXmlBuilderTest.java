package net.accellog.sefaz4j.mdfe.xml;

import net.accellog.sefaz4j.chave.ChaveAcessoCalculator;
import net.accellog.sefaz4j.mdfe.model.ObjectFactory;
import net.accellog.sefaz4j.mdfe.model.TMDFe;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.Assert.assertEquals;

public class MDFeXmlBuilderTest {

    @Test
    public void injetaChaveDeAcessoEIdQuandoAusente() throws Exception {
        ObjectFactory fabrica = new ObjectFactory();
        TMDFe mdfe = fabrica.createTMDFe();
        TMDFe.InfMDFe infMDFe = fabrica.createTMDFeInfMDFe();
        infMDFe.setVersao("3.00");

        TMDFe.InfMDFe.Ide ide = fabrica.createTMDFeInfMDFeIde();
        ide.setCUF("35");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setMod("58");
        ide.setSerie("001");
        ide.setNMDF("000000123");
        ide.setTpEmis("1");
        ide.setCMDF("12345678");
        infMDFe.setIde(ide);

        TMDFe.InfMDFe.Emit emit = fabrica.createTMDFeInfMDFeEmit();
        emit.setCNPJ("12345678000195");
        infMDFe.setEmit(emit);

        mdfe.setInfMDFe(infMDFe);

        String chaveEsperada = ChaveAcessoCalculator.calcular(
            "35", "2508", "12345678000195", "58", "001", "000000123", "1", "12345678"
        );

        Document documento = MDFeXmlBuilder.marcarChaveEMontarDocumento(mdfe);

        Element raizMDFe = documento.getDocumentElement();
        assertEquals("MDFe", raizMDFe.getLocalName());
        Element infMDFeElemento = (Element) raizMDFe
            .getElementsByTagNameNS("http://www.portalfiscal.inf.br/mdfe", "infMDFe").item(0);
        String id = infMDFeElemento.getAttribute("Id");

        assertEquals("MDFe" + chaveEsperada, id);
        assertEquals(0, raizMDFe.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").getLength());
        // cDV é o último dígito da própria chave injetada em Id — deve ser preenchido
        // automaticamente quando ausente, mesmo padrão de NFeXmlBuilder/CTeXmlBuilder.
        assertEquals(chaveEsperada.substring(chaveEsperada.length() - 1), ide.getCDV());
    }
}
