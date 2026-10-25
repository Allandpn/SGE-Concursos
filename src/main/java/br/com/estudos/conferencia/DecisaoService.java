package br.com.estudos.conferencia;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.arvore.ArvoreService;
import br.com.estudos.conferencia.ConferenciaPedidos.EditarDecisao;
import br.com.estudos.conferencia.ConferenciaResponses.DecisaoResponse;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * A lista de decisões da pessoa: ver, editar e revogar (01_DOMINIO §3.12, D-86, D-87). Revogar só marca a decisão como
 * inativa: nada é apagado (D-18), e a ligação já aplicada nas partes não muda.
 */
@Service
public class DecisaoService {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ArvoreService arvore;

    public DecisaoService(JdbcTemplate jdbc, JsonMapper json, ArvoreService arvore) {
        this.jdbc = jdbc;
        this.json = json;
        this.arvore = arvore;
    }

    @Transactional(readOnly = true)
    public List<DecisaoResponse> listar(boolean soAtivas) {
        var linhas = jdbc.queryForList("""
            SELECT d.id, d.parte_texto, d.contexto_texto, d.nos, d.natureza, d.escopo, d.analise_id, d.arvore_versao_id,
                   v.rotulo, d.ativa, d.criada_em, d.revogada_em, d.comentario
              FROM edital_decisao d JOIN arvore_versao v ON v.id = d.arvore_versao_id
             WHERE (? = false OR d.ativa) ORDER BY d.id DESC""", soAtivas);
        var saida = new ArrayList<DecisaoResponse>();
        for (var l : linhas) saida.add(resposta(l));
        return saida;
    }

    @Transactional(readOnly = true)
    public DecisaoResponse buscar(long id) {
        var linhas = jdbc.queryForList("""
            SELECT d.id, d.parte_texto, d.contexto_texto, d.nos, d.natureza, d.escopo, d.analise_id, d.arvore_versao_id,
                   v.rotulo, d.ativa, d.criada_em, d.revogada_em, d.comentario
              FROM edital_decisao d JOIN arvore_versao v ON v.id = d.arvore_versao_id WHERE d.id = ?""", id);
        if (linhas.isEmpty()) throw new NotFoundException("Decisão não encontrada.", "DECISAO_INEXISTENTE");
        return resposta(linhas.get(0));
    }

    @Transactional
    public DecisaoResponse editar(long id, EditarDecisao pedido) {
        var atual = buscar(id);
        if (!atual.ativa()) throw new ValidationException("A decisão foi revogada; crie outra pela conferência.", "DECISAO_REVOGADA", "id");
        var nos = pedido.nos() == null ? new ArrayList<Long>() : new ArrayList<>(new LinkedHashSet<>(pedido.nos()));
        long versao = jdbc.queryForObject("SELECT arvore_versao_id FROM edital_decisao WHERE id = ?", Long.class, id);
        if (nos.size() > 3) throw new ValidationException("Uma decisão liga a até 3 nós.", "NOS_INVALIDOS", "nos");
        if (!nos.isEmpty() && !List.of("EXATO", "MAIS_AMPLO", "PARCIAL").contains(pedido.natureza())) {
            throw new ValidationException("A natureza do vínculo é EXATO, MAIS_AMPLO ou PARCIAL.", "NATUREZA_INVALIDA", "natureza");
        }
        for (var no : nos) {
            if (no < 0 || !arvore.existe(no, versao)) throw new ValidationException("O nó " + no + " não serve nesta árvore.", "NO_INEXISTENTE", "nos");
        }
        jdbc.update("UPDATE edital_decisao SET nos = ?, natureza = ?, comentario = ? WHERE id = ?",
            json.writeValueAsString(nos), nos.isEmpty() ? null : pedido.natureza(), pedido.comentario(), id);
        jdbc.update("INSERT INTO edital_evento (analise_id, parte_id, tipo, antes, depois) VALUES (?, NULL, 'DECISAO_EDITADA', ?, ?)",
            atual.analiseId(), json.writeValueAsString(java.util.Map.of("decisao", id)), json.writeValueAsString(java.util.Map.of("nos", nos)));
        return buscar(id);
    }

    @Transactional
    public void revogar(long id) {
        var atual = buscar(id);
        jdbc.update("UPDATE edital_decisao SET ativa = false, revogada_em = now() WHERE id = ? AND ativa", id);
        jdbc.update("INSERT INTO edital_evento (analise_id, parte_id, tipo, antes, depois) VALUES (?, NULL, 'DECISAO_REVOGADA', ?, NULL)",
            atual.analiseId(), json.writeValueAsString(java.util.Map.of("decisao", id)));
    }

    private DecisaoResponse resposta(java.util.Map<String, Object> l) {
        var ids = new ArrayList<Long>();
        json.readTree((String) l.get("nos")).forEach(x -> ids.add(x.asLong()));
        long versao = ((Number) l.get("arvore_versao_id")).longValue();
        var resolvidos = arvore.nosPorId(ids, versao);
        var nos = new ArrayList<br.com.estudos.arvore.ArvoreResponses.No>();
        for (var id : ids) nos.add(resolvidos.getOrDefault(id, new br.com.estudos.arvore.ArvoreResponses.No(id, null, List.of(), 0, 0, null)));
        var contexto = (String) l.get("contexto_texto");
        return new DecisaoResponse(((Number) l.get("id")).longValue(), (String) l.get("parte_texto"), contexto == null || contexto.isEmpty() ? null : contexto,
            nos, (String) l.get("natureza"), (String) l.get("escopo"), ((Number) l.get("analise_id")).longValue(), (String) l.get("rotulo"),
            (Boolean) l.get("ativa"), String.valueOf(((java.sql.Timestamp) l.get("criada_em")).toInstant()),
            l.get("revogada_em") == null ? null : String.valueOf(((java.sql.Timestamp) l.get("revogada_em")).toInstant()), (String) l.get("comentario"));
    }
}
