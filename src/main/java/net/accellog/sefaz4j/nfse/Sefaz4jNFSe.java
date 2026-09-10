package net.accellog.sefaz4j.nfse;

import net.accellog.sefaz4j.assinatura.AssinadorXml;
import net.accellog.sefaz4j.nfse.model.TCDPS;
import net.accellog.sefaz4j.nfse.model.TCSubstituicao;
import net.accellog.sefaz4j.nfse.webservice.PayloadCompactado;
import net.accellog.sefaz4j.nfse.webservice.RespostaNFSe;
import net.accellog.sefaz4j.nfse.webservice.RespostaNFSeParser;
import net.accellog.sefaz4j.nfse.xml.DpsXmlBuilder;
import net.accellog.sefaz4j.nfse.xml.EventoNFSeXmlBuilder;
import net.accellog.sefaz4j.validacao.ValidadorXsd;
import net.accellog.sefaz4j.webservice.SefazHttpClient;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.signature.XMLSignature;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Fachada da NFS-e (Padrão Nacional / SEFIN Nacional).
 *
 * <p>Diferente de {@link net.accellog.sefaz4j.nfe.Sefaz4jNFe} e
 * {@link net.accellog.sefaz4j.cte.Sefaz4jCTe}, o transporte aqui é REST/JSON (não SOAP): o XML da DPS
 * assinado é comprimido em gzip, codificado em Base64 e enviado dentro de um objeto JSON; a resposta
 * traz o XML da NFS-e pelo mesmo caminho inverso.</p>
 */
public final class Sefaz4jNFSe {

