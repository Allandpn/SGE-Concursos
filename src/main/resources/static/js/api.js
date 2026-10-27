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
// Diz qual endereço falhou e, no endereço do tailnet, sugere o outro (o app instalado no celular guarda só um deles).
function mensagemSemConexao() {
  const { host, hostname, port, protocol } = window.location;
  const base = `Sem conexão com ${host}. Confira a rede e tente de novo.`;
  if (!hostname.endsWith('.ts.net')) return base;
  const outro = port === '8443' ? `${protocol}//${hostname}` : `${protocol}//${hostname}:8443`;
  return `${base} Se continuar, tente pelo outro endereço: ${outro}`;
}

async function api(caminho, opcoes = {}) {
  let resposta;
  try {
    resposta = await fetch(`/api${caminho}`, {
      headers: { 'Content-Type': 'application/json' },
      ...opcoes,
    });
  } catch (e) {
    throw new ApiError(mensagemSemConexao(), 'REDE_INDISPONIVEL', null, 0);
  }
  if (!resposta.ok) {
    const tipo = resposta.headers.get('Content-Type') || '';
    if (tipo.includes('json')) {
      const problema = await resposta.json(); // application/problem+json, ADR-026
      throw new ApiError(problema.detail, problema.codigo, problema.campo, resposta.status);
    }
    throw new ApiError(`O servidor respondeu com erro (${resposta.status}). Tente de novo em instantes.`, 'ERRO_SERVIDOR', null, resposta.status);
  }
  return resposta.status === 204 ? null : resposta.json();
}

// Data local (não UTC) no formato que LocalDate espera — new Date().toISOString()
// erraria o dia perto da meia-noite em fuso negativo (America/Sao_Paulo).
function hojeISO() {
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
