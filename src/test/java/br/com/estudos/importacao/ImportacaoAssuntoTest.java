package br.com.estudos.importacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

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

    @Autowired
    private AssuntoRepository assuntoRepository;

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

    private void confirmarSemRecusa(String conteudo) throws Exception {
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar").file(csv(conteudo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recusadas").isEmpty());
    }

    @Test
    void reimportarPorChaveExterna_atualizaEReordena() throws Exception {
        confirmarSemRecusa("""
            disciplina,assunto,peso,ordem,chaveExterna
            Disciplina Chave Externa,Assunto A,ALTO,1,uuid-teste-a
            Disciplina Chave Externa,Assunto B,MEDIO,2,uuid-teste-b
            """);

        // Mesmo arquivo de outro edital: sem id, peso e ordem trocados (1<->2),
        // e um nome corrigido — 02_JORNADAS J-1 v1.6.0.
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar").file(csv("""
            disciplina,assunto,peso,ordem,chaveExterna
            Disciplina Chave Externa,Assunto A,BAIXO,2,uuid-teste-a
            Disciplina Chave Externa,Assunto B Corrigido,ALTO,1,uuid-teste-b
            """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recusadas").isEmpty())
            .andExpect(jsonPath("$.assuntosNovos").value(0))
            .andExpect(jsonPath("$.assuntosAtualizados").value(2));

        var a = assuntoRepository.findByChaveExterna("uuid-teste-a").orElseThrow();
        var b = assuntoRepository.findByChaveExterna("uuid-teste-b").orElseThrow();
        assertEquals(TipoPeso.BAIXO, a.getPeso());
        assertEquals(2, a.getOrdem());
        assertEquals("Assunto B Corrigido", b.getNome());
        assertEquals(1, b.getOrdem());
    }

    private MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }

    /** J-1 v1.9.0: assunto sem chave (criado pela análise de edital) é adotado pela linha de mesmo nome. */
    @Test
    void confirmar_adotaAssuntoSemChaveDeMesmoNome() throws Exception {
        var semChave = """
            disciplina,assunto,peso,ordem
            Disciplina Adocao,Normalização de Dados,MEDIO,1
            """;
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar").file(csv(semChave)))
            .andExpect(jsonPath("$.assuntosNovos").value(1));

        var doPlanejamento = """
            disciplina,assunto,peso,ordem,chaveExterna
            Disciplina Adocao,normalizacao de dados,ALTO,1,uuid-adocao-1
            """;
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar").file(csv(doPlanejamento)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recusadas").isEmpty())
            .andExpect(jsonPath("$.assuntosNovos").value(0))
            .andExpect(jsonPath("$.assuntosAtualizados").value(1));

        var adotado = assuntoRepository.findByChaveExterna("uuid-adocao-1").orElseThrow();
        assertEquals(TipoPeso.ALTO, adotado.getPeso());
        assertEquals(1, assuntoRepository.findByDisciplinaIdAndAtivoTrue(adotado.getDisciplina().getId()).size());
    }
}
