package net.accellog.sefaz4j.cte;

public final class InfCorrecao {
    private final String grupoAlterado;
    private final String campoAlterado;
    private final String valorAlterado;
    private final Integer nroItemAlterado;

    public InfCorrecao(String grupoAlterado, String campoAlterado, String valorAlterado) {
        this(grupoAlterado, campoAlterado, valorAlterado, null);
    }

    public InfCorrecao(String grupoAlterado, String campoAlterado, String valorAlterado, Integer nroItemAlterado) {
        this.grupoAlterado = grupoAlterado;
        this.campoAlterado = campoAlterado;
        this.valorAlterado = valorAlterado;
        this.nroItemAlterado = nroItemAlterado;
    }

    public String getGrupoAlterado() {
        return grupoAlterado;
    }

    public String getCampoAlterado() {
        return campoAlterado;
    }

    public String getValorAlterado() {
        return valorAlterado;
    }

    public Integer getNroItemAlterado() {
        return nroItemAlterado;
    }
}
