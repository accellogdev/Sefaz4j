package net.accellog.sefaz4j.nfe.endpoints;

public enum Ambiente {
    PRODUCAO("P"), HOMOLOGACAO("H");

    private final String sufixo;

    Ambiente(String sufixo) {
        this.sufixo = sufixo;
    }

    public String getSufixo() {
        return sufixo;
    }
}
