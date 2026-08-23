package net.accellog.sefaz4j.cte.endpoints;

public enum Servico {
    CTE_RECEPCAO_SINC("CTeRecepcaoSinc_4.00");

    private final String chaveIni;

    Servico(String chaveIni) {
        this.chaveIni = chaveIni;
    }

    public String getChaveIni() {
        return chaveIni;
    }
}
