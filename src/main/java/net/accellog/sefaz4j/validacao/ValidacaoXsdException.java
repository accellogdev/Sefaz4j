package net.accellog.sefaz4j.validacao;

import java.util.List;

public class ValidacaoXsdException extends RuntimeException {
    private final List<String> violacoes;
    private final String xmlInvalido;

    public ValidacaoXsdException(List<String> violacoes, String xmlInvalido) {
        super("XML inválido contra o schema XSD: " + violacoes.size() + " violação(ões)");
        this.violacoes = violacoes;
        this.xmlInvalido = xmlInvalido;
    }

    public List<String> getViolacoes() {
        return violacoes;
    }

    // O XML (já montado e assinado) que reprovou a validação -- sem isso, quem chama
    // ValidadorXsd.validar não tem como persistir/inspecionar o documento que falhou, só a lista
    // de violações. Necessário para gravar doe_xmlenvio mesmo quando o envio é rejeitado antes de
    // chegar à SEFAZ (ver XxxProcessor.processarFila, catch de ValidacaoXsdException).
    public String getXmlInvalido() {
        return xmlInvalido;
    }
}
