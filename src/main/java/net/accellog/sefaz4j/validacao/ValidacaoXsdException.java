package net.accellog.sefaz4j.validacao;

import java.util.List;

public class ValidacaoXsdException extends RuntimeException {
    private final List<String> violacoes;

    public ValidacaoXsdException(List<String> violacoes) {
        super("XML inválido contra o schema XSD: " + violacoes.size() + " violação(ões)");
        this.violacoes = violacoes;
    }

    public List<String> getViolacoes() {
        return violacoes;
    }
}
