package net.accellog.sefaz4j.cte.integration;

import net.accellog.sefaz4j.cte.ResultadoEmissao;
import net.accellog.sefaz4j.cte.Sefaz4jCTe;
import net.accellog.sefaz4j.cte.Sefaz4jConfig;
import net.accellog.sefaz4j.cte.model.ObjectFactory;
import net.accellog.sefaz4j.cte.model.TCTe;
import net.accellog.sefaz4j.cte.model.TEndeEmi;
import net.accellog.sefaz4j.cte.model.TEndereco;
import net.accellog.sefaz4j.cte.model.TImp;
import net.accellog.sefaz4j.cte.model.TUFSemEX;
import net.accellog.sefaz4j.cte.model.TUf;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

public class HomologacaoIntegrationTestCTe {

    @Test
    public void emiteUmCteRealEmHomologacao() throws Exception {
        String caminhoPfx = System.getenv("SEFAZ4J_TEST_PFX_PATH");
        String senhaPfx = System.getenv("SEFAZ4J_TEST_PFX_SENHA");
        assumeTrue(
            "Defina SEFAZ4J_TEST_PFX_PATH e SEFAZ4J_TEST_PFX_SENHA para rodar este teste contra Homologação real",
            caminhoPfx != null && senhaPfx != null
        );

        byte[] pfxBytes = Files.readAllBytes(Path.of(caminhoPfx));
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, pfxBytes, senhaPfx);

        TCTe cte = montarCteMinimoValido();

        ResultadoEmissao resultado = Sefaz4jCTe.emitir(config, cte);

