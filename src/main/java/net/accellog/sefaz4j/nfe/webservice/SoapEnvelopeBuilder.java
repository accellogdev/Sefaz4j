package net.accellog.sefaz4j.nfe.webservice;

public final class SoapEnvelopeBuilder {

    private SoapEnvelopeBuilder() {
    }

    public static String envelopeAutorizacao(String nfeXmlAssinado, long idLote) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<nfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">" +
            "<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
            "<idLote>" + idLote + "</idLote>" +
            "<indSinc>1</indSinc>" +
            nfeXmlAssinado +
            "</enviNFe>" +
            "</nfeDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }

    public static String envelopeRetAutorizacao(String nRec, int tpAmb) {
        return "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap12:Body>" +
            "<nfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRetAutorizacao4\">" +
            "<consReciNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
            "<tpAmb>" + tpAmb + "</tpAmb>" +
            "<nRec>" + nRec + "</nRec>" +
            "</consReciNFe>" +
            "</nfeDadosMsg>" +
            "</soap12:Body>" +
            "</soap12:Envelope>";
    }
}
