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

    @Test
    void d177_cadaMarcaTrazOsParametrosQueSaemDoEndereco() {
        var r = new LinkQConcursos.Recortes(List.of(3, 1), List.of(2, 4), "DIFICEIS", true, true, true, true,
            List.of(new LinkQConcursos.Aprendido("INSTITUICAO", 29, "CGU")));
        var m = LinkQConcursos.montar(new LinkQConcursos.Filtro(List.of(1L), List.of(), 2, "CERTO_ERRADO", 2025, 2026, 5), r, "CESPE");
        for (var marca : m.marcas()) {
            for (var p : marca.parametros()) assertThat(m.url()).contains(p);
        }
        assertThat(m.marcas()).extracting(LinkQConcursos.Marca::chave)
            .containsExactly("banca", "formato", "anos", "nivel", "dificuldade", "ordem", "comentadas", "INSTITUICAO:29");
        assertThat(m.marcas()).extracting(LinkQConcursos.Marca::rotulo)
            .containsExactly("CESPE", "Certo/errado", "2025–2026", "Fundamental e Superior", "Fácil, Difícil", "Mais difíceis",
                "Com gabarito comentado", "CGU");
        assertThat(m.url()).contains("sort=hardest").contains("institute_ids%5B%5D=29").contains("scholarity_ids%5B%5D=1&scholarity_ids%5B%5D=3");
    }

    @Test
    void d176_osAprendidosDoEndereco_semRepetir_eIgnorandoOResto() {
        assertThat(LinkQConcursos.aprendidos("https://www.qconcursos.com/questoes-de-concursos/questoes?job_ids[]=43&job_ids%5B%5D=43"
            + "&institute_ids%5B%5D=29&examining_board_ids%5B%5D=2&knowledge_area_ids%5B%5D=x"))
            .containsExactly(new LinkQConcursos.Aprendido("CARGO", 43, null), new LinkQConcursos.Aprendido("INSTITUICAO", 29, null));
        assertThat(LinkQConcursos.aprendidos("não é endereço")).isEmpty();
        assertThat(LinkQConcursos.aprendidos(null)).isEmpty();
    }
}
