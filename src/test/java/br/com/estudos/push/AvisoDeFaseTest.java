package br.com.estudos.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.estudos.foco.AvisoDeFase;
import br.com.estudos.foco.FaseFoco.Especie;
import br.com.estudos.foco.FocoService;
import br.com.estudos.foco.RelogioMutavel;
import br.com.estudos.shared.IntegracaoTestBase;

/**
 * D-104: o aviso de fim de fase só vai a quem pediu, uma vez por fase, e só quando a fase venceu (não quando a pessoa a encerrou).
 * O transporte é falso: nada sai para a rede.
 */
@Import(AvisoDeFaseTest.Config.class)
class AvisoDeFaseTest extends IntegracaoTestBase {

    record Envio(URI endpoint, byte[] corpo) {}

    static final class Falso implements TransportePush {
        final List<Envio> enviados = new CopyOnWriteArrayList<>();
        volatile int codigo = 201;

        @Override
        public int enviar(URI endpoint, Map<String, String> cabecalhos, byte[] corpo) {
            enviados.add(new Envio(endpoint, corpo));
            return codigo;
        }
    }

    static final Falso FALSO = new Falso();
    static final Instant T0 = Instant.parse("2026-10-30T15:00:00Z");
    static final RelogioMutavel RELOGIO = new RelogioMutavel(T0, ZoneId.of("America/Sao_Paulo"));

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TransportePush transporteFalso() {
            return FALSO;
        }

        @Bean
        @Primary
        Clock relogioMutavel() {
            return RELOGIO;
        }
    }

    @Autowired private FocoService foco;
    @Autowired private AvisoDeFase aviso;
    @Autowired private JdbcTemplate jdbc;

    private PushCriptoTest.Aparelho aparelho;
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/aparelho-fase";

    @BeforeEach
    void limpar() throws Exception {
        jdbc.update("DELETE FROM fase_foco");
        jdbc.update("DELETE FROM push_inscricao");
        FALSO.enviados.clear();
        FALSO.codigo = 201;
        RELOGIO.definir(T0);
        aparelho = PushCriptoTest.Aparelho.novo();
    }

    private void inscrever(boolean avisaFase) throws Exception {
        mockMvc.perform(post("/api/push/inscricoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}}""".formatted(ENDPOINT, aparelho.p256dh(), aparelho.autenticacao())))
            .andExpect(status().isNoContent());
        if (avisaFase) {
            mockMvc.perform(put("/api/push/inscricoes/fase").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\": \"%s\", \"avisa\": true}".formatted(ENDPOINT))).andExpect(status().isNoContent());
        }
    }

    private void esperar(int quantos) throws Exception {
        for (int i = 0; i < 100 && FALSO.enviados.size() < quantos; i++) Thread.sleep(50);
        assertEquals(quantos, FALSO.enviados.size());
    }

    private void garantirQueNaoEnviouMais(int quantos) throws Exception {
        Thread.sleep(300);
        assertEquals(quantos, FALSO.enviados.size());
    }

    @Test
    void d104_semInscricaoNaoEnvia_eAindaMarcaParaNaoReavisar() throws Exception {
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, null, null);
        RELOGIO.avancar(Duration.ofMinutes(25));
        aviso.varrer();
        garantirQueNaoEnviouMais(0);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM fase_foco WHERE aviso_fim_em IS NOT NULL", Integer.class));
    }

    @Test
    void d104_inscricaoSoDeProcesso_naoRecebeAvisoDeFase() throws Exception {
        inscrever(false);
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, null, null);
        RELOGIO.avancar(Duration.ofMinutes(25));
        aviso.varrer();
        garantirQueNaoEnviouMais(0);
    }

    @Test
    void d104_comAvisoLigado_enviaUmaVezAoVencer_comTextoQueSoDizOQueAconteceu() throws Exception {
        inscrever(true);
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, null, null);
        RELOGIO.avancar(Duration.ofMinutes(24));
        aviso.varrer();
        garantirQueNaoEnviouMais(0);
        RELOGIO.avancar(Duration.ofMinutes(1));
        aviso.varrer();
        esperar(1);
        aviso.varrer();
        garantirQueNaoEnviouMais(1);

        var aberto = PushCriptoTest.decifrarComoNavegador(FALSO.enviados.get(0).corpo(), aparelho.privada(), aparelho.publicaBruta(), aparelho.auth());
        var carga = new String(aberto, StandardCharsets.UTF_8);
        assertEquals("O foco terminou.", JsonPath.read(carga, "$.corpo"));
    }

    @Test
    void d104_encerradaPelaPessoaAntesDoFim_naoEnvia() throws Exception {
        inscrever(true);
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, null, null);
        RELOGIO.avancar(Duration.ofMinutes(10));
        foco.encerrar();
        RELOGIO.avancar(Duration.ofMinutes(30));
        aviso.varrer();
        garantirQueNaoEnviouMais(0);
    }

    @Test
    void d104_faseQueVenceuHaMuitoTempo_naoAvisaForaDeHora() throws Exception {
        inscrever(true);
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, null, null);
        RELOGIO.avancar(Duration.ofHours(3));
        aviso.varrer();
        garantirQueNaoEnviouMais(0);
    }

    @Test
    void d104_encerradaPelaConsultaDaTela_aindaAvisaNaVarredura() throws Exception {
        inscrever(true);
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, null, null);
        RELOGIO.avancar(Duration.ofMinutes(26));
        foco.atual();   // a tela chegou primeiro e encerrou a fase
        aviso.varrer();
        esperar(1);
    }

    @Test
    void d104_inscricaoRejeitada404_eRemovida() throws Exception {
        inscrever(true);
        FALSO.codigo = 404;
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, null, null);
        RELOGIO.avancar(Duration.ofMinutes(25));
        aviso.varrer();
        esperar(1);
        for (int i = 0; i < 40 && jdbc.queryForObject("SELECT count(*) FROM push_inscricao", Integer.class) > 0; i++) Thread.sleep(50);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM push_inscricao", Integer.class));
    }

    @Test
    void d104_avisoDeFaseEmAparelhoNaoInscrito_ehNaoEncontrado() throws Exception {
        mockMvc.perform(put("/api/push/inscricoes/fase").contentType(MediaType.APPLICATION_JSON)
            .content("{\"endpoint\": \"%s\", \"avisa\": true}".formatted(ENDPOINT))).andExpect(status().isNotFound());
    }
}
