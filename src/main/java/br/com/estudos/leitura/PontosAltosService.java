package br.com.estudos.leitura;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;

/**
 * Os pontos altos da recuperação (D-172; docs/SPRINT-44-ICONES-E-PONTOS-ALTOS.md): as marcações vigentes da pessoa nos blocos
 * do assunto, para a tela mostrar só depois de conferir. Destaque leva a nota de trecho junto; nota de página vem sozinha.
 * Bloco arquivado fica de fora. Derivado (D-16): não grava nada.
 */
@Service
public class PontosAltosService {

    /** {@code tipo}: DESTAQUE ou NOTA_PAGINA. {@code cor}: só no destaque. {@code nota}: a nota do trecho ou o texto da nota de página. */
    public record PontoAlto(UUID chave, long segmentoId, int bloco, int pagina, String tipo, String cor, String citacao, String nota) {}

    private final JdbcTemplate jdbc;
    private final MarcacaoService marcacoes;

    public PontosAltosService(JdbcTemplate jdbc, MarcacaoService marcacoes) {
        this.jdbc = jdbc;
        this.marcacoes = marcacoes;
    }

    @Transactional(readOnly = true)
    public List<PontoAlto> doAssunto(long assuntoId) {
        if (jdbc.queryForList("SELECT 1 FROM assunto WHERE id = ?", Integer.class, assuntoId).isEmpty()) {
            throw new NotFoundException("Assunto não encontrado.", "ASSUNTO_INEXISTENTE");
        }
        var blocos = jdbc.query("SELECT id, ordem FROM segmento WHERE assunto_id = ? AND arquivado_em IS NULL ORDER BY ordem, id",
            (rs, n) -> new long[] {rs.getLong("id"), rs.getInt("ordem")}, assuntoId);
        var saida = new ArrayList<PontoAlto>();
        for (var b : blocos) {
            var vigentes = marcacoes.vigentes(b[0]);
            var notas = new HashMap<UUID, String>();
            vigentes.stream().filter(m -> m.tipo() == MarcacaoService.Tipo.NOTA_TRECHO).forEach(m -> notas.put(m.destaqueChave(), m.texto()));
            var doBloco = new ArrayList<MarcacaoService.Marcacao>();
            vigentes.stream().filter(m -> m.tipo() != MarcacaoService.Tipo.NOTA_TRECHO).forEach(doBloco::add);
            doBloco.sort(Comparator.comparingInt(MarcacaoService.Marcacao::pagina).thenComparingDouble(PontosAltosService::altura));
            for (var m : doBloco) {
                boolean destaque = m.tipo() == MarcacaoService.Tipo.DESTAQUE;
                saida.add(new PontoAlto(m.chave(), b[0], (int) b[1], m.pagina(), m.tipo().name(), destaque ? m.cor() : null,
                    destaque ? m.citacao() : null, destaque ? notas.get(m.chave()) : m.texto()));
            }
        }
        return saida;
    }

    /** A altura na página (fração): o topo do primeiro retângulo do destaque, ou o y da nota de página. */
    private static double altura(MarcacaoService.Marcacao m) {
        if (m.retangulos() != null && !m.retangulos().isEmpty() && m.retangulos().get(0).size() > 1) {
            return m.retangulos().get(0).get(1);
        }
        return m.posicaoY() == null ? 0 : m.posicaoY();
    }
}
