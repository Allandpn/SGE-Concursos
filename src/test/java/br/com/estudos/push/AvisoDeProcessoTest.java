package br.com.estudos.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import br.com.estudos.arvore.ArvoreTest;
import br.com.estudos.conferencia.EntregaEditalTest;
import br.com.estudos.edital.AnaliseEdital;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.shared.ConferenciaTestBase;
import br.com.estudos.shared.enums.StatusAnaliseEdital;

/**
 * Os avisos de processo concluído (D-96, docs/SPRINT-27): sem inscrição não envia; com inscrição envia só nos dois eventos, uma
 * vez por rodada; o serviço de push que recusa a inscrição a remove. O transporte é falso: nada sai para a rede.
 */
@Import(AvisoDeProcessoTest.Config.class)
class AvisoDeProcessoTest extends ConferenciaTestBase {

    record Envio(URI endpoint, Map<String, String> cabecalhos, byte[] corpo) {}

    static final class Falso implements TransportePush {
        final List<Envio> enviados = new CopyOnWriteArrayList<>();
        volatile int codigo = 201;

        @Override
        public int enviar(URI endpoint, Map<String, String> cabecalhos, byte[] corpo) {
            enviados.add(new Envio(endpoint, cabecalhos, corpo));
            return codigo;
        }
    }

