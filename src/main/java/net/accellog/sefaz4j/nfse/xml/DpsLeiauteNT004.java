package net.accellog.sefaz4j.nfse.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebaixa o DOM de uma DPS montada com o modelo da NT 009 v1.01 para o leiaute da NT 004 v2.0,
 * o que o SEFIN Nacional de produção aceita até 30/11/2026.
 *
 * <p>Cobre as diferenças que uma DPS sem IBSCBS pode ter (as mesmas que o ACBr condiciona a
 * {@code FpVersaoNT} em {@code PadraoNacional.GravarXml}):</p>
 * <ul>
 *   <li>{@code infDPS/finNFSe} não existe na NT 004 — é removido;</li>
 *   <li>{@code valores/vAjusteBC} era {@code vDedRed}, com {@code pAjusteBCISSQN}/{@code vAjusteBCISSQN}
 *       no lugar de {@code pDR}/{@code vDR}. Os {@code documentos} de ajuste têm outra estrutura
 *       ({@code docDedRed}) e não são convertidos.</li>
 * </ul>
 * <p>Qualquer outro conteúdo exclusivo da NT 009 sobra e é apontado pela validação contra a XSD
 * da NT 004, feita antes da transmissão.</p>
 */
public final class DpsLeiauteNT004 {

    private static final String NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";

    private DpsLeiauteNT004() {
    }

    public static void rebaixar(Document documento) {
        Element infDPS = filho(documento.getDocumentElement(), "infDPS");
        if (infDPS == null) {
            throw new IllegalArgumentException("DPS sem infDPS");
        }

        Element finNFSe = filho(infDPS, "finNFSe");
        if (finNFSe != null) {
            infDPS.removeChild(finNFSe);
        }

        Element valores = filho(infDPS, "valores");
        Element vAjusteBC = valores == null ? null : filho(valores, "vAjusteBC");
        if (vAjusteBC == null) {
            return;
        }
        if (filho(vAjusteBC, "documentos") != null) {
            throw new IllegalArgumentException(
                "valores/vAjusteBC/documentos não tem equivalente no leiaute da NT 004 (vDedRed/documentos/docDedRed)");
        }
        for (Element ajuste : filhos(vAjusteBC)) {
            if ("pAjusteBCISSQN".equals(ajuste.getLocalName())) {
                renomear(documento, ajuste, "pDR");
            } else if ("vAjusteBCISSQN".equals(ajuste.getLocalName())) {
                renomear(documento, ajuste, "vDR");
            }
        }
        renomear(documento, vAjusteBC, "vDedRed");
    }

    private static void renomear(Document documento, Element elemento, String nomeLocal) {
        String prefixo = elemento.getPrefix();
        String nomeQualificado = prefixo == null || prefixo.isEmpty() ? nomeLocal : prefixo + ":" + nomeLocal;
        documento.renameNode(elemento, NFSE_NAMESPACE, nomeQualificado);
    }

    private static Element filho(Element pai, String nomeLocal) {
        for (Element filho : filhos(pai)) {
            if (nomeLocal.equals(filho.getLocalName()) && NFSE_NAMESPACE.equals(filho.getNamespaceURI())) {
                return filho;
            }
        }
        return null;
    }

    private static List<Element> filhos(Element pai) {
        List<Element> elementos = new ArrayList<>();
        for (Node no = pai.getFirstChild(); no != null; no = no.getNextSibling()) {
            if (no.getNodeType() == Node.ELEMENT_NODE) {
                elementos.add((Element) no);
            }
        }
        return elementos;
    }
}
