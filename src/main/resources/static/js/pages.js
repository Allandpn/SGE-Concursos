// js/pages.js — um componente Alpine por página (09_CODE_STYLE §8).

// Rótulo amigável por chave de parâmetro (04_FRONTEND §7E) — a tela
// Ajustes nunca expõe a chave crua ao usuário. Chave nova na tabela
// `parametro` sem entrada aqui cai no fallback (a própria chave) em vez de
// quebrar — ver paginaAjustes.rotulo().
const ROTULOS_PARAMETRO = {
  teto_diario_recuperacoes: 'Recuperações por dia',
  teto_global_frente: 'Assuntos na frente (global)',
  teto_disciplina_frente: 'Assuntos na frente (por disciplina)',
  limiar_sucesso_multipla_escolha: 'Sucesso · múltipla escolha',
  limiar_parcial_multipla_escolha: 'Parcial · múltipla escolha',
  limiar_sucesso_certo_errado: 'Sucesso · certo/errado',
  limiar_parcial_certo_errado: 'Parcial · certo/errado',
  limiar_sucesso_flashcards: 'Sucesso · flashcards',
  limiar_parcial_flashcards: 'Parcial · flashcards',
  intervalo_nivel_1: 'Intervalo nível 1',
  intervalo_nivel_2: 'Intervalo nível 2',
  intervalo_nivel_3: 'Intervalo nível 3',
  intervalo_nivel_4: 'Intervalo nível 4',
  intervalo_nivel_5: 'Intervalo nível 5',
  intervalo_nivel_6: 'Intervalo nível 6',
  intervalo_manutencao_dias: 'Manutenção (consolidado)',
  janela_tolerancia_percentual: 'Janela de tolerância',
  lote_minimo_questoes: 'Lote mínimo de questões',
  duracao_bloco_min: 'Duração sugerida do bloco (min)',
  pomodoro_foco_min: 'Foco (min)',
  pomodoro_pausa_curta_min: 'Pausa curta (min)',
  pomodoro_pausa_longa_min: 'Pausa longa (min)',
  pomodoro_focos_ate_longa: 'Focos até a pausa longa',
  discursiva_limiar_sucesso: 'Sucesso · discursiva (nota)',
  discursiva_limiar_parcial: 'Parcial · discursiva (nota)',
  reta_final_semanas_aviso: 'Sugerir a reta final (semanas antes da prova)',
  banca_anos: 'Provas da banca dos últimos (anos)',
  banca_provas_minimas: 'Mínimo de provas por disciplina',
  banca_questoes_minimas: 'Mínimo de questões por disciplina',
  minutos_por_questao: 'Minutos por questão',
  questoes_por_vez: 'Questões por vez (QConcursos)',
};

