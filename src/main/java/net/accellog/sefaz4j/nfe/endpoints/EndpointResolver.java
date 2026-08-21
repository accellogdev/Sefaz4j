package net.accellog.sefaz4j.nfe.endpoints;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class EndpointResolver {

    private static final Map<String, Map<String, String>> SECOES = carregar();

    private EndpointResolver() {
    }

    public static String resolver(UF uf, Ambiente ambiente, Servico servico) {
        String secaoNome = "NFE_" + uf.name() + "_" + ambiente.getSufixo();
        Map<String, String> secao = SECOES.get(secaoNome);
        if (secao == null) {
            throw new IllegalStateException("Seção não encontrada no nfe-servicos.ini: " + secaoNome);
        }

        String chaveServico = servico.getChaveIni().toUpperCase();
        String url = secao.get(chaveServico);
        if (url != null) {
            return url;
        }

        String chaveUsar = secao.get("USAR");
        if (chaveUsar != null) {
            Map<String, String> secaoCompartilhada = SECOES.get(chaveUsar.toUpperCase());
            if (secaoCompartilhada == null) {
                throw new IllegalStateException("Seção compartilhada não encontrada: " + chaveUsar);
            }
            url = secaoCompartilhada.get(chaveServico);
        }

        if (url == null) {
            throw new IllegalStateException(
                "Serviço " + servico + " não encontrado na seção " + secaoNome + " (nem na indireção Usar=, se houve)"
            );
        }
        return url;
    }

    private static Map<String, Map<String, String>> carregar() {
        Map<String, Map<String, String>> secoes = new HashMap<>();
        try (InputStream in = EndpointResolver.class.getResourceAsStream("/endpoints/nfe-servicos.ini")) {
            if (in == null) {
                throw new IllegalStateException("Recurso /endpoints/nfe-servicos.ini não encontrado no classpath");
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
            throw new UncheckedIOException("Falha ao carregar nfe-servicos.ini", e);
        }
        return secoes;
    }
}
