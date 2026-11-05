// js/api.js — wrapper fino de fetch, sem biblioteca (04_FRONTEND.md §4).

class ApiError extends Error {
  constructor(detail, codigo, campo, status) {
    super(detail);
    this.codigo = codigo;
    this.campo = campo;
    this.status = status;
  }
}

// Erro que não é do domínio vira código próprio, para a tela mostrar algo
// útil em vez de "Unexpected token <" (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §4):
// - REDE_INDISPONIVEL: fetch nem chegou ao servidor (sem rede, Pi fora do ar);
// - ERRO_SERVIDOR: resposta de erro que não é application/problem+json
//   (502 do proxy, página HTML de erro).
// Nenhum dos dois limpa o que foi digitado — quem chama decide (04_FRONTEND §5).
// Diz qual endereço falhou e o que costuma resolver: no endereço da tailnet, quase sempre é o Tailscale desligado no aparelho.
function mensagemSemConexao() {
  const { host, hostname } = window.location;
  const base = `Sem conexão com ${host}.`;
  return hostname.endsWith('.ts.net') ? `${base} Confira se o Tailscale está ligado neste aparelho e tente de novo.` : `${base} Confira a rede e tente de novo.`;
}

async function api(caminho, opcoes = {}) {
  let resposta;
  try {
    resposta = await fetch(`/api${caminho}`, {
      // FormData leva o Content-Type com a fronteira, que só o navegador sabe
      headers: opcoes.body instanceof FormData ? {} : { 'Content-Type': 'application/json' },
      ...opcoes,
    });
  } catch (e) {
    throw new ApiError(mensagemSemConexao(), 'REDE_INDISPONIVEL', null, 0);
  }
  if (!resposta.ok) {
    const tipo = resposta.headers.get('Content-Type') || '';
    if (tipo.includes('json')) {
      const problema = await resposta.json(); // application/problem+json, ADR-026
      // D-106: sem sessão (expirou, saiu de todos), volta para a porta. Na própria porta, o erro é dela.
      if (resposta.status === 401 && problema.codigo === 'SESSAO_AUSENTE' && window.location.pathname !== '/entrar.html') {
        window.location.replace('/entrar.html');
      }
      const erro = new ApiError(problema.detail, problema.codigo, problema.campo, resposta.status);
      if (problema.ate) erro.ate = problema.ate; // D-112: quando o bloqueio acaba
      throw erro;
    }
    throw new ApiError(`O servidor respondeu com erro (${resposta.status}). Tente de novo em instantes.`, 'ERRO_SERVIDOR', null, resposta.status);
  }
  return resposta.status === 204 ? null : resposta.json();
}

// Data local (não UTC) no formato que LocalDate espera — new Date().toISOString()
// erraria o dia perto da meia-noite em fuso negativo (America/Sao_Paulo).
// ADR-061: no modo de teste, o "hoje" é o do servidor (a data do teste), não o do aparelho.
let HOJE_DO_TESTE = null;

function hojeData() {
  return HOJE_DO_TESTE ? dataLocal(HOJE_DO_TESTE) : new Date();
}

function hojeISO() {
  if (HOJE_DO_TESTE) return HOJE_DO_TESTE;
  const d = new Date();
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}

// "Bloco 2 de 5 · p. 31–60 · ~60 min" — o mesmo texto no card de Hoje e no
// seletor de Registrar (docs/SPRINT-17-USO-REAL.md §1.3). `total` só vem no
// segmento sugerido pelo plano; na lista de Registrar fica só "Bloco 2".
function descreverSegmento(s) {
  if (!s) return '';
  const partes = [s.total ? `Bloco ${s.ordem} de ${s.total}` : `Bloco ${s.ordem}`];
  if (s.paginaInicial && s.paginaFinal) partes.push(`p. ${s.paginaInicial}–${s.paginaFinal}`);
  if (s.tempoEstimadoMin) partes.push(`~${s.tempoEstimadoMin} min`);
  return partes.join(' · ');
}

// Envio de arquivo (multipart) — sem o Content-Type JSON de api(): o navegador
// monta o boundary sozinho. Usado pelo PDF do edital (04_FRONTEND §7J).
async function apiEnviarArquivo(caminho, campo, arquivo) {
  const dados = new FormData();
  dados.append(campo, arquivo);
  return api(caminho, { method: 'POST', body: dados, headers: {} });
}

// "2026-12-07" → "07/12/2026"; instante ISO → data local.
function dataBR(iso) {
  return iso ? iso.split('-').reverse().join('/') : '';
}
function dataInstante(instante) {
  return instante ? new Date(instante).toLocaleDateString('pt-BR') : '';
}

// §8B: datas humanas — "hoje", "amanhã", "ontem", "segunda, 02/11"; nunca AAAA-MM-DD na tela.
const DIAS_DA_SEMANA = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];

function dataLocal(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  return new Date(a, m - 1, d);
}

function diasAte(iso) {
  const hoje = hojeData();
  hoje.setHours(0, 0, 0, 0);
  return Math.round((dataLocal(iso) - hoje) / 86400000);
}

function diaEMes(d) {
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function dataHumana(iso) {
  if (!iso) return '';
  const n = diasAte(iso);
  if (n === 0) return 'hoje';
  if (n === 1) return 'amanhã';
  if (n === -1) return 'ontem';
  const d = dataLocal(iso);
  return `${DIAS_DA_SEMANA[d.getDay()]}, ${diaEMes(d)}`;
}

function atrasoHumano(iso) {
  const n = iso ? -diasAte(iso) : 0;
  return n > 0 ? ` (${n} ${n === 1 ? 'dia' : 'dias'} de atraso)` : '';
}

function hojeHumano() {
  const d = hojeData();
  return `${DIAS_DA_SEMANA[d.getDay()]}, ${diaEMes(d)}`;
}
