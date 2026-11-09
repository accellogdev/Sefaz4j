package net.accellog.sefaz4j.chave;

/**
 * Extrai da chave de acesso o documento do emitente — CNPJ (14) ou CPF (11) — para preencher o
 * autor ({@code infEvento/CNPJ|CPF}) dos eventos.
 *
 * <p>A chave sempre tem 14 posições para o emitente; um emitente pessoa física entra como
 * {@code "000" + CPF}. Isso sozinho não distingue CPF de CNPJ (existe CNPJ começando com 000),
 * então a regra é a mesma do ACBr ({@code ACBrDFeUtil.ExtrairCNPJCPFChaveAcesso}): NF-e/NFC-e
 * com séries 910–969 e MDF-e com séries 920–969 são reservadas a CPF; na NFF ({@code tpEmis=3})
 * o 5º dígito do número diz (NF-e: 2 = CPF), e no CT-e só a NFF é CPF.
 */
public final class ChaveAcessoEmitente {

    private ChaveAcessoEmitente() {
    }

    public static String cnpjOuCpf(String chave) {
        String cnpj = chave.substring(6, 20);
        String cpf = chave.substring(9, 20);
        String modelo = chave.substring(20, 22);
        int serie = Integer.parseInt(chave.substring(22, 25));
        char tpEmis = chave.charAt(34);

        switch (modelo) {
            case "55":
            case "65":
                if (tpEmis == '3') {
                    return chave.charAt(29) == '2' ? cpf : cnpj;
                }
                return serie >= 910 && serie <= 969 ? cpf : cnpj;
            case "57":
                return tpEmis == '3' ? cpf : cnpj;
            case "58":
                return serie >= 920 && serie <= 969 ? cpf : cnpj;
            default:
                return cnpj;
        }
    }

    /**
     * Fragmento {@code <CPF>} (11 caracteres) ou {@code <CNPJ>} do autor do evento — o
     * {@code infEvento} de NF-e, CT-e e MDF-e é um choice CNPJ|CPF.
     */
    public static String elementoAutor(String cnpjOuCpf) {
        String tag = cnpjOuCpf != null && cnpjOuCpf.length() == 11 ? "CPF" : "CNPJ";
        return "<" + tag + ">" + cnpjOuCpf + "</" + tag + ">";
    }
}
