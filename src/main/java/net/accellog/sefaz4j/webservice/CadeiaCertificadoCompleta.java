package net.accellog.sefaz4j.webservice;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Completa a cadeia de certificados de um {@code KeyStore} PKCS12 que só contém o
 * certificado-folha (sem a(s) AC(s) intermediária(s)), buscando as ACs faltantes via a extensão
 * X.509 "Authority Information Access" (AIA, OID 1.3.6.1.5.5.7.1.1) do próprio certificado-folha.
 *
 * <p>Isso resolve um caso real observado em produção: alguns PFX de clientes cadastrados no Wezi
 * (ex. emp_id=4, ~3,7 KB) foram exportados só com o certificado-folha, sem a cadeia. O bot Delphi
 * legado nunca sentiu esse problema porque usa o cert store do Windows para o handshake TLS, que já
 * tem essas ACs cacheadas de outras interações e completa a cadeia por trás dos panos. O bot Java
 * monta o {@code KeyManager} só com o que está literalmente dentro dos bytes do PFX — se a AC
 * intermediária não estiver lá, ele apresenta só a folha no handshake, e o IIS/WAF da SVRS (que não
 * consegue montar o caminho até uma raiz confiável a partir só da folha) rejeita com HTTP 403
 * "Forbidden: Access is denied" antes mesmo de qualquer validação de schema/negócio.</p>
 */
final class CadeiaCertificadoCompleta {

    private static final String OID_AUTHORITY_INFO_ACCESS = "1.3.6.1.5.5.7.1.1";
    private static final String OID_CA_ISSUERS = "1.3.6.1.5.5.7.48.2";

    private CadeiaCertificadoCompleta() {
    }

    /**
     * Se {@code cadeiaOriginal} já tiver mais de um certificado, é devolvida sem alteração (nada a
     * completar). Se tiver exatamente um (só a folha), tenta buscar a(s) AC(s) faltante(s) via AIA,
     * usando {@code downloader} para buscar os bytes de cada URL de "CA Issuers" encontrada — o
     * primeiro download bem-sucedido (não nulo, sem lançar) é usado. Falha ao extrair a AIA, ao
     * baixar, ou ausência de qualquer AC candidata: devolve {@code cadeiaOriginal} sem alteração
     * (mesmo comportamento de antes desta funcionalidade existir) em vez de propagar a exceção —
     * best effort, não deve impedir o envio de tentar com só a folha, como já acontecia.
     */
    static X509Certificate[] completar(X509Certificate[] cadeiaOriginal, Function<String, byte[]> downloader) {
        if (cadeiaOriginal == null || cadeiaOriginal.length != 1) {
            return cadeiaOriginal;
        }

        X509Certificate folha = cadeiaOriginal[0];
        List<X509Certificate> candidatas = new ArrayList<>();
        for (String url : urlsCaIssuers(folha)) {
            try {
                byte[] bytes = downloader.apply(url);
                if (bytes != null) {
                    candidatas.addAll(parseCertificados(bytes));
                }
            } catch (Exception ignorada) {
                // tenta a próxima URL de CA Issuers, se houver
            }
            if (!candidatas.isEmpty()) {
                break;
            }
        }

        List<X509Certificate> cadeia = montarCadeiaEmOrdem(folha, candidatas);
        return cadeia.size() > 1 ? cadeia.toArray(new X509Certificate[0]) : cadeiaOriginal;
    }

    /**
     * Monta, a partir da folha e de um conjunto (em qualquer ordem) de certificados candidatos, a
     * cadeia ordenada folha -> AC imediata -> ... -> raiz, seguindo o encadeamento
     * subject/issuer. Para no primeiro certificado autoassinado (subject == issuer) encontrado, ou
     * quando não há mais nenhum candidato cujo subject bate com o issuer do último da cadeia.
     */
    private static List<X509Certificate> montarCadeiaEmOrdem(X509Certificate folha, List<X509Certificate> candidatas) {
        List<X509Certificate> cadeia = new ArrayList<>();
        cadeia.add(folha);
        Set<X509Certificate> usadas = new HashSet<>();

        X509Certificate atual = folha;
        while (!atual.getSubjectX500Principal().equals(atual.getIssuerX500Principal())) {
            X509Certificate proxima = null;
            for (X509Certificate candidata : candidatas) {
                if (!usadas.contains(candidata)
                    && candidata.getSubjectX500Principal().equals(atual.getIssuerX500Principal())) {
                    proxima = candidata;
                    break;
                }
            }
            if (proxima == null) {
                break;
            }
            cadeia.add(proxima);
            usadas.add(proxima);
            atual = proxima;
        }
        return cadeia;
    }

