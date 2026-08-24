package net.accellog.sefaz4j.nfse.webservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RespostaNFSeParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RespostaNFSeParser() {
    }

    public static RespostaNFSe parsear(String corpoJson, String campoXmlComprimido) {
        try {
            JsonNode raiz = OBJECT_MAPPER.readTree(corpoJson);

            JsonNode erros = raiz.has("erros") ? raiz.get("erros") : null;
            if (erros != null && erros.isArray() && erros.size() > 0) {
                JsonNode primeiroErro = erros.get(0);
                String codigo = textoDoPrimeiroCampo(primeiroErro, "Codigo", "codigo");
                String descricao = textoDoPrimeiroCampo(primeiroErro, "Descricao", "descricao");
                return new RespostaNFSe(false, null, null, codigo, descricao);
            }

            String chaveAcesso = raiz.has("chaveAcesso") ? raiz.get("chaveAcesso").asText() : null;
            JsonNode xmlComprimidoNode = raiz.get(campoXmlComprimido);
            String xmlDescomprimido = xmlComprimidoNode != null
                ? PayloadCompactado.decodificarEDescomprimir(xmlComprimidoNode.asText())
                : null;

            return new RespostaNFSe(true, chaveAcesso, xmlDescomprimido, null, null);
        } catch (Exception e) {
            throw new net.accellog.sefaz4j.webservice.ComunicacaoException("Falha ao interpretar a resposta da NFS-e", e);
        }
    }

    private static String textoDoPrimeiroCampo(JsonNode objeto, String... nomesPossiveis) {
        for (String nome : nomesPossiveis) {
            if (objeto.has(nome)) {
                return objeto.get(nome).asText();
            }
        }
        return null;
    }
}
