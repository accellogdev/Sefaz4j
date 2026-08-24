package net.accellog.sefaz4j.mdfe.webservice;

public final class SoapEnvelopeBuilder {

    private SoapEnvelopeBuilder() {
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
