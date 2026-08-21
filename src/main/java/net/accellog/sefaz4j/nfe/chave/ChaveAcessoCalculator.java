package net.accellog.sefaz4j.nfe.chave;

public final class ChaveAcessoCalculator {

    private static final int[] PESOS = {2, 3, 4, 5, 6, 7, 8, 9};

    private ChaveAcessoCalculator() {
    }

    public static String calcular(
        String cUF,
        String aamm,
        String cnpj,
        String modelo,
        String serie,
        String numeroNF,
        String tpEmis,
        String cNF
    ) {
        String chave43 = cUF + aamm + cnpj + modelo + serie + numeroNF + tpEmis + cNF;
        if (chave43.length() != 43) {
            throw new IllegalArgumentException(
                "A concatenação dos campos da chave de acesso deve ter 43 dígitos, obteve " + chave43.length()
            );
        }
        if (!chave43.matches("[0-9]{43}")) {
            throw new IllegalArgumentException(
                "A concatenação dos campos da chave de acesso deve conter apenas dígitos: '" + chave43 + "'"
            );
        }
        return chave43 + calcularDigitoVerificador(chave43);
    }

    static int calcularDigitoVerificador(String chave43) {
        int soma = 0;
        int pesoIndex = 0;
        for (int i = chave43.length() - 1; i >= 0; i--) {
            int digito = Character.digit(chave43.charAt(i), 10);
            soma += digito * PESOS[pesoIndex % PESOS.length];
            pesoIndex++;
        }
        int resto = soma % 11;
        return (resto == 0 || resto == 1) ? 0 : 11 - resto;
    }
}
