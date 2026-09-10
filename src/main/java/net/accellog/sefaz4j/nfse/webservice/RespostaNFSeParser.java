package net.accellog.sefaz4j.nfse.webservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RespostaNFSeParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RespostaNFSeParser() {
    }

    /**
     * @param statusHttp status HTTP da resposta (sempre 2xx para chamadas via
     *        {@code SefazHttpClient.buscar}, que já lança {@code ComunicacaoException} fora dessa
     *        faixa antes de chegar aqui). Necessário porque a SEFIN Nacional às vezes devolve um
     *        erro de infraestrutura/roteamento (ex.: HTTP 500 com {@code {"message":"An error has
     *        occurred."}}, sem o array "erros" da rejeição de negócio) que não tem nenhum campo
     *        reconhecível — sem checar o status, esse corpo caía no caminho de sucesso por
     *        omissão (nem "erros" nem o campo XML esperado presentes não é o mesmo que sucesso).
     */
    public static RespostaNFSe parsear(String corpoJson, String campoXmlComprimido, int statusHttp) {
        try {
            JsonNode raiz = OBJECT_MAPPER.readTree(corpoJson);

            JsonNode erros = raiz.has("erros") ? raiz.get("erros") : null;
            if (erros != null && erros.isArray() && erros.size() > 0) {
                JsonNode primeiroErro = erros.get(0);
                String codigo = textoDoPrimeiroCampo(primeiroErro, "Codigo", "codigo");
                String descricao = textoDoPrimeiroCampo(primeiroErro, "Descricao", "descricao");
                return new RespostaNFSe(false, null, null, codigo, descricao);
            }

            if (statusHttp / 100 != 2) {
                String mensagem = raiz.has("message") ? raiz.get("message").asText() : corpoJson;
                return new RespostaNFSe(false, null, null, "HTTP " + statusHttp, mensagem);
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
