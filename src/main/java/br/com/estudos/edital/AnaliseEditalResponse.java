package br.com.estudos.edital;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import br.com.estudos.shared.enums.StatusAnaliseEdital;

/** Estado de uma análise de edital (docs/SPRINT-18-CONCURSOS.md §2, §4). */
public record AnaliseEditalResponse(
    @Schema(description = "Identificador da análise") Long id,
    @Schema(description = "Nome do PDF enviado") String nomeArquivo,
    @Schema(description = "Etapa atual") StatusAnaliseEdital status,
    @Schema(description = "Com status FALHOU: a etapa que falhou") String etapaFalha,
    @Schema(description = "Com status FALHOU: o motivo, para mostrar como veio") String erro,
    @Schema(description = "Modelo do OpenRouter usado") String modelo,
    @Schema(description = "Nome do concurso lido no edital") String concursoNome,
    @Schema(description = "Banca lida no edital") String banca,
    @Schema(description = "Data da prova, se o edital fixa") LocalDate dataProva,
    @Schema(description = "Cargos com conteúdo programático próprio (etapa 1)") List<String> cargos,
    @Schema(description = "Cargo escolhido pelo usuário") String cargoEscolhido,
    @Schema(description = "Com status CONFIRMADA: o concurso criado") Long concursoId,
    Instant criadoEm,
    Instant atualizadoEm
) {
}
