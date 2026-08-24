package net.accellog.sefaz4j.mdfe.endpoints;

public enum Servico {
    MDFE_RECEPCAO("MDFeRecepcao_3.00"),
    MDFE_RET_RECEPCAO("MDFeRetRecepcao_3.00"),
    MDFE_CONSULTA_PROTOCOLO("MDFeConsultaProtocolo_3.00"),
    RECEPCAO_EVENTO("RecepcaoEvento_3.00");

    private final String chaveIni;

    Servico(String chaveIni) {
        this.chaveIni = chaveIni;
    }

    public String getChaveIni() {
        return chaveIni;
    }
}
