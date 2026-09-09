package net.accellog.sefaz4j.webservice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * Compressão gzip + codificação Base64 exigida pelo Manual de Orientação do Contribuinte
 * (MOC) para o conteúdo de "*DadosMsg" das operações de recepção de NFe/CTe/MDFe
 * (NFeAutorizacao4, CTeRecepcaoSinc, MDFeRecepcao) — ao contrário das demais operações
 * (consulta, eventos, inutilização), que trafegam XML puro sem compressão.
 *
 * <p>Confirmado empiricamente: sem esta compressão, a SEFAZ analisa o SOAP normalmente mas
 * rejeita com cStat 244 "Falha na descompactação da área de dados" — a mensagem já vem
 * assinada e válida contra a XSD, só falta este passo de transporte.</p>
 */
public final class GzipBase64 {

    private GzipBase64() {
    }

    public static String comprimirECodificar(String xml) {
        try {
            ByteArrayOutputStream saidaComprimida = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(saidaComprimida)) {
                gzip.write(xml.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(saidaComprimida.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao comprimir/codificar o XML para envio à SEFAZ", e);
        }
    }
}
