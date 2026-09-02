package br.com.estudos.revisao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
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
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.SituacaoRevisao;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * D-49 (01_DOMINIO §3.4) — reativar nunca reescreve a CANCELADA, cria uma
 * PENDENTE nova no mesmo nível, data recalculada a partir de hoje. "Hoje"
 * fixado pelo mesmo motivo de FrenteServiceTest: a data recalculada depende
 * do relógio, e 03_INVARIANTES §10 exige data congelada nesses casos.
 */
@Transactional
class ReativarTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private RevisaoRepository revisaoRepository;

    private Disciplina novaDisciplina(String nome) {
        return disciplinaService.criar(new DisciplinaRequest(nome, TipoPeso.MEDIO));
    }

    private Assunto novoAssunto(Long disciplinaId, String nome, int ordem) {
        return assuntoService.criar(new AssuntoRequest(disciplinaId, nome, TipoPeso.BAIXO, (short) 3, ordem));
    }

    private void registrarEstudo(Long assuntoId, LocalDate data) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}
            """.formatted(assuntoId, data, UUID.randomUUID())))
            .andExpect(status().isCreated());
    }

    private Revisao pendente(Long assuntoId) {
        return revisaoRepository.findByAssuntoIdAndSituacao(assuntoId, SituacaoRevisao.PENDENTE).orElseThrow();
    }

    @Test
    void semRevisaoAnterior_reativarSoVoltaAtivo() throws Exception {
        var disciplina = novaDisciplina("Disciplina Reativar Sem Revisao");
        var assunto = novoAssunto(disciplina.getId(), "Assunto Sem Revisao", 1);

        mockMvc.perform(post("/api/assuntos/{id}/arquivar", assunto.getId())).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/assuntos/{id}/reativar", assunto.getId())).andExpect(status().isNoContent());

        assertTrue(assuntoService.buscar(assunto.getId()).isAtivo());
        assertTrue(revisaoRepository.findByAssuntoId(assunto.getId()).isEmpty(), "nunca teve revisão, reativar não cria nenhuma");
    }

    @Test
    void comPendenteCancelada_reativarCriaNovaNoMesmoNivel() throws Exception {
        var disciplina = novaDisciplina("Disciplina Reativar Com Pendente");
        var assunto = novoAssunto(disciplina.getId(), "Assunto Com Pendente", 1);
        registrarEstudo(assunto.getId(), HOJE.minusDays(10)); // nível 1 pendente, dataPrevista = HOJE-10+1

        mockMvc.perform(post("/api/assuntos/{id}/arquivar", assunto.getId())).andExpect(status().isNoContent());
        assertTrue(revisaoRepository.findByAssuntoIdAndSituacao(assunto.getId(), SituacaoRevisao.PENDENTE).isEmpty(),
            "arquivar cancelou a única pendente");

        mockMvc.perform(post("/api/assuntos/{id}/reativar", assunto.getId())).andExpect(status().isNoContent());

        var nova = pendente(assunto.getId());
        assertEquals(1, nova.getNivel(), "herda o nível da revisão cancelada, não reinicia a escada");
        assertEquals(HOJE.plusDays(1), nova.getDataPrevista(), "data recalculada a partir de HOJE, não da data original");
        assertEquals(2, revisaoRepository.findByAssuntoId(assunto.getId()).size(), "a CANCELADA continua existindo, não foi reescrita (D-18)");
    }

    @Test
    void ordemColidiuEnquantoArquivado_reativarDevolve409() throws Exception {
        var disciplina = novaDisciplina("Disciplina Reativar Ordem Colide");
        var assunto = novoAssunto(disciplina.getId(), "Assunto Original", 1);
        mockMvc.perform(post("/api/assuntos/{id}/arquivar", assunto.getId())).andExpect(status().isNoContent());

        // D-48 só vale entre ativos: com o original arquivado, outro assunto pode assumir a mesma ordem.
        novoAssunto(disciplina.getId(), "Assunto Que Assumiu A Ordem", 1);

        mockMvc.perform(post("/api/assuntos/{id}/reativar", assunto.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ORDEM_DUPLICADA"));
    }

    @Test
    void disciplinaReativar_naoRessuscitaAssuntoArquivadoAParte() throws Exception {
        var disciplina = novaDisciplina("Disciplina Reativar Mista");
        var assuntoArquivadoAParte = novoAssunto(disciplina.getId(), "Assunto Arquivado A Parte", 1);
        var assuntoNaDisciplina = novoAssunto(disciplina.getId(), "Assunto Da Disciplina", 2);
        registrarEstudo(assuntoArquivadoAParte.getId(), HOJE.minusDays(5));
        registrarEstudo(assuntoNaDisciplina.getId(), HOJE.minusDays(5));

        // Arquivado por conta própria, ANTES da disciplina — decisão independente.
        mockMvc.perform(post("/api/assuntos/{id}/arquivar", assuntoArquivadoAParte.getId())).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/disciplinas/{id}/arquivar", disciplina.getId())).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/disciplinas/{id}/reativar", disciplina.getId())).andExpect(status().isNoContent());

        // O assunto arquivado à parte continua arquivado, e a revisão dele continua CANCELADA — sem linha nova.
        assertFalse(assuntoService.buscar(assuntoArquivadoAParte.getId()).isAtivo());
        assertEquals(1, revisaoRepository.findByAssuntoId(assuntoArquivadoAParte.getId()).size());
        assertTrue(revisaoRepository.findByAssuntoIdAndSituacao(assuntoArquivadoAParte.getId(), SituacaoRevisao.PENDENTE).isEmpty());

        // O outro assunto, que só foi arquivado como efeito da disciplina, teve a revisão restaurada.
        var restaurada = pendente(assuntoNaDisciplina.getId());
        assertEquals(1, restaurada.getNivel());
        assertEquals(HOJE.plusDays(1), restaurada.getDataPrevista());
    }
}
