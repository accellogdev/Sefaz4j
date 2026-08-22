package net.accellog.sefaz4j.nfe.webservice;

import net.accellog.sefaz4j.webservice.RespostaSefaz;

import org.junit.Test;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public class ReciboPollerTest {

    @Test
    public void paraNaPrimeiraRespostaQueNaoEhMaisEmProcessamento() {
        List<RespostaSefaz> respostas = List.of(
            new RespostaSefaz("103", "Lote recebido com sucesso", "111", null, null),
            new RespostaSefaz("104", "Lote processado", null, "35250812345678000195550010000001231123456789", "<protNFe/>")
        );
        Supplier<RespostaSefaz> proximaResposta = respostas.iterator()::next;

        RespostaSefaz resultado = ReciboPoller.aguardarProtocolo(proximaResposta, 5, Duration.ofMillis(1));

        assertEquals("104", resultado.getCStat());
    }

    @Test
    public void desisteAposMaxTentativasEDevolveAUltimaRespostaEmProcessamento() {
        Supplier<RespostaSefaz> sempreEmProcessamento =
            () -> new RespostaSefaz("103", "Lote recebido com sucesso", "111", null, null);

        RespostaSefaz resultado = ReciboPoller.aguardarProtocolo(sempreEmProcessamento, 3, Duration.ofMillis(1));

        assertEquals("103", resultado.getCStat());
    }
}
