package net.accellog.sefaz4j.nfse.xml;

import net.accellog.sefaz4j.validacao.ValidadorXsd;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EventoNFSeXmlBuilderTest {

    private static final String NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";

    /**
     * Chave de acesso de 50 caracteres construída para satisfazer SIMULTANEAMENTE os três padrões
     * envolvidos: {@code TSChaveNFSe} ({@code [0-9]{6}([0-9A-Z]{14})[0-9]{30}}),
     * {@code TSIdPedRegEvt} ({@code PRE[0-9]{8}(1[0-9]{14}|2[0-9A-Z]{14})[0-9]{33}}) e
     * {@code TSIdEvento} ({@code EVT[0-9]{8}(1[0-9]{14}|2[0-9A-Z]{14})[0-9]{36}}).
     *
     * <p>Os dois padrões de Id revelam o leiaute interno real da chave, que o
     * {@code TSChaveNFSe} sozinho (só {@code maxLength}/pattern genérico) não deixa explícito:
     * cLocEmi(7) + ambGer(1) = 8 dígitos, depois o tipo de inscrição federal (1=CPF / 2=CNPJ)
     * seguido dos 14 caracteres da inscrição, e mais 27 dígitos até completar 50.
     * Aqui: "3550308" + "1" + "2" + "12345678000195" + 27 dígitos.
     */
    private static final String CH_NFSE = "35503081" + "2" + "12345678000195" + "1000000000001" + "2508" + "000012345" + "0";

    private static final String PADRAO_ID_PED_REG_EVT = "PRE[0-9]{8}(1[0-9]{14}|2[0-9A-Z]{14})[0-9]{33}";
    private static final String PADRAO_ID_EVENTO = "EVT[0-9]{8}(1[0-9]{14}|2[0-9A-Z]{14})[0-9]{36}";

    @Test
    public void chaveDeTesteTemOs50CaracteresDoTSChaveNFSe() {
        assertEquals(50, CH_NFSE.length());
        assertTrue(CH_NFSE.matches("[0-9]{6}([0-9A-Z]{14})[0-9]{30}"));
    }

    @Test
    public void montarCancelamentoGeraAEstruturaAninhadaDeQuatroNiveis() {
        Document documento = EventoNFSeXmlBuilder.montarCancelamento("2", "12345678000195", CH_NFSE, "1", "Cancelamento por erro na emissao");

        Element evento = documento.getDocumentElement();
        assertEquals("evento", evento.getLocalName());
        assertEquals(NFSE_NAMESPACE, evento.getNamespaceURI());
        assertEquals("1.01", evento.getAttribute("versao"));

        Element infEvento = (Element) evento.getElementsByTagNameNS(NFSE_NAMESPACE, "infEvento").item(0);
        assertNotNull(infEvento);
        assertEquals("o infEvento deve ser filho direto do evento", evento, infEvento.getParentNode());

        Element pedRegEvento = (Element) documento.getElementsByTagNameNS(NFSE_NAMESPACE, "pedRegEvento").item(0);
        assertNotNull(pedRegEvento);
        assertEquals("o pedRegEvento é EMBUTIDO no infEvento, não é elemento de topo",
            infEvento, pedRegEvento.getParentNode());
        assertEquals("1.01", pedRegEvento.getAttribute("versao"));

        Element infPedReg = (Element) documento.getElementsByTagNameNS(NFSE_NAMESPACE, "infPedReg").item(0);
        assertNotNull(infPedReg);
        assertEquals(pedRegEvento, infPedReg.getParentNode());

        Element e101101 = (Element) documento.getElementsByTagNameNS(NFSE_NAMESPACE, "e101101").item(0);
        assertNotNull(e101101);
        assertEquals(infPedReg, e101101.getParentNode());
    }

    @Test
    public void montarCancelamentoPreencheOsCamposFixosDeInfEventoEInfPedReg() {
        Document documento = EventoNFSeXmlBuilder.montarCancelamento("2", "12345678000195", CH_NFSE, "9", "Motivo generico de cancelamento");

        assertEquals("1.0.0", texto(documento, "verAplic"));
        assertEquals("2", texto(documento, "ambGer"));
        assertEquals("001", texto(documento, "nSeqEvento"));
        assertEquals("1", texto(documento, "nDFSe"));
        assertEquals("2", texto(documento, "tpAmb"));
        assertEquals("12345678000195", texto(documento, "CNPJAutor"));
        assertEquals(CH_NFSE, texto(documento, "chNFSe"));
    }

    @Test
    public void e101101UsaOXDescFixoExatoDoXsdMaisCMotivoEXMotivo() {
        Document documento = EventoNFSeXmlBuilder.montarCancelamento("2", "12345678000195", CH_NFSE, "2", "Servico nao foi prestado ao tomador");

        assertEquals("Cancelamento de NFS-e", texto(documento, "xDesc"));
        assertEquals("2", texto(documento, "cMotivo"));
        assertEquals("Servico nao foi prestado ao tomador", texto(documento, "xMotivo"));
    }

    /**
     * O comentário de documentação do {@code TSIdPedRegEvt} diz "PRE" + chave + tipo do evento +
     * nPedRegEvento, mas o {@code maxLength=59} e o próprio pattern (8+15+33 = 56 caracteres depois
     * do "PRE") só fecham com 3 + 50 + 6 = 59 — ou seja, SEM o nPedRegEvento. Já o
     * {@code TSIdEvento} (maxLength=62, pattern com 8+15+36 = 59 depois do "EVT") fecha com
     * 3 + 50 + 6 + 3 = 62, COM o nSeqEvento de 3 dígitos, exatamente como seu comentário explicita
     * as larguras. Este teste fixa essa leitura (o pattern manda, não o comentário).
     */
    @Test
    public void idsSeguemOsPadroesEAsLarguraDosTiposDoXsd() {
        Document documento = EventoNFSeXmlBuilder.montarCancelamento("2", "12345678000195", CH_NFSE, "1", "Cancelamento por erro na emissao");

        String idPedReg = ((Element) documento.getElementsByTagNameNS(NFSE_NAMESPACE, "infPedReg").item(0)).getAttribute("Id");
        String idEvento = ((Element) documento.getElementsByTagNameNS(NFSE_NAMESPACE, "infEvento").item(0)).getAttribute("Id");

        assertEquals("PRE" + CH_NFSE + "101101", idPedReg);
        assertEquals(59, idPedReg.length());
        assertTrue("infPedReg/@Id deve casar com TSIdPedRegEvt: " + idPedReg, idPedReg.matches(PADRAO_ID_PED_REG_EVT));

        assertEquals("EVT" + CH_NFSE + "101101" + "001", idEvento);
        assertEquals(62, idEvento.length());
        assertTrue("infEvento/@Id deve casar com TSIdEvento: " + idEvento, idEvento.matches(PADRAO_ID_EVENTO));
    }

    /**
     * dhProc (infEvento) e dhEvento (infPedReg) representam o mesmo instante de processamento neste
     * fluxo síncrono e devem ser idênticos; o formato TSDateTimeUTC não aceita "Z" — só "+hh:mm"/"-hh:mm".
     */
    @Test
    public void dhProcEDhEventoSaoOMesmoInstanteNoFormatoTSDateTimeUTC() {
        Document documento = EventoNFSeXmlBuilder.montarCancelamento("2", "12345678000195", CH_NFSE, "1", "Cancelamento por erro na emissao");

        String dhProc = texto(documento, "dhProc");
        String dhEvento = texto(documento, "dhEvento");

        assertEquals(dhProc, dhEvento);
        assertTrue("dhProc deve terminar com +hh:mm/-hh:mm, nunca 'Z': " + dhProc,
            dhProc.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-][0-9]{2}:00"));
    }

    /**
     * Primeira passada da validação em duas etapas: o pedRegEvento é declarado como elemento raiz
     * próprio em pedRegEvento_v1.01.xsd, então o fragmento é validável isoladamente antes de ser
     * embutido no evento (o evento completo só valida depois de assinado, pois TCEvento exige
     * ds:Signature).
     */
    @Test
    public void pedRegEventoIsoladoValidaContraOSeuProprioSchema() {
        String pedRegEvento = EventoNFSeXmlBuilder.montarPedRegEvento(
            "2",
            "12345678000195",
            CH_NFSE,
            EventoNFSeXmlBuilder.TIPO_EVENTO_CANCELAMENTO,
            EventoNFSeXmlBuilder.agora(),
            EventoNFSeXmlBuilder.fragmentoCancelamento("1", "Cancelamento por erro na emissao")
        );

        ValidadorXsd.validar(pedRegEvento, "/schemas/nfse/pedRegEvento_v1.01.xsd");
    }

    /**
     * Prova que o wrapper (evento/infEvento/pedRegEvento/infPedReg + os dois Ids) é genérico e
     * reaproveitável por outro tipo de evento — o que a Task 11 (cancelamento por substituição,
     * e105102) precisa fazer sem duplicar nada da montagem acima.
     */
    @Test
    public void wrapperCompartilhadoAceitaOutroTipoDeEvento() {
        String fragmentoFicticio = "<e105102><xDesc>Cancelamento de NFS-e por Substituição</xDesc></e105102>";

        String pedRegEvento = EventoNFSeXmlBuilder.montarPedRegEvento(
            "2", "12345678000195", CH_NFSE, "105102", EventoNFSeXmlBuilder.agora(), fragmentoFicticio
        );
        Document documento = EventoNFSeXmlBuilder.envolverEmEvento(CH_NFSE, "105102", EventoNFSeXmlBuilder.agora(), pedRegEvento);

        assertEquals("PRE" + CH_NFSE + "105102",
            ((Element) documento.getElementsByTagNameNS(NFSE_NAMESPACE, "infPedReg").item(0)).getAttribute("Id"));
        assertEquals("EVT" + CH_NFSE + "105102" + "001",
            ((Element) documento.getElementsByTagNameNS(NFSE_NAMESPACE, "infEvento").item(0)).getAttribute("Id"));

        NodeList e105102 = documento.getElementsByTagNameNS(NFSE_NAMESPACE, "e105102");
        assertEquals(1, e105102.getLength());
    }

    /** Chave da NFS-e SUBSTITUTA (a nova), distinta da {@link #CH_NFSE} (a substituída). */
    private static final String CH_NFSE_SUBSTITUTA =
        "35503081" + "2" + "12345678000195" + "1000000000001" + "2508" + "000067890" + "0";

    /**
     * {@code TE105102/xDesc} é uma {@code xs:enumeration} de valor único — o texto tem de ser
     * exatamente este, com acentuação. {@code cMotivo} é {@code TSCodJustSubst}, cuja enumeração são
     * strings de DOIS dígitos ("01".."05", "99"), não inteiros.
     */
    @Test
    public void fragmentoSubstituicaoUsaOXDescFixoExatoMaisCMotivoEChSubstituta() {
        String fragmento = EventoNFSeXmlBuilder.fragmentoSubstituicao(
            "01", "Substituicao por desenquadramento do Simples Nacional", CH_NFSE_SUBSTITUTA
        );

        assertTrue(fragmento.startsWith("<e105102>"));
        assertTrue(fragmento.endsWith("</e105102>"));
        assertTrue("o xDesc é uma enumeração de valor único no TE105102",
            fragmento.contains("<xDesc>Cancelamento de NFS-e por Substituição</xDesc>"));
        assertTrue(fragmento.contains("<cMotivo>01</cMotivo>"));
        assertTrue(fragmento.contains("<xMotivo>Substituicao por desenquadramento do Simples Nacional</xMotivo>"));
        assertTrue(fragmento.contains("<chSubstituta>" + CH_NFSE_SUBSTITUTA + "</chSubstituta>"));
    }

    /** {@code xMotivo} é {@code minOccurs="0"} no {@code TE105102} (diferente do TE101101). */
    @Test
    public void fragmentoSubstituicaoOmiteXMotivoQuandoNulo() {
        String fragmento = EventoNFSeXmlBuilder.fragmentoSubstituicao("99", null, CH_NFSE_SUBSTITUTA);

        assertFalse("xMotivo é opcional no TE105102 — passando null o elemento não deve aparecer",
            fragmento.contains("<xMotivo>"));
        assertTrue(fragmento.contains("<cMotivo>99</cMotivo>"));
        assertTrue(fragmento.contains("<chSubstituta>" + CH_NFSE_SUBSTITUTA + "</chSubstituta>"));
    }

    @Test
    public void fragmentoSubstituicaoEscapaOXMotivo() {
        String fragmento = EventoNFSeXmlBuilder.fragmentoSubstituicao(
            "05", "Rejeitada pelo tomador <A> & <B> conforme acordo", CH_NFSE_SUBSTITUTA
        );

        assertTrue(fragmento.contains("<xMotivo>Rejeitada pelo tomador &lt;A&gt; &amp; &lt;B&gt; conforme acordo</xMotivo>"));
    }

    /**
     * Mesma validação em duas passadas do cancelamento simples: o {@code pedRegEvento} do evento
     * {@code e105102} também é validável isoladamente contra o seu próprio schema.
     */
    @Test
    public void pedRegEventoDeSubstituicaoValidaContraOSeuProprioSchema() {
        String pedRegEvento = EventoNFSeXmlBuilder.montarPedRegEvento(
            "2",
            "12345678000195",
            CH_NFSE,
            EventoNFSeXmlBuilder.TIPO_EVENTO_SUBSTITUICAO,
            EventoNFSeXmlBuilder.agora(),
            EventoNFSeXmlBuilder.fragmentoSubstituicao("01", "Substituicao por erro de enquadramento", CH_NFSE_SUBSTITUTA)
        );

        ValidadorXsd.validar(pedRegEvento, "/schemas/nfse/pedRegEvento_v1.01.xsd");
    }

    /** Mesmo caso, sem o {@code xMotivo} opcional — prova que o fragmento continua válido. */
    @Test
    public void pedRegEventoDeSubstituicaoSemXMotivoValidaContraOSeuProprioSchema() {
        String pedRegEvento = EventoNFSeXmlBuilder.montarPedRegEvento(
            "2",
            "12345678000195",
            CH_NFSE,
            EventoNFSeXmlBuilder.TIPO_EVENTO_SUBSTITUICAO,
            EventoNFSeXmlBuilder.agora(),
            EventoNFSeXmlBuilder.fragmentoSubstituicao("99", null, CH_NFSE_SUBSTITUTA)
        );

        ValidadorXsd.validar(pedRegEvento, "/schemas/nfse/pedRegEvento_v1.01.xsd");
    }

    @Test
    public void tipoEventoSubstituicaoEOSufixoDoElementoE105102() {
        assertEquals("105102", EventoNFSeXmlBuilder.TIPO_EVENTO_SUBSTITUICAO);
    }

    @Test
    public void xMotivoComCaracteresEspeciaisEEscapado() {
        Document documento = EventoNFSeXmlBuilder.montarCancelamento(
            "2", "12345678000195", CH_NFSE, "9", "Erro no campo <valor> & na descricao"
        );

        assertEquals("Erro no campo <valor> & na descricao", texto(documento, "xMotivo"));
    }

    private static String texto(Document documento, String nomeLocal) {
        NodeList lista = documento.getElementsByTagNameNS(NFSE_NAMESPACE, nomeLocal);
        return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
    }
}
