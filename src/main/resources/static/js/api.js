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
async function api(caminho, opcoes = {}) {
  let resposta;
  try {
    resposta = await fetch(`/api${caminho}`, {
      headers: { 'Content-Type': 'application/json' },
      ...opcoes,
    });
  } catch (e) {
    throw new ApiError('Sem conexão com o servidor. Confira a rede e tente de novo.', 'REDE_INDISPONIVEL', null, 0);
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
