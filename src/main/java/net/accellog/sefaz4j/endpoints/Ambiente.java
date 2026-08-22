package net.accellog.sefaz4j.endpoints;

public enum Ambiente {
    PRODUCAO(1, "P"), HOMOLOGACAO(2, "H");

    private final int tpAmb;
    private final String sufixo;

    Ambiente(int tpAmb, String sufixo) {
        this.tpAmb = tpAmb;
        this.sufixo = sufixo;
    }

    public int getTpAmb() {
        return tpAmb;
    }

    public String getSufixo() {
        return sufixo;
    }
}
