package net.accellog.sefaz4j.assinatura;

import net.accellog.sefaz4j.nfe.model.ObjectFactory;
import net.accellog.sefaz4j.nfe.model.TEnderEmi;
import net.accellog.sefaz4j.nfe.model.TNFe;
import net.accellog.sefaz4j.nfe.model.TUfEmi;
import net.accellog.sefaz4j.validacao.ValidadorXsd;
import net.accellog.sefaz4j.nfe.xml.NFeXmlBuilder;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class AssinadorXmlTest {

    private static final String NFE_NAMESPACE = "http://www.portalfiscal.inf.br/nfe";

    private static final String XML_NAO_ASSINADO =
        "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">" +
        "<infNFe Id=\"NFe35250812345678000195550010000001231123456789\" versao=\"4.00\">" +
        "<ide><cUF>35</cUF></ide>" +
        "</infNFe>" +
        "</NFe>";

    private byte[] pfxBytes() throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/certs/teste.pfx"));
    }

    @Test
    public void assinaEAdicionaElementoSignatureComoUltimoFilhoDeNFe() throws Exception {
        Document documento = parseDocumento(XML_NAO_ASSINADO);

        AssinadorXml.assinar(documento, pfxBytes(), "teste123", NFE_NAMESPACE, "infNFe");

        Element raizNFe = documento.getDocumentElement();
        NodeList assinaturas = raizNFe.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature");
        assertEquals(1, assinaturas.getLength());
        assertEquals("Signature deve ser o último filho de NFe", assinaturas.item(0), raizNFe.getLastChild());
    }

    @Test(expected = CertificadoException.class)
    public void rejeitaSenhaErrada() throws Exception {
        Document documento = parseDocumento(XML_NAO_ASSINADO);
        AssinadorXml.assinar(documento, pfxBytes(), "senha-errada", NFE_NAMESPACE, "infNFe");
    }

    private static final String EVENTO_NAO_ASSINADO =
        "<evento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\">" +
        "<infEvento Id=\"ID11011135250812345678000195550010000001231123456789" + "01\">" +
        "<cOrgao>35</cOrgao><tpAmb>2</tpAmb><CNPJ>12345678000195</CNPJ>" +
        "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
        "<dhEvento>2025-08-12T10:10:00-03:00</dhEvento><tpEvento>110111</tpEvento>" +
        "<nSeqEvento>1</nSeqEvento><verEvento>1.00</verEvento>" +
        "<detEvento versao=\"1.00\"><descEvento>Cancelamento</descEvento>" +
        "<nProt>135250000000001</nProt><xJust>Justificativa de teste com quinze ou mais caracteres</xJust>" +
        "</detEvento>" +
        "</infEvento>" +
        "</evento>";

    @Test
    public void assinaEventoEAdicionaElementoSignatureComoUltimoFilhoDeEvento() throws Exception {
        Document documento = parseDocumento(EVENTO_NAO_ASSINADO);

        AssinadorXml.assinar(documento, pfxBytes(), "teste123", NFE_NAMESPACE, "infEvento");

        Element raizEvento = documento.getDocumentElement();
        NodeList assinaturas = raizEvento.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature");
        assertEquals(1, assinaturas.getLength());
        assertEquals("Signature deve ser o último filho de evento", assinaturas.item(0), raizEvento.getLastChild());
    }

    private Document parseDocumento(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // Teste "seam": nenhum outro teste cruzava a fronteira
    // AssinadorXml.assinar -> ValidadorXsd.validar. É exatamente essa
    // fronteira que escondia o bug crítico do algoritmo de assinatura (a
    // combinação rsa-sha256/sha256/C14N-WithComments que AssinadorXml usava
    // antes é rejeitada pelo schema oficial xmldsig-core-schema_v1.01.xsd,
    // que fixa rsa-sha1/sha1/C14N sem comentários). Monta um TNFe
    // schema-mínimo-válido (mesmo conjunto de campos já usado em
    // Sefaz4jNFeTest/NFeXmlBuilderTest/HomologacaoIntegrationTest), assina
    // com AssinadorXml e garante que o resultado passa por ValidadorXsd sem
    // lançar.
    @Test
    public void documentoAssinadoPorAssinadorXmlPassaNaValidacaoXsd() throws Exception {
        Document documento = NFeXmlBuilder.marcarChaveEMontarDocumento(montarNfeMinimaValida());

        AssinadorXml.assinar(documento, pfxBytes(), "teste123", NFE_NAMESPACE, "infNFe");

        String xmlAssinado = serializar(documento);

        ValidadorXsd.validar(xmlAssinado);
    }

    private static TNFe montarNfeMinimaValida() {
        ObjectFactory fabrica = new ObjectFactory();
        TNFe nfe = fabrica.createTNFe();

        TNFe.InfNFe infNFe = fabrica.createTNFeInfNFe();
        infNFe.setVersao("4.00");

        TNFe.InfNFe.Ide ide = fabrica.createTNFeInfNFeIde();
        ide.setCUF("35");
        ide.setCNF("12345678");
        ide.setNatOp("Venda");
        ide.setMod("55");
        // TSerie/TNF (tiposBasico_v4.00.xsd) proíbem zeros à esquerda (exceto
        // serie "0"), então usamos valores sem padding que somem, junto com
        // os demais campos de 43 dígitos exigidos por ChaveAcessoCalculator
        // (2+4+14+2+3+9+1+8 = 43): serie de 3 dígitos + nNF de 9 dígitos.
        ide.setSerie("123");
        ide.setNNF("123456789");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG("3550308");
        ide.setTpImp("1");
        ide.setTpEmis("1");
        ide.setCDV("9");
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
        return nfe;
    }

    private static String serializar(Document documento) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(documento), new StreamResult(writer));
        return writer.toString();
    }
}