    private static List<X509Certificate> parseCertificados(byte[] bytes) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        // CertificateFactory.generateCertificates aceita tanto X.509 solto (DER/PEM) quanto um
        // PKCS#7 "certs-only" (o formato .p7b que as ACs ICP-Brasil normalmente publicam) — não
        // precisa de biblioteca extra para nenhum dos dois casos.
        Collection<? extends java.security.cert.Certificate> certs =
            cf.generateCertificates(new ByteArrayInputStream(bytes));
        List<X509Certificate> resultado = new ArrayList<>();
        for (java.security.cert.Certificate c : certs) {
            if (c instanceof X509Certificate x509) {
                resultado.add(x509);
            }
        }
        return resultado;
    }

    /**
     * Extrai as URLs de "CA Issuers" (accessMethod id-ad-caIssuers, 1.3.6.1.5.5.7.48.2) da extensão
     * Authority Information Access do certificado, na ordem em que aparecem. Devolve lista vazia se
     * o certificado não tiver a extensão, ou se ela não puder ser interpretada (ASN.1 inesperado) —
     * nunca lança.
     */
    static List<String> urlsCaIssuers(X509Certificate certificado) {
        byte[] extensao = certificado.getExtensionValue(OID_AUTHORITY_INFO_ACCESS);
        if (extensao == null) {
            return List.of();
        }
        return urlsCaIssuersDeExtensao(extensao);
    }

    /**
     * Parte pura (sem depender de {@link X509Certificate}) de {@link #urlsCaIssuers}: recebe
     * diretamente os bytes já retornados por {@code certificado.getExtensionValue(...)} —
     * separado assim pra poder testar o parser ASN.1 com bytes sintéticos, sem precisar de um
     * certificado real (ou de forjar um) só pra exercitar essa extensão.
     */
    static List<String> urlsCaIssuersDeExtensao(byte[] extensao) {
        List<String> urls = new ArrayList<>();
        try {
            // getExtensionValue devolve o DER de um OCTET STRING cujo conteúdo é o DER do próprio
            // AuthorityInfoAccessSyntax (uma SEQUENCE de AccessDescription).
            Tlv envelope = lerTlv(extensao, 0);
            Tlv sequenciaAia = lerTlv(envelope.valor, 0);

            int offset = 0;
            while (offset < sequenciaAia.valor.length) {
                Tlv accessDescription = lerTlv(sequenciaAia.valor, offset);

                Tlv oid = lerTlv(accessDescription.valor, 0);
                if (OID_CA_ISSUERS.equals(decodificarOid(oid.valor))) {
                    Tlv generalName = lerTlv(accessDescription.valor, oid.proximoOffset);
                    // GeneralName escolhido como uniformResourceIdentifier: tag [6] IMPLICIT,
                    // primitivo, classe context-specific -> byte de tag 0x86.
                    if (generalName.tag == 0x86) {
                        urls.add(new String(generalName.valor, StandardCharsets.US_ASCII));
                    }
                }

                offset = accessDescription.proximoOffset;
            }
        } catch (RuntimeException e) {
            return List.of();
        }
        return urls;
    }

    private static String decodificarOid(byte[] valor) {
        StringBuilder sb = new StringBuilder();
        int primeiroByte = valor[0] & 0xFF;
        sb.append(primeiroByte / 40).append('.').append(primeiroByte % 40);

        long acumulado = 0;
        for (int i = 1; i < valor.length; i++) {
            int b = valor[i] & 0xFF;
            acumulado = (acumulado << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                sb.append('.').append(acumulado);
                acumulado = 0;
            }
        }
        return sb.toString();
    }

    /** Um TLV (tag/length/value) DER, com o offset de onde o próximo TLV começaria. */
    private record Tlv(int tag, byte[] valor, int proximoOffset) {
    }

    /**
     * Lê um único TLV DER de forma minimalista: assume tag de 1 byte (suficiente para os tipos
     * usados no AIA — OCTET STRING, SEQUENCE, OBJECT IDENTIFIER, e o GeneralName IA5String
     * implícito) e comprimento em forma curta ou longa (sem length indefinido, que não é permitido
     * em DER).
     */
    private static Tlv lerTlv(byte[] data, int offset) {
        int tag = data[offset] & 0xFF;
        int primeiroByteComprimento = data[offset + 1] & 0xFF;
        int inicioValor;
        int comprimento;
        if ((primeiroByteComprimento & 0x80) == 0) {
            comprimento = primeiroByteComprimento;
            inicioValor = offset + 2;
        } else {
            int quantidadeBytesComprimento = primeiroByteComprimento & 0x7F;
            comprimento = 0;
            for (int i = 0; i < quantidadeBytesComprimento; i++) {
                comprimento = (comprimento << 8) | (data[offset + 2 + i] & 0xFF);
            }
            inicioValor = offset + 2 + quantidadeBytesComprimento;
        }
        byte[] valor = new byte[comprimento];
        System.arraycopy(data, inicioValor, valor, 0, comprimento);
        return new Tlv(tag, valor, inicioValor + comprimento);
    }
}
