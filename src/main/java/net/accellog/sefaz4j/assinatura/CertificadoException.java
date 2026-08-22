package net.accellog.sefaz4j.assinatura;

public class CertificadoException extends RuntimeException {
    public CertificadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
