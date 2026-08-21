package net.accellog.sefaz4j.nfe.endpoints;

public enum Servico {
    NFE_AUTORIZACAO("NfeAutorizacao_4.00"),
    NFE_RET_AUTORIZACAO("NfeRetAutorizacao_4.00"),
    NFE_CONSULTA_PROTOCOLO("NfeConsultaProtocolo_4.00");

    private final String chaveIni;

    Servico(String chaveIni) {
        this.chaveIni = chaveIni;
    }

    public String getChaveIni() {
        return chaveIni;
    }
}