// Preferências do Pomodoro que são do aparelho, não do domínio (docs/SPRINT-29-POMODORO.md §6): ficam no navegador.
function preferenciaFoco(chave, padrao) {
  try {
    const v = window.localStorage.getItem('foco.' + chave);
    return v === null ? padrao : v === '1';
  } catch (e) {
    return padrao;
  }
}
function guardarPreferenciaFoco(chave, valor) {
  try { window.localStorage.setItem('foco.' + chave, valor ? '1' : '0'); } catch (e) { /* sem armazenamento, segue com o padrão */ }
}
function nomesDeAssuntoEmFoco() {
  try { return JSON.parse(window.localStorage.getItem('foco.nomes') || '{}'); } catch (e) { return {}; }
}
function lembrarNomeDeAssunto(id, nome) {
  if (!id || !nome) return;
  try {
    const nomes = nomesDeAssuntoEmFoco();
    nomes[id] = nome;
    window.localStorage.setItem('foco.nomes', JSON.stringify(nomes));
  } catch (e) { /* o nome é só conveniência da barra */ }
}
function relogioFoco(segundos) {
  const s = Math.max(0, Math.floor(segundos));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

// Dá destino a uma lacuna do material (POR_FORA, IGNORADA ou null para desfazer); devolve o material do assunto atualizado.
function destinoDaLacuna(assuntoId, parte, decisao) {
  return api(`/assuntos/${assuntoId}/lacunas`, { method: 'PUT', body: JSON.stringify({ parte, decisao }) });
}

// Anexa o PDF a uma parte sem material (D-97); devolve o material do assunto atualizado.
function anexarPdfALacuna(assuntoId, parte, arquivo) {
  const corpo = new FormData();
  corpo.append('parte', parte);
  corpo.append('arquivo', arquivo);
  return api(`/assuntos/${assuntoId}/lacunas/anexo`, { method: 'POST', body: corpo });
}

async function tirarAnexoDaLacuna(assuntoId, parte) {
  await api(`/assuntos/${assuntoId}/lacunas/anexo?parte=${encodeURIComponent(parte)}`, { method: 'DELETE' });
  return api(`/assuntos/${assuntoId}/material-da-conferencia`);
}

const temAnexoPreparando = (lacunas) => (lacunas ?? []).some((l) => l.anexo && l.anexo.estado === 'PENDENTE');

// "pronto: 3 segmentos · 3 h", "preparando…" ou "falhou: motivo" (D-97).
function textoAnexo(l) {
  const a = l.anexo;
  if (!a) return '';
  if (a.estado === 'PRONTO') {
    const min = a.minutos ?? 0;
    const tempo = min < 60 ? `${min} min` : (min % 60 ? `${Math.floor(min / 60)} h ${min % 60} min` : `${Math.floor(min / 60)} h`);
    return `PDF anexado (${a.nome}): ${a.segmentos} ${a.segmentos === 1 ? 'segmento' : 'segmentos'} · ${tempo}`;
  }
  if (a.estado === 'FALHOU') return `Não deu para preparar o PDF ${a.nome}: ${a.erro ?? 'erro desconhecido'}`;
  return `PDF anexado (${a.nome}, ${a.paginas} págs.): preparando, leva alguns minutos…`;
}

function textoLacuna(l) {
  return l.motivo && !l.decisao ? `${l.parte}: ${l.motivo}` : l.parte;
}

document.addEventListener('alpine:init', () => {

  // Pomodoro (01_DOMINIO §3.15, D-98 a D-105; ADR-050; docs/SPRINT-29-POMODORO.md §6). O relógio é do servidor: aqui só se desenha o
  // ponteiro a partir do último estado recebido, e se confere de novo ao abrir, ao voltar à aba e a cada 30 s. Auxiliar: se algo
  // falhar, o resto do app segue.
  Alpine.store('foco', {
    fase: null,          // { fase, decorridoS, restanteS, pausada } como o servidor devolveu
    recebidoEm: Date.now(),
    agora: Date.now(),
    proposta: null,      // D-102: o que vem depois, só proposto; nada começa sozinho
    ultimoFoco: null,    // { assuntoId, segmentoId, nome } do último foco visto, para "mais um foco"
    erro: null,
    trabalhando: false,
    avisouVencimento: false,

    init() {
      this.sincronizar();
      setInterval(() => this.tocar(), 1000);
      setInterval(() => this.sincronizar(), 30000);
      document.addEventListener('visibilitychange', () => { if (!document.hidden) this.sincronizar(); });
    },

    get emCurso() { return !!this.fase; },
    get ehPausa() { return !!this.fase && this.fase.fase.especie !== 'FOCO'; },
    get pausada() { return !!this.fase && this.fase.pausada; },

    get restante() {
      if (!this.fase) return 0;
      if (this.fase.pausada) return this.fase.restanteS;
      return Math.max(0, this.fase.restanteS - Math.floor((this.agora - this.recebidoEm) / 1000));
    },
    get rotulo() { return relogioFoco(this.restante); },

    get descricao() {
      if (!this.fase) return '';
      const e = this.fase.fase.especie;
      if (e === 'PAUSA_CURTA') return 'Pausa curta';
      if (e === 'PAUSA_LONGA') return 'Pausa longa';
      const nome = nomesDeAssuntoEmFoco()[this.fase.fase.assuntoId];
      return (this.pausada ? 'Foco pausado' : 'Foco') + (nome ? ' · ' + nome : '');
    },

    get textoProposta() {
      const p = this.proposta;
      if (!p) return '';
      const min = Math.round(p.duracaoS / 60);
      if (p.especie === 'FOCO') return `Iniciar foco · ${min} min`;
      return `Iniciar ${p.especie === 'PAUSA_LONGA' ? 'pausa longa' : 'pausa curta'} · ${min} min`;
    },

    receber(situacao) {
      const mudou = !situacao || !this.fase || situacao.fase.id !== this.fase.fase.id;
      this.fase = situacao;
      this.recebidoEm = Date.now();
      this.agora = this.recebidoEm;
      if (mudou) this.avisouVencimento = false;
      if (situacao && situacao.fase.especie === 'FOCO') {
        const nome = nomesDeAssuntoEmFoco()[situacao.fase.assuntoId] ?? null;
        this.ultimoFoco = { assuntoId: situacao.fase.assuntoId, segmentoId: situacao.fase.segmentoId, nome };
      }
    },

    async sincronizar() {
      const tinha = !!this.fase;
      try {
        this.receber(await api('/foco/atual'));
        if (tinha && !this.fase) await this.carregarProposta();   // a fase terminou (aqui ou em outro aparelho)
        if (this.fase) this.proposta = null;
      } catch (e) {
        // sem rede o ponteiro segue do último estado; a próxima conferência acerta
      }
    },

    tocar() {
      this.agora = Date.now();
      if (this.fase && !this.fase.pausada && this.restante === 0 && !this.avisouVencimento) {
        this.avisouVencimento = true;
        this.sinalizar();
        this.sincronizar();
      }
    },

    // D-104: com o app aberto, o fim de fase sempre dá sinal na tela; som e vibração são escolha do aparelho.
    sinalizar() {
      if (preferenciaFoco('vibracao', true) && navigator.vibrate) navigator.vibrate([200, 100, 200]);
      if (!preferenciaFoco('som', true)) return;
      try {
        const Contexto = window.AudioContext || window.webkitAudioContext;
        const ctx = new Contexto();
        const osc = ctx.createOscillator();
        osc.frequency.value = 880;
        osc.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + 0.4);
      } catch (e) { /* sem áudio, fica o sinal na tela */ }
    },

    async carregarProposta() {
      try {
        this.proposta = await api('/foco/proposta');
      } catch (e) {
        this.proposta = null;
      }
    },

    async agir(fazer) {
      this.trabalhando = true;
      this.erro = null;
      try {
        await fazer();
      } catch (e) {
        this.erro = e.message || 'Não deu certo. Tente de novo.';
        await this.sincronizar();
      } finally {
        this.trabalhando = false;
      }
    },

    // D-98: uma fase por vez; o servidor recusa a segunda com 409, e a barra mostra a que está em curso.
    iniciar(especie, assuntoId = null, segmentoId = null, nome = null) {
      return this.agir(async () => {
        lembrarNomeDeAssunto(assuntoId, nome);
        this.proposta = null;
        await api('/foco/iniciar', { method: 'POST', body: JSON.stringify({ tentativaId: crypto.randomUUID(), especie, assuntoId, segmentoId }) });
        await this.sincronizar();
      });
    },

    iniciarProposta() {
      const p = this.proposta;
      if (!p) return Promise.resolve();
      const u = this.ultimoFoco;
      return p.especie === 'FOCO'
        ? this.iniciar('FOCO', p.assuntoId ?? u?.assuntoId ?? null, p.segmentoId ?? u?.segmentoId ?? null, u?.nome ?? null)
        : this.iniciar(p.especie);
    },

    maisUmFoco() {
      const u = this.ultimoFoco;
      return this.iniciar('FOCO', u?.assuntoId ?? null, u?.segmentoId ?? null, u?.nome ?? null);
    },

    pausar() {
      return this.agir(async () => { this.receber(await api('/foco/atual/pausar', { method: 'POST' })); });
    },
    retomar() {
      return this.agir(async () => { this.receber(await api('/foco/atual/retomar', { method: 'POST' })); });
    },
    encerrar() {
      return this.agir(async () => {
        await api('/foco/atual/encerrar', { method: 'POST' });
        this.fase = null;
        await this.carregarProposta();
      });
    },

    descartarProposta() { this.proposta = null; },
  });

  // IA em uso e o aviso de assinatura parada (ADR-045, docs/SPRINT-19-MATERIAL-AUTOMATICO.md
  // §3A). Um store só para Hoje, Concursos e Ajustes mostrarem o mesmo aviso. Auxiliar:
  // falhar aqui nunca esconde a tela.
  Alpine.store('ia', {
    config: null,
    erro: null,
    trocando: false,

    async carregar() {
      try {
        this.config = await api('/ia/configuracao');
      } catch (e) {
        this.config = null;
      }
    },

    get assinaturaParou() {
      return !!(this.config && this.config.provedor === 'CLAUDE' && this.config.claude.falha);
    },

    get podeUsarOpenRouter() {
      return !!(this.config && this.config.openrouter.configurado);
    },

    async escolher(provedor) {
      this.erro = null;
      this.trocando = true;
      try {
        this.config = await api('/ia/configuracao', { method: 'PUT', body: JSON.stringify({ provedor }) });
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trocando = false;
      }
    },
  });

  // Handoff Hoje → Recuperar (04_FRONTEND.md §6, "decisão de código, não de
  // arquitetura"): a tela Recuperar não busca nível/data prevista de novo —
  // usa o que a fila do turno já trouxe. Store Alpine global, não parâmetro
  // na URL, porque o dado inteiro (FilaRecuperacaoItemResponse) precisa
  // sobreviver à navegação sem virar query string.
  Alpine.store('recuperacao', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  // Mesmo handoff, pro card "Conteúdo novo" de Hoje → Registrar (04_FRONTEND
  // §7A) — carrega o AssuntoResponse, não o FilaRecuperacaoItemResponse do
  // store acima (formas diferentes, stores separados).
  Alpine.store('registrar', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  // Mesmo handoff, pra lista de Assuntos → detalhe (04_FRONTEND §7B). Não
  // existe GET /api/assuntos/{id} — a lista já monta assunto+disciplina+fase
  // pra desenhar a própria linha, então passa isso adiante em vez de o
  // detalhe buscar de novo. Consequência aceita, mesma de Recuperar/
  // Registrar: abrir a URL direto sem passar pela lista cai em semDados.
  Alpine.store('assuntoDetalhe', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  // Mesmo handoff, pra Erros (04_FRONTEND §7C) — escrito por Recuperar
  // (etapa 2) e pelo detalhe de Assuntos, os dois pontos de entrada
  // contextuais desta sprint. Só { assuntoId, nome } — sessaoId fica de
  // fora nesta sprint (ver §7C do doc técnico pra entender por quê).
  Alpine.store('erro', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  Alpine.data('paginaHoje', () => ({
    carregando: true,
    erro: null,
    fila: [],
    reestudos: [],
    blocoConteudo: null,
    seloEdital: null, // §9 da Sprint 25: parte do edital sem filtro de questões, no assunto em foco
    blocoContinuacao: false,
    segmentoSugerido: null,
    guardados: new Set(), // D-119: blocos já no aparelho (selo "sem rede ✓")
    reestudoBloco: {}, // assuntoId → o primeiro bloco ativo, para o "Ler" do reestudo (J-10)
    alertaRepresamento: false,
    diasSemEstudar: null,
    represado: 0,
    estimativaDiasParaNormalizar: 0,
    vencidasAlemDoTeto: 0,
    proximoBloco: null,
    antecipaveis: [],
    // D-157: os próximos 7 dias (consulta) e se o bloco fica aberto no celular (preferência do aparelho).
    proximos: null,
    feitasHoje: 0, // §8B: sessões registradas hoje (o progresso do dia)
    proximosAberto: proximosAbertoNoAparelho(),
    mostrarAntecipaveis: false,
    // "Drenar mais" (02_JORNADAS §4.3): cada pedido soma um teto diário à fila,
    // só nesta tela; voltar para Hoje recomeça do teto normal (D-40).
    extraRecuperacoes: 0,
    passoDrenar: 0,
    // 02_JORNADAS §4.4: o concurso-alvo no topo. Falhar aqui não esconde o plano.
    concurso: null,
    concursoCarregado: false,

    async init() {
      this.extraRecuperacoes = 0;
      this.mostrarAntecipaveis = false;
      this.carregarConcurso();
      await this.carregar();
    },

    // O material do concurso-alvo (01_DOMINIO §3.10): contagem enquanto prepara; o aviso de pronto some quando a pessoa o dispensa.
    get materialEmPreparo() {
      const m = this.concurso?.material;
      if (!m || !m.aPreparar) return null;
      return { feitos: m.prontos + m.semMaterial, total: m.prontos + m.semMaterial + m.aPreparar };
    },
    get materialPronto() {
      const m = this.concurso?.material;
      if (!m || m.aPreparar || !(m.prontos + m.semMaterial) || this.materialVisto) return null;
      return m;
    },
    materialVisto: false,
    chaveMaterialVisto() { return `materialVisto:${this.concurso?.id}`; },
    lerMaterialVisto() {
      try { this.materialVisto = localStorage.getItem(this.chaveMaterialVisto()) === '1'; } catch (e) { this.materialVisto = false; }
    },
    dispensarMaterialPronto() {
      this.materialVisto = true;
      try { localStorage.setItem(this.chaveMaterialVisto(), '1'); } catch (e) { /* só uma conveniência deste aparelho */ }
    },

    carregarConcurso() {
      api('/concursos/ativo')
        .then((c) => { this.concurso = c; this.concursoCarregado = true; this.lerMaterialVisto(); })
        .catch(() => { this.concursoCarregado = false; });
    },

    // Os dias vêm do servidor (derivado lá, 02_JORNADAS §4.4).
    get textoProva() {
      const dias = this.concurso?.diasParaProva;
      if (dias === null || dias === undefined) return 'sem data de prova';
      if (dias === 0) return 'prova hoje';
      if (dias < 0) return 'a prova já passou';
      return dias === 1 ? 'prova amanhã' : `prova em ${dias} dias`;
    },

    // §8B: a próxima ação, pela ordem do plano: corrigir (reestudo) antes de recuperar, recuperar antes de abrir (01_DOMINIO §7.4).
    get proximaAcao() {
      if (this.reestudos.length) return { tipo: 'reestudo', item: this.reestudos[0] };
      if (this.fila.length) return { tipo: 'recuperacao', item: this.fila[0] };
      if (this.blocoConteudo) return { tipo: 'conteudo' };
      if (this.simuladoDaSemana) return { tipo: 'simulado' };
      return null;
    },

    // §8I (Sprint 46): os avisos numa faixa só, na ordem de importância; o primeiro aparece com a faixa fechada.
    avisosAbertos: false,

    get avisoIa() {
      return !!(Alpine.store('ia').assinaturaParou && this.concurso?.material?.aPreparar > 0);
    },

    get avisos() {
      const l = [];
      if (this.avisoIa) l.push('A assinatura do Claude parou');
      if (this.alertaRepresamento && !this.mostrarRetorno) l.push('Revisões acumuladas');
      if (this.mostrarRetorno) l.push(`Você esteve fora ${this.diasSemEstudar} dias`);
      if (this.retaFinal) l.push(`Reta final · ${this.retaFinal.dias} ${this.retaFinal.dias === 1 ? 'dia' : 'dias'}`);
      else if (this.sugestaoReta) l.push(`Faltam ${this.sugestaoReta.semanas} ${this.sugestaoReta.semanas === 1 ? 'semana' : 'semanas'} para a prova`);
      return l;
    },

    // §8I: quantos itens o Depois tem (a nota da manutenção que espera a prova não conta).
    get totalDepois() {
      return this.reestudosDepois.length + this.filaDepois.length
        + (this.blocoConteudo && this.proximaAcao?.tipo !== 'conteudo' ? 1 : 0)
        + (this.simuladoDaSemana && this.proximaAcao?.tipo !== 'simulado' ? 1 : 0);
    },

    get filaDepois() {
      return this.proximaAcao?.tipo === 'recuperacao' ? this.fila.slice(1) : this.fila;
    },

    get reestudosDepois() {
      return this.proximaAcao?.tipo === 'reestudo' ? this.reestudos.slice(1) : this.reestudos;
    },

    get totalDoDia() {
      return this.feitasHoje + this.fila.length + this.reestudos.length + (this.blocoConteudo ? 1 : 0) + (this.simuladoDaSemana ? 1 : 0);
    },

    textoFaltaHoje() {
      const partes = [];
      if (this.reestudos.length) partes.push(this.reestudos.length + (this.reestudos.length === 1 ? ' reestudo' : ' reestudos'));
      if (this.fila.length) partes.push(this.fila.length + (this.fila.length === 1 ? ' recuperação' : ' recuperações'));
      if (this.blocoConteudo) {
        const min = this.segmentoSugerido?.tempoEstimadoMin;
        partes.push(min ? `1 bloco de ${min} min` : '1 bloco de leitura');
      }
      if (this.simuladoDaSemana) partes.push('o simulado');
      return partes.length ? 'falta: ' + partes.join(' · ') : 'tudo feito';
    },

    tituloProximaAcao() {
      const p = this.proximaAcao;
      if (!p) return '';
      if (p.tipo === 'reestudo') return 'Reestudar ' + p.item.nome;
      if (p.tipo === 'recuperacao') return 'Recuperar ' + p.item.nome;
      if (p.tipo === 'conteudo') return (this.blocoContinuacao ? 'Continuar ' : 'Ler ') + this.blocoConteudo.nome;
      return 'Simulado da semana';
    },

    detalheProximaAcao() {
      const p = this.proximaAcao;
      if (!p) return '';
      if (p.tipo === 'reestudo') return 'Não veio ' + dataHumana(p.item.dataFalha) + ' · nível ' + p.item.nivel;
      if (p.tipo === 'recuperacao') return 'Nível ' + p.item.nivel + (p.item.dataPrevista ? ' · prevista para ' + dataHumana(p.item.dataPrevista) + atrasoHumano(p.item.dataPrevista) : '');
      if (p.tipo === 'conteudo') return this.segmentoSugerido ? this.descricaoSegmento : (this.blocoContinuacao ? 'Continuar a leitura' : 'Assunto novo');
      return 'Nenhum simulado desde segunda';
    },

    // §8G: o bloco que o botão principal abre no leitor (para o ícone e o sinal de baixado), ou null.
    blocoDaProximaAcao() {
      const p = this.proximaAcao;
      if (!p) return null;
      if (p.tipo === 'reestudo') return this.reestudoBloco[p.item.assuntoId] || null;
      if (p.tipo === 'conteudo' && this.segmentoSugerido && this.materialLocal(this.segmentoSugerido)) return this.segmentoSugerido.id;
      return null;
    },

    botaoProximaAcao() {
      const p = this.proximaAcao;
      if (!p) return '';
      if (p.tipo === 'reestudo') return this.reestudoBloco[p.item.assuntoId] ? 'Ler o material' : 'Reestudar';
      if (p.tipo === 'recuperacao') return 'Começar a recuperação';
      if (p.tipo === 'conteudo') return this.segmentoSugerido && this.materialLocal(this.segmentoSugerido) ? 'Ler o bloco' : 'Registrar a leitura';
      return 'Registrar o simulado';
    },

    fazerProximaAcao() {
      const p = this.proximaAcao;
      if (!p) return;
      if (p.tipo === 'reestudo') {
        if (this.reestudoBloco[p.item.assuntoId]) window.location.hash = '#/ler/' + this.reestudoBloco[p.item.assuntoId];
        else this.abrirReestudo(p.item);
      } else if (p.tipo === 'recuperacao') {
        this.abrirRecuperacao(p.item);
      } else if (p.tipo === 'conteudo') {
        if (this.segmentoSugerido && this.materialLocal(this.segmentoSugerido)) window.location.hash = '#/ler/' + this.segmentoSugerido.id;
        else this.abrirBlocoConteudo();
      } else {
        window.location.hash = '#/registrar/simulado';
      }
    },

    alternarProximos() {
      this.proximosAberto = !this.proximosAberto;
      guardarProximosAberto(this.proximosAberto);
    },

    resumoProximos() {
      if (!this.proximos) return '';
      const total = this.proximos.dias.reduce((s, d) => s + d.total, 0);
      const acima = this.proximos.dias.filter((d) => d.acimaDoTeto).length;
      return `${total} ${total === 1 ? 'revisão' : 'revisões'}` + (acima ? ` · ${acima} ${acima === 1 ? 'dia' : 'dias'} acima do teto` : '');
    },

    async drenarMais() {
      this.extraRecuperacoes += this.passoDrenar;
      await this.carregar();
    },

    async carregar() {
      this.carregando = true;
      this.erro = null;
      Alpine.store('ia').carregar(); // aviso de assinatura parada (ADR-045), sem esperar
      try {
        const plano = await api(`/turno/plano?extraRecuperacoes=${this.extraRecuperacoes}`);
        this.fila = plano.filaRecuperacao;
        this.reestudos = plano.reestudos;
        this.blocoConteudo = plano.blocoConteudo;
        this.carregarSelo();
        this.prepararLeitura();
        this.blocoContinuacao = plano.blocoContinuacao;
        this.segmentoSugerido = plano.segmentoSugerido;
        this.alertaRepresamento = plano.alertaRepresamento;
        this.diasSemEstudar = plano.diasSemEstudar;
        this.represado = plano.represado;
        this.estimativaDiasParaNormalizar = plano.estimativaDiasParaNormalizar;
        this.vencidasAlemDoTeto = plano.vencidasAlemDoTeto;
        this.proximoBloco = plano.proximoBloco;
        this.antecipaveis = plano.antecipaveis;
        this.feitasHoje = plano.feitasHoje || 0;
        // D-150 a D-154: os atalhos do QConcursos da fila e das antecipáveis, em lote (auxiliar)
        Alpine.store('qc').esquecer();
        api('/turno/proximos-dias').then((p) => { this.proximos = p; }).catch(() => { this.proximos = null; });
        Alpine.store('qc').carregar([...this.fila, ...this.antecipaveis].map((i) => i.assuntoId));
        // D-140 a D-144: a reta final ativa, o que ela escondeu e o simulado da semana; sem ela, a sugestão de entrar.
        this.retaFinal = plano.retaFinal;
        this.manutencoesEsperando = plano.manutencoesEsperando;
        this.simuladoDaSemana = plano.simuladoDaSemana;
        api('/reta-final').then((r) => { this.sugestaoReta = r && r.sugestao; }).catch(() => { this.sugestaoReta = null; });
        if (this.extraRecuperacoes === 0) this.passoDrenar = this.fila.length; // a fila sem pedido = o teto diário
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    retaFinal: null,
    manutencoesEsperando: 0,
    simuladoDaSemana: false,
    sugestaoReta: null,
    retaEntrada: null,
    erroReta: null,

    // D-140: só entra por gesto da pessoa.
    async entrarReta() {
      this.erroReta = null;
      try {
        this.retaEntrada = await api(`/concursos/${this.sugestaoReta.concursoId}/reta-final`, { method: 'POST' });
        this.sugestaoReta = null;
        await this.carregar();
      } catch (e) {
        this.erroReta = e.message;
      }
    },

    async agoraNaoReta() {
      const id = this.sugestaoReta.concursoId;
      this.sugestaoReta = null;
      await api(`/concursos/${id}/reta-final/dispensar`, { method: 'POST' }).catch(() => {});
    },

    textoRetaEntrada() {
      const e = this.retaEntrada;
      if (!e) return '';
      if (!e.antecipadas) return 'Reta final ligada. Nenhuma manutenção de peso alto precisou ser antecipada.';
      return e.antecipadas + (e.antecipadas === 1 ? ' revisão de peso alto distribuída' : ' revisões de peso alto distribuídas')
        + ' até ' + e.ate.split('-').reverse().join('/') + '.';
    },

    abrirRegistroDeQuestoes(assunto) {
      Alpine.store('registrar').selecionar(assunto);
      window.location.hash = `#/registrar/questoes/${assunto.id}`;
    },

    materialLocal(s) {
      return !!(s && s.arquivo && s.arquivo.startsWith('/material/'));
    },

    // J-10 e D-119: acha o bloco de cada reestudo e guarda no aparelho os blocos do plano de hoje. Auxiliar: falhar não
    // atrapalha a tela, e sem rede só mostra o que já está guardado.
    async prepararLeitura() {
      this.guardados = await offline.guardados();
      const blocos = {};
      for (const r of this.reestudos) {
        try {
          const segmentos = await api(`/assuntos/${r.assuntoId}/segmentos`);
          const primeiro = segmentos.find((x) => this.materialLocal(x));
          if (primeiro) blocos[r.assuntoId] = primeiro.id;
        } catch (e) {
          // sem rede: o "Ler" do reestudo só aparece com rede ou com o bloco já guardado
        }
      }
      this.reestudoBloco = blocos;
      const deHoje = [this.materialLocal(this.segmentoSugerido) ? this.segmentoSugerido.id : null, ...Object.values(blocos)];
      await offline.guardarDeHoje(deHoje);
      this.guardados = await offline.guardados();
    },

    // Aviso discreto, nunca bloqueia: o assunto em foco tem parte sem filtro de questões ou com vínculo largo.
    async carregarSelo() {
      this.seloEdital = null;
      const id = this.blocoConteudo?.id;
      if (!id) return;
      try {
        const partes = await api(`/assuntos/${id}/partes-do-edital`);
        const semFiltro = partes.filter((p) => p.aviso === 'SEM_NO' || p.aviso === 'NO_MUDOU').length;
        const largo = partes.filter((p) => p.aviso === 'MAIS_AMPLO' || p.aviso === 'PARCIAL').length;
        if (!semFiltro && !largo) return;
        const texto = [];
        if (semFiltro) texto.push(semFiltro === 1 ? '1 parte sem filtro de questões' : `${semFiltro} partes sem filtro de questões`);
        if (largo) texto.push(largo === 1 ? '1 vínculo largo' : `${largo} vínculos largos`);
        this.seloEdital = { texto: texto.join(' · '), analiseId: partes[0].analiseId };
      } catch (e) {
        this.seloEdital = null; // auxiliar: sem o selo, a tela segue
      }
    },

    get vazio() {
      return this.fila.length === 0 && this.reestudos.length === 0 && !this.blocoConteudo;
    },

    // J-4: dois dias ou mais sem nenhuma sessão — limiar desta implementação
    // (docs/SPRINT-17-USO-REAL.md §6.3). Some sozinha no primeiro registro do dia.
    get mostrarRetorno() {
      return this.diasSemEstudar !== null && this.diasSemEstudar >= 2;
    },

    get temAdiantar() {
      return this.vencidasAlemDoTeto > 0 || !!this.proximoBloco || this.antecipaveis.length > 0;
    },

    get descricaoProximoBloco() {
      return descreverSegmento(this.proximoBloco);
    },

    abrirOutroBloco() {
      this.abrirRegistroConteudo({ ...this.blocoConteudo, segmentoSugeridoId: this.proximoBloco.id });
    },

    abrirRecuperacao(item) {
      Alpine.store('recuperacao').selecionar(item);
      window.location.hash = `#/recuperar/${item.assuntoId}`;
    },

    abrirRegistroConteudo(assunto) {
      Alpine.store('registrar').selecionar(assunto);
      window.location.hash = `#/registrar/conteudo/${assunto.id}`;
    },

    // D-55: o card leva o segmento sugerido pra Registrar já vir com ele marcado.
    abrirBlocoConteudo() {
      this.abrirRegistroConteudo({ ...this.blocoConteudo, segmentoSugeridoId: this.segmentoSugerido?.id ?? null });
    },

    get descricaoSegmento() {
      return descreverSegmento(this.segmentoSugerido);
    },

    get linkMaterial() {
      const arquivo = this.segmentoSugerido?.arquivo;
      // Link do Drive (legado) ou PDF servido pelo próprio SGE (ADR-044).
      return arquivo && (/^https?:\/\//.test(arquivo) || arquivo.startsWith('/material/')) ? arquivo : null;
    },

    // D-07/D-11: reestudo é uma sessão ESTUDO do mesmo assunto — mesma tela
    // de Registrar conteúdo (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §4).
    abrirReestudo(item) {
      this.abrirRegistroConteudo({ id: item.assuntoId, nome: item.nome });
    },
  }));

  Alpine.data('paginaRecuperar', () => ({
    etapa: 1,
    assuntoId: null,
    nome: null,
    nivel: null,
    dataPrevista: null,
    previsao: null,
    resultado: null,
    tentativaId: null,
    inicioEm: null,
    fasesIds: [], // D-101: focos do Pomodoro que esta recuperação consome
    enviando: false,
    erro: null,
    semDados: false,
    // Perguntas de recuperação (01_DOMINIO §3.18): a etapa 1 só tem os enunciados; o gabarito chega na etapa 2 (D-125).
    perguntas: [],
    respostas: {}, // perguntaId → texto digitado; fica só na tela, nunca vai ao servidor (D-125)
    gabarito: {}, // perguntaId → pontos
    vieram: {}, // pontoId → true
    ruins: {}, // perguntaId → true (D-127)
    faltaram: [], // depois de gravar PARCIAL: os pontos que faltaram (D-128)
    pontosAltos: [], // D-172: as marcações do assunto, pedidas só ao conferir
    // D-159: em ensaio, "o que aconteceria" no lugar de gravar.
    ensaio: false,
    ensaioTexto: '',
    // §8B: Prever (1) · Reconstruir (2) · Conferir (3) · o que faltou (4); a escolha do resultado vai para o botão fixo.
    escolha: null,
    resultados: [
      { valor: 'SUCESSO', rotulo: 'Reconstruí', ancora: 'os pontos principais vieram; faltou detalhe, não estrutura' },
      { valor: 'PARCIAL', rotulo: 'Faltou um pedaço', ancora: 'a estrutura veio, mas um bloco inteiro não' },
      { valor: 'FALHA', rotulo: 'Não veio', ancora: 'preciso reler antes de tentar de novo' },
    ],

    get etapaVisivel() {
      return Math.min(this.etapa, 3);
    },

    get escolhaEfetiva() {
      return this.escolha || this.sugestao;
    },

    rotuloResultado(v) {
      return { SUCESSO: 'reconstruí', PARCIAL: 'faltou um pedaço', FALHA: 'não veio' }[v] || '';
    },

    rotuloPrevisao() {
      return { SUCESSO: 'vou reconstruir', PARCIAL: 'vai faltar um pedaço', FALHA: 'não vai vir' }[this.previsao] || '';
    },

    textoPrevista() {
      const partes = [];
      if (this.nivel) partes.push('Nível ' + this.nivel);
      if (this.dataPrevista) partes.push('prevista para ' + dataHumana(this.dataPrevista) + atrasoHumano(this.dataPrevista));
      return partes.join(' · ');
    },

    // §8B: sair no meio pergunta antes; a recuperação não é gravada.
    sair() {
      if (this.etapa >= 2 && this.etapa <= 3 && !this.ensaio && !window.confirm('Sair sem registrar esta recuperação?')) return;
      window.location.hash = '#/hoje';
    },

    // Chamado pelo router a cada navegação para #/recuperar/{id} — inclusive
    // quando confirmar() volta pra cá depois de uma falha de envio (§5.1).
    // Reabrir o MESMO assunto não pode resetar o que já foi respondido, ou
    // "reenviar sem perder o que foi digitado" quebra.
    abrir(assuntoId) {
      const emEnsaio = Alpine.store('ensaio').para(assuntoId);
      if (this.assuntoId === assuntoId && this.tentativaId && this.ensaio === emEnsaio) return;
      this.ensaio = emEnsaio;
      this.ensaioTexto = '';

      const item = Alpine.store('recuperacao').item;
      this.semDados = !item || item.assuntoId !== assuntoId;

      this.assuntoId = assuntoId;
      this.nome = this.semDados ? null : item.nome;
      this.nivel = this.semDados ? null : item.nivel;
      this.dataPrevista = this.semDados ? null : item.dataPrevista;
      this.etapa = 1;
      this.escolha = null;
      this.previsao = null;
      this.resultado = null;
      this.erro = null;
      this.tentativaId = crypto.randomUUID(); // D-45: gerado ao abrir a tela, não ao enviar
      this.inicioEm = Date.now();
      // D-101: o foco que correu ao lado é consumido por esta recuperação, sem tocar o tempo de tela.
      this.fasesIds = [];
      if (!this.ensaio) api(`/foco/livres?assuntoId=${assuntoId}`).then((l) => { this.fasesIds = l.fasesIds; }).catch(() => {});
      this.perguntas = [];
      this.respostas = {};
      this.gabarito = {};
      this.vieram = {};
      this.ruins = {};
      this.faltaram = [];
      this.pontosAltos = [];
      // D-124: sem pergunta (ou sem rede para buscá-las), a tela é a recuperação livre de sempre.
      api(`/assuntos/${assuntoId}/perguntas`).then((ps) => { if (this.etapa <= 2) this.perguntas = ps; }).catch(() => {});
    },

    prever(valor) {
      this.previsao = valor;
      this.etapa = 2;
    },

    conferir() {
      this.etapa = 3;
      // D-172: as marcações do assunto só agora, depois da tentativa; sem rede, a seção não aparece.
      const assuntoDaVez = this.assuntoId;
      api(`/assuntos/${assuntoDaVez}/pontos-altos`)
        .then((ps) => { if (this.assuntoId === assuntoDaVez && this.etapa >= 3) this.pontosAltos = ps; })
        .catch(() => { this.pontosAltos = []; });
      if (this.perguntas.length) {
        // D-125: o gabarito só existe a partir daqui.
        api(`/perguntas/gabarito?ids=${this.perguntas.map((p) => p.id).join(',')}`)
          .then((gs) => { this.gabarito = Object.fromEntries(gs.map((g) => [g.perguntaId, g.pontos])); })
          .catch(() => { this.gabarito = {}; });
      }
    },

    corDoPonto(m) {
      if (m.tipo === 'NOTA_PAGINA') return 'rgb(var(--c-gray-500))';
      // no escuro, a cor do leitor fica apagada no fundo; um tom mais claro da mesma cor
      const escuro = getComputedStyle(document.documentElement).colorScheme === 'dark';
      if (escuro) return { IMPORTANTE: '#f2c94c', DUVIDA: '#f2a59a', DECORAR: '#7fd18f' }[m.cor] || '#f2c94c';
      return (CORES[m.cor] || CORES.IMPORTANTE).borda;
    },

    rotuloDoPonto(m) {
      return m.tipo === 'NOTA_PAGINA' ? 'Nota da página' : { IMPORTANTE: 'Importante', DUVIDA: 'Dúvida', DECORAR: 'Decorar' }[m.cor] || 'Destaque';
    },

    get blocosDosPontos() {
      return new Set(this.pontosAltos.map((m) => m.segmentoId)).size;
    },

    tipoDaPergunta(tipo) {
      return { EVOCACAO: 'evocação', COMPLETAR: 'completar', PORQUE: 'porquê', COMPARACAO: 'comparação' }[tipo] || '';
    },

    alternarPonto(id) {
      this.vieram = { ...this.vieram, [id]: !this.vieram[id] };
    },

    async alternarRuim(p) {
      if (this.ensaio) return; // D-159
      const ruim = !this.ruins[p.id];
      this.ruins = { ...this.ruins, [p.id]: ruim };
      try {
        await api(`/perguntas/${p.id}/ruim`, { method: ruim ? 'POST' : 'DELETE' });
      } catch (e) {
        this.ruins = { ...this.ruins, [p.id]: !ruim };
      }
    },

    // Os pontos das perguntas que contam (não tiradas como ruins).
    get pontosQueContam() {
      return this.perguntas.filter((p) => !this.ruins[p.id]).flatMap((p) => this.gabarito[p.id] || []);
    },

    // D-126: a sugestão pelos pontos principais (todos → SUCESSO; metade ou mais → PARCIAL; menos → FALHA). Só sugere.
    get sugestao() {
      const principais = this.pontosQueContam.filter((pt) => pt.principal);
      if (!principais.length) return null;
      const vieram = principais.filter((pt) => this.vieram[pt.id]).length;
      if (vieram === principais.length) return 'SUCESSO';
      return vieram * 2 >= principais.length ? 'PARCIAL' : 'FALHA';
    },

    get textoSugestao() {
      const principais = this.pontosQueContam.filter((pt) => pt.principal);
      return `Sugestão pelos pontos principais: ${principais.filter((pt) => this.vieram[pt.id]).length} de ${principais.length} vieram`;
    },

    async confirmar(valor) {
      this.resultado = valor;
      this.erro = null;
      this.enviando = true;

      // D-29: tempo é medido, nunca constante — aqui é o tempo de tela
      // (abrir → confirmar), já que a Recuperar não tem campo próprio de
      // duração no wireframe (02_JORNADAS §4.1).
      const tempoMinutos = Math.max(1, Math.round((Date.now() - this.inicioEm) / 60000));

      const temGabarito = this.pontosQueContam.length > 0;
      const conferencia = temGabarito ? {
        perguntasIds: this.perguntas.filter((p) => !this.ruins[p.id]).map((p) => p.id),
        pontosQueVieram: this.pontosQueContam.filter((pt) => this.vieram[pt.id]).map((pt) => pt.id),
      } : undefined;
      // D-159: o ensaio pergunta ao registro real, que desfaz tudo (ADR-059); fica nesta tela.
      if (this.ensaio) {
        try {
          const e = await api('/sessoes/ensaio', { method: 'POST', body: JSON.stringify({
            assuntoId: this.assuntoId, tipo: 'RECUPERACAO', data: Alpine.store('ensaio').data || hojeISO(), tempoMinutos, previsaoReconstrucao: this.previsao,
            resultado: this.resultado, tentativaId: crypto.randomUUID(), conferencia }) });
          this.ensaioTexto = textoDoEnsaio(e, Alpine.store('ensaio').data);
        } catch (err) {
          this.erro = err.message;
        } finally {
          this.enviando = false;
        }
        return;
      }
      // D-128: depois de PARCIAL, o que faltou aparece aqui, já gravado; FALHA leva os pontos ao reestudo de Hoje.
      this.faltaram = valor === 'PARCIAL' ? this.pontosQueContam.filter((pt) => !this.vieram[pt.id]) : [];

      // Otimista (02_JORNADAS §5.1): volta pra Hoje antes da resposta confirmar (ou mostra o que faltou).
      if (this.faltaram.length) this.etapa = 4;
      else window.location.hash = '#/hoje';

      try {
        await api('/sessoes', {
          method: 'POST',
          body: JSON.stringify({
            assuntoId: this.assuntoId,
            tipo: 'RECUPERACAO',
            data: hojeISO(),
            tempoMinutos,
            previsaoReconstrucao: this.previsao,
            resultado: this.resultado,
            tentativaId: this.tentativaId,
            fasesIds: this.fasesIds.length ? this.fasesIds : undefined,
            conferencia,
          }),
        });
        window.dispatchEvent(new CustomEvent('sge:sessao-registrada')); // §8B: Hoje recarrega o progresso do dia
        // D-45: a tentativa gravada acabou; abrir esta tela de novo (mesmo assunto) começa outra, com o formulário limpo.
        this.tentativaId = null;
      } catch (e) {
        // Falha não destrutiva: volta pra cá com previsão/resultado intactos.
        // Reenviar usa o MESMO tentativaId (D-45) — nunca duplica, mesmo que
        // a primeira tentativa tenha gravado e só a resposta tenha se perdido.
        this.erro = e.message;
        window.location.hash = `#/recuperar/${this.assuntoId}`;
      } finally {
        this.enviando = false;
      }
    },

    reenviar() {
      this.confirmar(this.resultado);
    },

    // Link "+ registrar um erro" da etapa 2 (04_FRONTEND §7C) — aparece
    // ANTES de confirmar(), então não existe sessaoId nenhum ainda; leva
    // só o assunto de contexto, mesmo mecanismo de store de Hoje→Registrar.
    abrirErro() {
      Alpine.store('erro').selecionar({ assuntoId: this.assuntoId, nome: this.nome });
      window.location.hash = `#/erros/${this.assuntoId}`;
    },
  }));

  // D-148: o registro do simulado (J-16). Sem assunto; as linhas vêm da régua do alvo (as disciplinas do edital).
  Alpine.data('paginaSimulado', () => ({
    data: null,
    duracao: 240,
    formato: 'MULTIPLA_ESCOLHA',
    linhas: [],
    disciplinasAtivas: [],
    outra: '',
    semAlvo: false,
    enviando: false,
    erro: null,
    resultado: null, // depois de registrar: { acertos, total, pontos }
    tentou: false, // o aviso das linhas só aparece depois da primeira tentativa, e some quando elas ficam certas

    get avisoDasLinhas() {
      return this.tentou && this.linhas.some((l) => this.erroDaLinha(l)) ? 'Confira as linhas marcadas.' : '';
    },

    async abrir() {
      this.data = hojeISO();
      this.duracao = 240;
      this.formato = 'MULTIPLA_ESCOLHA';
      this.erro = null;
      this.tentou = false;
      this.resultado = null;
      this.outra = '';
      this.linhas = [];
      const alvo = await api('/concursos/ativo').catch(() => null);
      this.semAlvo = !alvo || !alvo.id;
      const [regua, disciplinas] = await Promise.all([
        alvo && alvo.id ? api(`/concursos/${alvo.id}/regua`).catch(() => null) : null,
        api('/disciplinas').catch(() => []),
      ]);
      this.disciplinasAtivas = disciplinas.filter((d) => d.ativo !== false);
      this.linhas = (regua ? regua.disciplinas : []).map((d) => this.novaLinha(d.disciplinaId, d.nome));
    },

    novaLinha(disciplinaId, nome) {
      return { disciplinaId, nome, certas: '', total: '', brancos: '' };
    },

    get foraDoEdital() {
      const ja = new Set(this.linhas.map((l) => l.disciplinaId));
      return this.disciplinasAtivas.filter((d) => !ja.has(d.id));
    },

    acrescentar() {
      const d = this.disciplinasAtivas.find((x) => String(x.id) === String(this.outra));
      if (d) this.linhas.push(this.novaLinha(d.id, d.nome));
      this.outra = '';
    },

    // D-148: certas + em branco não passam do total — avisado na linha, recusado também no servidor e no banco.
    erroDaLinha(l) {
      if (l.total === '' || l.total === null) return '';
      const t = Number(l.total), c = Number(l.certas || 0), b = Number(l.brancos || 0);
      if (t <= 0) return 'total precisa ser maior que zero';
      if (c > t) return 'certas passam do total';
      if (c + b > t) return 'certas + em branco passam do total';
      return '';
    },

    get preenchidas() {
      return this.linhas.filter((l) => l.total !== '' && l.total !== null && Number(l.total) > 0);
    },

    async registrar() {
      this.erro = null;
      if (!this.preenchidas.length) { this.erro = 'Preencha o total de ao menos uma disciplina.'; return; }
      this.tentou = true;
      if (this.linhas.some((l) => this.erroDaLinha(l))) return;
      this.enviando = true;
      try {
        const corpo = { data: this.data, duracaoMinutos: Number(this.duracao), resultados: this.preenchidas.map((l) => ({
          disciplinaId: l.disciplinaId, formato: this.formato, questoesCorretas: Number(l.certas || 0), questoesTotal: Number(l.total),
          questoesEmBranco: l.brancos === '' || l.brancos === null ? null : Number(l.brancos) })) };
        const salvo = await api('/simulados', { method: 'POST', body: JSON.stringify(corpo) });
        const acertos = salvo.resultados.reduce((s, r) => s + r.questoesCorretas, 0);
        const total = salvo.resultados.reduce((s, r) => s + r.questoesTotal, 0);
        const pontos = await api('/simulados/pontos').catch(() => []);
        const meu = pontos.find((p) => p.simuladoId === salvo.id);
        this.resultado = { data: salvo.data, acertos, total, nota: meu && meu.nota.porDisciplina.length ? meu.nota : null };
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.enviando = false;
      }
    },
  }));

  Alpine.data('paginaRegistrar', () => ({
    // D-159: em ensaio, "Ver o que aconteceria" no lugar de gravar.
    ensaio: false,
    ensaioTexto: '',
    tipo: 'conteudo', // conteudo | questoes | flashcards | discursiva
    previsaoNivel: null, // D-134: a previsão da discursiva, em três níveis
    nota: null, // D-134: a nota da discursiva, 0–100
    limiaresDiscursiva: { sucesso: 70, parcial: 40 },
    limiaresLote: null, // §8B: para o botão dizer o que vai acontecer
    assuntoId: null,
    nome: null,
    segmentos: [],
    segmentoId: null,
    formato: 'MULTIPLA_ESCOLHA',
    feitas: null,
    certas: null,
    previsao: null,
    tempo: null,
    tempoEditado: false,
    duracaoBloco: null,
    livres: null, // D-100: os focos do Pomodoro ainda não usados deste assunto (minutos e ids)
    tentativaId: null,
    enviando: false,
    erro: null,
    semDados: false,

    // Chamado pelo router a cada navegação para #/registrar/{tipo}/{id} —
    // inclusive quando registrar() volta pra cá depois de uma falha de envio.
    // Mesmo cuidado de paginaRecuperar.abrir: reabrir o MESMO assunto não
    // pode resetar o que já foi preenchido.
    async abrir(tipo, assuntoId) {
      this.tipo = tipo;
      const emEnsaio = Alpine.store('ensaio').para(assuntoId);
      if (this.assuntoId === assuntoId && this.tentativaId && this.ensaio === emEnsaio) return;
      this.ensaio = emEnsaio;
      this.ensaioTexto = '';
      this.previsaoNivel = null;
      this.nota = null;

      const item = Alpine.store('registrar').item;
      this.semDados = !item || item.id !== assuntoId;

      this.assuntoId = assuntoId;
      this.nome = this.semDados ? null : item.nome;
      this.segmentos = [];
      this.segmentoId = null;
      this.feitas = null;
      this.certas = null;
      this.previsao = null;
      this.tempo = null;
      this.tempoEditado = false;
      this.livres = null;
      this.erro = null;
      this.tentativaId = crypto.randomUUID(); // D-45: gerado ao abrir a tela

      if (!this.semDados) {
        // D-100: o tempo medido pelo Pomodoro vence a estimativa; auxiliar, falhar só deixa a estimativa.
        if (!this.ensaio) api(`/foco/livres?assuntoId=${assuntoId}`).then((l) => { this.livres = l; this.sugerirTempo(); }).catch(() => { this.livres = null; });
        // D-29: a duração de bloco é parâmetro — lida a cada abertura, então
        // mudar em Ajustes vale na próxima vez, sem recarregar o app.
        api('/parametros')
          .then((ps) => {
            const p = ps.find((x) => x.chave === 'duracao_bloco_min');
            const ls = ps.find((x) => x.chave === 'discursiva_limiar_sucesso');
            const lp = ps.find((x) => x.chave === 'discursiva_limiar_parcial');
            if (ls && lp) this.limiaresDiscursiva = { sucesso: Number(ls.valor), parcial: Number(lp.valor) };
            const valor = (chave) => Number((ps.find((x) => x.chave === chave) || {}).valor);
            this.limiaresLote = {
              MULTIPLA_ESCOLHA: { sucesso: valor('limiar_sucesso_multipla_escolha'), parcial: valor('limiar_parcial_multipla_escolha') },
              CERTO_ERRADO: { sucesso: valor('limiar_sucesso_certo_errado'), parcial: valor('limiar_parcial_certo_errado') },
              FLASHCARDS: { sucesso: valor('limiar_sucesso_flashcards'), parcial: valor('limiar_parcial_flashcards') },
            };
            this.duracaoBloco = p ? Number(p.valor) : null;
            this.sugerirTempo();
          })
          .catch(() => { this.duracaoBloco = null; });
        try {
          this.segmentos = await api(`/assuntos/${assuntoId}/segmentos`);
          // D-55: vindo do card de Hoje, o segmento sugerido já vem marcado.
          if (item.segmentoSugeridoId && this.segmentos.some((s) => s.id === item.segmentoSugeridoId)) {
            this.segmentoId = String(item.segmentoSugeridoId);
          }
        } catch (e) {
          // Lista de segmentos é auxiliar, não bloqueia o registro — se
          // falhar, o campo de segmento simplesmente não aparece (§7A).
          this.segmentos = [];
        }
        this.sugerirTempo();
      }
    },

    // D-29 / 01_DOMINIO §7.7: em conteúdo, o tempo vem preenchido com a
    // estimativa do bloco escolhido ou, sem ela, com a duração parametrizada.
    // Depois que o usuário mexe no campo, nada mais o sobrescreve. Questões e
    // flashcards ficam vazios — não há estimativa honesta para um lote livre.
    sugerirTempo() {
      if (this.tempoEditado) return;
      // D-100: o foco medido pré-preenche conteúdo, questões e flashcards; a pessoa confirma ou corrige.
      if (this.livres && this.livres.minutos > 0) {
        this.tempo = this.livres.minutos;
        return;
      }
      if (this.tipo !== 'conteudo') {
        this.tempo = null;
        return;
      }
      const segmento = this.segmentos.find((s) => String(s.id) === this.segmentoId);
      this.tempo = segmento?.tempoEstimadoMin ?? this.duracaoBloco;
    },

    rotuloSegmento(s) {
      return descreverSegmento(s);
    },

    // D-134: o nível que a nota dá, pelos limiares de Ajustes (padrão 70 e 40), mostrado antes de gravar.
    get resultadoDaNota() {
      const n = Number(this.nota);
      if (this.nota === null || this.nota === '' || Number.isNaN(n)) return '';
      const sucesso = this.limiaresDiscursiva.sucesso;
      const parcial = this.limiaresDiscursiva.parcial;
      if (n >= sucesso) return `${n} → Reconstruí (a partir de ${sucesso})`;
      if (n >= parcial) return `${n} → Faltou um pedaço (de ${parcial} a ${sucesso - 1})`;
      return `${n} → Não veio (abaixo de ${parcial})`;
    },

    selecionarTipo(tipo) {
      this.tipo = tipo;
      this.segmentoId = null;
      this.feitas = null;
      this.certas = null;
      this.previsao = null;
      this.previsaoNivel = null;
      this.nota = null;
      this.tempoEditado = false;
      this.sugerirTempo();
    },

    rotuloTipo() {
      return { conteudo: 'Conteúdo', questoes: 'Questões', flashcards: 'Flashcards', discursiva: 'Discursiva' }[this.tipo] || 'Registrar';
    },

    // §8B: 1 Dados (feitas e certas) · 2 Previsão · 3 Registrar.
    get etapaDoLote() {
      const dados = Number(this.feitas) > 0 && this.certas !== null && this.certas !== '';
      if (!dados) return 1;
      return this.previsao === null || this.previsao === '' ? 2 : 3;
    },

    rotuloRegistrar() {
      const verbo = this.ensaio ? 'Ver o que aconteceria' : 'Registrar';
      if ((this.tipo !== 'questoes' && this.tipo !== 'flashcards') || this.etapaDoLote < 3) return verbo;
      const feitas = Number(this.feitas);
      const certas = Number(this.certas);
      if (!(feitas > 0) || certas > feitas) return verbo;
      const pct = Math.round((certas * 100) / feitas);
      const l = this.limiaresLote && this.limiaresLote[this.tipo === 'flashcards' ? 'FLASHCARDS' : this.formato];
      if (!l || Number.isNaN(l.sucesso)) return `${verbo} · ${pct}%`;
      const r = pct >= l.sucesso ? 'reconstruí' : pct >= l.parcial ? 'faltou um pedaço' : 'não veio';
      return `${verbo} · ${pct}% · ${r}`;
    },

    // §8B: sair com algo preenchido pergunta antes.
    sair() {
      const preenchido = this.feitas || this.certas || this.nota || (this.tempoEditado && this.tempo);
      if (preenchido && !this.ensaio && !window.confirm('Sair sem registrar?')) return;
      window.location.hash = '#/hoje';
    },

    get temSegmentos() {
      return this.segmentos.length > 0;
    },

    async registrar() {
      this.erro = null;

      // 01_DOMINIO §7.7: o tempo de bloco e de lote é declarado, não o de tela
      // — a leitura e as questões aconteceram antes de abrir esta tela.
      const tempoMinutos = Number(this.tempo);
      if (!Number.isInteger(tempoMinutos) || tempoMinutos < 1) {
        this.erro = 'Informe o tempo em minutos.';
        return;
      }

      this.enviando = true;
      const corpoBase = {
        assuntoId: this.assuntoId,
        data: hojeISO(),
        tempoMinutos,
        tentativaId: this.tentativaId,
        // D-101: os focos usados ficam presos a esta sessão, no mesmo envio.
        fasesIds: this.livres && this.livres.fasesIds.length ? this.livres.fasesIds : undefined,
      };
      // x-model de <select> sempre devolve string — "" quando "Nenhum em
      // especial" está selecionado (nunca null; um <option value="null">
      // literal já foi tentado e vira a STRING "null" no DOM, bug real
      // achado testando isto no navegador).
      // D-134: a discursiva leva a nota (0–100) e a previsão em três níveis; o resultado sai da nota no servidor.
      const corpo = this.tipo === 'conteudo'
        ? { ...corpoBase, tipo: 'ESTUDO', segmentoId: this.segmentoId ? Number(this.segmentoId) : null }
        : this.tipo === 'discursiva'
        ? { ...corpoBase, tipo: 'DISCURSIVA', notaDiscursiva: this.nota === null || this.nota === '' ? null : Number(this.nota),
            previsaoReconstrucao: this.previsaoNivel }
        : {
            ...corpoBase,
            tipo: this.tipo === 'questoes' ? 'QUESTOES' : 'FLASHCARDS',
            formato: this.tipo === 'questoes' ? this.formato : null,
            questoesTotal: this.feitas === null ? null : Number(this.feitas),
            questoesCorretas: this.certas === null ? null : Number(this.certas),
            previsaoPercentual: this.previsao === null ? null : Number(this.previsao),
          };

      // D-159: o ensaio pergunta ao registro real, que desfaz tudo (ADR-059); fica nesta tela e não consome focos.
      if (this.ensaio) {
        try {
          const dia = Alpine.store('ensaio').data;
          const e = await api('/sessoes/ensaio', { method: 'POST', body: JSON.stringify({ ...corpo, data: dia || corpo.data, fasesIds: undefined, tentativaId: crypto.randomUUID() }) });
          this.ensaioTexto = textoDoEnsaio(e, dia);
        } catch (err) {
          this.erro = err.message;
        } finally {
          this.enviando = false;
        }
        return;
      }

      // Otimista (02_JORNADAS §5.1): volta pra Hoje antes da resposta confirmar.
      window.location.hash = '#/hoje';

      try {
        await api('/sessoes', { method: 'POST', body: JSON.stringify(corpo) });
        window.dispatchEvent(new CustomEvent('sge:sessao-registrada')); // §8B: Hoje recarrega o progresso do dia
        // D-45: a tentativa gravada acabou; abrir esta tela de novo (mesmo assunto) começa outra, com o formulário limpo.
        this.tentativaId = null;
      } catch (e) {
        // Falha não destrutiva: volta pra cá com os campos intactos.
        // Reenviar usa o MESMO tentativaId (D-45) — nunca duplica.
        this.erro = e.message;
        window.location.hash = `#/registrar/${this.tipo}/${this.assuntoId}`;
      } finally {
        this.enviando = false;
      }
    },
  }));

  // Sprint 40 (04_FRONTEND §8B, "As demais telas"): busca, filtro de fase e uma consulta só.
  Alpine.data('paginaAssuntos', () => ({
    carregando: true,
    erro: null,
    grupos: [], // [{ disciplina, assuntos: [{...AssuntoResponse, fase, proximaRevisao}] }]
    abertas: {}, // disciplinaId → expandida (02_JORNADAS §4.5)
    busca: '',
    filtro: 'TODOS',

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        const painel = await api('/assuntos/painel');
        this.grupos = painel.map((g) => ({
          disciplina: g.disciplina,
          assuntos: g.assuntos.map((i) => ({ ...i.assunto, fase: i.fase, proximaRevisao: i.proximaRevisao })),
        }));
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    get vazio() {
      return this.grupos.length === 0;
    },

    get filtros() {
      const todos = this.grupos.flatMap((g) => g.assuntos);
      const n = (f) => todos.filter((a) => a.fase === f).length;
      return [['TODOS', 'Todos', todos.length], ['BACKLOG', 'Na fila', n('BACKLOG')], ['FRENTE', 'Estudando', n('FRENTE')], ['CONSOLIDADO', 'Consolidado', n('CONSOLIDADO')]];
    },

    passa(a) {
      if (this.filtro !== 'TODOS' && a.fase !== this.filtro) return false;
      const b = this.busca.trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/\p{M}/gu, '');
      return !b || a.nome.toLocaleLowerCase('pt-BR').normalize('NFD').replace(/\p{M}/gu, '').includes(b);
    },

    get visiveis() {
      const filtrando = this.filtro !== 'TODOS' || this.busca.trim();
      return this.grupos
        .map((g) => ({ ...g, mostrados: g.assuntos.filter((a) => this.passa(a)) }))
        .filter((g) => !filtrando || g.mostrados.length);
    },

    aberta(g) {
      return !!this.abertas[g.disciplina.id] || this.filtro !== 'TODOS' || !!this.busca.trim();
    },

    alternar(disciplinaId) {
      this.abertas = { ...this.abertas, [disciplinaId]: !this.abertas[disciplinaId] };
    },

    contagem(grupo) {
      const n = (f) => grupo.assuntos.filter((a) => a.fase === f).length;
      const c = n('CONSOLIDADO');
      return `${n('BACKLOG')} na fila · ${n('FRENTE')} estudando · ${c} ${c === 1 ? 'consolidado' : 'consolidados'}`;
    },

    rotuloFase(f) {
      return { BACKLOG: 'Na fila', FRENTE: 'Estudando', CONSOLIDADO: 'Consolidado' }[f] || '';
    },

    revisao(a) {
      if (a.fase === 'BACKLOG') return 'ainda não estudado';
      if (!a.proximaRevisao) return 'sem revisão marcada';
      const n = diasAte(a.proximaRevisao);
      if (n < 0) return `revisão atrasada há ${-n} ${n === -1 ? 'dia' : 'dias'}`;
      return 'revisão ' + dataHumana(a.proximaRevisao);
    },

    abrirDetalhe(disciplina, assuntoComFase) {
      Alpine.store('assuntoDetalhe').selecionar({ disciplina, assunto: assuntoComFase });
      window.location.hash = `#/assuntos/${assuntoComFase.id}`;
    },
  }));

  Alpine.data('paginaAssuntoDetalhe', () => ({
    assuntoId: null,
    nome: null,
    disciplinaNome: null,
    peso: null,
    fase: null,
    segmentos: [],
    focoSegmentos: [], // D-105: estimado × real por segmento
    perguntasEstado: [], // D-122: as perguntas de cada bloco (só o estado; as perguntas em si só na recuperação)
    pdfsProprios: [], // D-129: os PDFs da pessoa neste assunto, com os blocos de cada um
    adicionandoPdf: false,
    arquivoPdf: null,
    tipoPdf: 'APOSTILA',
    enviandoPdf: false,
    resultadoPdf: null,
    erroPdf: null,
    lacuna: null,
    refazendo: false,
    refeito: false,
    erroRefazer: null,
    retencao: [],
    partesEdital: [], // 01_DOMINIO §3.12 — as partes do edital deste assunto e o aviso de cada uma
    materialConferencia: null, // o que o planejamento entregou do material (situação, lacunas por parte, consulta)
    carregando: false,
    semDados: false,

    // Chamado pelo router a cada navegação para #/assuntos/{id}. Sem
    // GET /api/assuntos/{id} (§7B do doc técnico), os dados do próprio
    // assunto vêm do store escrito pela lista — só os segmentos são um
    // fetch novo.
    // D-105: só informação — "foco real 52 min" ao lado da estimativa, quando houve foco medido naquele segmento.
    textoFocoDoSegmento(s) {
      const f = this.focoSegmentos.find((x) => x.segmentoId === s.id);
      return f && f.realMin > 0 ? `foco real ${f.realMin} min` : '';
    },

    // "Do acervo" (tudo que não veio de PDF próprio) e um grupo por PDF, na ordem de envio (J-11).
    get gruposDoAssunto() {
      const doPdf = new Map();
      this.pdfsProprios.forEach((p) => p.segmentos.forEach((id) => doPdf.set(id, p)));
      const grupos = [{ chave: 'acervo', titulo: 'Do acervo', pdf: null, segmentos: this.segmentos.filter((s) => !doPdf.has(s.id)) }];
      this.pdfsProprios.forEach((p) => grupos.push({ chave: 'pdf-' + p.id, titulo: '📄 ' + p.nome + ' · seu PDF', pdf: p,
        segmentos: this.segmentos.filter((s) => doPdf.get(s.id) === p) }));
      return grupos.filter((g) => g.segmentos.length);
    },

    async adicionarPdf() {
      this.erroPdf = null;
      this.enviandoPdf = true;
      try {
        const dados = new FormData();
        dados.append('arquivo', this.arquivoPdf);
        dados.append('tipo', this.tipoPdf);
        this.resultadoPdf = await api(`/assuntos/${this.assuntoId}/pdfs`, { method: 'POST', body: dados, headers: {} });
        this.adicionandoPdf = false;
        this.arquivoPdf = null;
        await this.recarregarMaterial();
      } catch (e) {
        this.erroPdf = e.message;
      } finally {
        this.enviandoPdf = false;
      }
    },

    textoResultadoPdf() {
      const r = this.resultadoPdf;
      if (!r) return '';
      const h = Math.floor(r.minutos / 60);
      const tempo = h ? `~${h} h ${String(r.minutos % 60).padStart(2, '0')}` : `~${r.minutos} min`;
      const base = `✓ ${r.nome}: ${r.blocos} ${r.blocos === 1 ? 'bloco' : 'blocos'} · ${tempo}`;
      return r.semTexto ? `${base}. Sem texto (escaneado): dá para ler e marcar com notas de página, mas a IA não prepara perguntas.` : base;
    },

    // D-132: arquiva os blocos deste PDF de uma vez; nada é apagado.
    async tirarPdf(p) {
      const n = p.segmentos.length;
      if (!confirm(`Tirar ${p.nome}? ${n === 1 ? 'O bloco dele sai' : `Os ${n} blocos dele saem`} do material e do turno. O que você leu, marcou e as perguntas ficam no histórico; nada é apagado.`)) return;
      try {
        await api(`/pdfs/${p.id}/tirar`, { method: 'POST' });
        this.resultadoPdf = null;
        await this.recarregarMaterial();
      } catch (e) {
        this.erroPdf = e.message;
      }
    },

    async recarregarMaterial() {
      this.segmentos = await api(`/assuntos/${this.assuntoId}/segmentos`).catch(() => this.segmentos);
      this.pdfsProprios = await api(`/assuntos/${this.assuntoId}/pdfs`).catch(() => []);
      this.perguntasEstado = await api(`/assuntos/${this.assuntoId}/perguntas/estado`).catch(() => []);
    },

    textoPerguntas(s) {
      const e = this.perguntasEstado.find((x) => x.segmentoId === s.id);
      if (!e) return '';
      if (e.situacao === 'PRONTA') return `Perguntas: ${e.prontas} prontas` + (e.ruins ? ` · ${e.ruins} tiradas` : '');
      return { NAO_LIDO: 'Perguntas: depois da leitura', PENDENTE: 'Perguntas: preparando…', SEM_TEXTO: 'Perguntas: o PDF não tem texto',
        FALHOU: 'Perguntas: a IA não conseguiu preparar' }[e.situacao] || '';
    },

    async anexarPdfDoAssunto(l, evento) {
      const arquivo = evento.target.files[0];
      evento.target.value = '';
      if (!arquivo) return;
      try {
        this.materialConferencia = await anexarPdfALacuna(this.assuntoId, l.parte, arquivo);
        this.acompanharAnexo();
      } catch (e) {
        window.alert(e.message);
      }
    },
    async tirarAnexoDoAssunto(l) {
      try {
        this.materialConferencia = await tirarAnexoDaLacuna(this.assuntoId, l.parte);
      } catch (e) {
        window.alert(e.message);
      }
    },
    acompanharAnexo() {
      clearTimeout(this.timerAnexo);
      if (!temAnexoPreparando(this.materialConferencia?.lacunas)) return;
      this.timerAnexo = setTimeout(async () => {
        try {
          this.materialConferencia = await api(`/assuntos/${this.assuntoId}/material-da-conferencia`);
          this.segmentos = await api(`/assuntos/${this.assuntoId}/segmentos`);
        } catch (e) { /* tenta de novo no próximo ciclo */ }
        this.acompanharAnexo();
      }, 10000);
    },
    textoAnexo,
    async destinoLacunaDoAssunto(l, decisao) {
      try {
        this.materialConferencia = await destinoDaLacuna(this.assuntoId, l.parte, decisao);
      } catch (e) {
        window.alert(e.message);
      }
    },

    async abrir(assuntoId) {
      const item = Alpine.store('assuntoDetalhe').item;
      this.semDados = !item || item.assunto.id !== assuntoId;
      if (this.semDados) return;

      this.assuntoId = assuntoId;
      this.nome = item.assunto.nome;
      this.disciplinaNome = item.disciplina.nome;
      Alpine.store('qc').carregar([assuntoId]); // D-154 (auxiliar)
      this.peso = item.assunto.peso;
      this.fase = item.assunto.fase;
      this.lacuna = null;
      this.refeito = false;
      this.erroRefazer = null;
      this.carregando = true;
      try {
        this.segmentos = await api(`/assuntos/${assuntoId}/segmentos`);
      } catch (e) {
        // Segmentos são auxiliares, não bloqueiam o detalhe (mesmo
        // princípio de paginaRegistrar §7A) — se falhar, a lista de
        // material simplesmente não aparece.
        this.segmentos = [];
      } finally {
        this.carregando = false;
      }
      this.focoSegmentos = await api(`/foco/assuntos/${assuntoId}/segmentos`).catch(() => []);
      this.perguntasEstado = await api(`/assuntos/${assuntoId}/perguntas/estado`).catch(() => []);
      this.pdfsProprios = await api(`/assuntos/${assuntoId}/pdfs`).catch(() => []);
      this.adicionandoPdf = false;
      this.resultadoPdf = null;
      this.erroPdf = null;

      // D-65: sem segmento, o motivo de o acervo não ensinar o assunto, se houver.
      // 404 é o normal (sem lacuna); qualquer falha só esconde a linha.
      if (this.segmentos.length === 0) {
        try {
          this.lacuna = await api(`/assuntos/${assuntoId}/lacuna`);
        } catch (e) {
          this.lacuna = null;
        }
      }

      // Sprint 25 — as partes do edital; auxiliar, vazio se o assunto não nasceu de uma conferência.
      try {
        this.partesEdital = await api(`/assuntos/${assuntoId}/partes-do-edital`);
      } catch (e) {
        this.partesEdital = [];
      }
      try {
        this.materialConferencia = await api(`/assuntos/${assuntoId}/material-da-conferencia`);
        this.acompanharAnexo();
      } catch (e) {
        this.materialConferencia = null;
      }

      // M-2, adicionado na Sprint 14 (04_FRONTEND §7B) — mesmo princípio
      // de auxiliar, não bloqueia o detalhe se falhar.
      try {
        const m2 = await api(`/metricas/m2?assuntoId=${assuntoId}`);
        this.retencao = m2.series;
      } catch (e) {
        this.retencao = [];
      }
    },

    get temSegmentos() {
      return this.segmentos.length > 0;
    },

    // O casamento por partes (Sprint 26): "parcial · 2 h 30".
    textoCasamento(a) {
      const rotulo = { COMPLETO: 'completo', PARCIAL: 'parcial', SEM_MATERIAL: 'sem material', RECUSADO: 'recusado pelo fatiador' }[a.situacaoDoCasamento];
      if (!a.minutosEstimados) return `Casamento por partes: ${rotulo}`;
      const h = Math.floor(a.minutosEstimados / 60);
      const tempo = a.minutosEstimados < 60 ? `${a.minutosEstimados} min` : (a.minutosEstimados % 60 ? `${h} h ${a.minutosEstimados % 60} min` : `${h} h`);
      return `Casamento por partes: ${rotulo} · ${tempo}`;
    },

    // D-66: arquiva os segmentos (e descarta a lacuna) para o material ser preparado
    // de novo. Nada é apagado; o progresso de leitura deste assunto recomeça.
    async refazerMaterial() {
      if (!window.confirm('Preparar de novo o material deste assunto? O material atual sai do turno e a leitura dele recomeça pelos blocos novos.')) return;
      this.refazendo = true;
      this.erroRefazer = null;
      try {
        await api(`/assuntos/${this.assuntoId}/material/refazer`, { method: 'POST' });
        this.segmentos = [];
        this.lacuna = null;
        this.refeito = true;
      } catch (e) {
        this.erroRefazer = e.message;
      } finally {
        this.refazendo = false;
      }
    },

    get temRetencao() {
      return this.retencao.length > 0;
    },

    // Mesmo mecanismo de abrirRegistroConteudo (Hoje §6/§7A) — escreve no
    // MESMO store que paginaRegistrar.abrir() lê, só que a partir do
    // detalhe de Assuntos em vez do card de Hoje. Fecha a pendência da
    // Sprint 11: agora Questões/Flashcards/Conteúdo em qualquer assunto
    // têm de onde partir.
    irRegistrar(tipo) {
      Alpine.store('registrar').selecionar({ id: this.assuntoId, nome: this.nome });
      window.location.hash = `#/registrar/${tipo}/${this.assuntoId}`;
    },

    // Quarta ação do detalhe, ao lado de Conteúdo/Questões/Flashcards
    // (04_FRONTEND §7C) — mesmo mecanismo de store de irRegistrar.
    irErros() {
      Alpine.store('erro').selecionar({ assuntoId: this.assuntoId, nome: this.nome });
      window.location.hash = `#/erros/${this.assuntoId}`;
    },
  }));

  Alpine.data('paginaErros', () => ({
    assuntoId: null,
    nome: null,
    semDados: false,
    descricao: '',
    causa: null,
    confianca: null,
    enviando: false,
    erroEnvio: null,
    erros: [],
    carregandoLista: true,

    // Chamado pelo router a cada navegação para #/erros/{assuntoId}. Sem
    // GET /api/assuntos/{id} (mesma ausência de §7B), nome/assunto vêm do
    // store escrito por Recuperar ou pelo detalhe de Assuntos.
    async abrir(assuntoId) {
      const item = Alpine.store('erro').item;
      this.semDados = !item || item.assuntoId !== assuntoId;
      if (this.semDados) return;

      this.assuntoId = assuntoId;
      this.nome = item.nome;
      this.descricao = '';
      this.causa = null;
      this.confianca = null;
      this.erroEnvio = null;
      await this.carregarLista();
    },

    async carregarLista() {
      this.carregandoLista = true;
      try {
        this.erros = await api(`/erros?assuntoId=${this.assuntoId}`);
      } catch (e) {
        this.erros = [];
      } finally {
        this.carregandoLista = false;
      }
    },

    // Não otimista, de propósito — esta tela não navega embora depois de
    // registrar (04_FRONTEND §7C, "não é otimista, e é deliberado"): espera
    // o POST, limpa o formulário e atualiza a lista só depois de confirmar.
    async registrar() {
      this.erroEnvio = null;
      this.enviando = true;
      try {
        await api('/erros', {
          method: 'POST',
          body: JSON.stringify({
            assuntoId: this.assuntoId,
            sessaoId: null,
            descricao: this.descricao,
            causa: this.causa,
            confianca: this.confianca,
          }),
        });
        this.descricao = '';
        this.causa = null;
        this.confianca = null;
        await this.carregarLista();
      } catch (e) {
        this.erroEnvio = e.message;
      } finally {
        this.enviando = false;
      }
    },
  }));

  Alpine.data('paginaProgresso', () => ({
    carregando: true,
    erro: null,
    janela: 'GLOBAL',
    disciplinasPorId: {},
    m1: null,
    m1Global: null,
    m3: null,
    m4: null,
    frente: null,
    divisoes: [],
    foco: null, // D-105: foco por dia e completos × encerrados antes; informação, nunca meta
    pontosSimulados: [], // D-136: a nota em pontos do edital de cada simulado, com a nota de corte
    simulados: [], // J-16: todos, com arquivadoEm (D-149)
    mostrarArquivados: false,

    get simuladosValendo() {
      return this.simulados.filter((s) => !s.arquivadoEm);
    },

    get simuladosArquivados() {
      return this.simulados.filter((s) => s.arquivadoEm);
    },

    acertosDe(sm) {
      const c = sm.resultados.reduce((s, r) => s + r.questoesCorretas, 0);
      const t = sm.resultados.reduce((s, r) => s + r.questoesTotal, 0);
      return c + '/' + t + ' acertos';
    },

    pontosDe(id) {
      const p = this.pontosSimulados.find((x) => x.simuladoId === id);
      return p && p.nota.porDisciplina.length ? p.nota : null;
    },

    // D-149: arquivar, nunca apagar nem editar.
    async arquivarSimulado(sm) {
      if (!window.confirm('Arquivar o simulado de ' + dataBR(sm.data) + '? Ele sai das métricas e dos pontos, mas não é apagado.')) return;
      await api(`/simulados/${sm.id}/arquivar`, { method: 'POST' }).catch(() => {});
      [this.simulados, this.pontosSimulados] = await Promise.all([api('/simulados').catch(() => []), api('/simulados/pontos').catch(() => [])]);
    },

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        this.foco = await api('/foco/resumo').catch(() => null);
        this.pontosSimulados = await api('/simulados/pontos').catch(() => []);
        this.simulados = await api('/simulados').catch(() => []);
        const disciplinas = await api('/disciplinas');
        this.disciplinasPorId = Object.fromEntries(disciplinas.map((d) => [d.id, d.nome]));
        await this.carregarM1();
        this.m1Global = this.janela === 'GLOBAL' ? this.m1 : await api('/metricas/m1?janela=GLOBAL');
        this.m3 = await api('/metricas/m3');
        this.m4 = await api('/metricas/m4');
        // 02_JORNADAS J-3: a frente da semana e as sugestões de divisão (D-35).
        this.frente = await api('/frente');
        this.divisoes = await api('/frente/sugestoes-divisao');
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    async carregarM1() {
      this.m1 = await api(`/metricas/m1?janela=${this.janela}`);
    },

    // Sprint 40: o cartão de M-1 usa sempre a janela global, o formato com mais questões.
    get cartaoM1() {
      const linhas = (this.m1Global?.linhas ?? []).slice().sort((a, b) => b.total - a.total);
      const l = linhas[0];
      if (!l || !l.total) return { valor: '—', frase: 'sem questões ainda' };
      const formato = { MULTIPLA_ESCOLHA: 'múltipla escolha', CERTO_ERRADO: 'certo/errado' }[l.formato] || '';
      return { valor: l.percentual === null ? '—' : Math.round(l.percentual) + '%', frase: `${l.acertos} de ${l.total} · ${formato}` };
    },

    get cartaoM3() {
      const o = this.m3?.objetiva;
      if (!o || !o.n) return { valor: '—', frase: 'sem previsões ainda' };
      const d = Math.round(o.mediaDesvio);
      if (Math.abs(d) <= 5) return { valor: (d > 0 ? '+' : '') + d, frase: 'você se conhece bem' };
      return { valor: (d > 0 ? '+' : '') + d, frase: d > 0 ? 'você se superestima' : 'você se subestima' };
    },

    get focoTemDados() {
      return !!this.foco && (this.foco.completos + this.foco.encerradosAntes) > 0;
    },
    get focoMaiorDia() {
      return Math.max(1, ...(this.foco?.porDia ?? []).map((d) => d.segundos));
    },
    alturaDoDia(d) {
      return Math.max(4, Math.round((d.segundos / this.focoMaiorDia) * 100));
    },
    rotuloDoDia(d) {
      const [, mes, dia] = d.dia.split('-');
      return `${dia}/${mes}`;
    },
    minutosDoDia(d) {
      return Math.round(d.segundos / 60);
    },

    async trocarJanela(janela) {
      this.janela = janela;
      await this.carregarM1();
    },

    // GLOBAL vem com disciplinaId nulo (soma entre disciplinas, D-19);
    // POR_DISCIPLINA precisa do nome de verdade (04_FRONTEND §7D).
    rotuloLinha(linha) {
      return linha.disciplinaId === null ? 'Global' : (this.disciplinasPorId[linha.disciplinaId] ?? '—');
    },
  }));

  // Ajustes → Avisos neste aparelho (D-96, ADR-048, docs/SPRINT-27 §5): liga e desliga o Web Push deste aparelho.
  // Auxiliar: se algo falhar aqui, o resto de Ajustes segue.
  Alpine.data('avisosAjustes', () => ({
    suporte: typeof navigator !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window,
    permissao: typeof Notification !== 'undefined' ? Notification.permission : 'denied',
    ligado: false,
    trabalhando: false,
    mensagem: null,
    erro: null,

    async init() {
      if (!this.suporte) return;
      try {
        const registro = await navigator.serviceWorker.ready;
        const inscricao = await registro.pushManager.getSubscription();
        this.ligado = !!inscricao;
        if (inscricao) await this.avisarServidor(inscricao); // mantém o servidor em dia se ele perdeu a inscrição (idempotente)
      } catch (e) {
        this.ligado = false;
      }
    },

    chaveParaBytes(chave) {
      const base64 = (chave + '='.repeat((4 - (chave.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
      return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    },

    avisarServidor(inscricao) {
      const { endpoint, keys } = inscricao.toJSON();
      return api('/push/inscricoes', { method: 'POST', body: JSON.stringify({ endpoint, keys }) });
    },

    async agir(fazer) {
      this.trabalhando = true;
      this.erro = null;
      this.mensagem = null;
      try {
        await fazer();
      } catch (e) {
        this.erro = e.message || 'Não deu certo. Tente de novo.';
      } finally {
        this.trabalhando = false;
        if (typeof Notification !== 'undefined') this.permissao = Notification.permission;
      }
    },

    ligar() {
      return this.agir(async () => {
        const permissao = await Notification.requestPermission();
        if (permissao !== 'granted') throw new Error('Sem a permissão do navegador não dá para ligar os avisos.');
        const { chavePublica } = await api('/push/chave');
        const registro = await navigator.serviceWorker.ready;
        let inscricao;
        try {
          inscricao = await registro.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: this.chaveParaBytes(chavePublica) });
        } catch (e) {
          throw new Error('O navegador não conseguiu se inscrever no serviço de push. Confira a conexão e as notificações do site, e tente de novo.');
        }
        await this.avisarServidor(inscricao);
        this.ligado = true;
        this.mensagem = 'Avisos ligados. Use "Enviar aviso de teste" para conferir.';
      });
    },

    desligar() {
      return this.agir(async () => {
        const registro = await navigator.serviceWorker.ready;
        const inscricao = await registro.pushManager.getSubscription();
        if (inscricao) {
          await api('/push/inscricoes', { method: 'DELETE', body: JSON.stringify({ endpoint: inscricao.endpoint }) });
          await inscricao.unsubscribe();
        }
        this.ligado = false;
        this.mensagem = 'Avisos desligados neste aparelho.';
      });
    },

    testar() {
      return this.agir(async () => {
        const r = await api('/push/teste', { method: 'POST' });
        if (r.enviados > 0) this.mensagem = 'Aviso enviado. Se não aparecer em alguns segundos, confira as notificações do navegador.';
        else if (r.removidos > 0) { this.ligado = false; this.mensagem = 'O serviço de push recusou este aparelho. Ligue os avisos de novo.'; }
        else this.erro = 'O serviço de push não aceitou o aviso agora. Tente de novo em instantes.';
      });
    },
  }));

  // Ajustes → Pomodoro (D-103, D-104): som e vibração são do aparelho; o aviso com o app fechado é Web Push opcional, desligado por padrão,
  // e só vale para um aparelho já inscrito nos avisos. As durações são parâmetros e aparecem no grupo "Pomodoro" abaixo.
  Alpine.data('pomodoroAjustes', () => ({
    som: preferenciaFoco('som', true),
    vibracao: preferenciaFoco('vibracao', true),
    avisaFase: preferenciaFoco('avisaFase', false),
    inscrito: false,
    trabalhando: false,
    mensagem: null,
    erro: null,

    async init() {
      try {
        if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;
        const registro = await navigator.serviceWorker.ready;
        this.inscrito = !!(await registro.pushManager.getSubscription());
      } catch (e) {
        this.inscrito = false;
      }
    },

    alternarSom() { this.som = !this.som; guardarPreferenciaFoco('som', this.som); },
    alternarVibracao() { this.vibracao = !this.vibracao; guardarPreferenciaFoco('vibracao', this.vibracao); },

    async definirAviso(avisa) {
      this.trabalhando = true;
      this.erro = null;
      this.mensagem = null;
      try {
        const registro = await navigator.serviceWorker.ready;
        const inscricao = await registro.pushManager.getSubscription();
        if (!inscricao) throw new Error('Ligue antes os avisos neste aparelho, logo acima.');
        await api('/push/inscricoes/fase', { method: 'PUT', body: JSON.stringify({ endpoint: inscricao.endpoint, avisa }) });
        this.avisaFase = avisa;
        guardarPreferenciaFoco('avisaFase', avisa);
        this.mensagem = avisa ? 'Aviso de fim de fase ligado neste aparelho.' : 'Aviso de fim de fase desligado neste aparelho.';
      } catch (e) {
        this.erro = e.message || 'Não deu certo. Tente de novo.';
      } finally {
        this.trabalhando = false;
      }
    },

    async testar() {
      this.erro = null;
      this.mensagem = null;
      try {
        const r = await api('/push/teste-fase', { method: 'POST' });
        if (r.enviados > 0) this.mensagem = 'Aviso enviado. Se não aparecer em alguns segundos, confira as notificações do navegador.';
        else this.erro = 'Nenhum aparelho recebeu. Ligue o aviso de fim de fase e tente de novo.';
      } catch (e) {
        this.erro = e.message;
      }
    },
  }));

  // Ajustes → Acesso (J-9, D-109 a D-111): passkeys, sair, sair de todos e os códigos de recuperação.
  Alpine.data('acessoAjustes', () => ({
    passkeys: [],
    restantes: null,
    codigosNovos: [],
    confirmarCodigos: false,
    trabalhando: false,
    mensagem: null,
    erro: null,

    async init() {
      await this.carregar();
    },

    async carregar() {
      try {
        this.passkeys = await api('/acesso/passkeys');
        this.restantes = (await api('/acesso/codigos')).restantes;
      } catch (e) {
        this.erro = e.message;
      }
    },

    ultimoUso(p) {
      return p.ultimoUsoEm ? `último uso em ${dataInstante(p.ultimoUsoEm)}` : 'ainda não usado para entrar';
    },

    async cadastrar() {
      this.trabalhando = true;
      this.erro = null;
      this.mensagem = null;
      try {
        await cadastrarPasskeyNesteAparelho();
        this.mensagem = 'Passkey cadastrado neste aparelho.';
        await this.carregar();
      } catch (e) {
        this.erro = e && e.name === 'NotAllowedError' ? 'Cancelado.' : (e.message || 'Não foi possível cadastrar.');
      } finally {
        this.trabalhando = false;
      }
    },

    async remover(p) {
      if (!confirm(`Remover o passkey "${p.nome}"? Esse aparelho deixa de entrar com ele.`)) return;
      this.erro = null;
      try {
        await api(`/acesso/passkeys/${p.id}`, { method: 'DELETE' });
        await this.carregar();
      } catch (e) {
        this.erro = e.message;
      }
    },

    async sair() {
      try { await api('/acesso/sair', { method: 'POST' }); } catch (e) { /* a porta resolve */ }
      window.location.replace('/entrar.html');
    },

    async sairDeTodos() {
      if (!confirm('Sair de todos os aparelhos? Cada um vai pedir para entrar de novo, inclusive este.')) return;
      try { await api('/acesso/sair-de-todos', { method: 'POST' }); } catch (e) { /* a porta resolve */ }
      window.location.replace('/entrar.html');
    },

    async gerarCodigos() {
      this.trabalhando = true;
      this.erro = null;
      try {
        this.codigosNovos = (await api('/acesso/codigos', { method: 'POST' })).codigos;
        this.confirmarCodigos = false;
        this.restantes = this.codigosNovos.length;
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },
  }));

  Alpine.data('paginaAjustes', () => ({
    carregando: true,
    erro: null,
    parametros: [],
    draft: {}, // cópia editável por chave — só o que o <input> lê/escreve
    salvando: {},
    erroPorChave: {},
    // Ajustes → IA (ADR-045, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A). Token e chave
    // digitados só vão no PUT; a resposta nunca os traz de volta.
    modelos: [],
    tokenNovo: '',
    claudeModelo: 'sonnet',
    chaveNova: '',
    modeloEscolhido: '',
    salvandoIa: false,
    erroIa: null,
    salvoIa: false,
    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        this.parametros = await api('/parametros');
        this.draft = Object.fromEntries(this.parametros.map((p) => [p.chave, p.valor]));
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
      this.carregarIa();
    },

    // Auxiliar: falhar aqui não esconde os parâmetros.
    async carregarIa() {
      await Alpine.store('ia').carregar();
      const config = Alpine.store('ia').config;
      if (config) {
        this.claudeModelo = config.claude.modelo;
        this.modeloEscolhido = config.openrouter.modelo;
      } else {
        this.erroIa = 'Não foi possível ler a configuração de IA.';
      }
      try {
        this.modelos = await api('/ia/modelos');
      } catch (e) {
        this.modelos = []; // sem a lista, o modelo atual continua no seletor
      }
    },

    rotuloModelo(m) {
      const preco = m.precoEntradaPorMilhao == null ? ''
        : ` · US$ ${m.precoEntradaPorMilhao.toFixed(2)} / ${m.precoSaidaPorMilhao?.toFixed(2) ?? '?'} por milhão`;
      return m.nome + preco;
    },

    get opcoesModelo() {
      // O modelo em uso aparece mesmo que a lista não tenha carregado.
      if (this.modelos.some((m) => m.id === this.modeloEscolhido) || !this.modeloEscolhido) return this.modelos;
      return [{ id: this.modeloEscolhido, nome: this.modeloEscolhido, precoEntradaPorMilhao: null }, ...this.modelos];
    },

    async salvarIa(provedor) {
      this.erroIa = null;
      this.salvoIa = false;
      this.salvandoIa = true;
      try {
        Alpine.store('ia').config = await api('/ia/configuracao', {
          method: 'PUT',
          body: JSON.stringify({
            provedor: provedor ?? null,
            claudeToken: this.tokenNovo || null,
            claudeModelo: this.claudeModelo || null,
            chave: this.chaveNova || null,
            modelo: this.modeloEscolhido || null,
          }),
        });
        this.tokenNovo = '';
        this.chaveNova = '';
        this.salvoIa = true;
      } catch (e) {
        this.erroIa = e.message;
      } finally {
        this.salvandoIa = false;
      }
    },

    rotulo(chave) {
      return ROTULOS_PARAMETRO[chave] ?? chave;
    },

    // Seções por prefixo de chave, mesmo agrupamento do wireframe de
    // referência (04_FRONTEND §7E); 'Turno' nasceu com duracao_bloco_min (§7H).
    get grupos() {
      const nomeDoGrupo = (chave) => {
        if (chave.startsWith('teto_')) return 'Tetos';
        if (chave.startsWith('limiar_') || chave.startsWith('discursiva_')) return 'Limiares de resultado';
        if (chave.startsWith('duracao_')) return 'Turno';
        if (chave.startsWith('pomodoro_')) return 'Pomodoro';
        if (chave.startsWith('reta_final_')) return 'Reta final';
        if (chave.startsWith('banca_')) return 'Peso pela banca';
        if (chave === 'minutos_por_questao' || chave === 'lote_minimo_questoes' || chave === 'questoes_por_vez') return 'Questões';
        return 'Escada';
      };
      const porGrupo = {};
      for (const p of this.parametros) {
        const grupo = nomeDoGrupo(p.chave);
        (porGrupo[grupo] ??= []).push(p);
      }
      return ['Tetos', 'Turno', 'Pomodoro', 'Questões', 'Limiares de resultado', 'Reta final', 'Peso pela banca', 'Escada']
        .filter((nome) => porGrupo[nome])
        .map((nome) => ({ nome, itens: porGrupo[nome] }));
    },

    // Cada linha salva sozinha, sem otimismo (não há navegação pra
    // antecipar, 04_FRONTEND §7E). `draft` é o que o <input> lê/escreve
    // (x-model); `parametro.valor` só muda quando o PATCH confirma. Em
    // caso de falha, reatribuir draft[chave] força o Alpine a reescrever o
    // campo — só reatribuir p.valor (que nunca mudou) não dispara nada, o
    // valor "novo" seria igual ao antigo e o Alpine não re-renderiza uma
    // propriedade que não mudou (achado testando isto no navegador: o
    // campo ficava com o texto inválido preso, mesmo com o estado certo).
    async salvar(parametro, valorNovo) {
      if (valorNovo === parametro.valor) return;
      this.erroPorChave[parametro.chave] = null;
      this.salvando[parametro.chave] = true;
      try {
        const atualizado = await api(`/parametros/${parametro.chave}`, {
          method: 'PATCH',
          body: JSON.stringify({ valor: valorNovo }),
        });
        parametro.valor = atualizado.valor;
        this.draft[parametro.chave] = atualizado.valor;
      } catch (e) {
        this.erroPorChave[parametro.chave] = e.message;
        this.draft[parametro.chave] = parametro.valor;
      } finally {
        this.salvando[parametro.chave] = false;
      }
    },
  }));

  // Concursos — concurso-alvo, histórico, edital novo (02_JORNADAS J-5, 04_FRONTEND §7J).
  Alpine.data('paginaConcursos', () => ({
    textoStatusProva,

    // D-135: salva a régua inteira; a nota em pontos de todo o histórico passa a usar a nova (D-136).
    async salvarRegua() {
      this.erroRegua = null;
      this.reguaSalva = false;
      try {
        this.regua = await api(`/concursos/${this.ativo.id}/regua`, { method: 'PUT', body: JSON.stringify({
          erradaAnulaCerta: !!this.regua.erradaAnulaCerta,
          notaCorte: this.regua.notaCorte === '' || this.regua.notaCorte === null ? null : Number(this.regua.notaCorte),
          disciplinas: this.regua.disciplinas.map((d) => ({ disciplinaId: d.disciplinaId, nome: d.nome, pontos: Number(d.pontos) })),
        }) });
        this.reguaSalva = true;
      } catch (e) {
        this.erroRegua = e.message;
      }
    },
    carregando: true,
    erro: null,
    erroAcao: null,
    concursos: [],
    ia: null,
    analises: [],
    reativando: null,
    refazendo: false,
    // SPRINT-19 §5A — "Ver por assunto": null = fechada, [] = aberta e vazia.
    porAssunto: null,
    soSemMaterial: false,
    carregandoLista: false,
    avisos: null, // Sprint 25 — o que a conferência deixou por resolver no concurso-alvo
    provas: [], // J-6: as provas do concurso-alvo
    regua: null, // D-135: a régua da prova do alvo
    reguaAberta: false,
    // D-137 a D-139: o diagnóstico de um concurso; data e ritmo são simulação, nunca gravados.
    diag: null,
    // D-145 a D-147: o peso pela banca do alvo — a proposta é derivada; só entra por aceite.
    banca: null,
    bancaAberta: false,
    bancaErro: null,
    // D-151: a banca do alvo no catálogo do QConcursos e o "trocar" por endereço colado.
    bancaQc: null,
    bancaQcAberta: false,
    bancaQcEndereco: '',
    bancaQcErro: null,

    async carregarBanca() {
      if (!this.ativo) { this.banca = null; this.bancaQc = null; return; }
      this.banca = await api(`/concursos/${this.ativo.id}/peso-banca`).catch(() => null);
      this.bancaQc = await api(`/concursos/${this.ativo.id}/banca-qconcursos`).catch(() => null);
      this.carregarAtalhosDaBanca();
    },

    // D-154: os atalhos das propostas e dos ajustados (auxiliar).
    carregarAtalhosDaBanca() {
      if (!this.banca) return;
      const ids = this.banca.disciplinas.flatMap((d) => d.propostas.map((p) => p.assuntoId)).concat(this.banca.ajustados.map((a) => a.assuntoId));
      Alpine.store('qc').carregar(ids);
    },

    async aprenderBancaQc() {
      this.bancaQcErro = null;
      try {
        this.bancaQc = await api(`/concursos/${this.ativo.id}/banca-qconcursos`, { method: 'PUT', body: JSON.stringify({ endereco: this.bancaQcEndereco }) });
        this.bancaQcAberta = false;
        Alpine.store('qc').esquecer(); // os atalhos já carregados levavam a banca antiga
        this.carregarAtalhosDaBanca();
      } catch (e) {
        this.bancaQcErro = e.message;
      }
    },

    async aceitarBanca(assuntoIds) {
      this.bancaErro = null;
      try {
        this.banca = await api(`/concursos/${this.ativo.id}/peso-banca/aceitar`, { method: 'POST', body: JSON.stringify({ assuntoIds }) });
      } catch (e) {
        this.bancaErro = e.message;
      }
    },

    async desfazerBanca(assuntoId) {
      this.bancaErro = null;
      try {
        this.banca = await api(`/concursos/${this.ativo.id}/peso-banca/${assuntoId}/desfazer`, { method: 'POST' });
      } catch (e) {
        this.bancaErro = e.message;
      }
    },

    todasPropostas() {
      return (this.banca ? this.banca.disciplinas : []).flatMap((d) => d.propostas.map((p) => p.assuntoId));
    },

    pesoBr(p) {
      return { ALTO: 'alto', MEDIO: 'médio', BAIXO: 'baixo' }[p] || p;
    },

    // D-140, D-141: a reta final do alvo — entrar (só com data futura) e sair.
    retaAtiva: null,
    retaAviso: null,

    async carregarReta() {
      const r = await api('/reta-final').catch(() => null);
      this.retaAtiva = r && r.ativa;
    },

    get podeEntrarReta() {
      return this.ativo && !this.retaAtiva && this.ativo.dataProva && this.ativo.dataProva > hojeISO();
    },

    async entrarRetaFinal() {
      this.retaAviso = null;
      try {
        const e = await api(`/concursos/${this.ativo.id}/reta-final`, { method: 'POST' });
        this.retaAviso = e.antecipadas ? e.antecipadas + (e.antecipadas === 1 ? ' revisão de peso alto distribuída' : ' revisões de peso alto distribuídas') + ' até ' + e.ate.split('-').reverse().join('/') + '.'
          : 'Reta final ligada.';
      } catch (err) {
        this.retaAviso = err.message;
      }
      await this.carregarReta();
    },

    async sairRetaFinal() {
      if (!window.confirm('Sair da reta final? As revisões já antecipadas ficam como estão.')) return;
      await api(`/concursos/${this.ativo.id}/reta-final/sair`, { method: 'POST' }).catch((err) => { this.retaAviso = err.message; });
      this.retaAviso = null;
      await this.carregarReta();
    },

    async abrirDiagnostico(c) {
      this.diag = { concurso: c, dados: null, carregando: true, erro: null, data: '', ritmo: '' };
      await this.carregarDiagnostico();
    },

    async carregarDiagnostico() {
      const d = this.diag;
      if (!d) return;
      d.carregando = true;
      d.erro = null;
      const q = new URLSearchParams();
      if (d.data) q.set('data', d.data);
      if (d.ritmo !== '' && d.ritmo !== null && Number(d.ritmo) > 0) q.set('ritmo', String(d.ritmo));
      try {
        d.dados = await api(`/concursos/${d.concurso.id}/diagnostico` + (q.toString() ? '?' + q : ''));
        if (d.dados.cabe) Alpine.store('qc').carregar(d.dados.cabe.ficamDeFora.map((f) => f.assuntoId)); // D-154 (auxiliar)
      } catch (e) {
        d.erro = e.message;
      } finally {
        d.carregando = false;
      }
    },

    // Barra empilhada de uma disciplina: largura de cada fase em % do total dela.
    larguraFase(disc, n) {
      return disc.total ? (100 * n / disc.total).toFixed(1) + '%' : '0%';
    },

    dataBr(iso) {
      if (!iso) return '';
      const [a, m, d] = iso.split('-');
      return `${d}/${m}/${a}`;
    },

    minutosBr(min) {
      if (!min) return '—';
      const h = Math.floor(min / 60);
      const m = min % 60;
      return h ? (m ? `${h} h ${m}` : `${h} h`) : `${m} min`;
    },

    numeroBr(n) {
      return String(n).replace('.', ',');
    },

    textoCabe(c) {
      const ritmo = this.numeroBr(c.ritmo);
      const origem = c.ritmoMedido ? 'no seu ritmo (' + ritmo + ' assuntos novos por semana, últimas 4 semanas)' : 'a ' + ritmo + ' por semana';
      if (c.nuncaVistos === 0) return 'Não há assunto nunca visto neste edital.';
      return (c.cabem >= c.nuncaVistos ? 'Até ' + this.dataBr(c.data) + ', ' + origem + ', cabem todos os ' + c.nuncaVistos + ' nunca vistos.'
        : 'Até ' + this.dataBr(c.data) + ', ' + origem + ', cabem ' + c.cabem + ' dos ' + c.nuncaVistos + ' nunca vistos.');
    },

    textoTodosVistos(c) {
      if (!c.todosVistosEm) return '';
      const dif = Math.round((new Date(c.todosVistosEm) - new Date(c.data)) / 86400000);
      return 'Todos vistos em ' + this.dataBr(c.todosVistosEm)
        + (dif > 0 ? ', ' + dif + (dif === 1 ? ' dia depois' : ' dias depois') + (c.simulada ? ' da data.' : ' da prova.')
          : dif < 0 ? ', ' + (-dif) + (dif === -1 ? ' dia antes.' : ' dias antes.') : ', na própria data.');
    },
    reguaSalva: false,
    erroRegua: null,

    async init() {
      this.carregando = true;
      this.erro = null;
      this.erroAcao = null;
      try {
        Alpine.store('ia').carregar(); // aviso de assinatura parada (ADR-045), sem esperar
        this.carregarReta();
        const [concursos, ia, analises] = await Promise.all([
          api('/concursos'), api('/editais/ia'), api('/editais/analises'),
        ]);
        this.concursos = concursos;
        this.ia = ia;
        // Só as que ainda pedem algo do usuário; confirmada já está no histórico.
        this.analises = analises.filter((a) => a.status !== 'CONFIRMADA');
        this.avisos = null;
        this.provas = this.ativo ? await api('/provas').catch(() => []) : [];
        this.regua = this.ativo ? await api(`/concursos/${this.ativo.id}/regua`).catch(() => null) : null;
        this.carregarBanca(); // D-146: a contagem de propostas no botão
        if (this.ativo) {
          try {
            this.avisos = await api(`/concursos/${this.ativo.id}/avisos-de-conferencia`);
          } catch (e) {
            this.avisos = null; // auxiliar: sem o aviso, a tela segue
          }
        }
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    get ativo() {
      return this.concursos.find((c) => c.ativo) ?? null;
    },

    // "12 partes sem filtro de questões" — não bloqueia nada, fica até a pessoa resolver.
    get textoAvisos() {
      const a = this.avisos;
      if (!a || !a.analiseId) return '';
      const partes = [];
      if (a.partesSemNo) partes.push(`${a.partesSemNo} ${a.partesSemNo === 1 ? 'parte sem filtro' : 'partes sem filtro'} de questões`);
      if (a.vinculosAmplosOuParciais) partes.push(`${a.vinculosAmplosOuParciais} ${a.vinculosAmplosOuParciais === 1 ? 'assunto' : 'assuntos'} do QConcursos mais largo(s) ou parcial(is)`);
      if (a.nosQueMudaram) partes.push(`${a.nosQueMudaram} ${a.nosQueMudaram === 1 ? 'nó mudou' : 'nós mudaram'} na árvore`);
      return partes.join(' · ');
    },

    get anteriores() {
      return this.concursos.filter((c) => !c.ativo);
    },

    // SPRINT-19 §5A — abre/fecha a lista do material, assunto a assunto.
    async verPorAssunto(c) {
      if (this.porAssunto) { this.porAssunto = null; return; }
      this.carregandoLista = true;
      this.erroAcao = null;
      try {
        this.porAssunto = await api(`/concursos/${c.id}/material`);
        this.acompanharAnexos();
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.carregandoLista = false;
      }
    },

    // Agrupa por disciplina na ordem do edital; dentro dela, os sem material primeiro.
    get gruposMaterial() {
      const grupos = new Map();
      for (const a of this.porAssunto ?? []) {
        if (this.soSemMaterial && a.situacao !== 'SEM_MATERIAL') continue;
        if (!grupos.has(a.disciplina)) grupos.set(a.disciplina, []);
        grupos.get(a.disciplina).push(a);
      }
      const peso = (a) => (a.situacao === 'SEM_MATERIAL' ? 0 : 1);
      return [...grupos].map(([disciplina, assuntos]) => ({
        disciplina,
        assuntos: assuntos.map((a, i) => [a, i]).sort((x, y) => peso(x[0]) - peso(y[0]) || x[1] - y[1]).map((x) => x[0]),
      }));
    },

    minutosTexto(min) {
      if (min < 60) return `${min} min`;
      const h = Math.floor(min / 60);
      return min % 60 ? `${h} h ${min % 60} min` : `${h} h`;
    },

    // O casamento por partes (Sprint 26): "parcial · 2 h 30".
    textoCasamento(a) {
      const rotulo = { COMPLETO: 'completo', PARCIAL: 'parcial', SEM_MATERIAL: 'sem material', RECUSADO: 'recusado pelo fatiador' }[a.situacaoDoCasamento];
      return a.minutosEstimados ? `Casamento por partes: ${rotulo} · ${this.minutosTexto(a.minutosEstimados)}` : `Casamento por partes: ${rotulo}`;
    },

    async anexarPdf(a, l, evento) {
      const arquivo = evento.target.files[0];
      evento.target.value = '';
      if (!arquivo) return;
      try {
        a.lacunasPorParte = (await anexarPdfALacuna(a.assuntoId, l.parte, arquivo)).lacunas;
        this.acompanharAnexos();
      } catch (e) {
        window.alert(e.message);
      }
    },
    async tirarAnexo(a, l) {
      try {
        a.lacunasPorParte = (await tirarAnexoDaLacuna(a.assuntoId, l.parte)).lacunas;
      } catch (e) {
        window.alert(e.message);
      }
    },
    // Enquanto o worker prepara um anexo, a lista se atualiza sozinha (a cada 10 s, até acabar).
    acompanharAnexos() {
      clearTimeout(this.timerAnexos);
      if (!this.porAssunto || !this.porAssunto.some((a) => temAnexoPreparando(a.lacunasPorParte))) return;
      this.timerAnexos = setTimeout(async () => {
        try {
          const ativo = this.concursos.find((c) => c.ativo);
          if (ativo && this.porAssunto) this.porAssunto = await api(`/concursos/${ativo.id}/material`);
        } catch (e) { /* tenta de novo no próximo ciclo */ }
        this.acompanharAnexos();
      }, 10000);
    },
    textoAnexo,
    async destinoLacuna(a, l, decisao) {
      try {
        a.lacunasPorParte = (await destinoDaLacuna(a.assuntoId, l.parte, decisao)).lacunas;
      } catch (e) {
        window.alert(e.message);
      }
    },

    textoAssuntoMaterial(a) {
      if (a.situacao === 'SEM_MATERIAL') return 'Sem material no acervo: ' + a.motivo;
      if (a.situacao === 'A_PREPARAR') return 'A preparar';
      const partes = [`${a.segmentos} ${a.segmentos === 1 ? 'segmento' : 'segmentos'}`, this.minutosTexto(a.minutos)];
      if (a.paginas) partes.push(`${a.paginas} págs.`);
      return partes.join(' · ');
    },

    // 01_DOMINIO §3.10 — andamento do material preparado sozinho.
    textoMaterial(m) {
      if (!m) return '';
      const total = m.prontos + m.semMaterial + m.aPreparar;
      const partes = [`Material: ${m.prontos} de ${total} prontos`];
      if (m.semMaterial) partes.push(`${m.semMaterial} sem material no acervo`);
      if (m.aPreparar) partes.push(`${m.aPreparar} a preparar`);
      return partes.join(' · ');
    },

    detalhes(c) {
      const prova = c.dataProva ? 'prova em ' + dataBR(c.dataProva) : 'sem data de prova';
      return [c.banca, prova].filter(Boolean).join(' · ');
    },

    periodo(c) {
      return dataInstante(c.ativadoEm) + ' – ' + (c.desativadoEm ? dataInstante(c.desativadoEm) : 'hoje');
    },

    rotuloStatus(status) {
      return {
        LENDO_EDITAL: 'Lendo o edital…',
        ESCOLHER_CARGO: 'Escolha o cargo',
        SEPARANDO_ASSUNTOS: 'Lendo o conteúdo programático…',
        EM_CONFERENCIA: 'Conferir o edital',
        FALHOU: 'Falhou — toque para tentar de novo',
      }[status] ?? status;
    },

    // D-66: prepara de novo o material de todo o concurso — confirma, porque a
    // leitura em curso recomeça pelos blocos novos. Nada é apagado.
    async refazerMaterial(c) {
      const feitos = c.material.prontos + c.material.semMaterial;
      if (!window.confirm(`Preparar de novo o material de ${feitos} assunto(s) de ${c.nome}? O material atual sai do turno e é refeito em segundo plano.`)) return;
      this.refazendo = true;
      this.erroAcao = null;
      try {
        await api(`/concursos/${c.id}/material/refazer`, { method: 'POST' });
        await this.init();
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.refazendo = false;
      }
    },

    // D-60: reativar muda peso e ordem — pede confirmação, nunca toca o estudo.
    async reativar(c) {
      if (!window.confirm(`Reativar ${c.nome}? Peso e ordem dos assuntos voltam a ser os deste edital; o estudo feito não muda.`)) return;
      this.reativando = c.id;
      this.erroAcao = null;
      try {
        await api(`/concursos/${c.id}/ativar`, { method: 'POST' });
        await this.init();
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.reativando = null;
      }
    },
  }));

  // Novo edital — análise por IA em etapas; nada gravado até confirmar (D-62).
  Alpine.data('paginaEdital', () => ({
    modo: 'novo', // novo | analise
    id: null,
    analise: null,
    carregando: false,
    erro: null,
    erroAcao: null,
    enviando: false,
    arquivo: null,
    cargo: null,
    timer: null,

    abrirNovo() {
      this.parar();
      this.modo = 'novo';
      this.analise = null;
      this.id = null;
      this.arquivo = null;
      this.erroAcao = null;
      this.enviando = false;
    },

    async abrir(id) {
      this.parar();
      this.modo = 'analise';
      if (this.id !== id) {
        this.analise = null;
        this.cargo = null;
      }
      this.id = id;
      this.erroAcao = null;
      await this.carregar();
    },

    parar() {
      if (this.timer) clearTimeout(this.timer);
      this.timer = null;
    },

    async carregar() {
      this.carregando = !this.analise;
      this.erro = null;
      try {
        this.aplicar(await api(`/editais/analises/${this.id}`));
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    // Enquanto a IA trabalha, consulta a cada 3 s — só se a tela ainda é esta.
    aplicar(analise) {
      this.analise = analise;
      if (analise.cargos?.length === 1 && !this.cargo) this.cargo = analise.cargos[0];
      this.parar();
      if (['LENDO_EDITAL', 'SEPARANDO_ASSUNTOS'].includes(analise.status)
          && window.location.hash === `#/concursos/analise/${analise.id}`) {
        this.timer = setTimeout(() => this.carregar(), 3000);
      }
    },

    escolherArquivo(evento) {
      this.arquivo = evento.target.files[0] ?? null;
    },

    async enviar() {
      if (!this.arquivo) {
        this.erroAcao = 'Escolha o PDF do edital.';
        return;
      }
      this.enviando = true;
      this.erroAcao = null;
      try {
        const analise = await apiEnviarArquivo('/editais/analises', 'arquivo', this.arquivo);
        window.location.hash = `#/concursos/analise/${analise.id}`;
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.enviando = false;
      }
    },

    async escolherCargo() {
      this.enviando = true;
      this.erroAcao = null;
      try {
        this.aplicar(await api(`/editais/analises/${this.id}/cargo`, {
          method: 'POST', body: JSON.stringify({ cargo: this.cargo }),
        }));
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.enviando = false;
      }
    },

    async tentarDeNovo() {
      this.enviando = true;
      this.erroAcao = null;
      try {
        this.aplicar(await api(`/editais/analises/${this.id}/tentar-de-novo`, { method: 'POST' }));
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.enviando = false;
      }
    },
  }));

  // Ajustes → Árvore do QConcursos (D-90): atualizar é gesto da pessoa, e o impacto vem antes.
  Alpine.data('arvoreAjustes', () => ({
    versoes: [],
    impacto: null,
    carregando: true,
    trabalhando: false,
    erro: null,

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        this.versoes = await api('/arvore/versoes');
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    async ver(v) {
      this.trabalhando = true;
      this.erro = null;
      try {
        this.impacto = await api(`/arvore/versoes/${v.id}/impacto`);
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },

    async adotar(v) {
      if (!window.confirm(`Adotar a árvore ${v.rotulo}? As partes cujo nó saiu ou mudou de lugar voltam à fila para você reconfirmar. Nenhum nó é trocado sozinho.`)) return;
      this.trabalhando = true;
      this.erro = null;
      try {
        await api(`/arvore/versoes/${v.id}/adotar?confirmar=true`, { method: 'POST' });
        this.impacto = null;
        await this.init();
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },
  }));

  // Conferência do edital (01_DOMINIO §3.12, D-86 a D-92; docs/SPRINT-25-CONFERENCIA-EDITAL.md §10).
  // A IA propõe, a regra confere, a pessoa resolve; cada gesto vira decisão.
  Alpine.data('paginaConferencia', () => ({
    id: null,
    resumo: null,
    disciplinas: [],
    fila: [],
    aceitos: [], // o que a regra aceitou sem pedir a pessoa (docs/REDESENHO-FLUXO §4)
    decisoes: [],
    aba: 'fila',
    abertas: {},
    carregando: false,
    trabalhando: false,
    erro: null,
    erroAcao: null,
    erroPainel: null,
    lendo: false, // o planejamento está refazendo a leitura (Reprocessar)
    timerLeitura: null,
    painel: null, // { tipo: 'parte'|'assunto'|'trecho'|'decisao', modo, ... }
    formulario: {},
    picker: { q: '', resultados: [], ramo: [], filhos: [], selecionados: [], natureza: 'EXATO' },
    buscaSeq: 0,

    async abrir(id) {
      if (this.id !== id) { this.resumo = null; this.painel = null; this.abertas = {}; }
      this.id = id;
      this.erroAcao = null;
      await this.recarregar(true);
    },

    async recarregar(inicial = false) {
      this.carregando = inicial && !this.resumo;
      this.erro = null;
      try {
        const base = `/editais/analises/${this.id}/conferencia`;
        const [resumo, disciplinas, fila, aceitos] = await Promise.all([api(base), api(`${base}/disciplinas`), api(`${base}/fila`), api(`${base}/aceitos-sozinhos`)]);
        this.lendo = false;
        clearTimeout(this.timerLeitura);
        this.resumo = resumo;
        this.disciplinas = disciplinas;
        this.fila = fila;
        this.aceitos = aceitos;
        if (inicial) this.aba = fila.length ? 'fila' : 'disciplinas';
        if (this.painel) this.atualizarPainel();
      } catch (e) {
        if (e.codigo === 'ETAPA_INVALIDA' && this.id) {
          // Reprocessando: a conferência volta quando o planejamento entregar. Consulta de novo em alguns segundos.
          this.lendo = true;
          this.resumo = null; // o que estava na tela é da leitura antiga: some até a nova chegar
          clearTimeout(this.timerLeitura);
          this.timerLeitura = setTimeout(() => this.recarregar(), 5000);
        } else {
          this.erro = e.message;
        }
      } finally {
        this.carregando = false;
      }
    },

    // Roda uma ação, mostra o erro como veio e recarrega o que depende dela.
    async agir(fazer, { fecharPainel = false } = {}) {
      this.trabalhando = true;
      this.erroAcao = null;
      this.erroPainel = null;
      try {
        await fazer();
        await this.recarregar();
        if (fecharPainel) this.painel = null;
        return true;
      } catch (e) {
        if (this.painel) this.erroPainel = e.message; else this.erroAcao = e.message;
        return false;
      } finally {
        this.trabalhando = false;
      }
    },

    pedido(caminho, metodo, corpo) {
      return api(`/editais/analises/${this.id}${caminho}`, { method: metodo, body: corpo === undefined ? undefined : JSON.stringify(corpo) });
    },

    // ------------------------------------------------ a fila, por disciplina
    get filaAgrupada() {
      const grupos = [];
      const por = new Map();
      for (const it of this.fila) {
        const chave = it.disciplinaId ?? 0;
        if (!por.has(chave)) { const g = { chave, disciplinaId: it.disciplinaId, nome: it.disciplina ?? '', itens: [], aceitaveis: 0 }; por.set(chave, g); grupos.push(g); }
        const g = por.get(chave);
        g.itens.push(it);
        if (it.tipo === 'VINCULO_AMPLO_OU_PARCIAL') g.aceitaveis++;
      }
      return grupos;
    },
    get aceitosPorDisciplina() {
      const por = new Map();
      for (const a of this.aceitos) {
        if (!por.has(a.disciplina)) por.set(a.disciplina, { nome: a.disciplina, itens: [] });
        por.get(a.disciplina).itens.push(a);
      }
      return [...por.values()];
    },
    aceitarGrupo(g) { return this.aceitarAmplos({ id: g.disciplinaId, nome: g.nome }); },

    // O que ainda falta aceitar na disciplina: sem isso o botão de "aceitar todos" não faria nada e a pessoa não saberia por quê.
    porAceitar(d) {
      let iguais = 0, largos = 0;
      for (const a of d.assuntos) {
        if (!a.aparece) continue;
        for (const p of a.partes) {
          if (!p.aparece || p.estado !== 'PROPOSTA' || !p.nos.length) continue;
          if (p.natureza === 'EXATO') iguais++; else if (p.natureza === 'MAIS_AMPLO' || p.natureza === 'PARCIAL') largos++;
        }
      }
      return { iguais, largos };
    },

    // Uma frase que diz o que o sistema achou, no lugar dos termos internos.
    explicacao(it) {
      if (!it.parteId) return '';
      const parte = this.achar(it.assuntoId)?.a.partes.find((p) => p.id === it.parteId);
      if (!parte) return '';
      const no = parte.nos.find((n) => n.caminho);
      const onde = no ? `«${no.caminho.join(' › ')}»${no.filhos ? ` (${no.filhos} subitens)` : ''}` : '';
      if (it.tipo === 'PARTE_SEM_NO') return 'Não achei este assunto no QConcursos.';
      if (it.tipo === 'VINCULO_AMPLO_OU_PARCIAL') {
        return parte.natureza === 'PARCIAL' ? `O mais próximo no QConcursos é ${onde}, que cobre só uma parte do que o edital pede.`
          : `O mais próximo no QConcursos é ${onde}, que é mais largo que o edital: as questões virão mais abrangentes.`;
      }
      if (it.tipo === 'NO_TROCADO_NO_REFINO') return `A segunda checagem trocou para ${onde}. Confira se serve.`;
      if (it.tipo === 'NO_MUDOU_NA_ARVORE') return 'O QConcursos mudou ou tirou o assunto escolhido: confirme de novo ou escolha outro.';
      return '';
    },

    // ------------------------------------------------ rótulos
    rotuloTipo(tipo) {
      return {
        LINHA_FORA: 'Linhas fora de qualquer disciplina', SEM_ASSUNTO: 'Trecho do edital sem assunto',
        ASSUNTO_RECUSADO: 'Assunto que o sistema recusou', ALERTA_DE_PARTE: 'Assunto dividido de um jeito duvidoso',
        PARTE_SEM_NO: 'Sem correspondência no QConcursos', VINCULO_AMPLO_OU_PARCIAL: 'O QConcursos não tem igual',
        NO_TROCADO_NO_REFINO: 'Trocado pela segunda checagem', NO_MUDOU_NA_ARVORE: 'O QConcursos mudou este assunto',
        NAO_APARECE_MAIS: 'Não aparece mais no reprocessamento',
      }[tipo] ?? tipo;
    },
    rotuloNatureza(n) {
      return { EXATO: 'igual no QConcursos', MAIS_AMPLO: 'mais largo no QConcursos', PARCIAL: 'cobre só uma parte' }[n] ?? 'sem correspondência';
    },
    rotuloOrigem(o) {
      return { IA: 'proposto pela IA', REFINO: 'trocado na segunda checagem', DECISAO: 'vindo de uma decisão sua anterior', PESSOA: 'escolhido por você' }[o] ?? o;
    },
    rotuloEstado(p) {
      if (p.noMudou) return 'reconfirmar';
      return { PROPOSTA: 'por conferir', ACEITA: 'aceita', DECIDIDA: 'decidida', DESCARTADA: 'descartada' }[p.estado] ?? p.estado;
    },

    alternarAssunto(id) { this.abertas = { ...this.abertas, [id]: !this.abertas[id] }; },

    // ------------------------------------------------ painel
    fechar() { this.painel = null; this.erroPainel = null; },

    achar(assuntoId) {
      for (const d of this.disciplinas) for (const a of d.assuntos) if (a.id === assuntoId) return { d, a };
      return null;
    },

    abrirParte(parte, assunto) {
      this.erroPainel = null;
      this.formulario = { comentario: parte.comentario ?? '', paraTodos: false };
      this.painel = { tipo: 'parte', modo: 'ver', titulo: 'Parte do edital', parte, assunto };
    },

    abrirItem(it) {
      this.erroPainel = null;
      if (it.parteId) {
        const achado = this.achar(it.assuntoId);
        const parte = achado?.a.partes.find((p) => p.id === it.parteId);
        if (parte) this.abrirParte(parte, achado.a);
      } else if (it.assuntoId) {
        this.abrirAssunto(this.achar(it.assuntoId).a);
      } else {
        this.formulario = { motivo: '', texto: (it.detalhe ?? []).join(' '), disciplinaId: '', novaDisciplina: '' };
        this.painel = { tipo: 'trecho', modo: 'ver', titulo: this.rotuloTipo(it.tipo), item: it };
      }
    },

    abrirAssunto(assunto) {
      this.formulario = {};
      this.painel = { tipo: 'assunto', modo: 'ver', titulo: 'Assunto do edital', assunto };
    },

    abrirPartesDoAssunto() {
      this.abertas = { ...this.abertas, [this.painel.assunto.id]: true };
      this.aba = 'disciplinas';
      this.painel = null;
    },

    // Depois de cada ação o painel aponta para o dado novo (a parte pode ter sido trocada por outra).
    atualizarPainel() {
      const p = this.painel;
      if (p.tipo === 'parte') {
        const achado = this.achar(p.assunto.id);
        const parte = achado?.a.partes.find((x) => x.id === p.parte.id);
        if (!achado || !parte || !parte.aparece || parte.estado === 'DESCARTADA') { this.painel = null; return; }
        p.assunto = achado.a;
        p.parte = parte;
      } else if (p.tipo === 'assunto') {
        const achado = this.achar(p.assunto.id);
        if (achado) p.assunto = achado.a;
      } else if (p.tipo === 'trecho' && !this.fila.some((i) => i.trechoId === p.item.trechoId)) {
        this.painel = null;
      }
    },

    // ------------------------------------------------ ações sobre a parte
    // D-87: "todos os editais" mostra antes quantas partes de outros editais mudariam e só segue com a confirmação.
    async escopo(nos, natureza) {
      if (!this.formulario.paraTodos) return { escopo: 'EDITAL' };
      const q = new URLSearchParams();
      (nos ?? []).forEach((n) => q.append('nos', n));
      if (natureza) q.set('natureza', natureza);
      const previa = await this.pedido(`/partes/${this.painel.parte.id}/escopo-todos?${q}`, 'GET');
      const quantas = previa.partes === 1 ? '1 parte de outro edital' : `${previa.partes} partes de outros editais`;
      const exemplos = previa.exemplos.length ? `\n\nPor exemplo:\n- ${previa.exemplos.join('\n- ')}` : '';
      if (!window.confirm(`Valer para todos os editais muda ${quantas}, que ainda não foram conferidas. O que você já decidiu em outros editais não muda.${exemplos}\n\nConfirmar?`)) {
        throw new Error('Você escolheu não valer para todos os editais.');
      }
      return { escopo: 'TODOS', confirmarTodos: true };
    },
    aceitar() {
      return this.agir(async () => this.pedido(`/partes/${this.painel.parte.id}/aceitar`, 'POST',
        await this.escopo(this.painel.parte.nos.map((n) => n.id), this.painel.parte.natureza)), { fecharPainel: true });
    },
    semNo() {
      return this.agir(async () => this.pedido(`/partes/${this.painel.parte.id}/sem-no`, 'POST', await this.escopo([], null)), { fecharPainel: true });
    },
    reprocessar() {
      if (!window.confirm('Ler o edital de novo? O que você decidiu fica como está; a IA propõe só o que é novo.')) return;
      return this.agir(() => this.pedido('/conferencia/reprocessar', 'POST'));
    },
    comentar() { return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/comentario`, 'POST', { texto: this.formulario.comentario })); },
    descartar() { return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/descartar`, 'POST', { motivo: this.formulario.motivo }), { fecharPainel: true }); },
    salvarEdicao() {
      const f = this.formulario;
      return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}`, 'PUT', { texto: f.texto, contexto: f.contexto || null, herdado: f.herdado || null }), { fecharPainel: true });
    },
    dividir() {
      const partes = this.formulario.partes.filter((t) => t.trim()).map((texto) => ({ texto }));
      return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/dividir`, 'POST', { partes }), { fecharPainel: true });
    },
    juntar() {
      const f = this.formulario;
      return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/juntar`, 'POST', { outraParteId: Number(f.outra), texto: f.texto }), { fecharPainel: true });
    },

    // ------------------------------------------------ ações sobre assunto, disciplina e trecho
    aceitarAssunto() { return this.agir(() => this.pedido(`/assuntos/${this.painel.assunto.id}/aceitar`, 'POST'), { fecharPainel: true }); },
    corrigirAssunto() { return this.agir(() => this.pedido(`/assuntos/${this.painel.assunto.id}`, 'PUT', { texto: this.formulario.texto }), { fecharPainel: true }); },
    aceitarExatos(d) { return this.agir(() => this.pedido(`/disciplinas/${d.id}/aceitar-exatos`, 'POST')); },
    aceitarAmplos(d) {
      if (!window.confirm(`Aceitar de uma vez os assuntos de ${d.nome} em que o QConcursos é mais largo ou cobre só uma parte? As questões continuam a um toque, só que mais abrangentes.`)) return;
      return this.agir(() => this.pedido(`/disciplinas/${d.id}/aceitar-amplos`, 'POST'));
    },
    trocarPeso(d, peso) { return this.agir(() => this.pedido(`/disciplinas/${d.id}/peso`, 'PUT', { peso })); },
    resolverTrecho(acao) {
      const f = this.formulario;
      const corpo = acao === 'DESCARTAR'
        ? { acao, motivo: f.motivo }
        : { acao, texto: f.texto, disciplinaId: f.disciplinaId ? Number(f.disciplinaId) : null, novaDisciplina: f.novaDisciplina || null };
      return this.agir(() => this.pedido(`/trechos/${this.painel.item.trechoId}/resolver`, 'POST', corpo), { fecharPainel: true });
    },

    async concluir() {
      if (!window.confirm('Concluir a conferência? Os assuntos passam a existir, o concurso vira o alvo e o material é preparado. Você continua podendo trocar nós depois.')) return;
      this.trabalhando = true;
      this.erroAcao = null;
      try {
        await this.pedido('/conferencia/concluir', 'POST');
        window.location.hash = '#/concursos';
      } catch (e) {
        this.erroAcao = e.message;
        window.scrollTo(0, 0);
        await this.recarregar();
      } finally {
        this.trabalhando = false;
      }
    },

    // ------------------------------------------------ seletor de nó
    async iniciarTrocaDeNo() {
      const p = this.painel.parte;
      this.picker = { q: '', resultados: [], ramo: [], filhos: [], selecionados: p.nos.filter((n) => n.nome).map((n) => ({ ...n })), natureza: p.natureza ?? 'EXATO' };
      this.painel.modo = 'nos';
      await this.carregarRamo();
      // O que o sistema propôs e a pessoa não adotou entra como sugestão para a busca.
      if (p.propostos.length) this.picker.resultados = p.propostos.filter((n) => n.nome);
    },

    async carregarRamo() {
      try {
        const topo = this.picker.ramo.at(-1);
        this.picker.filhos = topo ? await api(`/arvore/nos/${topo.id}/filhos`) : await api('/arvore/raizes');
      } catch (e) {
        this.erroPainel = e.message;
      }
    },
    async descer(n) { this.picker.ramo.push(n); await this.carregarRamo(); },
    async subir() { this.picker.ramo.pop(); await this.carregarRamo(); },

    async buscar() {
      const q = this.picker.q.trim();
      const seq = ++this.buscaSeq;
      if (q.length < 2) { this.picker.resultados = []; return; }
      try {
        const achados = await api(`/arvore/busca?q=${encodeURIComponent(q)}&limite=30`);
        if (seq === this.buscaSeq) this.picker.resultados = achados;
      } catch (e) {
        this.erroPainel = e.message;
      }
    },

    alternarNo(n) {
      const i = this.picker.selecionados.findIndex((x) => x.id === n.id);
      if (i >= 0) { this.picker.selecionados.splice(i, 1); return; }
      if (this.picker.selecionados.length >= 3) { this.erroPainel = 'Uma parte liga a até 3 nós.'; return; }
      this.erroPainel = null;
      this.picker.selecionados.push(n.caminho ? n : { ...n, caminho: [...this.picker.ramo.map((r) => r.nome), n.nome] });
    },

    gravarNos() {
      const corpo = { nos: this.picker.selecionados.map((n) => n.id), natureza: this.picker.natureza };
      if (this.painel.tipo === 'decisao') {
        return this.agirDecisao(() => api(`/decisoes/${this.painel.decisao.id}`, { method: 'PUT', body: JSON.stringify({ ...corpo, comentario: this.painel.decisao.comentario }) }));
      }
      return this.agir(async () => this.pedido(`/partes/${this.painel.parte.id}/nos`, 'PUT',
        { ...corpo, ...(await this.escopo(corpo.nos, corpo.natureza)) }), { fecharPainel: true });
    },

    // ------------------------------------------------ decisões
    async abrirDecisoes() {
      this.aba = 'decisoes';
      await this.carregarDecisoes();
    },
    async carregarDecisoes() {
      try {
        this.decisoes = await api('/decisoes');
      } catch (e) {
        this.erroAcao = e.message;
      }
    },
    async agirDecisao(fazer) {
      this.trabalhando = true;
      this.erroPainel = null;
      try {
        await fazer();
        this.painel = null;
        await this.carregarDecisoes();
        await this.recarregar();
      } catch (e) {
        if (this.painel) this.erroPainel = e.message; else this.erroAcao = e.message;
      } finally {
        this.trabalhando = false;
      }
    },
    async abrirDecisao(d) {
      this.erroPainel = null;
      this.painel = { tipo: 'decisao', modo: 'nos', titulo: 'Decisão: ' + d.parte, decisao: d };
      this.picker = { q: '', resultados: [], ramo: [], filhos: [], selecionados: d.nos.filter((n) => n.nome).map((n) => ({ ...n })), natureza: d.natureza ?? 'EXATO' };
      await this.carregarRamo();
    },
    revogar(d) {
      if (!window.confirm(`Revogar a decisão sobre "${d.parte}"? Ela deixa de valer antes da IA; as partes já ligadas não mudam.`)) return;
      return this.agirDecisao(() => api(`/decisoes/${d.id}`, { method: 'DELETE' }));
    },
  }));
});
