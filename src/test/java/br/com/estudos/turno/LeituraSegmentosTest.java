package br.com.estudos.turno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.segmento.Segmento;
import br.com.estudos.segmento.SegmentoRequest;
import br.com.estudos.segmento.SegmentoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.SituacaoRevisao;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * Leitura de assunto com segmentos — D-54 (a escada começa quando a leitura
 * termina) e D-55 (o turno continua a leitura antes de abrir assunto novo).
 * docs/SPRINT-17-USO-REAL.md §1.4.
 *
 * Os outros testes que gravam de verdade (sem @Transactional) não deixam
 * assunto em estudo com segmento por ler, então a continuação encontrada aqui
 * é a do próprio teste.
 */
@Transactional
class LeituraSegmentosTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;
    @Autowired private SegmentoService segmentoService;
    @Autowired private RevisaoRepository revisaoRepository;
    @Autowired private ParametroRepository parametroRepository;
    @Autowired private TurnoService turnoService;

    private Assunto novoAssunto(String nome) {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina " + nome, TipoPeso.MEDIO));
        return assuntoService.criar(new AssuntoRequest(disciplina.getId(), nome, TipoPeso.MEDIO, (short) 3, 1, null));
    }

    private Segmento segmento(Assunto assunto, int ordem) {
        return segmentoService.criarOuAtualizar(new SegmentoRequest(
            assunto.getId(), "seg-" + UUID.randomUUID(), ordem, "https://drive.google.com/file/d/s" + ordem + "/view",
            (ordem - 1) * 15 + 1, ordem * 15, 60));
    }

    private void estudar(Long assuntoId, Long segmentoId, LocalDate data) throws Exception {
        var segmento = segmentoId == null ? "" : ", \"segmentoId\": " + segmentoId;
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 60, "tentativaId": "%s"%s}
            """.formatted(assuntoId, data, UUID.randomUUID(), segmento)))
            .andExpect(status().isCreated());
    }

    private boolean temRevisao(Long assuntoId) {
        return !revisaoRepository.findByAssuntoId(assuntoId).isEmpty();
    }

    @Test
    void estudarParteNaoAgendaRevisao_ultimoSegmentoAgenda() throws Exception {
        var assunto = novoAssunto("Leitura Em Tres");
        var s1 = segmento(assunto, 1);
        var s2 = segmento(assunto, 2);
        var s3 = segmento(assunto, 3);

        estudar(assunto.getId(), s1.getId(), HOJE.minusDays(5));
        estudar(assunto.getId(), s2.getId(), HOJE.minusDays(4));
        assertFalse(temRevisao(assunto.getId()), "D-54: com segmento por ler, a escada não começa");

        estudar(assunto.getId(), s3.getId(), HOJE.minusDays(2));
        var pendente = revisaoRepository.findByAssuntoIdAndSituacao(assunto.getId(), SituacaoRevisao.PENDENTE).orElseThrow();
        assertEquals(1, pendente.getNivel());
        assertEquals(HOJE.minusDays(1), pendente.getDataPrevista(), "agenda a partir do dia em que a leitura terminou");
    }

    @Test
    void estudoSemSegmentoAgenda() throws Exception {
        var assunto = novoAssunto("Leitura Assunto Inteiro");
        segmento(assunto, 1);
        segmento(assunto, 2);

        estudar(assunto.getId(), null, HOJE);

        assertTrue(temRevisao(assunto.getId()), "D-54: ESTUDO sem segmento é o assunto inteiro e começa a escada");
    }

    @Test
    void planoContinuaProximoSegmento() throws Exception {
        var assunto = novoAssunto("Leitura Continua");
        var s1 = segmento(assunto, 1);
        var s2 = segmento(assunto, 2);
        segmento(assunto, 3);
        novoAssunto("Leitura Backlog Concorrente"); // assunto novo disponível, não pode passar na frente

        estudar(assunto.getId(), s1.getId(), HOJE);
        var plano = turnoService.plano();

        assertTrue(plano.blocoContinuacao(), "D-55: continuar vem antes de abrir assunto novo");
        assertEquals(assunto.getId(), plano.blocoConteudo().id());
        assertEquals(s2.getId(), plano.segmentoSugerido().id());
        assertEquals(2, plano.segmentoSugerido().ordem());
        assertEquals(3, plano.segmentoSugerido().total());
    }

    @Test
    void continuacaoNaoDependeDoTetoDaFrente() throws Exception {
        var teto = parametroRepository.findById("teto_global_frente").orElseThrow();
        teto.setValor("1");
        parametroRepository.saveAndFlush(teto);

        var assunto = novoAssunto("Leitura Frente Cheia");
        var s1 = segmento(assunto, 1);
        var s2 = segmento(assunto, 2);
        estudar(assunto.getId(), s1.getId(), HOJE);

        var plano = turnoService.plano();

        assertNotNull(plano.blocoConteudo(), "frente cheia não impede terminar a leitura do que já começou");
        assertEquals(s2.getId(), plano.segmentoSugerido().id());
    }

    @Test
    void assuntoNovoJaIndicaPrimeiroSegmento() throws Exception {
        var assunto = novoAssunto("Leitura Nova");
        var s1 = segmento(assunto, 1);
        segmento(assunto, 2);

        var plano = turnoService.plano();

        if (plano.blocoConteudo() != null && plano.blocoConteudo().id().equals(assunto.getId())) {
            assertFalse(plano.blocoContinuacao());
            assertEquals(s1.getId(), plano.segmentoSugerido().id());
        }
        // Se outro assunto do backlog (de testes que gravam de verdade) vier antes,
        // a regra continua valendo para ele: assunto novo nunca é continuação.
        assertFalse(plano.blocoContinuacao());
    }
}
