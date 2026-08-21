package net.accellog.sefaz4j.nfe.webservice;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public final class SefazHttpClient {

    // Construir o SSLContext (carregar o PKCS12 + inicializar o
    // KeyManagerFactory) é caro e, como java.net.http.HttpClient não é
    // Closeable no Java 17, um HttpClient novo por chamada também vaza seu
    // pool de conexões/thread de seletor até o GC coletar. Estes caches
    // reaproveitam ambos entre chamadas com o mesmo certificado (e, para o
    // HttpClient, também o mesmo timeout de conexão) — chave e valor nunca
    // são removidos explicitamente; para o padrão de uso desta biblioteca
    // (um número pequeno e estável de certificados A1 por processo) isso é
    // um cache efetivamente limitado, não um leak.
    private static final ConcurrentHashMap<String, SSLContext> SSL_CONTEXT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, HttpClient> HTTP_CLIENT_CACHE = new ConcurrentHashMap<>();

    private SefazHttpClient() {
    }

    public static String postar(
        String url,
        String soapAction,
        String envelopeXml,
        byte[] pfxBytes,
        String senha,
        Duration timeout
    ) {
        try {
            String chaveCertificado = chaveCertificado(pfxBytes, senha);
            SSLContext sslContext = SSL_CONTEXT_CACHE.computeIfAbsent(chaveCertificado, k -> {
                try {
                    return criarSslContext(pfxBytes, senha);
                } catch (Exception e) {
                    throw new ComunicacaoException("Falha ao preparar SSLContext para comunicação com a SEFAZ", e);
                }
            });
            HttpClient httpClient = HTTP_CLIENT_CACHE.computeIfAbsent(
                chaveCertificado + "|" + timeout.toMillis(),
                k -> HttpClient.newBuilder().sslContext(sslContext).connectTimeout(timeout).build()
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + soapAction + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelopeXml))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ComunicacaoException("SEFAZ retornou HTTP " + response.statusCode() + ": " + response.body(), null);
            }
            return response.body();
        } catch (ComunicacaoException e) {
            throw e;
        } catch (Exception e) {
            throw new ComunicacaoException("Falha de comunicação com a SEFAZ em " + url, e);
        }
    }

    // Chave de cache derivada de um digest do PFX + senha, em vez das
    // próprias bytes/senha em claro, para não manter a senha do certificado
    // como chave de um Map de longa duração.
    private static String chaveCertificado(byte[] pfxBytes, String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pfxBytes);
            digest.update((byte) 0);
            digest.update(senha.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            // Nunca deveria faltar SHA-256 numa JVM padrão; se faltar, cai
            // para uma chave por instância de array (sem cache efetivo, mas
            // sem quebrar a chamada).
            return String.valueOf(System.identityHashCode(pfxBytes)) + "|" + senha.length();
        }
    }

    private static SSLContext criarSslContext(byte[] pfxBytes, String senha) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(pfxBytes), senha.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, senha.toCharArray());

        // "TLS" (em vez de "TLSv1.2" fixo) deixa a JVM negociar a melhor
        // versão mutuamente suportada com o servidor da SEFAZ.
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }
}
