package br.com.estudos.importacao;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.shared.IntegracaoTestBase;

/**
 * Importação CSV — tudo-ou-nada e validar nunca grava (`02_JORNADAS.md` §J-1
 * regras 1-3; `docs/SPRINT-2-CADASTRO.md` §5.1). Item 2.8 de PROGRESSO.md.
 *
 * Sem @Transactional (herdado de IntegracaoTestBase): o que está sendo
 * provado aqui é justamente o commit/rollback real de cada endpoint, então
 * cada teste usa nome de disciplina próprio, não uma transação que desfaz
 * tudo no final.
 */
class ImportacaoAssuntoTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaRepository disciplinaRepository;

    @Test
    void validar_resolveMasNaoGrava() throws Exception {
        var csv = """
            disciplina,assunto,peso,ordem
            Disciplina Importada Validar,Assunto Um,ALTO,1
            Disciplina Importada Validar,Assunto Dois,MEDIO,2
            """;

        mockMvc.perform(multipart("/api/assuntos/importacoes/validar").file(csv(csv)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disciplinasNovas").value(1))
            .andExpect(jsonPath("$.assuntosNovos").value(2))
            .andExpect(jsonPath("$.recusadas").isEmpty());

        assertTrue(disciplinaRepository.findByNomeIgnoreCase("Disciplina Importada Validar").isEmpty(),
            "validar não pode gravar nada no banco, mesmo resolvendo tudo sem erro");
    }

    @Test
    void confirmar_comLinhaRecusada_naoGravaNadaDoArquivo() throws Exception {
        var csv = """
            disciplina,assunto,peso,ordem
            Disciplina Importada ComErro,Assunto Valido,ALTO,1
            Disciplina Importada ComErro,Assunto Invalido,PESO_QUE_NAO_EXISTE,2
            """;

        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar").file(csv(csv)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recusadas").isNotEmpty());
        // assuntosNovos pode vir 1 aqui — a primeira linha resolveu de verdade DENTRO
        // da transação antes da segunda bater a validação. O que importa é que nada
        // disso sobrevive: recusadas não-vazio força rollback do arquivo inteiro.

        assertTrue(disciplinaRepository.findByNomeIgnoreCase("Disciplina Importada ComErro").isEmpty(),
            "arquivo com QUALQUER linha recusada não pode gravar nada — regra 1, tudo ou nada");
    }

    @Test
    void confirmar_semLinhaRecusada_grava() throws Exception {
        var csv = """
            disciplina,assunto,peso,ordem
            Disciplina Importada Confirma,Assunto Um,ALTO,1
            Disciplina Importada Confirma,Assunto Dois,MEDIO,2
            """;

        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar").file(csv(csv)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recusadas").isEmpty());

        assertTrue(disciplinaRepository.findByNomeIgnoreCase("Disciplina Importada Confirma").isPresent(),
            "confirmar sem linha recusada tem que gravar de verdade");
    }

    private MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }
}
