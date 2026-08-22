package net.accellog.sefaz4j.chave;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ChaveAcessoCalculatorTest {

    @Test
    public void calculaChaveComDigitoVerificadorNaoTrivial() {
        String chave = ChaveAcessoCalculator.calcular(
            "35", "2508", "12345678000195", "55", "001", "000000123", "1", "12345678"
        );
        assertEquals("35250812345678000195550010000001231123456789", chave);
    }

    @Test
    public void calculaChaveComDigitoVerificadorZero() {
        String chave = ChaveAcessoCalculator.calcular(
            "35", "2508", "12345678000195", "55", "001", "000000003", "1", "12345678"
        );
        assertEquals("35250812345678000195550010000000031123456780", chave);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejeitaCamposComTamanhoErrado() {
        ChaveAcessoCalculator.calcular("3", "2508", "12345678000195", "55", "001", "000000003", "1", "12345678");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejeitaCaractereNaoNumericoMesmoComTamanhoCorreto() {
        // "3X" no lugar de "35" mantém os 43 dígitos no total, mas
        // Character.digit('X', 10) devolveria -1 silenciosamente sem essa
        // verificação — deve falhar de forma explícita em vez disso.
        ChaveAcessoCalculator.calcular("3X", "2508", "12345678000195", "55", "001", "000000123", "1", "12345678");
    }
}
