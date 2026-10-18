package br.com.estudos.prova;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.prova.ProvaInterna.AlternativaLida;
import br.com.estudos.prova.ProvaInterna.GabaritoLido;
import br.com.estudos.prova.ProvaInterna.LeituraRequest;
import br.com.estudos.prova.ProvaInterna.QuestaoLida;
import br.com.estudos.prova.ProvaInterna.RespostaGabarito;
import br.com.estudos.segmento.SegmentoRequest;
import br.com.estudos.segmento.SegmentoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.SituacaoGabarito;

/**
 * Base dos testes da Sprint 24 (docs/SPRINT-24-PROVA-GABARITO.md §10): um contexto só, com o
 * token interno, para as quatro classes de teste da prova dividirem o mesmo pool.
 * Sem @Transactional, de propósito: o que se prova aqui é o que foi de fato commitado.
 */
abstract class ProvaTestBase extends IntegracaoTestBase {

    static final String TOKEN = "token-interno-da-prova";
    static final JsonMapper JSON = JsonMapper.builder().build();
    /** O volume `provas` dos testes: o SGE grava em entrada/, o worker em paginas/ (§2.5). */
    static final Path PROVAS;

    static {
        try {
            PROVAS = Files.createTempDirectory("provas");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void tokenInterno(DynamicPropertyRegistry registro) {
        registro.add("estudos.interno.token", () -> TOKEN);
        registro.add("estudos.provas.dir", PROVAS::toString);
    }

    @Autowired protected ProvaService provaService;
    @Autowired protected ProvaRepository provaRepository;
    @Autowired protected QuestaoRepository questaoRepository;
    @Autowired protected VereditoQuestaoRepository vereditoRepository;
    @Autowired protected AssuntoRepository assuntoRepository;
    @Autowired protected SegmentoService segmentoService;
    @Autowired protected JdbcTemplate jdbc;

    /** O concurso-alvo recém-importado, com três assuntos de chaves {@code p1-s}, {@code p2-s}, {@code p3-s}. */
    protected record Alvo(long concursoId, String s) {
        String chave(int n) { return "p" + n + "-" + s; }
    }

    protected Alvo alvo() throws Exception {
        var s = Long.toString(System.nanoTime());
        var csv = """
            disciplina,assunto,peso,ordem,chaveExterna
            Prova %1$s,Primeiro %1$s,ALTO,1,p1-%1$s
            Prova %1$s,Segundo %1$s,MEDIO,2,p2-%1$s
            Prova %1$s,Terceiro %1$s,BAIXO,3,p3-%1$s
            """.formatted(s);
        var json = "{\"slug\": \"prova-%1$s\", \"nome\": \"Prova %1$s\"}".formatted(s);
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar")
                .file(new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("concurso", "concurso.json", "application/json", json.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk());
        Integer id = JsonPath.read(mockMvc.perform(get("/api/concursos/ativo")).andReturn()
            .getResponse().getContentAsString(), "$.id");
        return new Alvo(id, s);
    }

    protected Prova prova(Alvo alvo, String gabaritoArquivo) {
        return provaService.registrar(alvo.concursoId(), "prova.pdf", UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", ""), "1", gabaritoArquivo);
    }

    /** A chave externa do assunto atribuído à questão (nulo se não há), lida do banco. */
    protected String chaveDoAssuntoDaQuestao(Long questaoId) {
        var chaves = jdbc.queryForList(
            "select a.chave_externa from questao q join assunto a on a.id = q.assunto_id where q.id = ?", String.class, questaoId);
        return chaves.isEmpty() ? null : chaves.get(0);
    }

    protected Long assuntoId(String chave) {
        return assuntoRepository.findByChaveExterna(chave).orElseThrow().getId();
    }

    /** Uma questão A–E lida, atribuída ao assunto {@code chave} (nulo = fora do edital). */
    protected static QuestaoLida questao(int numero, String chave) {
        return new QuestaoLida(numero, "Enunciado " + numero,
            null, null,
            List.of(new AlternativaLida("A", "a"), new AlternativaLida("B", "b"), new AlternativaLida("C", "c"),
                new AlternativaLida("D", "d"), new AlternativaLida("E", "e")),
            chave, false, false, null, null);
    }

    protected static GabaritoLido gabarito(SituacaoGabarito situacao, String... respostas) {
        var lista = new ArrayList<RespostaGabarito>();
        for (var i = 0; i < respostas.length; i++) {
            lista.add(new RespostaGabarito(i + 1, respostas[i], null));
        }
        return new GabaritoLido(situacao, "cargo X, tipo 1", lista);
    }

    protected static LeituraRequest leitura(List<QuestaoLida> questoes, GabaritoLido gabarito) {
        return new LeituraRequest("Prova lida", "Banca", (short) 2026, "1", "Cargo X",
            FormatoBanca.MULTIPLA_ESCOLHA, "modelo-teste", questoes, gabarito);
    }

    /** Uma prova pronta para o julgamento: lida com gabarito preliminar e em JULGANDO. */
    protected Prova provaJulgando(Alvo alvo, List<QuestaoLida> questoes, String... respostas) {
        var prova = prova(alvo, "gabarito.pdf");
        provaService.entregarLeitura(prova.getId(), leitura(questoes, gabarito(SituacaoGabarito.PRELIMINAR, respostas)));
        jdbc.update("update prova set status = 'JULGANDO' where id = ?", prova.getId());
        return provaRepository.findById(prova.getId()).orElseThrow();
    }

    protected Long segmento(Long assuntoId, int ordem, Integer inicio, Integer fim) {
        return segmentoService.criarOuAtualizar(new SegmentoRequest(
            assuntoId, "seg-" + UUID.randomUUID(), ordem, "/material/prova/" + ordem + ".pdf", inicio, fim, 30)).getId();
    }

    protected MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder pedido) {
        return pedido.header("X-Internal-Token", TOKEN);
    }

    protected String corpo(Object objeto) {
        return JSON.writeValueAsString(objeto);
    }
}