    private static final String NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";
    private static final String DPS_XSD_RAIZ = "/schemas/nfse/DPS_v1.01.xsd";
    private static final String PED_REG_EVENTO_XSD_RAIZ = "/schemas/nfse/pedRegEvento_v1.01.xsd";
    private static final String EVENTO_XSD_RAIZ = "/schemas/nfse/evento_v1.01.xsd";
    // ATENÇÃO: nome do campo JSON NÃO confirmado contra o Manual de Integração do SEFIN Nacional nem
    // testado contra Homologação — é uma inferência da convenção de nomes já observada em
    // "dpsXmlGZipB64"/"nfseXmlGZipB64". Mesma ressalva de ALGORITMO_ASSINATURA/ALGORITMO_DIGEST
    // abaixo: se a Homologação real rejeitar, troque só esta constante. Vale para a requisição e
    // para o campo lido na resposta.
    private static final String CAMPO_JSON_EVENTO = "eventoXmlGZipB64";
    // TSMotivo (tiposSimples_v1.01.xsd): minLength=15, maxLength=255.
    private static final int TAMANHO_MINIMO_X_MOTIVO = 15;
    private static final int TAMANHO_MAXIMO_X_MOTIVO = 255;
    // ATENÇÃO: algoritmo NÃO confirmado contra o Manual de Integração do SEFIN Nacional nem
    // testado empiricamente contra Homologação. RSA-SHA256/SHA-256 é o palpite mais provável
    // (padrão federal mais recente), não um fato verificado. Se a Homologação real rejeitar a
    // assinatura, troque só estas duas constantes.
    private static final String ALGORITMO_ASSINATURA = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256;
    private static final String ALGORITMO_DIGEST = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256;
    private static final String URL_PRODUCAO = "https://sefin.nfse.gov.br/sefinnacional/nfse";
    private static final String URL_HOMOLOGACAO = "https://sefin.producaorestrita.nfse.gov.br/SefinNacional/nfse";
    // API DPS (Manual dos Contribuintes - Sistema Nacional NFS-e, seção 1.4): GET /dps/{id}
    // recupera a chaveAcesso de 50 posições da NFS-e a partir do Id de 42 dígitos da DPS. É um
    // recurso irmão de /nfse, não um sub-recurso dele -- daí a base própria em vez de reaproveitar
    // URL_PRODUCAO/URL_HOMOLOGACAO.
    private static final String URL_DPS_PRODUCAO = "https://sefin.nfse.gov.br/sefinnacional/dps";
    private static final String URL_DPS_HOMOLOGACAO = "https://sefin.producaorestrita.nfse.gov.br/SefinNacional/dps";
    // Portal de Consulta Pública do SEFIN Nacional -- usado como "link de autenticidade" no
    // DANFSe (texto + QR code). Diferente da URL de transmissão acima (sefin.*), é um domínio
    // e formato de URL completamente separados (mesmo padrão do bot Delphi legado, que lia
    // ACBrNFSeX.NotasFiscais.Items[0].NFSe.Link -- calculado internamente pelo ACBr a partir
    // de ambiente+chave, não devolvido pela SEFAZ na resposta de autorização).
    private static final String LINK_CONSULTA_PUBLICA_PRODUCAO = "https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave=";
    private static final String LINK_CONSULTA_PUBLICA_HOMOLOGACAO = "https://www.producaorestrita.nfse.gov.br/ConsultaPublica/?tpc=1&chave=";
    // TCInfNFSe/cStat (TStat, tiposSimples_v1.01.xsd) enumera exatamente 100/102/103/107, e as
    // QUATRO representam uma NFS-e gerada com sucesso (Gerada / Decisão Judicial / Avulsa / MEI) —
    // ao contrário da NFe/CTe, este cStat classifica o TIPO de sucesso, não autorização-vs-rejeição;
    // rejeição de negócio de fato chega pelo array JSON "erros", já tratado acima por !isSucesso().
    private static final java.util.Set<String> CSTAT_SUCESSO = java.util.Set.of("100", "102", "103", "107");
    // TCInfNFSe NÃO tem um "xMotivo" companheiro do cStat na resposta de sucesso (diferente de
    // NFe/CTe/MDFe, cujo protNFe/infProt traz os dois) -- sem isso, doe_retorno_motivo ficava
    // sempre NULL numa autorização (só o motivo de ERRO era gravado, via getMensagemErro()), e o
    // bot Delphi legado (nfse_v210.pas) sempre hardcodeava exatamente "Autorizado o uso da NFSe"
    // para cStat 100 mesmo antes desta migração -- mantido aqui para não quebrar telas/relatórios
    // que já esperam esse texto literal.
    private static final java.util.Map<String, String> DESCRICAO_CSTAT_SUCESSO = java.util.Map.of(
        "100", "Autorizado o uso da NFSe",
        "102", "Autorizado o uso da NFSe - Decisão Judicial",
        "103", "Autorizado o uso da NFSe Avulsa",
        "107", "Autorizado o uso da NFSe - MEI"
    );

    private Sefaz4jNFSe() {
    }

