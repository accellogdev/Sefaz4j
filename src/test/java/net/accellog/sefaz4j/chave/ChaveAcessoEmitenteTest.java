package net.accellog.sefaz4j.chave;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Chaves sintéticas (CPF 12345678909, CNPJ 12345678000195) — nunca usar chave de cliente aqui.
 * Formato: cUF(2) AAMM(4) CNPJ/000+CPF(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1).
 */
public class ChaveAcessoEmitenteTest {

    @Test
    public void mdfeSerie920ComEmitentePessoaFisicaDevolveCpf() {
        String chave = "35261100012345678909" + "58" + "920" + "000000006" + "1" + "00139081" + "4";
        assertEquals("12345678909", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }

    @Test
    public void mdfeSerieComumDevolveCnpj() {
        String chave = "35261112345678000195" + "58" + "001" + "000000006" + "1" + "00139081" + "4";
        assertEquals("12345678000195", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }

    @Test
    public void nfeSerie969ComEmitentePessoaFisicaDevolveCpf() {
        String chave = "35261100012345678909" + "55" + "969" + "000000648" + "1" + "18008590" + "8";
        assertEquals("12345678909", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }

    @Test
    public void nfeSerie970DevolveCnpj() {
        String chave = "35261112345678000195" + "55" + "970" + "000000648" + "1" + "18008590" + "8";
        assertEquals("12345678000195", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }

    @Test
    public void nfeNffComIndicadorDeCpfDevolveCpf() {
        // tpEmis=3 (NFF): o 5º dígito do nNF (posição 30 da chave) diz se o emissor é CPF (2)
        String chave = "35261100012345678909" + "55" + "001" + "000020648" + "3" + "18008590" + "8";
        assertEquals("12345678909", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }

    @Test
    public void cteDevolveCnpjMesmoComSerie920() {
        // CT-e não reserva séries para CPF; só tpEmis=3 (NFF) indica emissor pessoa física
        String chave = "35261112345678000195" + "57" + "920" + "000000006" + "1" + "00139081" + "4";
        assertEquals("12345678000195", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }

    @Test
    public void cteNffDevolveCpf() {
        String chave = "35261100012345678909" + "57" + "001" + "000000006" + "3" + "00139081" + "4";
        assertEquals("12345678909", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }

    @Test
    public void cnpjAlfanumericoEhPreservado() {
        String chave = "3526111234ABCD000195" + "58" + "001" + "000000006" + "1" + "00139081" + "4";
        assertEquals("1234ABCD000195", ChaveAcessoEmitente.cnpjOuCpf(chave));
    }
}
