package net.accellog.sefaz4j.webservice;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
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

    // Praticamente todos os webservices tradicionais da SEFAZ (CTe/MDFe/NFe -- diferente da
    // NFSe Padrão Nacional, que roda atrás de um certificado GlobalSign convencional) usam
    // certificados emitidos sob a hierarquia ICP-Brasil. O cacerts padrão do OpenJDK NÃO
    // inclui a raiz "Autoridade Certificadora Raiz Brasileira v10" (ITI) -- só CAs comerciais
    // globais -- então o TrustManager default falha com PKIX path building failed mesmo
    // contra um endpoint de homologação legítimo. Corrigido mesclando essa raiz (empacotada
    // em certs/icp-brasil-raiz-v10.pem, exportada de uma instalação Windows onde ela já era
    // confiável) ao trust store padrão da JVM, em vez de usar TrustManager nulo (=default).
    private static volatile TrustManager[] trustManagersComIcpBrasil;

    private SefazHttpClient() {
    }

    private static TrustManager[] trustManagersComIcpBrasil() throws Exception {
        TrustManager[] cache = trustManagersComIcpBrasil;
        if (cache != null) {
            return cache;
        }

        synchronized (SefazHttpClient.class) {
            if (trustManagersComIcpBrasil != null) {
                return trustManagersComIcpBrasil;
            }

            // Carrega o trust store padrão da JVM (mesmo obtido implicitamente por
            // sslContext.init(..., null, ...)) para preservar as CAs comerciais globais.
            TrustManagerFactory tmfPadrao = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmfPadrao.init((KeyStore) null);
            X509TrustManager tmPadrao = null;
            for (TrustManager tm : tmfPadrao.getTrustManagers()) {
                if (tm instanceof X509TrustManager x509) {
                    tmPadrao = x509;
                    break;
                }
            }

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            if (tmPadrao != null) {
                int i = 0;
                for (X509Certificate cert : tmPadrao.getAcceptedIssuers()) {
                    trustStore.setCertificateEntry("jdk-default-" + (i++), cert);
                }
            }

            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            try (InputStream in = SefazHttpClient.class.getResourceAsStream("/certs/icp-brasil-raiz-v10.pem")) {
                Certificate raizIcpBrasil = certFactory.generateCertificate(in);
                trustStore.setCertificateEntry("icp-brasil-raiz-v10", raizIcpBrasil);
            }

            TrustManagerFactory tmfCombinado = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmfCombinado.init(trustStore);

            trustManagersComIcpBrasil = tmfCombinado.getTrustManagers();
            return trustManagersComIcpBrasil;
        }
    }

    public static String postar(
        String url,
        String contentType,
        String corpo,
        byte[] pfxBytes,
        String senha,
        Duration timeout
    ) {
        HttpResponse<String> response = enviarPost(url, contentType, corpo, pfxBytes, senha, timeout);
        if (response.statusCode() / 100 != 2) {
            throw new ComunicacaoException("SEFAZ retornou HTTP " + response.statusCode() + ": " + response.body(), null);
        }
        return response.body();
    }

    /**
     * Mesmo transporte de {@link #postar}, mas devolve o corpo da resposta INDEPENDENTE do
     * status HTTP, em vez de lançar {@link ComunicacaoException} fora da faixa 2xx.
     *
     * <p>Uso: SEFIN Nacional (NFS-e) — ao contrário dos webservices SOAP tradicionais
     * (NFe/CTe/MDFe, onde um status HTTP não-2xx é mesmo falha de transporte e a rejeição de
     * negócio chega dentro de um corpo 200 com cStat≠100), a API REST da NFS-e devolve
     * REJEIÇÃO DE NEGÓCIO (ex.: E0014 "DPS já existe") como HTTP 400 com um corpo JSON
     * perfeitamente válido — lançar exceção nesse caso descarta esse corpo (e, por
     * consequência, o XML enviado, que só o chamador tem em mãos) em vez de deixar
     * {@code RespostaNFSeParser} interpretá-lo como a rejeição estruturada que ele é.</p>
     */
    public static String postarAceitandoQualquerStatus(
        String url,
        String contentType,
        String corpo,
        byte[] pfxBytes,
        String senha,
        Duration timeout
    ) {
        return enviarPost(url, contentType, corpo, pfxBytes, senha, timeout).body();
    }

    private static HttpResponse<String> enviarPost(
        String url,
        String contentType,
        String corpo,
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
                k -> HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(timeout)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (ComunicacaoException e) {
            throw e;
        } catch (Exception e) {
            throw new ComunicacaoException("Falha de comunicação com a SEFAZ em " + url, e);
        }
    }

    public static String buscar(String url, byte[] pfxBytes, String senha, Duration timeout) {
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
                k -> HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(timeout)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET()
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
        sslContext.init(kmf.getKeyManagers(), trustManagersComIcpBrasil(), null);
        return sslContext;
    }
}
