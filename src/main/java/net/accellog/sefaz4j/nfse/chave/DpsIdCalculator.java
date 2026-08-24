package net.accellog.sefaz4j.nfse.chave;

public final class DpsIdCalculator {

    private DpsIdCalculator() {
    }

    public static String calcular(
        String codMunIbge,
        String tipoInscricaoFederal,
        String inscricaoFederal,
        String serieDps,
        String numDps
    ) {
        String id = "DPS" + codMunIbge + tipoInscricaoFederal + inscricaoFederal + serieDps + numDps;
        if (id.length() != 3 + 7 + 1 + 14 + 5 + 15) {
            throw new IllegalArgumentException(
                "A concatenação dos campos do Id da DPS deve ter " + (3 + 7 + 1 + 14 + 5 + 15)
                    + " caracteres (prefixo 'DPS' + 7+1+14+5+15), obteve " + id.length() + ": '" + id + "'"
            );
        }
        String parteNumerica = id.substring(3);
        if (!parteNumerica.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                "Os campos do Id da DPS (após o prefixo 'DPS') devem conter apenas dígitos: '" + parteNumerica + "'"
            );
        }
        return id;
    }
}
