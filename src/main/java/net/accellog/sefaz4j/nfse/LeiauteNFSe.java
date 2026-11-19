package net.accellog.sefaz4j.nfse;

import net.accellog.sefaz4j.endpoints.Ambiente;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Leiaute da DPS aceito pelo SEFIN Nacional.
 *
 * <p>O modelo JAXB ({@code net.accellog.sefaz4j.nfse.model}) é gerado do leiaute da NT 009 v1.01
 * ({@code schemas/nfse/}). O leiaute anterior (NT 004 v2.0, {@code schemas/nfse/nt004/}) vale em
 * produção até 30/11/2026 — a produção restrita já está na NT 009 — e é obtido rebaixando o DOM
 * da DPS antes da assinatura ({@code DpsLeiauteNT004}). Mesmo papel do {@code ProVersaoNT}/
 * {@code HomVersaoNT} do {@code ACBrNFSeXServicos.ini}.</p>
 */
public enum LeiauteNFSe {

    /** NT 004 v2.0: produção até 30/11/2026. */
    NT004("/schemas/nfse/nt004/DPS_v1.01.xsd"),

    /** NT 009 v1.01: produção restrita desde 20/10/2026, produção a partir de 01/12/2026. */
    NT009("/schemas/nfse/DPS_v1.01.xsd");

    /**
     * Início da NT 009 em produção ("o leiaute em produção permanece até 30/11/2026 sendo o definido
     * pela Nota Técnica nº 004, Versão 2.0" — notícia CGNFS-e de 20/10/2026).
     */
    public static final Instant INICIO_NT009_PRODUCAO =
        LocalDateTime.of(2026, 12, 1, 0, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();

    private final String xsdRaiz;

    LeiauteNFSe(String xsdRaiz) {
        this.xsdRaiz = xsdRaiz;
    }

    public String getXsdRaiz() {
        return xsdRaiz;
    }

    /** Leiaute que o SEFIN Nacional do ambiente aceita no instante informado. */
    public static LeiauteNFSe vigente(Ambiente ambiente, Instant instante) {
        if (ambiente == Ambiente.PRODUCAO && instante.isBefore(INICIO_NT009_PRODUCAO)) {
            return NT004;
        }
        return NT009;
    }
}
