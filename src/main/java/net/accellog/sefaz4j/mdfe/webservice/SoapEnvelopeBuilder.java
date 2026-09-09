package net.accellog.sefaz4j.mdfe.webservice;

public final class SoapEnvelopeBuilder {

    private SoapEnvelopeBuilder() {
    }

    /**
     * MDFeRecepcaoSinc (recepção síncrona, substitui MDFeRecepcao/MDFeRetRecepcao — ver
     * comentário em {@code mdfe-servicos.ini}). Mesmo padrão de
     * {@code net.accellog.sefaz4j.cte.webservice.SoapEnvelopeBuilder#envelopeRecepcaoSinc}: o
     * conteúdo de {@code mdfeDadosMsg} precisa vir comprimido em gzip e codificado em Base64
     * (confirmado no MOC do MDF-e/CONFAZ) — {@code mdfeDadosComprimido} já deve chegar pronto
     * (ver {@code net.accellog.sefaz4j.webservice.GzipBase64}), este método só monta o envelope.
     */
    public static String envelopeRecepcaoSinc(String mdfeDadosComprimido) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<mdfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoSinc\">" +
            mdfeDadosComprimido +
            "</mdfeDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }

    public static String envelopeRecepcao(String mdfeXmlAssinado, long idLote) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<mdfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcao\">" +
            "<enviMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
            "<idLote>" + idLote + "</idLote>" +
            mdfeXmlAssinado +
            "</enviMDFe>" +
            "</mdfeDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }

    public static String envelopeRetRecepcao(String nRec, int tpAmb) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<mdfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRetRecepcao\">" +
            "<consReciMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
            "<tpAmb>" + tpAmb + "</tpAmb>" +
            "<nRec>" + nRec + "</nRec>" +
            "</consReciMDFe>" +
            "</mdfeDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }

    public static String envelopeConsultaSituacao(String consSitMDFeXml) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<mdfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeConsulta\">" +
            consSitMDFeXml +
            "</mdfeDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }

    public static String envelopeRecepcaoEvento(String eventoMDFeXmlAssinado) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<mdfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento\">" +
            eventoMDFeXmlAssinado +
            "</mdfeDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }
}
