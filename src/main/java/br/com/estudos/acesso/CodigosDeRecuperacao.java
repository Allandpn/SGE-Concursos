package br.com.estudos.acesso;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** D-110: 8 códigos de uso único, mostrados uma vez e guardados só como resumo; gerar novos invalida os antigos. */
@Component
public class CodigosDeRecuperacao {

    static final int QUANTOS = 8;

    private final JdbcTemplate jdbc;

    public CodigosDeRecuperacao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Chamado dentro da transação de quem pede: invalida os que restavam e devolve os 8 novos, que só existem aqui. */
    List<String> gerar(OffsetDateTime agora) {
        jdbc.update("UPDATE codigo_recuperacao SET invalidado_em = ? WHERE usado_em IS NULL AND invalidado_em IS NULL", agora);
        var codigos = new ArrayList<String>(QUANTOS);
        while (codigos.size() < QUANTOS) {
            var codigo = Segredo.codigoDeRecuperacao();
            if (!codigos.contains(codigo)) {
                codigos.add(codigo);
                jdbc.update("INSERT INTO codigo_recuperacao (resumo, criado_em) VALUES (?, ?)",
                    Segredo.resumo(Segredo.normalizarCodigo(codigo)), agora);
            }
        }
        return codigos;
    }

    /** Consome o código, se valer. Um código vale uma vez. */
    boolean usar(String codigo, OffsetDateTime agora) {
        var normalizado = Segredo.normalizarCodigo(codigo);
        if (normalizado.length() != 12) {
            return false;
        }
        return jdbc.update("UPDATE codigo_recuperacao SET usado_em = ? WHERE resumo = ? AND usado_em IS NULL AND invalidado_em IS NULL",
            agora, Segredo.resumo(normalizado)) == 1;
    }

    int restantes() {
        return jdbc.queryForObject("SELECT count(*) FROM codigo_recuperacao WHERE usado_em IS NULL AND invalidado_em IS NULL", Integer.class);
    }
}
