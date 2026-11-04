package br.com.estudos.qconcursos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** D-150 a D-152 (docs/SPRINT-36-QCONCURSOS.md §4). */
class LinkQConcursosTest {

    @Test
    void d150_oEnderecoComAssuntoBancaAnosModalidadeEPagina() {
        var url = LinkQConcursos.montar(new LinkQConcursos.Filtro(List.of(26L), List.of(28150L), 63, "MULTIPLA_ESCOLHA", 2021, 2026, 10));
        assertThat(url).isEqualTo(LinkQConcursos.BASE + "?discipline_ids%5B%5D=26&examining_board_ids%5B%5D=63"
            + "&exclude_nullified=true&exclude_outdated=true&modality_ids%5B%5D=1&my_questions=not_resolved&per_page=10"
            + "&publication_year%5B%5D=2021&publication_year%5B%5D=2022&publication_year%5B%5D=2023&publication_year%5B%5D=2024"
            + "&publication_year%5B%5D=2025&publication_year%5B%5D=2026&subject_ids%5B%5D=28150");
    }

    @Test
    void d150_semBancaNemFormato_eDisciplinasRepetidasUmaVez() {
        var url = LinkQConcursos.montar(new LinkQConcursos.Filtro(List.of(1L, 1L, 2L), List.of(), null, null, null, null, 20));
        assertThat(url).contains("discipline_ids%5B%5D=1&discipline_ids%5B%5D=2&exclude_nullified")
            .doesNotContain("examining_board").doesNotContain("modality").doesNotContain("publication_year").doesNotContain("subject_ids");
        assertThat(LinkQConcursos.montar(new LinkQConcursos.Filtro(List.of(1L), List.of(), null, "CERTO_ERRADO", null, null, 5)))
            .contains("modality_ids%5B%5D=2");
    }

    @Test
    void d150_d152_porPaginaESugeridas() {
        assertThat(LinkQConcursos.porPagina(3)).isEqualTo(5);
        assertThat(LinkQConcursos.porPagina(8)).isEqualTo(10);
        assertThat(LinkQConcursos.porPagina(15)).isEqualTo(15);
        assertThat(LinkQConcursos.porPagina(40)).isEqualTo(20);
        assertThat(LinkQConcursos.sugeridas(25, 3, 5)).isEqualTo(8);
        assertThat(LinkQConcursos.sugeridas(10, 3, 5)).isEqualTo(5);
        assertThat(LinkQConcursos.sugeridas(25, 2.5, 5)).isEqualTo(10);
    }

    @Test
    void d151_oNumeroDaBancaDeUmEndereco() {
        assertThat(LinkQConcursos.numeroDaBanca(
            "https://www.qconcursos.com/questoes-de-concursos/questoes?discipline_ids%5B%5D=26&examining_board_ids%5B%5D=63&per_page=20"))
            .contains(63);
        assertThat(LinkQConcursos.numeroDaBanca("https://www.qconcursos.com/questoes-de-concursos/questoes?examining_board_ids[]=379&page=405"))
            .contains(379);
        assertThat(LinkQConcursos.numeroDaBanca(" 207 ")).contains(207);
        assertThat(LinkQConcursos.numeroDaBanca("https://www.qconcursos.com/questoes-de-concursos/questoes?discipline_ids%5B%5D=26")).isEmpty();
        assertThat(LinkQConcursos.numeroDaBanca(
            "https://www.qconcursos.com/questoes-de-concursos/questoes?examining_board_ids%5B%5D=2&examining_board_ids%5B%5D=63")).isEmpty();
        assertThat(LinkQConcursos.numeroDaBanca("0")).isEmpty();
        assertThat(LinkQConcursos.numeroDaBanca("FGV")).isEmpty();
        assertThat(LinkQConcursos.numeroDaBanca(null)).isEmpty();
    }
}
