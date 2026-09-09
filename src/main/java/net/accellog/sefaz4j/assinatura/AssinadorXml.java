package net.accellog.sefaz4j.assinatura;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.ElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

public final class AssinadorXml {

    static {
        // Sem isso, o Apache Santuario quebra o Base64 de SignatureValue/X509Certificate em
        // linhas de 76 colunas separadas por CRLF (System.lineSeparator(), "\r\n" no Windows) --
        // o "\r" embutido no texto é serializado como entidade "&#13;" no XML. A SEFAZ (ao menos
        // o autorizador de CT-e da SEFAZ-PR, em homologação) rejeita isso com cStat 298
        // ("Assinatura difere do padrao do Projeto"), mesmo com a assinatura estruturalmente e
        // criptograficamente correta. Precisa ser setado ANTES de Init.init() (JIRA
        // SANTUARIO-482/494/525) para valer para toda assinatura deste processo.
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true");
        Init.init();
    }

    private AssinadorXml() {
    }

    public static void assinar(Document documentoNaoAssinado, byte[] pfxBytes, String senha, String namespace, String nomeElemento, String algoritmoAssinatura, String algoritmoDigest) {
        assinar(documentoNaoAssinado, pfxBytes, senha, namespace, nomeElemento, algoritmoAssinatura, algoritmoDigest, ElementProxy.getDefaultPrefix(Constants.SignatureSpecNS));
    }

    /**
     * Mesma assinatura de {@link #assinar(Document, byte[], String, String, String, String, String)},
     * mas com o prefixo do {@code ds:Signature} explícito. O SEFIN Nacional (NFS-e) rejeita QUALQUER
     * elemento com prefixo de namespace (E1228 "Xml declarado com prefixo de namespace") — inclusive o
     * próprio {@code ds:Signature}, ao contrário de NFe/CTe/MDFe (SOAP estadual), que aceitam o
     * {@code ds:} padrão do Apache Santuario normalmente. {@code prefixoAssinatura=""} produz
     * {@code <Signature xmlns="...">} (sem prefixo) em vez de {@code <ds:Signature xmlns:ds="...">}.
     *
     * <p>{@code ElementProxy.setDefaultPrefix} é estado estático global do Santuario — por isso o
     * valor anterior é salvo e restaurado num {@code finally}, e a troca fica sincronizada, para não
     * vazar para uma assinatura concorrente de outro tipo de documento (NFe/CTe/MDFe) enquanto esta
     * roda.</p>
     */
    public static synchronized void assinar(
        Document documentoNaoAssinado, byte[] pfxBytes, String senha, String namespace, String nomeElemento,
        String algoritmoAssinatura, String algoritmoDigest, String prefixoAssinatura
    ) {
        String prefixoAnterior = ElementProxy.getDefaultPrefix(Constants.SignatureSpecNS);
        try {
            ElementProxy.setDefaultPrefix(Constants.SignatureSpecNS, prefixoAssinatura);
            assinarComPrefixoJaConfigurado(documentoNaoAssinado, pfxBytes, senha, namespace, nomeElemento, algoritmoAssinatura, algoritmoDigest);
        } catch (org.apache.xml.security.exceptions.XMLSecurityException e) {
            throw new CertificadoException("Falha ao configurar o prefixo de namespace da assinatura (" + nomeElemento + ")", e);
        } finally {
            try {
                ElementProxy.setDefaultPrefix(Constants.SignatureSpecNS, prefixoAnterior);
            } catch (org.apache.xml.security.exceptions.XMLSecurityException ignore) {
                // Restaurar o prefixo anterior não deve mascarar uma falha de assinatura já lançada
                // acima; se o próprio Santuario não aceitar seu valor original de volta, não há nada
                // de melhor a fazer aqui além de ignorar.
            }
        }
    }

    private static void assinarComPrefixoJaConfigurado(Document documentoNaoAssinado, byte[] pfxBytes, String senha, String namespace, String nomeElemento, String algoritmoAssinatura, String algoritmoDigest) {
        KeyStore.PrivateKeyEntry chavePrivada = CertificadoA1.carregar(pfxBytes, senha);

        NodeList elementos = documentoNaoAssinado.getElementsByTagNameNS(namespace, nomeElemento);
        Element elementoAssinado = (Element) elementos.item(0);
        String id = elementoAssinado.getAttribute("Id");
        // Sem DTD/schema, o DOM não reconhece "Id" como atributo do tipo ID por padrão;
        // isso é necessário para que o resolver de referência "#id" do Santuario funcione.
        elementoAssinado.setIdAttribute("Id", true);

        try {
            // O algoritmo de assinatura e digest agora é parametrizado, permitindo diferentes
            // document types (p.ex. NFe/CTe usam RSA-SHA1/SHA-1 conforme exigência dos schemas
            // oficiais bundled xmldsig-core-schema_v1.01.xsd, enquanto NFSe pode usar RSA-SHA256).
            // Para NFe/CTe, o schema oficial fixa SignatureMethod/DigestMethod em rsa-sha1/sha1
            // e restringe os Transform aceitos a enveloped-signature + C14N puro (sem "WithComments")
            // — não são valores default, são <xsd:restriction> fixas. SEFAZ exige RSA-SHA1 para a
            // assinatura do DFe em si (TLS é outro assunto, não afetado por esta escolha). Vale
            // para qualquer elemento assinado de qualquer documento (infNFe/infEvento/infInut/infCte).
            XMLSignature assinatura = new XMLSignature(
                documentoNaoAssinado, "", algoritmoAssinatura
            );

            Transforms transforms = new Transforms(documentoNaoAssinado);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_OMIT_COMMENTS);
            assinatura.addDocument("#" + id, transforms, algoritmoDigest);

            assinatura.addKeyInfo((X509Certificate) chavePrivada.getCertificate());
            assinatura.sign(chavePrivada.getPrivateKey());

            documentoNaoAssinado.getDocumentElement().appendChild(assinatura.getElement());
        } catch (Exception e) {
            // Falha de assinatura em si (fora da carga/senha do certificado, já tratada em
            // CertificadoA1.carregar) é tratada como o mesmo tipo de falha técnica: o spec
            // documenta apenas 3 tipos de exceção técnica (CertificadoException/
            // ValidacaoXsdException/ComunicacaoException) e assinatura mal-sucedida é, na
            // prática, um problema de certificado/chave inutilizável.
            throw new CertificadoException("Falha ao assinar o XML (" + nomeElemento + ")", e);
        }
    }
}
