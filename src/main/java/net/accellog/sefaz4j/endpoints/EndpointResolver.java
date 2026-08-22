package net.accellog.sefaz4j.endpoints;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EndpointResolver {

    private static final ConcurrentHashMap<String, Map<String, Map<String, String>>> INI_CACHE = new ConcurrentHashMap<>();

    private EndpointResolver() {
    }

    public static String resolver(String caminhoIni, String prefixoSecao, UF uf, Ambiente ambiente, String chaveServico) {
        Map<String, Map<String, String>> secoes = INI_CACHE.computeIfAbsent(caminhoIni, EndpointResolver::carregar);

        String secaoNome = prefixoSecao + uf.name() + "_" + ambiente.getSufixo();
        Map<String, String> secao = secoes.get(secaoNome);
        if (secao == null) {
            throw new IllegalStateException("Seção não encontrada em " + caminhoIni + ": " + secaoNome);
        }

        String chaveServicoNormalizada = chaveServico.toUpperCase();
        String url = secao.get(chaveServicoNormalizada);
        if (url != null) {
            return url;
        }

        String chaveUsar = secao.get("USAR");
        if (chaveUsar != null) {
            Map<String, String> secaoCompartilhada = secoes.get(chaveUsar.toUpperCase());
            if (secaoCompartilhada == null) {
                throw new IllegalStateException("Seção compartilhada não encontrada: " + chaveUsar);
            }
            url = secaoCompartilhada.get(chaveServicoNormalizada);
        }

        if (url == null) {
            throw new IllegalStateException(
                "Serviço " + chaveServico + " não encontrado na seção " + secaoNome + " (nem na indireção Usar=, se houve)"
            );
        }
        return url;
    }

    private static Map<String, Map<String, String>> carregar(String caminhoIni) {
        Map<String, Map<String, String>> secoes = new HashMap<>();
        try (InputStream in = EndpointResolver.class.getResourceAsStream(caminhoIni)) {
            if (in == null) {
                throw new IllegalStateException("Recurso " + caminhoIni + " não encontrado no classpath");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> secaoAtual = null;
            String linha;
            while ((linha = reader.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty() || linha.startsWith(";")) {
                    continue;
                }
                if (linha.startsWith("[") && linha.endsWith("]")) {
                    String nomeSecao = linha.substring(1, linha.length() - 1).toUpperCase();
                    secaoAtual = new HashMap<>();
                    secoes.put(nomeSecao, secaoAtual);
                    continue;
                }
                int igual = linha.indexOf('=');
                if (igual > 0 && secaoAtual != null) {
                    String chave = linha.substring(0, igual).trim().toUpperCase();
                    String valor = linha.substring(igual + 1).trim();
                    secaoAtual.put(chave, valor);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar " + caminhoIni, e);
        }
        return secoes;
    }
}
