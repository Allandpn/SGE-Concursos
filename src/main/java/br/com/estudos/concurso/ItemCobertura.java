package br.com.estudos.concurso;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.shared.enums.TipoPeso;

/** O que um edital diz de um assunto (01_DOMINIO §3.8): entrada da rotina de aplicar cobertura. */
public record ItemCobertura(Assunto assunto, Disciplina disciplina, TipoPeso peso, int ordem) {}
