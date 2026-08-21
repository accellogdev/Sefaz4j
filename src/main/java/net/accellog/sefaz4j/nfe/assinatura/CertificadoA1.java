package net.accellog.sefaz4j.nfe.assinatura;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;

public final class CertificadoA1 {

    private CertificadoA1() {
    }

    public static KeyStore.PrivateKeyEntry carregar(byte[] pfxBytes, String senha) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pfxBytes), senha.toCharArray());

            String alias = keyStore.aliases().nextElement();
            KeyStore.ProtectionParameter protecao = new KeyStore.PasswordProtection(senha.toCharArray());
            return (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, protecao);
        } catch (Exception e) {
            throw new CertificadoException("Falha ao carregar certificado A1 (senha incorreta ou PFX corrompido)", e);
        }
    }
}
