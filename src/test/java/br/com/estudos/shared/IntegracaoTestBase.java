package br.com.estudos.shared;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de service/controller da Sprint 2 (docs/SPRINT-2-CADASTRO.md
 * §8): "os testes chamam o service ou o controller, não INSERT cru" — troca o
 * JDBC puro de RestricaoTestBase (Sprint 1) por @SpringBootTest real, com o
 * mesmo Postgres real via Testcontainers (nunca H2, ADR-017).
 *
 * Sem @Testcontainers/@Container de propósito, mesmo padrão de
 * RestricaoTestBase: um contêiner por classe (o que @Container faria)
 * derrubava e recriava o Postgres entre as classes de teste desta suíte, e a
 * segunda classe caía num DataSource do Spring ainda apontando pra porta do
 * contêiner já morto ("singleton container pattern" do Testcontainers — sobe
 * uma vez no carregamento da classe, nunca para, o Ryuk limpa no fim da JVM).
 *
 * Sem @Transactional aqui de propósito: MockMvc despacha na mesma thread do
 * teste, então um @Transactional na classe faria o service JOIN a transação
 * do próprio teste (propagação REQUIRED) — e um teste que precisa provar que
 * algo foi (ou não foi) commitado de verdade (ex.: validar força rollback)
 * não consegue ver isso de dentro da mesma transação ainda aberta. Cada
 * subclasse decide: quem só confere HTTP/JSON pode anotar @Transactional
 * nela mesma para limpeza automática; quem precisa do commit real não usa.
 */
@Tag("integracao")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegracaoTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;
}
