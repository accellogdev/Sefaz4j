package net.accellog.sefaz4j.nfe.webservice;

public class ComunicacaoException extends RuntimeException {
    public ComunicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
