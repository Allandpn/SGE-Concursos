package br.com.estudos.edital;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.enums.TipoPeso;

/** Estado de uma análise de edital (docs/SPRINT-18-CONCURSOS.md §2, §4). */
public record AnaliseEditalResponse(
    @Schema(description = "Identificador da análise") Long id,
    @Schema(description = "Nome do PDF enviado") String nomeArquivo,
    @Schema(description = "Etapa atual") StatusAnaliseEdital status,
    @Schema(description = "Com status FALHOU: a etapa que \"tentar de novo\" repete") StatusAnaliseEdital etapaFalha,
    @Schema(description = "Com status FALHOU: o motivo, para mostrar como veio") String erro,
    @Schema(description = "Modelo do OpenRouter usado") String modelo,
    @Schema(description = "Nome do concurso lido no edital") String concursoNome,
    @Schema(description = "Banca lida no edital") String banca,
    @Schema(description = "Data da prova, se o edital fixa") LocalDate dataProva,
    @Schema(description = "Cargos com conteúdo programático próprio (etapa 1)") List<String> cargos,
    @Schema(description = "Cargo escolhido pelo usuário") String cargoEscolhido,
    @Schema(description = "Com status PRONTA: a proposta para revisar (D-62)") List<Disciplina> proposta,
    @Schema(description = "Com status PRONTA: novos, em comum e fora do edital (derivado)") Resumo resumo,
    @Schema(description = "Com status CONFIRMADA: o concurso criado") Long concursoId,
    Instant criadoEm,
    Instant atualizadoEm
) {

    public record Disciplina(
        @Schema(description = "Nome da disciplina no edital") String nome,
        @Schema(description = "Questões da disciplina no quadro de provas; nulo se o edital não informa") Integer questoes,
        @Schema(description = "Peso calculado pelo servidor (D-61)") TipoPeso peso,
        @Schema(description = "false: o edital não tem quadro de questões para ela — peso MEDIO por padrão") boolean pesoDeterminado,
        @Schema(description = "Disciplina já existente que é a mesma, ou nulo") Long disciplinaExistenteId,
        String disciplinaExistenteNome,
        List<Assunto> assuntos) {}

    public record Assunto(
        @Schema(description = "Nome do assunto no edital") String nome,
        @Schema(description = "Assunto já existente que é o mesmo conteúdo (D-63), ou nulo = novo") Long assuntoExistenteId,
        String assuntoExistenteNome,
        @Schema(description = "Disciplina em que o assunto existente está hoje") String disciplinaAtualNome) {}

    public record Resumo(
        @Schema(description = "Assuntos que serão criados") int novos,
        @Schema(description = "Assuntos que já existem — mantêm escada e histórico") int emComum,
        @Schema(description = "Assuntos ativos que o edital não cita — continuam como estão") int foraDoEdital) {}
}
