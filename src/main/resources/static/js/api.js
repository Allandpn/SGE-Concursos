// js/api.js — wrapper fino de fetch, sem biblioteca (04_FRONTEND.md §4).

class ApiError extends Error {
  constructor(detail, codigo, campo) {
    super(detail);
    this.codigo = codigo;
    this.campo = campo;
  }
}

async function api(caminho, opcoes = {}) {
  const resposta = await fetch(`/api${caminho}`, {
    headers: { 'Content-Type': 'application/json' },
    ...opcoes,
  });
  if (!resposta.ok) {
    const problema = await resposta.json(); // application/problem+json, ADR-026
    throw new ApiError(problema.detail, problema.codigo, problema.campo);
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
