package net.accellog.sefaz4j.nfse.xml;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Monta o XML de um evento da NFS-e (Padrão Nacional) — cancelamento, cancelamento por
 * substituição etc. Não assina nem valida contra XSD (isso é responsabilidade da fachada
 * {@code Sefaz4jNFSe}).
 *
 * <p>Diferente do evento de NFe/CTe (dois níveis: {@code evento}/{@code infEvento} +
 * {@code detEvento}), o evento da NFS-e tem QUATRO níveis aninhados
 * ({@code evento} → {@code infEvento} → {@code pedRegEvento} → {@code infPedReg} → elemento
 * específico do evento) e DOIS atributos {@code Id} calculados separadamente:</p>
 *
 * <ul>
 *   <li>{@code infPedReg/@Id} = {@code "PRE"} + chNFSe(50) + tipoEvento(6) — 59 caracteres.</li>
 *   <li>{@code infEvento/@Id} = {@code "EVT"} + chNFSe(50) + tipoEvento(6) + nPedRegEvento(3) — 62.</li>
 * </ul>
 *
 * <p><b>Por que o "PRE" não leva o nPedRegEvento:</b> o comentário de documentação do
 * {@code TSIdPedRegEvt} (tiposSimples_v1.01.xsd) diz "PRE" + chave + tipo do evento + nPedRegEvento,
 * mas o próprio tipo tem {@code maxLength="59"} e o pattern
 * {@code PRE[0-9]{8}(1[0-9]{14}|2[0-9A-Z]{14})[0-9]{33}}, isto é, exatamente 56 caracteres depois do
 * "PRE" — 50 (chave) + 6 (tipo) e nada mais. Com o nPedRegEvento o valor teria 62 caracteres e
 * estouraria o próprio {@code maxLength}. Já o {@code TSIdEvento} tem {@code maxLength="62"},
 * pattern com 59 caracteres depois do "EVT" (50 + 6 + 3) e um comentário que explicita as larguras
 * ("Chave de acesso(50) Tipo do evento (6) + Pedido de Registro do Evento(3)"). O pattern é o que o
 * validador aplica, então ele manda sobre o comentário divergente do "PRE".</p>
 *
 * <p>O {@code pedRegEvento} é EMBUTIDO no {@code infEvento} (não é documento de topo), mas também é
 * declarado como elemento raiz próprio em {@code pedRegEvento_v1.01.xsd} — por isso
 * {@link #montarPedRegEvento} devolve uma {@code String} autocontida (com o {@code xmlns} próprio),
 * validável isoladamente antes de ser embutida por {@link #envolverEmEvento}. Só o
 * {@code ds:Signature} do {@code evento} externo é obrigatório; o do {@code pedRegEvento} é
 * {@code minOccurs="0"} e esta biblioteca não o gera.</p>
 */
public final class EventoNFSeXmlBuilder {

    public static final String NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";

    /** Sufixo numérico do elemento {@code e101101} (evento de cancelamento). */
    public static final String TIPO_EVENTO_CANCELAMENTO = "101101";

    /** Sufixo numérico do elemento {@code e105102} (evento de cancelamento por substituição). */
    public static final String TIPO_EVENTO_SUBSTITUICAO = "105102";

    /** {@code TVerNFSe} aceita só "1.00" ou "1.01"; esta biblioteca emite sempre a mais recente. */
    private static final String VERSAO = "1.01";

    private static final String VER_APLIC = "1.0.0";

    /** {@code TSAmbGeradorEvt}: 1=Prefeitura, 2=Sefin Nacional, 3=ADN. */
    private static final String AMB_GERADOR = "2";

    /**
     * {@code nSeqEvento}/nPedRegEvento: "Para os eventos que ocorrem somente uma vez, como é o caso
     * do cancelamento, o nSeqEvento = 001" (documentação do próprio {@code TCInfEvento}).
     */
    private static final String N_SEQ_EVENTO = "001";

    /**
     * {@code nDFSe} ({@code TSNumDFe}, 1 a 13 dígitos) é o sequencial do documento no ambiente
     * gerador do município — esta biblioteca não mantém uma sequência local, então emite "1".
     */
    private static final String N_DFSE = "1";

    private EventoNFSeXmlBuilder() {
    }

    /**
     * Instante corrente no formato {@code TSDateTimeUTC} ({@code AAAA-MM-DDThh:mm:ssTZD}). O "xxx"
     * minúsculo sempre emite "+HH:mm"/"-HH:mm" e nunca colapsa para "Z" em host UTC — o pattern do
     * tipo não aceita "Z". Mesma precaução já usada em {@code EventoCTeXmlBuilder}.
     */
    public static String agora() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));
    }

    /** Fragmento {@code <e101101>} do evento de cancelamento. */
    public static String fragmentoCancelamento(String cMotivo, String xMotivo) {
        // xDesc é uma xs:enumeration de valor único no TE101101 — o texto tem de ser exatamente este,
        // com acentuação e hífen inclusos.
        return "<e101101>" +
            "<xDesc>Cancelamento de NFS-e</xDesc>" +
            "<cMotivo>" + cMotivo + "</cMotivo>" +
            "<xMotivo>" + escaparTextoXml(xMotivo) + "</xMotivo>" +
            "</e101101>";
    }

    /**
     * Fragmento {@code <e105102>} do evento de cancelamento por substituição.
     *
     * <p>{@code cMotivo} é {@code TSCodJustSubst}, cuja enumeração são strings de DOIS dígitos
     * ("01" a "05" e "99"), não inteiros — diferente do {@code TSCodJustCanc} do {@code e101101}.
     * {@code xMotivo} é {@code minOccurs="0"} no {@code TE105102} (também diferente do
     * {@code e101101}, onde é obrigatório): passe {@code null} para omiti-lo. Ambos são "obtidos do
     * campo da DPS {@code DPS/infDPS/subst/cMotivo}/{@code xMotivo}", segundo a documentação do
     * próprio XSD. Já o {@code chSubstituta} é a chave da NFS-e NOVA, independente da DPS.</p>
     */
    public static String fragmentoSubstituicao(String cMotivo, String xMotivo, String chSubstituta) {
        // xDesc é uma xs:enumeration de valor único no TE105102 — o texto tem de ser exatamente este,
        // com acentuação e hífen inclusos.
        return "<e105102>" +
            "<xDesc>Cancelamento de NFS-e por Substituição</xDesc>" +
            "<cMotivo>" + cMotivo + "</cMotivo>" +
            (xMotivo != null ? "<xMotivo>" + escaparTextoXml(xMotivo) + "</xMotivo>" : "") +
            "<chSubstituta>" + chSubstituta + "</chSubstituta>" +
            "</e105102>";
    }

    /**
     * Monta o {@code pedRegEvento} autocontido (com {@code xmlns} próprio), pronto tanto para ser
     * validado isoladamente contra {@code pedRegEvento_v1.01.xsd} quanto para ser embutido, sem
     * alteração, por {@link #envolverEmEvento}.
     *
     * @param eventoEspecificoXmlFragmento fragmento já serializado do elemento do evento
     *                                     ({@code <e101101>...}, {@code <e105102>...} etc.)
     */
    public static String montarPedRegEvento(
        String tpAmb,
        String cnpjAutor,
        String chNFSe,
        String tipoEvento,
        String dhEvento,
        String eventoEspecificoXmlFragmento
    ) {
        return "<pedRegEvento xmlns=\"" + NFSE_NAMESPACE + "\" versao=\"" + VERSAO + "\">" +
            "<infPedReg Id=\"" + idPedRegEvento(chNFSe, tipoEvento) + "\">" +
            "<tpAmb>" + tpAmb + "</tpAmb>" +
            "<verAplic>" + VER_APLIC + "</verAplic>" +
            "<dhEvento>" + dhEvento + "</dhEvento>" +
            "<CNPJAutor>" + cnpjAutor + "</CNPJAutor>" +
            "<chNFSe>" + chNFSe + "</chNFSe>" +
            eventoEspecificoXmlFragmento +
            "</infPedReg>" +
            "</pedRegEvento>";
    }

    /**
     * Envolve um {@code pedRegEvento} já montado (e, idealmente, já validado) na estrutura externa
     * {@code evento}/{@code infEvento}. O documento resultante ainda NÃO está assinado —
     * {@code TCEvento} exige {@code ds:Signature}, então ele só passa em
     * {@code evento_v1.01.xsd} depois da assinatura do {@code infEvento}.
     */
    public static Document envolverEmEvento(String chNFSe, String tipoEvento, String dhProc, String pedRegEventoXml) {
        String xml = "<evento xmlns=\"" + NFSE_NAMESPACE + "\" versao=\"" + VERSAO + "\">" +
            "<infEvento Id=\"" + idEvento(chNFSe, tipoEvento) + "\">" +
            "<verAplic>" + VER_APLIC + "</verAplic>" +
            "<ambGer>" + AMB_GERADOR + "</ambGer>" +
            "<nSeqEvento>" + N_SEQ_EVENTO + "</nSeqEvento>" +
            "<dhProc>" + dhProc + "</dhProc>" +
            "<nDFSe>" + N_DFSE + "</nDFSe>" +
            pedRegEventoXml +
            "</infEvento>" +
            "</evento>";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML do evento da NFS-e", e);
        }
    }

    /**
     * Conveniência: monta o evento de cancelamento completo (não assinado, não validado) em uma
     * chamada só. A fachada usa as peças separadas para poder validar o {@code pedRegEvento}
     * isoladamente antes de embuti-lo.
     *
     * <p>{@code dhProc} e {@code dhEvento} recebem o MESMO instante: neste fluxo síncrono o pedido
     * do autor e o processamento são o mesmo momento.</p>
     */
    public static Document montarCancelamento(String tpAmb, String cnpjAutor, String chNFSe, String cMotivo, String xMotivo) {
        String agora = agora();
        String pedRegEvento = montarPedRegEvento(
            tpAmb, cnpjAutor, chNFSe, TIPO_EVENTO_CANCELAMENTO, agora, fragmentoCancelamento(cMotivo, xMotivo)
        );
        return envolverEmEvento(chNFSe, TIPO_EVENTO_CANCELAMENTO, agora, pedRegEvento);
    }

    public static String idPedRegEvento(String chNFSe, String tipoEvento) {
        return "PRE" + chNFSe + tipoEvento;
    }

    public static String idEvento(String chNFSe, String tipoEvento) {
        return "EVT" + chNFSe + tipoEvento + N_SEQ_EVENTO;
    }

    private static String escaparTextoXml(String texto) {
        return texto
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
