package net.accellog.sefaz4j.nfe;

public final class ResultadoManifestacao {
    private final String xmlEnvio;
    private final ResultadoEvento resultadoEvento;

    public ResultadoManifestacao(String xmlEnvio, ResultadoEvento resultadoEvento) {
        this.xmlEnvio = xmlEnvio;
        this.resultadoEvento = resultadoEvento;
    }

    public String getXmlEnvio() { return xmlEnvio; }
    public ResultadoEvento getResultadoEvento() { return resultadoEvento; }
}