    static final Falso FALSO = new Falso();

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TransportePush transporteFalso() {
            return FALSO;
        }
    }

    @Autowired private AnaliseEditalRepository analises;
    @Autowired private AvisoDeProcesso avisos;

    private PushCriptoTest.Aparelho aparelho;

    @BeforeEach
    void limpar() throws Exception {
        jdbc.update("DELETE FROM push_inscricao");
        FALSO.enviados.clear();
        FALSO.codigo = 201;
        aparelho = PushCriptoTest.Aparelho.novo();
    }

    private void inscrever() throws Exception {
        mockMvc.perform(post("/api/push/inscricoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"endpoint": "https://fcm.googleapis.com/fcm/send/aparelho-1", "keys": {"p256dh": "%s", "auth": "%s"}}"""
            .formatted(aparelho.p256dh(), aparelho.autenticacao()))).andExpect(status().isNoContent());
    }

    private void esperar(int quantos) throws Exception {
        for (int i = 0; i < 100 && FALSO.enviados.size() < quantos; i++) Thread.sleep(50);
        assertEquals(quantos, FALSO.enviados.size());
    }

    private void garantirQueNaoEnviouMais(int quantos) throws Exception {
        Thread.sleep(400);
        assertEquals(quantos, FALSO.enviados.size());
    }

    private Map<String, String> abrir(Envio envio) throws Exception {
        var aberto = PushCriptoTest.decifrarComoNavegador(envio.corpo(), aparelho.privada(), aparelho.publicaBruta(), aparelho.auth());
        return JsonPath.read(new String(aberto, StandardCharsets.UTF_8), "$");
    }

    private long novaAnalise() throws Exception {
        var analise = new AnaliseEdital("edital.pdf", "texto", "modelo");
        analise.setConcursoNome("MPPR Aviso " + System.nanoTime());
        analise.setCargoEscolhido("Analista TI");
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        long id = analises.save(analise).getId();
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
        return id;
    }

    private void entregarLeitura(long id) throws Exception {
        entregarLeitura(id, 121);
    }

    /** O nó da primeira parte muda o corpo, e com ele o hash: a mesma entrega duas vezes é uma só (D-89). */
    private void entregarLeitura(long id, int no) throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", EntregaEditalTest.entrega("qconcursos-2026-10-19", no), id)).andExpect(status().isOk());
    }

    @Test
    void semInscricao_naoEnvia_eAMarcaFicaParaNaoRepetir() throws Exception {
        long id = novaAnalise();
        entregarLeitura(id);
        garantirQueNaoEnviouMais(0);
        assertTrue(jdbc.queryForObject("SELECT aviso_conferencia_em IS NOT NULL FROM analise_edital WHERE id = ?", Boolean.class, id));
    }

    @Test
    void conferenciaPronta_avisaUmaVez_eReprocessarAbreRodadaNova() throws Exception {
        inscrever();
        long id = novaAnalise();
        entregarLeitura(id);
        esperar(1);
        var aviso = abrir(FALSO.enviados.get(0));
        assertEquals("Edital lido", aviso.get("titulo"));
        assertTrue(aviso.get("corpo").endsWith(": pronto para conferir."), aviso.get("corpo"));
        assertEquals("/#/concursos/analise/" + id + "/conferencia", aviso.get("url"));
        assertTrue(FALSO.enviados.get(0).cabecalhos().get("Authorization").startsWith("vapid t="));
        assertEquals("aes128gcm", FALSO.enviados.get(0).cabecalhos().get("Content-Encoding"));

        avisos.conferenciaPronta(id);   // a mesma rodada: não repete
        garantirQueNaoEnviouMais(1);

        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/reprocessar", id)).andExpect(status().isOk());
        entregarLeitura(id, 122);
        esperar(2);
    }

    @Test
    void materialPronto_soQuandoNaoRestaNadaAPreparar_umaVezPorRodada() throws Exception {
        inscrever();
        long id = novaAnalise();
        entregarLeitura(id);
        esperar(1);
        for (long a : jdbc.queryForList("SELECT id FROM edital_assunto", Long.class)) {
            mockMvc.perform(post("/api/editais/analises/{id}/assuntos/{a}/aceitar", id, a)).andExpect(status().isOk());
        }
        for (long t : jdbc.queryForList("SELECT id FROM edital_trecho", Long.class)) {
            mockMvc.perform(post("/api/editais/analises/{id}/trechos/{t}/resolver", id, t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\": \"DESCARTAR\", \"motivo\": \"observação\"}")).andExpect(status().isOk());
        }
        long concursoId = ((Number) JsonPath.read(mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.concursoId")).longValue();
        var planejamento = mockMvc.perform(get("/api/concursos/{c}/planejamento", concursoId)).andReturn().getResponse().getContentAsString();
        List<String> externas = JsonPath.read(planejamento, "$.assuntos[*].chaveExterna");
        List<String> chaves = jdbc.queryForList("SELECT chave FROM edital_assunto ORDER BY id", String.class);
        assertEquals(3, externas.size());

        // o primeiro assunto entregue, os outros dois ainda a preparar: não avisa
        mockMvc.perform(internoPost("/internal/material/lacunas", "{\"chaveExterna\": \"" + externas.get(0) + "\", \"motivo\": \"o acervo não ensina\"}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega(chaves.get(0)), id)).andExpect(status().isOk());
        garantirQueNaoEnviouMais(1);

        for (int i = 1; i < 3; i++) {
            mockMvc.perform(internoPost("/internal/material/lacunas", "{\"chaveExterna\": \"" + externas.get(i) + "\", \"motivo\": \"o acervo não ensina\"}")).andExpect(status().is2xxSuccessful());
        }
        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega(chaves.get(1)), id)).andExpect(status().isOk());
        esperar(2);
        var aviso = abrir(FALSO.enviados.get(1));
        assertEquals("Material pronto", aviso.get("titulo"));
        assertEquals("/#/concursos", aviso.get("url"));

        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega(chaves.get(2)), id)).andExpect(status().isOk());
        garantirQueNaoEnviouMais(2);   // mesma rodada: não repete

        avisos.novaRodadaDoMaterial(concursoId);   // é o que "Refazer o material" faz
        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega(chaves.get(2)), id)).andExpect(status().isOk());
        esperar(3);
    }

    private String entrega(String chave) {
        return """
            {"assuntos": [{"chave": "%s", "situacao": "SEM_MATERIAL", "minutos": 0, "fontes": 0,
              "lacunas": [{"parte": "tudo", "motivo": "nenhum tema do acervo ensina esta parte"}]}]}""".formatted(chave);
    }

    @Test
    void servicoQueRecusaAInscricao_aRemove_eFalhaDeRedeNao() throws Exception {
        inscrever();
        FALSO.codigo = 500;
        mockMvc.perform(post("/api/push/teste")).andExpect(status().isOk())
            .andExpect(jsonPath("$.enviados").value(0)).andExpect(jsonPath("$.removidos").value(0)).andExpect(jsonPath("$.falhas").value(1));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM push_inscricao", Integer.class));

        FALSO.codigo = 410;
        mockMvc.perform(post("/api/push/teste")).andExpect(jsonPath("$.removidos").value(1));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM push_inscricao", Integer.class));
    }

    @Test
    void rotas_chaveEstavel_inscreverIdempotente_eRecusaEnderecoForaDaLista() throws Exception {
        var a = JsonPath.<String>read(mockMvc.perform(get("/api/push/chave")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.chavePublica");
        var b = JsonPath.<String>read(mockMvc.perform(get("/api/push/chave")).andReturn().getResponse().getContentAsString(), "$.chavePublica");
        assertEquals(a, b);
        assertEquals(65, PushCripto.deB64(a).length);

        inscrever();
        inscrever();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM push_inscricao", Integer.class));

        mockMvc.perform(post("/api/push/inscricoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"endpoint": "https://exemplo.com/x", "keys": {"p256dh": "%s", "auth": "%s"}}""".formatted(aparelho.p256dh(), aparelho.autenticacao())))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("ENDERECO_DE_PUSH_INVALIDO"));
        mockMvc.perform(post("/api/push/inscricoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"endpoint": "https://fcm.googleapis.com/fcm/send/x", "keys": {"p256dh": "curta", "auth": "curta"}}"""))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("CHAVES_DE_PUSH_INVALIDAS"));

        mockMvc.perform(delete("/api/push/inscricoes").contentType(MediaType.APPLICATION_JSON)
            .content("{\"endpoint\": \"https://fcm.googleapis.com/fcm/send/aparelho-1\"}")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/push/inscricoes").contentType(MediaType.APPLICATION_JSON)
            .content("{\"endpoint\": \"https://fcm.googleapis.com/fcm/send/aparelho-1\"}")).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM push_inscricao", Integer.class));
    }
}
