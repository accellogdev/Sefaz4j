package net.accellog.sefaz4j.assinatura;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.Signature;
import java.util.Base64;

/**
 * Parâmetro {@code sign} do QR Code exigido nas contingências offline — MDF-e com tpEmis=2 e
 * CT-e com tpEmis=4 (EPEC) ou 5 (FS-DA): a chave de acesso assinada com o certificado do emitente
 * (SHA1withRSA), em Base64, acrescentada como {@code &sign=} à URL do QR Code. É o mesmo cálculo do
 * ACBr ({@code SSL.CalcHash(chave, dgstSHA1, outBase64, True)}), conferido contra MDF-e autorizado
 * em produção com o certificado do próprio XML.
 */
public final class AssinaturaQrCode {

    private static final String PARAMETRO_SIGN = "&sign=";

    private AssinaturaQrCode() {
    }

    public static String assinarChave(byte[] pfxBytes, String senha, String chave) {
        KeyStore.PrivateKeyEntry chavePrivada = CertificadoA1.carregar(pfxBytes, senha);
        try {
            Signature assinatura = Signature.getInstance("SHA1withRSA");
            assinatura.initSign(chavePrivada.getPrivateKey());
            assinatura.update(chave.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(assinatura.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar a chave de acesso para o QR Code", e);
        }
    }

    /** Acrescenta {@code &sign=} ao texto do elemento do QR Code, se ele existir e ainda não tiver. */
    public static void completarQrCode(Document documento, String namespace, String elementoQrCode,
                                       String chave, byte[] pfxBytes, String senha) {
        NodeList lista = documento.getElementsByTagNameNS(namespace, elementoQrCode);
        if (lista.getLength() == 0) {
            return;
        }
        String url = lista.item(0).getTextContent();
        if (url == null || url.isBlank() || url.contains(PARAMETRO_SIGN)) {
            return;
        }
        lista.item(0).setTextContent(url + PARAMETRO_SIGN + assinarChave(pfxBytes, senha, chave));
    }
}
