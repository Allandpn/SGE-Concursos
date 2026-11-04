// js/ensaio.js — os próximos 7 dias, a prévia do material e o ensaio (docs/SPRINT-38-PROXIMOS-DIAS-E-ENSAIO.md §3;
// 01_DOMINIO §3.25, D-157 a D-159; ADR-059). Nada aqui grava sessão, revisão ou marcação.

const PROXIMOS_ABERTO = 'hoje.proximos';

const ROTULO_RESULTADO = { SUCESSO: 'reconstruí', PARCIAL: 'faltou um pedaço', FALHA: 'não veio' };

function dataCurtaBr(iso) {
  if (!iso) return '';
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}

function diaDaSemanaBr(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  return ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'][new Date(a, m - 1, d).getDay()];
}

// D-159: o que o registro faria, em uma frase, sempre terminando em "Nada foi gravado."
function textoDoEnsaio(e, data = null) {
  if (!e) return '';
  const partes = [];
  if (e.percentual != null) partes.push(`${e.percentual}%`);
  if (e.resultado) partes.push(ROTULO_RESULTADO[e.resultado] || e.resultado);
  let frase = partes.join(' · ');
  if ((e.efeito === 'CUMPRE' || e.efeito === 'ABRE_ESCADA') && e.proxima) {
    frase += `${frase ? ' → ' : ''}a próxima revisão seria nível ${e.proxima.nivel} em ${dataCurtaBr(e.proxima.dataPrevista)}`;
    if (e.consolidaria) frase += ', e o assunto consolidaria';
    frase += '.';
  } else {
    const motivo = {
      FORA_DA_JANELA: 'a sessão contaria, mas fora da janela da revisão, que não mudaria',
      LOTE_PEQUENO: 'abaixo do lote mínimo: contaria nas métricas, sem mexer na escada',
      NUNCA_LIDO: 'contaria nas métricas; a escada não começaria, porque o assunto ainda não foi lido',
      LEITURA: 'a leitura contaria; a escada não mudaria agora',
    }[e.motivo] || 'a escada não mudaria';
    frase += `${frase ? ' → ' : ''}${motivo}.`;
  }
  const dia = data ? `No dia previsto (${diaDaSemanaBr(data)} ${dataCurtaBr(data)}): ` : '';
  const texto = dia ? frase : `${frase.charAt(0).toUpperCase()}${frase.slice(1)}`;
  return `${dia}${texto} Nada foi gravado.`;
}

document.addEventListener('alpine:init', () => {
  // D-159: o assunto e o tipo em ensaio; Recuperar e Registrar leem ao abrir. O router encerra ao sair delas.
  // `data`: ensaiado a partir dos próximos dias, a sessão é ensaiada no dia previsto da revisão; senão, hoje.
  Alpine.store('ensaio', {
    assuntoId: null,
    tipo: null,
    data: null,

    para(assuntoId) {
      return this.assuntoId === assuntoId;
    },

    iniciar(item, tipo) {
      this.assuntoId = item.assuntoId;
      this.tipo = tipo;
      this.data = item.dataPrevista ?? null;
      if (tipo === 'recuperacao') {
        Alpine.store('recuperacao').selecionar({ assuntoId: item.assuntoId, nome: item.nome, nivel: item.nivel ?? null, dataPrevista: item.dataPrevista ?? null });
        window.location.hash = `#/recuperar/${item.assuntoId}`;
      } else {
        Alpine.store('registrar').selecionar({ id: item.assuntoId, nome: item.nome });
        window.location.hash = `#/registrar/${tipo}/${item.assuntoId}`;
      }
    },

    sair() {
      this.assuntoId = null;
      this.tipo = null;
      this.data = null;
    },
  });

  // D-158 e D-159: o painel de um assunto — ver o material (os blocos) ou ensaiar (o aviso e o tipo).
  Alpine.store('previa', {
    visivel: false,
    item: null,
    etapa: 'opcoes',
    segmentos: null,
    erro: null,

    pedido: 0,

    async abrir(item) {
      // O Alpine embrulha o que vai para o store: a resposta vale se ainda for o mesmo pedido, não o mesmo objeto.
      const pedido = ++this.pedido;
      this.item = item;
      this.etapa = 'opcoes';
      this.segmentos = null;
      this.erro = null;
      this.visivel = true;
      try {
        const segs = await api(`/assuntos/${item.assuntoId}/segmentos`);
        if (this.pedido === pedido) this.segmentos = segs;
      } catch (e) {
        if (this.pedido === pedido) { this.segmentos = []; this.erro = e.message; }
      }
    },

    fechar() {
      this.visivel = false;
    },

    // O detalhe do assunto abre direto no aviso do ensaio.
    ensaiarAssunto(item) {
      this.abrir(item);
      this.etapa = 'ensaiar';
    },

    verBloco(segmento) {
      this.visivel = false;
      window.location.hash = `#/ler/${segmento.id}?previa=1`;
    },

    ensaiar(tipo) {
      this.visivel = false;
      Alpine.store('ensaio').iniciar(this.item, tipo);
    },
  });
});

// D-157: o bloco dos próximos 7 dias em Hoje. No computador fica sempre aberto (coluna); no celular, recolhido por padrão.
function proximosAbertoNoAparelho() {
  try {
    return window.localStorage.getItem(PROXIMOS_ABERTO) === '1';
  } catch (e) {
    return false;
  }
}

function guardarProximosAberto(aberto) {
  try {
    window.localStorage.setItem(PROXIMOS_ABERTO, aberto ? '1' : '0');
  } catch (e) {
    // sem armazenamento: só não lembra
  }
}
