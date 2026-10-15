package net.accellog.sefaz4j.nfe.xml;

import net.accellog.sefaz4j.nfe.model.TNFe;
import net.accellog.sefaz4j.nfe.model.ObjectFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NFeXmlBuilderTest {

    @Test
    public void injetaChaveDeAcessoEIdQuandoAusente() throws Exception {
        ObjectFactory fabrica = new ObjectFactory();
        TNFe nfe = fabrica.createTNFe();
        TNFe.InfNFe infNFe = fabrica.createTNFeInfNFe();
        infNFe.setVersao("4.00");

        // popular apenas os campos de 'ide'/'emit' necessários para a chave,
        // reaproveitando o vetor verificado em ChaveAcessoCalculatorTest (Task 2)
        TNFe.InfNFe.Ide ide = fabrica.createTNFeInfNFeIde();
        ide.setCUF("35");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setMod("55");
        ide.setSerie("001");
        ide.setNNF("000000123");
        ide.setTpEmis("1");
        ide.setCNF("12345678");
        infNFe.setIde(ide);

        TNFe.InfNFe.Emit emit = fabrica.createTNFeInfNFeEmit();
        emit.setCNPJ("12345678000195");
        infNFe.setEmit(emit);

        nfe.setInfNFe(infNFe);

        Document documento = NFeXmlBuilder.marcarChaveEMontarDocumento(nfe);

        Element raizNFe = documento.getDocumentElement();
        assertEquals("NFe", raizNFe.getLocalName());
        Element infNFeElemento = (Element) raizNFe.getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", "infNFe").item(0);
        String id = infNFeElemento.getAttribute("Id");

        assertTrue("Id deve começar com 'NFe' seguido de 44 dígitos", id.matches("NFe\\d{44}"));
        assertEquals("35250812345678000195550010000001231123456789", id.substring(3));
        assertEquals(0, raizNFe.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").getLength());

        // ide.cDV é o último dígito da própria chave de acesso injetada em
        // Id — deve ser preenchido automaticamente quando ausente, mesmo
        // padrão de "só preenche se ausente" já usado para Id.
        assertEquals("9", ide.getCDV());
    }

    @Test
    public void preencheIdEHashDoCsrtNoResponsavelTecnico() {
        // hashCSRT = Base64(SHA1(CSRT + chave de acesso)) — fórmula do ACBr (CalcularHashCSRT),
        // conferida contra uma NF-e real autorizada. CSRT e chave aqui são sintéticos.
        ObjectFactory fabrica = new ObjectFactory();
        TNFe nfe = fabrica.createTNFe();
        TNFe.InfNFe infNFe = fabrica.createTNFeInfNFe();
        TNFe.InfNFe.Ide ide = fabrica.createTNFeInfNFeIde();
        ide.setCUF("35");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setMod("55");
        ide.setSerie("001");
        ide.setNNF("000000123");
        ide.setTpEmis("1");
        ide.setCNF("12345678");
        infNFe.setIde(ide);
        TNFe.InfNFe.Emit emit = fabrica.createTNFeInfNFeEmit();
        emit.setCNPJ("12345678000195");
        infNFe.setEmit(emit);
        infNFe.setInfRespTec(fabrica.createTInfRespTec());
        nfe.setInfNFe(infNFe);

        NFeXmlBuilder.preencherCsrt(nfe, 2, "CSRTSINTETICODETESTE0000000000000000");

        assertEquals("NFe35250812345678000195550010000001231123456789", infNFe.getId());
        assertEquals("02", infNFe.getInfRespTec().getIdCSRT());
        assertEquals("gLFSwOlasn9/q+Yx8ul2o+lO9XE=",
                java.util.Base64.getEncoder().encodeToString(infNFe.getInfRespTec().getHashCSRT()));
    }

    @Test
    public void preencherCsrtSemResponsavelTecnicoNaoFazNada() {
        ObjectFactory fabrica = new ObjectFactory();
        TNFe nfe = fabrica.createTNFe();
        nfe.setInfNFe(fabrica.createTNFeInfNFe());

        NFeXmlBuilder.preencherCsrt(nfe, 2, "CSRTSINTETICODETESTE0000000000000000");

        assertEquals(null, nfe.getInfNFe().getInfRespTec());
        assertEquals(null, nfe.getInfNFe().getId());
    }
}
