package br.com.estudos.qconcursos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * D-150 e D-151 pela rota (docs/SPRINT-36-QCONCURSOS.md §4). Ano congelado em 2026; dentro da transação, os outros concursos e as
 * outras árvores deixam de valer.
 */
@Transactional
class QConcursosApiTest extends IntegracaoTestBase {

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(LocalDate.of(2026, 6, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;

    long concurso;
    long porNoDeAssunto, porNoDeDisciplina, pelaAnalise, pelaArvore, semNada;
    String portugues, administrativo;

    /**
     * Árvore vigente: Português (−26) com Regência (28150) e Direito Administrativo (−2). Na análise do alvo: um assunto com parte
     * aceita no nó 28150, outro com parte decidida no nó −2, outro só com parte proposta (cai para os ids da disciplina da análise,
     * [26]). Fora da análise: um assunto de disciplina com o mesmo nome de uma raiz (cai para ela) e outro sem nada.
     */
    @BeforeEach
    void cenario() {
        jdbc.update("UPDATE concurso SET ativo = false WHERE ativo");
        jdbc.update("UPDATE arvore_versao SET vigente = false WHERE vigente");
        var n = System.nanoTime();
        portugues = "Português " + n;
        administrativo = "Direito Administrativo " + n;
        var dPort = disciplinas.criar(new DisciplinaRequest(portugues, TipoPeso.ALTO)).getId();
        var dAdm = disciplinas.criar(new DisciplinaRequest(administrativo, TipoPeso.MEDIO)).getId();
        var dOutra = disciplinas.criar(new DisciplinaRequest("Sem par " + n, TipoPeso.BAIXO)).getId();
        porNoDeAssunto = assunto(dPort, "Regência " + n, 1);
        porNoDeDisciplina = assunto(dAdm, "Atos " + n, 1);
        pelaAnalise = assunto(dPort, "Crase " + n, 2);
        pelaArvore = assunto(dAdm, "Licitações " + n, 2);
        semNada = assunto(dOutra, "Solto " + n, 1);

        var versao = jdbc.queryForObject("""
            INSERT INTO arvore_versao (rotulo, fonte, data, quantidade_nos, vigente) VALUES (?, 'qconcursos', '2026-10-19', 3, true)
            RETURNING id""", Long.class, "qc-" + n);
        no(versao, -26, portugues, null, -26);
        no(versao, 28150, "Regência", -26L, -26);
        no(versao, -2, administrativo, null, -2);

        concurso = jdbc.queryForObject("""
            INSERT INTO concurso (nome, origem, slug, banca, ativo, ativado_em) VALUES ('Alvo', 'PLANEJAMENTO', 'alvo-' || ?,
            'FGV - Fundação Getulio Vargas', true, now()) RETURNING id""", Long.class, n);
        jdbc.update("INSERT INTO prova (concurso_id, nome, banca, ano, status, hash_prova, formato) VALUES (?, 'p', 'FGV', 2025, 'LENDO', ?, 'CERTO_ERRADO')",
            concurso, "h" + n);
        var analise = jdbc.queryForObject("""
            INSERT INTO analise_edital (nome_arquivo, texto, status, concurso_id) VALUES ('e.pdf', '', 'CONFIRMADA', ?) RETURNING id""",
            Long.class, concurso);
        var edPort = jdbc.queryForObject("INSERT INTO edital_disciplina (analise_id, ordem, nome, qconcursos_ids) VALUES (?, 1, 'Português', '[26]') RETURNING id",
            Long.class, analise);
        var edAdm = jdbc.queryForObject("INSERT INTO edital_disciplina (analise_id, ordem, nome) VALUES (?, 2, 'Administrativo') RETURNING id",
            Long.class, analise);
        parte(editalAssunto(edPort, "1", porNoDeAssunto), versao, "ACEITA", false, 28150);
        parte(editalAssunto(edAdm, "1", porNoDeDisciplina), versao, "DECIDIDA", false, -2);
        parte(editalAssunto(edPort, "2", pelaAnalise), versao, "PROPOSTA", true, 28150);
    }

    long assunto(long disciplina, String nome, int ordem) {
        return assuntos.criar(new AssuntoRequest(disciplina, nome, TipoPeso.MEDIO, (short) 3, ordem, null)).getId();
    }

    void no(long versao, long id, String nome, Long pai, long raiz) {
        jdbc.update("INSERT INTO no_arvore (versao_id, no_id, nome, pai_id, ordem, raiz_id) VALUES (?, ?, ?, ?, 1, ?)", versao, id, nome, pai, raiz);
    }

    long editalAssunto(long disciplina, String chave, long assunto) {
        return jdbc.queryForObject("""
            INSERT INTO edital_assunto (disciplina_id, chave, ordem, texto, assunto_id) VALUES (?, ?, 1, 't', ?) RETURNING id""",
            Long.class, disciplina, chave, assunto);
    }

    void parte(long editalAssunto, long versao, String estado, boolean proposto, long noId) {
        var parte = jdbc.queryForObject("""
            INSERT INTO edital_parte (assunto_edital_id, chave, ordem, texto, origem, estado, arvore_versao_id)
            VALUES (?, 'a', 1, 'parte', 'IA', ?, ?) RETURNING id""", Long.class, editalAssunto, estado, versao);
        jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, ?, 1, ?)", parte, proposto, noId);
    }

    String ids() {
        return porNoDeAssunto + "," + porNoDeDisciplina + "," + pelaAnalise + "," + pelaArvore + "," + semNada;
    }

    @Test
    void d150_osNosDaConferencia_aQuedaParaADisciplina_eSemAtalho() throws Exception {
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", ids()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].assuntoId").value(porNoDeAssunto))
            .andExpect(jsonPath("$[0].url").value(LinkQConcursos.BASE + "?discipline_ids%5B%5D=26&examining_board_ids%5B%5D=63"
                + "&exclude_nullified=true&exclude_outdated=true&modality_ids%5B%5D=2&my_questions=not_resolved&per_page=10"
                + "&publication_year%5B%5D=2021&publication_year%5B%5D=2022&publication_year%5B%5D=2023&publication_year%5B%5D=2024"
                + "&publication_year%5B%5D=2025&publication_year%5B%5D=2026&scholarity_ids%5B%5D=3&subject_ids%5B%5D=28150"))
            .andExpect(jsonPath("$[0].sugeridas").value(8))
            .andExpect(jsonPath("$[0].banca").value("FGV"))
            .andExpect(jsonPath("$[0].formato").value("CERTO_ERRADO"))
            .andExpect(jsonPath("$[0].filtro[0]").value("Regência (" + portugues + ")"))
            .andExpect(jsonPath("$[0].avisos.length()").value(0))
            .andExpect(jsonPath("$[1].assuntoId").value(porNoDeDisciplina))
            .andExpect(jsonPath("$[1].url", org.hamcrest.Matchers.containsString("discipline_ids%5B%5D=2&examining")))
            .andExpect(jsonPath("$[1].url", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("subject_ids"))))
            .andExpect(jsonPath("$[1].avisos.length()").value(0))
            .andExpect(jsonPath("$[2].assuntoId").value(pelaAnalise))
            .andExpect(jsonPath("$[2].url", org.hamcrest.Matchers.containsString("discipline_ids%5B%5D=26&")))
            .andExpect(jsonPath("$[2].url", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("subject_ids"))))
            .andExpect(jsonPath("$[2].avisos[0]").value("DISCIPLINA"))
            .andExpect(jsonPath("$[3].assuntoId").value(pelaArvore))
            .andExpect(jsonPath("$[3].url", org.hamcrest.Matchers.containsString("discipline_ids%5B%5D=2&")))
            .andExpect(jsonPath("$[3].avisos[0]").value("DISCIPLINA"));
    }

    @Test
    void d151_aBancaPeloCatalogo_CespeECebraspe() throws Exception {
        mockMvc.perform(get("/api/concursos/{id}/banca-qconcursos", concurso))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.banca").value("FGV - Fundação Getulio Vargas"))
            .andExpect(jsonPath("$.numero").value(63))
            .andExpect(jsonPath("$.nomeNoCatalogo").value("FGV"))
            .andExpect(jsonPath("$.origem").value("CATALOGO"));
        jdbc.update("UPDATE concurso SET banca = 'Cebraspe (CESPE)' WHERE id = ?", concurso);
        mockMvc.perform(get("/api/concursos/{id}/banca-qconcursos", concurso)).andExpect(jsonPath("$.numero").value(2));
    }

    @Test
    void d151_bancaForaDoCatalogo_vaiSemBanca_eAprendePeloEndereco() throws Exception {
        jdbc.update("UPDATE concurso SET banca = 'IBFC' WHERE id = ?", concurso);
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].avisos[0]").value("SEM_BANCA"))
            .andExpect(jsonPath("$[0].banca").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("examining_board"))));

        mockMvc.perform(put("/api/concursos/{id}/banca-qconcursos", concurso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endereco\": \"https://www.qconcursos.com/questoes-de-concursos/questoes?discipline_ids%5B%5D=26\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("ENDERECO_SEM_BANCA"));
        mockMvc.perform(put("/api/concursos/{id}/banca-qconcursos", concurso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endereco\": \"https://www.qconcursos.com/questoes-de-concursos/questoes?examining_board_ids%5B%5D=207\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numero").value(207))
            .andExpect(jsonPath("$.origem").value("ENDERECO"));
        assertThat(jdbc.queryForObject("SELECT numero FROM qconcursos_banca WHERE nome_norm = 'ibfc'", Integer.class)).isEqualTo(207);
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].avisos.length()").value(0))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("examining_board_ids%5B%5D=207")));
    }

    @Test
    void d151_semBancaNoConcurso_recusaAprender_eSemAlvoVaiSemBanca() throws Exception {
        jdbc.update("UPDATE concurso SET banca = NULL WHERE id = ?", concurso);
        jdbc.update("UPDATE prova SET banca = NULL WHERE concurso_id = ?", concurso);
        mockMvc.perform(put("/api/concursos/{id}/banca-qconcursos", concurso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endereco\": \"63\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("SEM_BANCA"));
        jdbc.update("UPDATE concurso SET ativo = false WHERE id = ?", concurso);
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].avisos[0]").value("SEM_BANCA"));
    }

    // ---------- Sprint 45: o vínculo escolhido (D-173) e as questões por vez (D-174) ----------

    String vinculo(String noIds) {
        return "{\"noIds\": [" + noIds + "]}";
    }

    @Test
    void d173_oVinculoDaAoAssuntoSemNadaUmAtalho_eVemAntesDoEdital_eTirarVoltaAoEdital() throws Exception {
        mockMvc.perform(get("/api/assuntos/{id}/qconcursos-vinculo", semNada)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/assuntos/{id}/qconcursos-vinculo", semNada).contentType(MediaType.APPLICATION_JSON).content(vinculo("28150")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].noId").value(28150))
            .andExpect(jsonPath("$[0].nome").value("Regência"))
            .andExpect(jsonPath("$[0].caminho[0]").value(portugues))
            .andExpect(jsonPath("$[0].naArvore").value(true));
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(semNada)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].vinculado").value(true))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("discipline_ids%5B%5D=26&")))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("subject_ids%5B%5D=28150")))
            .andExpect(jsonPath("$[0].avisos", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("DISCIPLINA"))));

        // o do edital (nó 28150) perde para o escolhido (disciplina −2)
        mockMvc.perform(put("/api/assuntos/{id}/qconcursos-vinculo", porNoDeAssunto).contentType(MediaType.APPLICATION_JSON).content(vinculo("-2")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].vinculado").value(true))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("discipline_ids%5B%5D=2&")))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("subject_ids"))))
            .andExpect(jsonPath("$[0].filtro[0]").value(administrativo));
        mockMvc.perform(delete("/api/assuntos/{id}/qconcursos-vinculo", porNoDeAssunto)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].vinculado").value(false))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("subject_ids%5B%5D=28150")));
    }

    @Test
    void d173_ateTres_soNosDaArvoreVigente_eONoQueSaiuDaArvoreAvisa() throws Exception {
        mockMvc.perform(put("/api/assuntos/{id}/qconcursos-vinculo", semNada).contentType(MediaType.APPLICATION_JSON).content(vinculo("")))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VINCULO_VAZIO"));
        mockMvc.perform(put("/api/assuntos/{id}/qconcursos-vinculo", semNada).contentType(MediaType.APPLICATION_JSON).content(vinculo("28150,-26,-2,99")))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VINCULO_DEMAIS"));
        mockMvc.perform(put("/api/assuntos/{id}/qconcursos-vinculo", semNada).contentType(MediaType.APPLICATION_JSON).content(vinculo("777")))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("NO_INEXISTENTE"));
        mockMvc.perform(put("/api/assuntos/{id}/qconcursos-vinculo", 999_999_999L).contentType(MediaType.APPLICATION_JSON).content(vinculo("28150")))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/assuntos/{id}/qconcursos-vinculo", porNoDeDisciplina).contentType(MediaType.APPLICATION_JSON).content(vinculo("28150")))
            .andExpect(status().isOk());

        // uma árvore nova, sem o 28150: o vínculo avisa e o atalho volta ao do edital (−2)
        jdbc.update("UPDATE arvore_versao SET vigente = false WHERE vigente");
        var nova = jdbc.queryForObject("""
            INSERT INTO arvore_versao (rotulo, fonte, data, quantidade_nos, vigente) VALUES (?, 'qconcursos', '2026-11-01', 2, true)
            RETURNING id""", Long.class, "qc-nova-" + System.nanoTime());
        no(nova, -26, portugues, null, -26);
        no(nova, -2, administrativo, null, -2);
        mockMvc.perform(get("/api/assuntos/{id}/qconcursos-vinculo", porNoDeDisciplina))
            .andExpect(jsonPath("$[0].naArvore").value(false));
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeDisciplina)))
            .andExpect(jsonPath("$[0].vinculado").value(false))
            .andExpect(jsonPath("$[0].avisos", org.hamcrest.Matchers.hasItem("NO_FORA_DA_ARVORE")))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("discipline_ids%5B%5D=2&")));
    }

    @Test
    void d174_questoesPorVez_aPaginaAbreComEsseNumero_eSoAceitaOsTamanhosDoQc() throws Exception {
        mockMvc.perform(patch("/api/parametros/questoes_por_vez").contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"7\"}"))
            .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(patch("/api/parametros/questoes_por_vez").contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"15\"}"))
            .andExpect(status().isOk());
        // o PATCH muda a entidade; dentro da transação do teste, o JdbcTemplate só a vê depois do flush
        jdbc.update("UPDATE parametro SET valor = '15' WHERE chave = 'questoes_por_vez'");
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].sugeridas").value(15))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("per_page=15")));
        mockMvc.perform(patch("/api/parametros/questoes_por_vez").contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"0\"}"))
            .andExpect(status().isOk());
        jdbc.update("UPDATE parametro SET valor = '0' WHERE chave = 'questoes_por_vez'");
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].sugeridas").value(8))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("per_page=10")));
    }

    static final String PADRAO_TROCADO = """
        {"niveis": [2, 3], "banca": "QUALQUER", "anos": 0, "formato": "QUALQUER", "dificuldades": [5, 3, 4], "ordem": "RECENTES",
         "tirarResolvidas": false, "tirarAnuladas": true, "tirarDesatualizadas": false, "soComentadas": true, "aprendidos": []}""";

    @Test
    void d175_oPadraoDeFabricaENivelSuperior_eAsMarcasTrazemOsParametros() throws Exception {
        mockMvc.perform(get("/api/qconcursos/filtro"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.niveis[0]").value(3)).andExpect(jsonPath("$.niveis.length()").value(1))
            .andExpect(jsonPath("$.banca").value("CONCURSO")).andExpect(jsonPath("$.anos").value(5))
            .andExpect(jsonPath("$.formato").value("BANCA")).andExpect(jsonPath("$.dificuldades.length()").value(0))
            .andExpect(jsonPath("$.ordem").value("QC")).andExpect(jsonPath("$.tirarResolvidas").value(true))
            .andExpect(jsonPath("$.soComentadas").value(false)).andExpect(jsonPath("$.aprendidos.length()").value(0));
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'nivel')].rotulo").value("Superior"))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'nivel')].parametros[0]").value("scholarity_ids%5B%5D=3"))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'banca')].rotulo").value("FGV"))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'anos')].rotulo").value("2021–2026"))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'formato')].rotulo").value("Certo/errado"))
            .andExpect(jsonPath("$[0].marcas.length()").value(4));
    }

    @Test
    void d175_oPadraoTrocadoMudaOEndereco_eIncompletoOuInvalidoERecusado() throws Exception {
        mockMvc.perform(put("/api/qconcursos/filtro").contentType(MediaType.APPLICATION_JSON).content(PADRAO_TROCADO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dificuldades[0]").value(3));
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].url").value(LinkQConcursos.BASE + "?difficulty%5B%5D=3&difficulty%5B%5D=4&difficulty%5B%5D=5"
                + "&discipline_ids%5B%5D=26&exclude_nullified=true&has_professor_commentaries=true&per_page=10"
                + "&scholarity_ids%5B%5D=2&scholarity_ids%5B%5D=3&sort=newest&subject_ids%5B%5D=28150"))
            .andExpect(jsonPath("$[0].banca").doesNotExist())
            .andExpect(jsonPath("$[0].avisos.length()").value(0))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'dificuldade')].rotulo").value("Médio a muito difícil"))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'nivel')].rotulo").value("Médio e Superior"))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'ordem')].rotulo").value("Mais recentes"))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'comentadas')].parametros[0]").value("has_professor_commentaries=true"));
        mockMvc.perform(put("/api/qconcursos/filtro").contentType(MediaType.APPLICATION_JSON).content("{\"niveis\": [3]}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("FILTRO_INCOMPLETO"));
        mockMvc.perform(put("/api/qconcursos/filtro").contentType(MediaType.APPLICATION_JSON).content(PADRAO_TROCADO.replace("[2, 3]", "[4]")))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("FILTRO_INVALIDO"));
        mockMvc.perform(put("/api/qconcursos/filtro").contentType(MediaType.APPLICATION_JSON).content(PADRAO_TROCADO.replace("\"anos\": 0", "\"anos\": 7")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void d175_aExcecaoDoConcursoGuardaSoOQueDifere_eSegueOPadraoNoResto() throws Exception {
        mockMvc.perform(get("/api/concursos/" + concurso + "/qconcursos-filtro"))
            .andExpect(jsonPath("$.excecao").doesNotExist()).andExpect(jsonPath("$.efetivo.niveis[0]").value(3));
        mockMvc.perform(put("/api/concursos/" + concurso + "/qconcursos-filtro").contentType(MediaType.APPLICATION_JSON).content("{\"niveis\": [2]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.excecao.niveis[0]").value(2)).andExpect(jsonPath("$.excecao.anos").doesNotExist())
            .andExpect(jsonPath("$.efetivo.niveis[0]").value(2)).andExpect(jsonPath("$.efetivo.anos").value(5));
        mockMvc.perform(put("/api/qconcursos/filtro").contentType(MediaType.APPLICATION_JSON).content(PADRAO_TROCADO))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/concursos/" + concurso + "/qconcursos-filtro"))
            .andExpect(jsonPath("$.efetivo.niveis.length()").value(1)).andExpect(jsonPath("$.efetivo.anos").value(0))
            .andExpect(jsonPath("$.efetivo.ordem").value("RECENTES"));
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("scholarity_ids%5B%5D=2&sort")))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("scholarity_ids%5B%5D=3"))));
        mockMvc.perform(delete("/api/concursos/" + concurso + "/qconcursos-filtro")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/concursos/" + concurso + "/qconcursos-filtro"))
            .andExpect(jsonPath("$.excecao").doesNotExist()).andExpect(jsonPath("$.efetivo.niveis.length()").value(2));
        mockMvc.perform(put("/api/concursos/999999999/qconcursos-filtro").contentType(MediaType.APPLICATION_JSON).content("{\"niveis\": [2]}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void d176_cargoEAreasPeloEndereco_oNomeFicaGuardado_eVaiSoONumero() throws Exception {
        var endereco = "{\"endereco\": \"https://www.qconcursos.com/questoes-de-concursos/questoes?administrative_level_ids%5B%5D=9"
            + "&knowledge_area_ids[]=13&scholarity_ids%5B%5D=3&job_ids%5B%5D=43&discipline_ids%5B%5D=1\"}";
        mockMvc.perform(post("/api/qconcursos/filtro/endereco").contentType(MediaType.APPLICATION_JSON).content(endereco))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].tipo").value("ATUACAO")).andExpect(jsonPath("$[0].numero").value(9)).andExpect(jsonPath("$[0].nome").doesNotExist())
            .andExpect(jsonPath("$[1].tipo").value("FORMACAO")).andExpect(jsonPath("$[1].numero").value(13))
            .andExpect(jsonPath("$[2].tipo").value("CARGO")).andExpect(jsonPath("$[2].nome").value("Analista Judiciário - Área Judiciária"));
        mockMvc.perform(post("/api/qconcursos/filtro/endereco").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endereco\": \"https://www.qconcursos.com/questoes-de-concursos/questoes?scholarity_ids%5B%5D=3\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("ENDERECO_SEM_FILTRO"));
        var semNome = PADRAO_TROCADO.replace("\"aprendidos\": []", "\"aprendidos\": [{\"tipo\": \"ATUACAO\", \"numero\": 9}]");
        mockMvc.perform(put("/api/qconcursos/filtro").contentType(MediaType.APPLICATION_JSON).content(semNome))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("NOME_FALTANDO"));
        var comNome = PADRAO_TROCADO.replace("\"aprendidos\": []",
            "\"aprendidos\": [{\"tipo\": \"ATUACAO\", \"numero\": 9, \"nome\": \"Fiscal\"}, {\"tipo\": \"CARGO\", \"numero\": 43}]");
        mockMvc.perform(put("/api/qconcursos/filtro").contentType(MediaType.APPLICATION_JSON).content(comNome))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aprendidos[?(@.tipo == 'ATUACAO')].nome").value("Fiscal"));
        mockMvc.perform(post("/api/qconcursos/filtro/endereco").contentType(MediaType.APPLICATION_JSON).content(endereco))
            .andExpect(jsonPath("$[0].nome").value("Fiscal"));
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.startsWith(LinkQConcursos.BASE + "?administrative_level_ids%5B%5D=9&")))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.containsString("&job_ids%5B%5D=43&")))
            .andExpect(jsonPath("$[0].marcas[?(@.chave == 'ATUACAO:9')].rotulo").value("Fiscal"));
        // a exceção com lista vazia tira os aprendidos só deste concurso
        mockMvc.perform(put("/api/concursos/" + concurso + "/qconcursos-filtro").contentType(MediaType.APPLICATION_JSON).content("{\"aprendidos\": []}"))
            .andExpect(jsonPath("$.efetivo.aprendidos.length()").value(0));
        mockMvc.perform(get("/api/qconcursos/atalhos").param("assuntoIds", String.valueOf(porNoDeAssunto)))
            .andExpect(jsonPath("$[0].url", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("job_ids"))));
    }
}
