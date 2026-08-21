package net.accellog.sefaz4j.nfe.integration;

import net.accellog.sefaz4j.nfe.Ambiente;
import net.accellog.sefaz4j.nfe.ResultadoEmissao;
import net.accellog.sefaz4j.nfe.Sefaz4jConfig;
import net.accellog.sefaz4j.nfe.Sefaz4jNFe;
import net.accellog.sefaz4j.nfe.endpoints.UF;
import net.accellog.sefaz4j.nfe.model.ObjectFactory;
import net.accellog.sefaz4j.nfe.model.TEnderEmi;
import net.accellog.sefaz4j.nfe.model.TNFe;
import net.accellog.sefaz4j.nfe.model.TUfEmi;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class HomologacaoIntegrationTest {

    @Test
    public void emiteUmaNfeRealEmHomologacao() throws Exception {
        String caminhoPfx = System.getenv("SEFAZ4J_TEST_PFX_PATH");
        String senhaPfx = System.getenv("SEFAZ4J_TEST_PFX_SENHA");
        assumeTrue(
            "Defina SEFAZ4J_TEST_PFX_PATH e SEFAZ4J_TEST_PFX_SENHA para rodar este teste contra Homologação real",
            caminhoPfx != null && senhaPfx != null
        );

        byte[] pfxBytes = Files.readAllBytes(Path.of(caminhoPfx));
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, pfxBytes, senhaPfx);

        ObjectFactory fabrica = new ObjectFactory();
        TNFe nfe = fabrica.createTNFe();

        // Caso mínimo, porém schema-válido: mesmo conjunto de grupos/campos
        // obrigatórios já confirmado contra leiauteNFe_v4.00.xsd/
        // tiposBasico_v4.00.xsd no Task 11 (Sefaz4jNFeTest, via XML literal
        // assinado manualmente) — aqui reconstruído como grafo JAXB, já que
        // emitir() monta/assina/valida/envia a partir de um TNFe. Chave de
        // acesso, o atributo Id de infNFe e o dígito verificador (ide/cDV)
        // são injetados por NFeXmlBuilder dentro de emitir(), por isso não
        // são setados aqui. serie/nNF usam valores sem zero à esquerda
        // (TSerie/TNF proíbem) que ainda somam exatamente os 3+9 dígitos
        // que ChaveAcessoCalculator exige.
        TNFe.InfNFe infNFe = fabrica.createTNFeInfNFe();
        infNFe.setVersao("4.00");

        TNFe.InfNFe.Ide ide = fabrica.createTNFeInfNFeIde();
        ide.setCUF("35");
        ide.setCNF("12345678");
        ide.setNatOp("Venda");
        ide.setMod("55");
        ide.setSerie("123");
        ide.setNNF("123456789");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG("3550308");
        ide.setTpImp("1");
        ide.setTpEmis("1");
        ide.setTpAmb("2");
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("1.0");
        infNFe.setIde(ide);

        TNFe.InfNFe.Emit emit = fabrica.createTNFeInfNFeEmit();
        emit.setCNPJ("12345678000195");
        emit.setXNome("Empresa Teste LTDA");

        TEnderEmi enderEmit = fabrica.createTEnderEmi();
        enderEmit.setXLgr("Rua Teste");
        enderEmit.setNro("100");
        enderEmit.setXBairro("Centro");
        enderEmit.setCMun("3550308");
        enderEmit.setXMun("Sao Paulo");
        enderEmit.setUF(TUfEmi.SP);
        enderEmit.setCEP("01310100");
        emit.setEnderEmit(enderEmit);

        emit.setIE("ISENTO");
        emit.setCRT("3");
        infNFe.setEmit(emit);

        TNFe.InfNFe.Det det = fabrica.createTNFeInfNFeDet();
        det.setNItem("1");

        TNFe.InfNFe.Det.Prod prod = fabrica.createTNFeInfNFeDetProd();
        prod.setCProd("PROD001");
        prod.setCEAN("SEM GTIN");
        prod.setXProd("Produto Teste");
        prod.setNCM("00");
        prod.setCFOP("5102");
        prod.setUCom("UN");
        prod.setQCom("1");
        prod.setVUnCom("10.00");
        prod.setVProd("10.00");
        prod.setCEANTrib("SEM GTIN");
        prod.setUTrib("UN");
        prod.setQTrib("1");
        prod.setVUnTrib("10.00");
        prod.setIndTot("1");
        det.setProd(prod);

        TNFe.InfNFe.Det.Imposto imposto = fabrica.createTNFeInfNFeDetImposto();
        det.setImposto(imposto);

        infNFe.getDet().add(det);

        TNFe.InfNFe.Total total = fabrica.createTNFeInfNFeTotal();
        TNFe.InfNFe.Total.ICMSTot icmsTot = fabrica.createTNFeInfNFeTotalICMSTot();
        icmsTot.setVBC("0.00");
        icmsTot.setVICMS("0.00");
        icmsTot.setVICMSDeson("0.00");
        icmsTot.setVFCP("0.00");
        icmsTot.setVBCST("0.00");
        icmsTot.setVST("0.00");
        icmsTot.setVFCPST("0.00");
        icmsTot.setVFCPSTRet("0.00");
        icmsTot.setVProd("10.00");
        icmsTot.setVFrete("0.00");
        icmsTot.setVSeg("0.00");
        icmsTot.setVDesc("0.00");
        icmsTot.setVII("0.00");
        icmsTot.setVIPI("0.00");
        icmsTot.setVIPIDevol("0.00");
        icmsTot.setVPIS("0.00");
        icmsTot.setVCOFINS("0.00");
        icmsTot.setVOutro("0.00");
        icmsTot.setVNF("10.00");
        total.setICMSTot(icmsTot);
        infNFe.setTotal(total);

        TNFe.InfNFe.Transp transp = fabrica.createTNFeInfNFeTransp();
        transp.setModFrete("9");
        infNFe.setTransp(transp);

        TNFe.InfNFe.Pag pag = fabrica.createTNFeInfNFePag();
        TNFe.InfNFe.Pag.DetPag detPag = fabrica.createTNFeInfNFePagDetPag();
        detPag.setTPag("01");
        detPag.setVPag("10.00");
        pag.getDetPag().add(detPag);
        infNFe.setPag(pag);

        nfe.setInfNFe(infNFe);

        ResultadoEmissao resultado = Sefaz4jNFe.emitir(config, nfe);

        assertTrue("Motivo retornado: " + resultado.getXMotivo(), resultado.isOk());
    }
}
