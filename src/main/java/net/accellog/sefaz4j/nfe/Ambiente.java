package net.accellog.sefaz4j.nfe;

public enum Ambiente {
    PRODUCAO(1), HOMOLOGACAO(2);

    private final int tpAmb;

    Ambiente(int tpAmb) {
        this.tpAmb = tpAmb;
    }

    public int getTpAmb() {
        return tpAmb;
    }

    public net.accellog.sefaz4j.nfe.endpoints.Ambiente paraEndpoints() {
        return this == PRODUCAO
            ? net.accellog.sefaz4j.nfe.endpoints.Ambiente.PRODUCAO
            : net.accellog.sefaz4j.nfe.endpoints.Ambiente.HOMOLOGACAO;
    }
}