        // Um autorizador de Homologação real pode legitimamente rejeitar um CT-e sintético
        // por motivo de negócio (CNPJ/IE inexistentes, RNTRC inválido etc.) — o objetivo deste
        // teste é provar que o formato de wire/assinatura/mTLS chega e volta corretamente, não
        // que este CT-e específico seja autorizado. Por isso só afirmamos que a chamada
        // completou sem lançar uma das exceções técnicas (CertificadoException/
        // ValidacaoXsdException/ComunicacaoException) e que a SEFAZ respondeu com cStat/xMotivo.
        System.out.println("HomologacaoIntegrationTestCTe: cStat=" + resultado.getCStat()
            + " xMotivo=" + resultado.getXMotivo());
        assertNotNull("SEFAZ deveria sempre responder com um cStat", resultado.getCStat());
        assertNotNull("SEFAZ deveria sempre responder com um xMotivo", resultado.getXMotivo());
    }

    /**
     * Mesmo CT-e mínimo, porém schema-válido, construído em
     * {@code net.accellog.sefaz4j.cte.Sefaz4jCTeTest#montarCteMinimoValido()} (Task 12) — copiado
     * aqui (classe/pacote diferentes não permitem reuso direto de um {@code private static}) e
     * adaptado para Homologação: {@code tpAmb="2"} em vez de {@code "1"}, condizente com o
     * {@code Ambiente.HOMOLOGACAO} usado no {@link Sefaz4jConfig} acima — {@code Sefaz4jCTe.emitir}
     * rejeita de propósito qualquer divergência entre o {@code tpAmb} do CT-e e o {@code Ambiente}
     * configurado (ver {@code Sefaz4jCTeTest.emitirRejeitaTpAmbDivergenteDoAmbienteConfigurado}).
     * Deliberadamente sem {@code infCte/@Id} e sem {@code ide/cDV}: {@code CTeXmlBuilder}, chamado
     * dentro de {@code emitir}, calcula a chave de acesso e injeta ambos.
     */
    private static TCTe montarCteMinimoValido() {
        ObjectFactory fabrica = new ObjectFactory();

        TCTe cte = fabrica.createTCTe();
        TCTe.InfCte infCte = fabrica.createTCTeInfCte();
        infCte.setVersao("4.00");

        TCTe.InfCte.Ide ide = fabrica.createTCTeInfCteIde();
        ide.setCUF("35");
        ide.setCCT("12345678");
        ide.setCFOP("5353");
        ide.setNatOp("Prestacao de servico de transporte");
        ide.setMod("57");
        ide.setSerie("123");
        ide.setNCT("100000123");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setTpImp("1");
        ide.setTpEmis("1");
        ide.setTpAmb("2");
        ide.setTpCTe("0");
        ide.setProcEmi("0");
        ide.setVerProc("1.0.0");
        ide.setCMunEnv("3550308");
        ide.setXMunEnv("Sao Paulo");
        ide.setUFEnv(TUf.SP);
        ide.setModal("01");
        ide.setTpServ("0");
        ide.setCMunIni("3550308");
        ide.setXMunIni("Sao Paulo");
        ide.setUFIni(TUf.SP);
        ide.setCMunFim("3304557");
        ide.setXMunFim("Rio de Janeiro");
        ide.setUFFim(TUf.RJ);
        ide.setRetira("1");
        ide.setIndIEToma("1");
        TCTe.InfCte.Ide.Toma3 toma3 = fabrica.createTCTeInfCteIdeToma3();
        toma3.setToma("0");
        ide.setToma3(toma3);
        infCte.setIde(ide);

        TCTe.InfCte.Emit emit = fabrica.createTCTeInfCteEmit();
        emit.setCNPJ("12345678000195");
        emit.setIE("111111111111");
        emit.setXNome("Transportadora Teste LTDA");
        TEndeEmi enderEmit = new TEndeEmi();
        enderEmit.setXLgr("Rua Teste");
        enderEmit.setNro("100");
        enderEmit.setXBairro("Centro");
        enderEmit.setCMun("3550308");
        enderEmit.setXMun("Sao Paulo");
        enderEmit.setCEP("01001000");
        enderEmit.setUF(TUFSemEX.SP);
        emit.setEnderEmit(enderEmit);
        emit.setCRT("3");
        infCte.setEmit(emit);

        TCTe.InfCte.Rem rem = fabrica.createTCTeInfCteRem();
        rem.setCNPJ("12345678000195");
        rem.setIE("111111111111");
        rem.setXNome("Remetente Teste LTDA");
        rem.setEnderReme(enderecoTeste());
        infCte.setRem(rem);

        TCTe.InfCte.Dest dest = fabrica.createTCTeInfCteDest();
        dest.setCNPJ("98765432000198");
        dest.setIE("222222222222");
        dest.setXNome("Destinatario Teste LTDA");
        dest.setEnderDest(enderecoTeste());
        infCte.setDest(dest);

        TCTe.InfCte.VPrest vPrest = fabrica.createTCTeInfCteVPrest();
        vPrest.setVTPrest("1000.00");
        vPrest.setVRec("1000.00");
        infCte.setVPrest(vPrest);

        TImp.ICMS00 icms00 = new TImp.ICMS00();
        icms00.setCST("00");
        icms00.setVBC("1000.00");
        icms00.setPICMS("12.00");
        icms00.setVICMS("120.00");
        TImp tImp = new TImp();
        tImp.setICMS00(icms00);
        TCTe.InfCte.Imp imp = fabrica.createTCTeInfCteImp();
        imp.setICMS(tImp);
        infCte.setImp(imp);

        TCTe.InfCte.InfCTeNorm norm = fabrica.createTCTeInfCteInfCTeNorm();

        TCTe.InfCte.InfCTeNorm.InfCarga infCarga = fabrica.createTCTeInfCteInfCTeNormInfCarga();
        infCarga.setVCarga("1000.00");
        infCarga.setProPred("Carga geral");
        TCTe.InfCte.InfCTeNorm.InfCarga.InfQ infQ = fabrica.createTCTeInfCteInfCTeNormInfCargaInfQ();
        infQ.setCUnid("01");
        infQ.setTpMed("PESO BRUTO");
        infQ.setQCarga("100.0000");
        infCarga.getInfQ().add(infQ);
        norm.setInfCarga(infCarga);

        TCTe.InfCte.InfCTeNorm.InfDoc infDoc = fabrica.createTCTeInfCteInfCTeNormInfDoc();
        TCTe.InfCte.InfCTeNorm.InfDoc.InfNFe infNFe = fabrica.createTCTeInfCteInfCTeNormInfDocInfNFe();
        infNFe.setChave("35250812345678000195550010000001231123456789");
        infDoc.getInfNFe().add(infNFe);
        norm.setInfDoc(infDoc);

        // infModal é <xs:any processContents="skip">: o conteúdo do modal não é validado pelo
        // cte_v4.00.xsd, só precisa ser um elemento bem-formado.
        TCTe.InfCte.InfCTeNorm.InfModal infModal = fabrica.createTCTeInfCteInfCTeNormInfModal();
        infModal.setVersaoModal("4.00");
        infModal.setAny(fragmentoModalRodoviario());
        norm.setInfModal(infModal);

        infCte.setInfCTeNorm(norm);

        cte.setInfCte(infCte);
        return cte;
    }

    private static TEndereco enderecoTeste() {
        TEndereco endereco = new TEndereco();
        endereco.setXLgr("Rua Teste");
        endereco.setNro("100");
        endereco.setXBairro("Centro");
        endereco.setCMun("3550308");
        endereco.setXMun("Sao Paulo");
        endereco.setCEP("01001000");
        endereco.setUF(TUf.SP);
        return endereco;
    }

    private static Element fragmentoModalRodoviario() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document documento = dbf.newDocumentBuilder().newDocument();
            Element rodo = documento.createElementNS("http://www.portalfiscal.inf.br/cte", "rodo");
            Element rntrc = documento.createElementNS("http://www.portalfiscal.inf.br/cte", "RNTRC");
            rntrc.setTextContent("12345678");
            rodo.appendChild(rntrc);
            documento.appendChild(rodo);
            return documento.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o fragmento de infModal do teste", e);
        }
    }
}
