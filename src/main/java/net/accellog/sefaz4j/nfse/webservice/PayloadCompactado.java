package net.accellog.sefaz4j.nfse.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PayloadCompactado {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PayloadCompactado() {
    }

    public static String comprimirECodificar(String xml) {
        try {
            ByteArrayOutputStream saidaComprimida = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(saidaComprimida)) {
                gzip.write(xml.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(saidaComprimida.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao comprimir/codificar o XML da NFS-e", e);
        }
    }

    public static String decodificarEDescomprimir(String base64) {
        try {
            byte[] comprimido = Base64.getDecoder().decode(base64);
            ByteArrayOutputStream saidaDescomprimida = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido))) {
                gzip.transferTo(saidaDescomprimida);
            }
            return saidaDescomprimida.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decodificar/descomprimir o XML recebido da NFS-e", e);
        }
    }

    public static String montarRequisicaoJson(String campoChave, String xmlAssinado) {
        ObjectNode no = OBJECT_MAPPER.createObjectNode();
        no.put(campoChave, comprimirECodificar(xmlAssinado));
        try {
            return OBJECT_MAPPER.writeValueAsString(no);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o JSON de requisição da NFS-e", e);
        }
    }
}
