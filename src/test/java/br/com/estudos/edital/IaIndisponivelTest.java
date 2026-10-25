package br.com.estudos.edital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.shared.exception.IndisponivelException;

/** ADR-043 — sem chave do OpenRouter, a análise fica indisponível com o motivo, antes de ler qualquer coisa. */
class IaIndisponivelTest {

    @Test
    void semChave_indisponivelComMotivo() {
        // Nada cadastrado em Ajustes (o mock devolve Optional vazio) e ambiente sem chave.
        var configuracao = new ConfiguracaoIa(mock(AjusteIaRepository.class), "", "modelo/x", "http://localhost", true, "claude");
        var service = new AnaliseEditalService(null, null, null, configuracao, null, null);

        var status = service.statusIa();
        assertFalse(status.disponivel());

        var erro = assertThrows(IndisponivelException.class,
            () -> service.iniciar(new MockMultipartFile("arquivo", "edital.pdf", "application/pdf", new byte[] {1})));
        assertEquals("IA_INDISPONIVEL", erro.getCodigo());
    }
}
