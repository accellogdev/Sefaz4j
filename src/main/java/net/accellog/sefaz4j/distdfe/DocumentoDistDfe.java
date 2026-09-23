package net.accellog.sefaz4j.distdfe;

public final class DocumentoDistDfe {
    private final String nsu;
    private final String schema;
    private final String xml;

    public DocumentoDistDfe(String nsu, String schema, String xml) {
        this.nsu = nsu;
        this.schema = schema;
        this.xml = xml;
    }

    public String getNsu() { return nsu; }
    public String getSchema() { return schema; }
    public String getXml() { return xml; }
}
