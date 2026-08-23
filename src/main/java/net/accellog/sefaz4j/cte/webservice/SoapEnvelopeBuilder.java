package net.accellog.sefaz4j.cte.webservice;

public final class SoapEnvelopeBuilder {

    private SoapEnvelopeBuilder() {
    }

    public static String envelopeRecepcaoSinc(String cteXmlAssinado) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<cteDadosMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSincV4\">" +
            cteXmlAssinado +
            "</cteDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }
}