    public static ResultadoEmissao emitir(Sefaz4jConfig config, TCDPS dps) {
        Document documento = montarDocumento(dps);

        // prefixoAssinatura="" : SEFIN Nacional rejeita ds:Signature com prefixo (E1228) — ver
        // AssinadorXml.assinar(..., prefixoAssinatura) para o porquê.
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), NFSE_NAMESPACE, "infDPS", ALGORITMO_ASSINATURA, ALGORITMO_DIGEST, "");

        String xmlAssinado = serializarDocumento(documento);

        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoEmissao enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado) {
        return enviarEProcessar(config, xmlAssinado);
    }

    public static ResultadoConsulta consultarSituacao(Sefaz4jConfig config, String chaveAcesso) {
        exigirChaveAcessoValida(chaveAcesso);

        String url = (config.getUrlOverride() != null ? config.getUrlOverride() : baseUrlEmissao(config)) + "/" + chaveAcesso;

        String respostaBruta = SefazHttpClient.buscar(url, config.getPfxBytes(), config.getSenhaPfx(), config.getTimeout());

        RespostaNFSe resposta = RespostaNFSeParser.parsear(respostaBruta, "nfseXmlGZipB64");

        if (!resposta.isSucesso()) {
            return new ResultadoConsulta(false, resposta.getCodigoErro(), resposta.getMensagemErro(), null, null);
        }

        String cStat = extrairTextoDoElemento(resposta.getXmlDescomprimido(), "cStat");
        return new ResultadoConsulta(CSTAT_SUCESSO.contains(cStat), cStat, DESCRICAO_CSTAT_SUCESSO.get(cStat), resposta.getChaveAcesso(), resposta.getXmlDescomprimido());
    }

    /**
     * Recupera a chaveAcesso (50 posições) da NFS-e a partir do Id de 42 dígitos da DPS (API DPS,
     * {@code GET /dps/{id}} -- ver seção 1.4 do Manual dos Contribuintes do Sistema Nacional
     * NFS-e). Necessário porque {@code doe_chave} (bot-sefaz) só guarda o Id da DPS -- nunca a
     * chaveAcesso --, mas {@link #consultarSituacao}/{@link #cancelar} exigem a chaveAcesso de 50
     * caracteres.
     *
     * <p>ATENÇÃO: formato do corpo de resposta e códigos HTTP de erro (DPS inexistente, NFS-e
     * ainda não gerada, ou sigilo fiscal -- solicitante não é ator da NFS-e) NÃO confirmados
     * contra o Manual de Integração nem testados contra Homologação -- só o formato de sucesso
     * ({@code {"chaveAcesso": "..."}}) é inferido da mesma convenção JSON usada por
     * {@code enviarEProcessar}/{@code consultarSituacao}.</p>
     *
     * @throws IllegalStateException se a resposta não trouxer uma chaveAcesso (erro de negócio,
     *         DPS ainda sem NFS-e gerada, ou acesso negado por sigilo fiscal)
     */
    public static String consultarChavePorDps(Sefaz4jConfig config, String idDps) {
        exigirIdDpsValido(idDps);

        String url = (config.getUrlOverride() != null ? config.getUrlOverride() : baseUrlDps(config)) + "/" + idDps;

        String respostaBruta = SefazHttpClient.buscar(url, config.getPfxBytes(), config.getSenhaPfx(), config.getTimeout());

        RespostaNFSe resposta = RespostaNFSeParser.parsear(respostaBruta, "");

        if (!resposta.isSucesso()) {
            throw new IllegalStateException(
                "Nao foi possivel obter a chaveAcesso da NFSe para o DPS " + idDps + ": " + resposta.getMensagemErro()
            );
        }
        if (resposta.getChaveAcesso() == null) {
            throw new IllegalStateException(
                "SEFIN Nacional nao devolveu chaveAcesso para o DPS " + idDps
            );
        }

        return resposta.getChaveAcesso();
    }

    /**
     * Cancela uma NFS-e já emitida enviando o evento {@code e101101} ao ADN/SEFIN Nacional.
     *
     * <p>Ao contrário de NFe/CTe, o CNPJ do autor do evento é um parâmetro explícito: a chave da
     * NFS-e não tem, nesta biblioteca, um leiaute interno confirmado do qual extraí-lo com segurança
     * por posição fixa (NFe/CTe usam {@code chaveAcesso.substring(6, 20)}).</p>
     *
     * @param cMotivo {@code TSCodJustCanc}: 1 = Erro na Emissão, 2 = Serviço não Prestado, 9 = Outros
     * @param xMotivo {@code TSMotivo}: 15 a 255 caracteres
     */
    public static ResultadoEvento cancelar(Sefaz4jConfig config, String chaveAcesso, String cnpjAutor, int cMotivo, String xMotivo) {
        exigirChaveAcessoValida(chaveAcesso);
        exigirCnpjValido(cnpjAutor);
        if (cMotivo != 1 && cMotivo != 2 && cMotivo != 9) {
            throw new IllegalArgumentException(
                "O campo 'cMotivo' deve ser 1 (Erro na Emissão), 2 (Serviço não Prestado) ou 9 (Outros), obteve: " + cMotivo
            );
        }
        exigirTamanho(xMotivo, TAMANHO_MINIMO_X_MOTIVO, TAMANHO_MAXIMO_X_MOTIVO, "xMotivo");

        String tpAmb = String.valueOf(config.getAmbiente().getTpAmb());
        // Mesmo instante para dhEvento (infPedReg) e dhProc (infEvento): neste fluxo síncrono o
        // pedido do autor e o processamento são o mesmo momento.
        String agora = EventoNFSeXmlBuilder.agora();
        String tipoEvento = EventoNFSeXmlBuilder.TIPO_EVENTO_CANCELAMENTO;

        String pedRegEventoXml = EventoNFSeXmlBuilder.montarPedRegEvento(
            tpAmb, cnpjAutor, chaveAcesso, tipoEvento, agora,
            EventoNFSeXmlBuilder.fragmentoCancelamento(String.valueOf(cMotivo), xMotivo)
        );
        // Validação em duas passadas (mesma convenção do evento de CT-e): o pedRegEvento é declarado
        // como elemento raiz próprio em pedRegEvento_v1.01.xsd, então é validável isolado ANTES de
        // ser embutido; o evento completo só valida depois de assinado (TCEvento exige ds:Signature).
        ValidadorXsd.validar(pedRegEventoXml, PED_REG_EVENTO_XSD_RAIZ);

        Document documento = EventoNFSeXmlBuilder.envolverEmEvento(chaveAcesso, tipoEvento, agora, pedRegEventoXml);
        // Só o infEvento externo é assinado — o ds:Signature do pedRegEvento é minOccurs="0" no
        // TCPedRegEvt e esta biblioteca não o gera.
        // ATENÇÃO: qual documento o ADN realmente espera receber assinado NÃO está confirmado contra
        // o Manual de Integração do SEFIN Nacional nem testado contra Homologação — é uma suposição
        // de que o ADN quer o "evento" completo, assinado na sua assinatura externa/obrigatória
        // (infEvento), espelhando a convenção de NFe/CTe de assinar o elemento de envelopamento mais
        // externo. A alternativa não descartada é o ADN querer apenas o "pedRegEvento" isolado,
        // assinado na sua assinatura interna/opcional (infPedReg — minOccurs="0" no schema). Se a
        // Homologação real rejeitar esta escolha, a correção é isolada: o builder já separa
        // montarPedRegEvento/envolverEmEvento, então basta assinar e transmitir o primeiro em vez do
        // segundo.
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), NFSE_NAMESPACE, "infEvento", ALGORITMO_ASSINATURA, ALGORITMO_DIGEST, "");

        String xmlEventoAssinado = serializarDocumento(documento);
        ValidadorXsd.validar(xmlEventoAssinado, EVENTO_XSD_RAIZ);

        return transmitirEvento(config, chaveAcesso, xmlEventoAssinado);
    }

    /**
     * Cancela uma NFS-e por substituição: emite a DPS substituta e, se aceita, envia o evento
     * {@code e105102} referenciando a nova chave contra a NFS-e antiga.
     *
     * <p>{@code cMotivo}/{@code xMotivo} do evento NÃO são parâmetros separados — o
     * {@code TE105102} documenta que vêm de {@code DPS/infDPS/subst/cMotivo}/{@code xMotivo}, então
     * {@code dpsSubstituta.getInfDPS().getSubst()} já deve estar preenchido pelo chamador com
     * {@code chSubstda} = {@code chaveAntiga}, {@code cMotivo} ({@code TSCodJustSubst}: "01" a "05"
     * ou "99") e, opcionalmente, {@code xMotivo} ({@code TSMotivo}: 15 a 255 caracteres). Os valores
     * em si são validados pelo XSD ao montar o {@code pedRegEvento}, não por esta fachada.</p>
     *
     * <p>Se a emissão da nova DPS falhar (tecnicamente ou por rejeição de negócio), o evento de
     * substituição NÃO é tentado — cancelar a NFS-e antiga sem uma substituta válida deixaria o
     * tomador sem documento fiscal algum. Nesse caso o {@code ResultadoEvento} devolvido carrega o
     * {@code cStat}/{@code mensagem} da EMISSÃO recusada.</p>
     *
     * @param chaveAntiga chave da NFS-e a ser substituída (a que o evento cancela)
     * @param cnpjAutor   CNPJ do autor do evento ({@code TSCNPJ}), pelo mesmo motivo de {@link #cancelar}
     */
    public static ResultadoEvento cancelarPorSubstituicao(Sefaz4jConfig config, String chaveAntiga, String cnpjAutor, TCDPS dpsSubstituta) {
        exigirChaveAcessoValida(chaveAntiga);
        exigirCnpjValido(cnpjAutor);

        TCSubstituicao subst = dpsSubstituta.getInfDPS() != null ? dpsSubstituta.getInfDPS().getSubst() : null;
        if (subst == null) {
            throw new IllegalArgumentException(
                "dpsSubstituta.infDPS.subst deve estar preenchido (chSubstda, cMotivo[, xMotivo]) antes de chamar cancelarPorSubstituicao"
            );
        }
        if (!chaveAntiga.equals(subst.getChSubstda())) {
            throw new IllegalArgumentException(
                "dpsSubstituta.infDPS.subst.chSubstda ('" + subst.getChSubstda() + "') deve ser igual a chaveAntiga ('" + chaveAntiga + "')"
            );
        }

        ResultadoEmissao novaEmissao = emitir(config, dpsSubstituta);
        if (!novaEmissao.isOk()) {
            return new ResultadoEvento(false, novaEmissao.getCStat(), novaEmissao.getMensagem(), null);
        }
        String chaveNova = novaEmissao.getChaveAcesso();

        String tpAmb = String.valueOf(config.getAmbiente().getTpAmb());
        // Mesmo instante para dhEvento (infPedReg) e dhProc (infEvento), como em cancelar.
        String agora = EventoNFSeXmlBuilder.agora();
        String tipoEvento = EventoNFSeXmlBuilder.TIPO_EVENTO_SUBSTITUICAO;

        String pedRegEventoXml = EventoNFSeXmlBuilder.montarPedRegEvento(
            tpAmb, cnpjAutor, chaveAntiga, tipoEvento, agora,
            EventoNFSeXmlBuilder.fragmentoSubstituicao(subst.getCMotivo(), subst.getXMotivo(), chaveNova)
        );
        // Mesma validação em duas passadas de cancelar: pedRegEvento isolado agora, evento completo
        // só depois de assinado.
        ValidadorXsd.validar(pedRegEventoXml, PED_REG_EVENTO_XSD_RAIZ);

        Document documento = EventoNFSeXmlBuilder.envolverEmEvento(chaveAntiga, tipoEvento, agora, pedRegEventoXml);
        // Vale aqui a mesma ressalva registrada em cancelar sobre QUAL documento o ADN espera
        // receber assinado (evento externo vs. pedRegEvento isolado).
        AssinadorXml.assinar(documento, config.getPfxBytes(), config.getSenhaPfx(), NFSE_NAMESPACE, "infEvento", ALGORITMO_ASSINATURA, ALGORITMO_DIGEST, "");

        String xmlEventoAssinado = serializarDocumento(documento);
        ValidadorXsd.validar(xmlEventoAssinado, EVENTO_XSD_RAIZ);

        return transmitirEvento(config, chaveAntiga, xmlEventoAssinado);
    }

    private static ResultadoEvento transmitirEvento(Sefaz4jConfig config, String chaveAcesso, String xmlEventoAssinado) {
        // ATENÇÃO: caminho não confirmado contra o Manual de Integração do SEFIN Nacional nem testado
        // contra Homologação.
        String url = (config.getUrlOverride() != null ? config.getUrlOverride() : baseUrlEmissao(config))
            + "/" + chaveAcesso + "/eventos";

        // Mesmo motivo do envio principal (ver enviarEProcessar): rejeição de negócio chega como
        // HTTP não-2xx com corpo JSON válido.
        String corpoJson = PayloadCompactado.montarRequisicaoJson(CAMPO_JSON_EVENTO, xmlEventoAssinado);
        String respostaBruta = SefazHttpClient.postarAceitandoQualquerStatus(
            url,
            "application/json",
            corpoJson,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaNFSe resposta = RespostaNFSeParser.parsear(respostaBruta, CAMPO_JSON_EVENTO);

        if (!resposta.isSucesso()) {
            return new ResultadoEvento(false, resposta.getCodigoErro(), resposta.getMensagemErro(), null);
        }

        // O leiaute de evento (tiposEventos_v1.01.xsd / evento_v1.01.xsd) NÃO tem nenhum campo de
        // status: não existe cStat em TCInfEvento nem em TCInfPedReg, e não há um "retEvento" com
        // enumeração de status equivalente ao TStat da NFS-e. Portanto CSTAT_SUCESSO (100/102/103/107,
        // do TStat de TCInfNFSe) NÃO se aplica aqui. O único sinal confiável de rejeição de negócio é
        // o array JSON "erros", já tratado acima — gatear o ok num cStat não confirmado produziria
        // falsos ok=false. O cStat abaixo é oportunista: se o ADN devolver algo com esse nome, ele é
        // repassado ao chamador para inspeção; caso contrário fica null.
        String cStat = extrairTextoDoElemento(resposta.getXmlDescomprimido(), "cStat");
        return new ResultadoEvento(true, cStat, null, resposta.getXmlDescomprimido());
    }

    private static void exigirCnpjValido(String cnpj) {
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{14}")) {
            throw new IllegalArgumentException(
                "O campo 'cnpjAutor' deve ter exatamente 14 caracteres em [0-9A-Z] (TSCNPJ), obteve: '" + cnpj + "'"
            );
        }
    }

    private static void exigirTamanho(String texto, int tamanhoMinimo, int tamanhoMaximo, String nomeCampo) {
        if (texto == null || texto.length() < tamanhoMinimo || texto.length() > tamanhoMaximo) {
            throw new IllegalArgumentException(
                "O campo '" + nomeCampo + "' deve ter entre " + tamanhoMinimo + " e " + tamanhoMaximo + " caracteres"
            );
        }
    }

    private static void exigirChaveAcessoValida(String chaveAcesso) {
        if (chaveAcesso == null || !chaveAcesso.matches("[0-9A-Za-z]{50}")) {
            throw new IllegalArgumentException(
                "O campo 'chaveAcesso' deve ter exatamente 50 caracteres alfanuméricos, obteve: '" + chaveAcesso + "'"
            );
        }
    }

    // TSIdDPS sem o prefixo "DPS": cLocEmi(7)+tpInsc(1)+inscricao(14)+serie(5)+nDPS(15) = 42
    // dígitos -- exatamente o valor que DpsIdCalculator calcula e que doe_chave (bot-sefaz) grava.
    private static void exigirIdDpsValido(String idDps) {
        if (idDps == null || !idDps.matches("[0-9]{42}")) {
            throw new IllegalArgumentException(
                "O campo 'idDps' deve ter exatamente 42 dígitos numéricos, obteve: '" + idDps + "'"
            );
        }
    }

    private static String baseUrlDps(Sefaz4jConfig config) {
        return config.getAmbiente() == net.accellog.sefaz4j.endpoints.Ambiente.PRODUCAO
            ? URL_DPS_PRODUCAO
            : URL_DPS_HOMOLOGACAO;
    }

    private static Document montarDocumento(TCDPS dps) {
        try {
            return DpsXmlBuilder.marcarIdEMontarDocumento(dps);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML da DPS", e);
        }
    }

    private static ResultadoEmissao enviarEProcessar(Sefaz4jConfig config, String xmlAssinado) {
        ValidadorXsd.validar(xmlAssinado, DPS_XSD_RAIZ);

        String url = config.getUrlOverride() != null
            ? config.getUrlOverride()
            : baseUrlEmissao(config);

        // postarAceitandoQualquerStatus (não postar): o SEFIN Nacional devolve rejeição de
        // negócio (ex.: E0014 "DPS já existe") como HTTP 400 com corpo JSON válido — usar
        // postar() aqui jogaria fora esse corpo (e o xmlAssinado do ResultadoEmissao abaixo)
        // numa ComunicacaoException, escondendo tanto a mensagem de erro real quanto o XML
        // enviado.
        String corpoJson = PayloadCompactado.montarRequisicaoJson("dpsXmlGZipB64", xmlAssinado);
        String respostaBruta = SefazHttpClient.postarAceitandoQualquerStatus(
            url,
            "application/json",
            corpoJson,
            config.getPfxBytes(),
            config.getSenhaPfx(),
            config.getTimeout()
        );

        RespostaNFSe resposta = RespostaNFSeParser.parsear(respostaBruta, "nfseXmlGZipB64");

        if (!resposta.isSucesso()) {
            // xmlAssinado (nao null) no lugar de xmlAutorizado: para a NFSe, diferente de
            // NFe/CTe/MDFe, nao existe "documento final com protocolo" quando rejeitada -- so o
            // que foi assinado e enviado. bot-sefaz grava isso em doe_xmlenvio (convencao
            // ACBr/legado: "XML real de saida", autorizado ou nao).
            return new ResultadoEmissao(false, resposta.getCodigoErro(), resposta.getMensagemErro(), null, xmlAssinado);
        }

        String cStat = extrairTextoDoElemento(resposta.getXmlDescomprimido(), "cStat");
        return new ResultadoEmissao(CSTAT_SUCESSO.contains(cStat), cStat, DESCRICAO_CSTAT_SUCESSO.get(cStat), resposta.getChaveAcesso(), resposta.getXmlDescomprimido());
    }

    static String baseUrlEmissao(Sefaz4jConfig config) {
        return config.getAmbiente() == net.accellog.sefaz4j.endpoints.Ambiente.PRODUCAO ? URL_PRODUCAO : URL_HOMOLOGACAO;
    }

    /**
     * Link do Portal de Consulta Pública (nfse.gov.br) usado como "link de autenticidade" no
     * DANFSe -- texto + QR code. Calculado localmente (ambiente+chave), não devolvido pela
     * SEFAZ na resposta de autorização.
     */
    public static String linkConsultaPublica(Sefaz4jConfig config, String chaveAcesso) {
        String base = config.getAmbiente() == net.accellog.sefaz4j.endpoints.Ambiente.PRODUCAO
            ? LINK_CONSULTA_PUBLICA_PRODUCAO
            : LINK_CONSULTA_PUBLICA_HOMOLOGACAO;
        return base + chaveAcesso;
    }

    private static String extrairTextoDoElemento(String xml, String nomeLocalElemento) {
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document documento = dbf.newDocumentBuilder().parse(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
            org.w3c.dom.NodeList lista = documento.getElementsByTagNameNS("*", nomeLocalElemento);
            return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String serializarDocumento(Document documento) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            // Ao contrário de NFe/CTe (SOAP), aqui o XML resultante é sempre o documento de topo
            // transmitido isolado (gzip+Base64 dentro do JSON REST) — não um fragmento embutido em
            // outro XML — então precisa do próprio prólogo `<?xml ... encoding="UTF-8"?>`. Omiti-lo
            // fazia o SEFIN Nacional rejeitar com E1229 ("Xml não está utilizando codificação UTF-8").
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(documento), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o XML da NFS-e", e);
        }
    }
}
