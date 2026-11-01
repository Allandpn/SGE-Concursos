// js/entrar.js — a porta do app e o WebAuthn do navegador (docs/SPRINT-30-LOGIN.md §5; J-9, D-106 a D-112).
// Fica na lista aberta: não carrega dado. Também é usado por Ajustes → Acesso para cadastrar outro passkey.

function b64urlParaBytes(texto) {
  const base64 = texto.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((texto.length + 3) % 4);
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function bytesParaB64url(buffer) {
  let binario = '';
  new Uint8Array(buffer).forEach((b) => { binario += String.fromCharCode(b); });
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

// As opções vêm do servidor com bytes em base64url; o navegador as quer em ArrayBuffer.
async function criarCredencial(opcoes) {
  const pk = { ...opcoes.publicKey };
  pk.challenge = b64urlParaBytes(pk.challenge);
  pk.user = { ...pk.user, id: b64urlParaBytes(pk.user.id) };
  pk.excludeCredentials = (pk.excludeCredentials || []).map((c) => ({ ...c, id: b64urlParaBytes(c.id) }));
  const credencial = await navigator.credentials.create({ publicKey: pk });
  return {
    type: credencial.type,
    id: credencial.id,
    rawId: bytesParaB64url(credencial.rawId),
    response: {
      clientDataJSON: bytesParaB64url(credencial.response.clientDataJSON),
      attestationObject: bytesParaB64url(credencial.response.attestationObject),
      transports: credencial.response.getTransports ? credencial.response.getTransports() : [],
    },
    clientExtensionResults: credencial.getClientExtensionResults(),
  };
}

async function obterCredencial(opcoes) {
  const pk = { ...opcoes.publicKey };
  pk.challenge = b64urlParaBytes(pk.challenge);
  pk.allowCredentials = (pk.allowCredentials || []).map((c) => ({ ...c, id: b64urlParaBytes(c.id) }));
  const credencial = await navigator.credentials.get({ publicKey: pk });
  return {
    type: credencial.type,
    id: credencial.id,
    rawId: bytesParaB64url(credencial.rawId),
    response: {
      clientDataJSON: bytesParaB64url(credencial.response.clientDataJSON),
      authenticatorData: bytesParaB64url(credencial.response.authenticatorData),
      signature: bytesParaB64url(credencial.response.signature),
      userHandle: credencial.response.userHandle ? bytesParaB64url(credencial.response.userHandle) : null,
    },
    clientExtensionResults: credencial.getClientExtensionResults(),
  };
}

// "Chrome · Android": o nome do passkey na lista de Ajustes → Acesso. Editável depois não é preciso: é só para reconhecer.
function nomeDoAparelho() {
  const ua = navigator.userAgent;
  const sistema = /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iPhone/iPad' : /Mac/.test(ua) ? 'Mac'
    : /Windows/.test(ua) ? 'Windows' : /Linux/.test(ua) ? 'Linux' : 'Aparelho';
  const navegador = /Edg\//.test(ua) ? 'Edge' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome'
    : /Safari\//.test(ua) ? 'Safari' : 'Navegador';
  return `${navegador} · ${sistema}`;
}

// Cadastra um passkey neste aparelho, estando dentro (D-111). Usado depois do código de recuperação e em Ajustes.
async function cadastrarPasskeyNesteAparelho() {
  const pedido = await api('/acesso/passkeys/opcoes', { method: 'POST' });
  const credencial = await criarCredencial(pedido.opcoes);
  return api('/acesso/passkeys', { method: 'POST', body: JSON.stringify({ pedidoId: pedido.pedidoId, credencial, nome: nomeDoAparelho() }) });
}

// A mensagem de uma falha na porta: nunca diz o que existe (J-9); o bloqueio diz quando volta (D-112).
function mensagemDaPorta(e) {
  if (e && e.codigo === 'MUITAS_TENTATIVAS' && e.ate) {
    const hora = new Date(e.ate).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    return `Muitas tentativas. Tente de novo às ${hora}.`;
  }
  if (e && (e.codigo === 'REDE_INDISPONIVEL' || e.codigo === 'HOST_NAO_PERMITIDO')) return e.message;
  if (e && e.name === 'NotAllowedError') return 'Cancelado. Toque de novo quando quiser.';
  return 'Não foi possível entrar.';
}

function portaApp() {
  return {
    carregando: true,
    trabalhando: false,
    primeiroAcesso: false,
    usandoCodigo: false,
    codigo: '',
    codigos: [],
    guardei: false,
    copiado: false,
    erro: '',
    suportado: !!(window.PublicKeyCredential && navigator.credentials),

    async iniciar() {
      try {
        const estado = await api('/acesso/estado');
        if (estado.logado) { window.location.replace('/'); return; }
        this.primeiroAcesso = estado.primeiroAcesso;
      } catch (e) {
        this.erro = mensagemDaPorta(e);
      } finally {
        this.carregando = false;
      }
    },

    async entrar() {
      this.erro = '';
      this.trabalhando = true;
      try {
        const pedido = await api('/acesso/entrar/opcoes', { method: 'POST' });
        const credencial = await obterCredencial(pedido.opcoes);
        await api('/acesso/entrar', { method: 'POST', body: JSON.stringify({ pedidoId: pedido.pedidoId, credencial }) });
        window.location.replace('/');
      } catch (e) {
        this.erro = mensagemDaPorta(e);
      } finally {
        this.trabalhando = false;
      }
    },

    async cadastrarPrimeiro() {
      this.erro = '';
      this.trabalhando = true;
      try {
        const pedido = await api('/acesso/primeiro/opcoes', { method: 'POST' });
        const credencial = await criarCredencial(pedido.opcoes);
        const r = await api('/acesso/primeiro', { method: 'POST', body: JSON.stringify({ pedidoId: pedido.pedidoId, credencial, nome: nomeDoAparelho() }) });
        this.codigos = r.codigos;
      } catch (e) {
        this.erro = e && e.codigo === 'PRIMEIRO_ACESSO_FECHADO' ? e.message : mensagemDaPorta(e);
      } finally {
        this.trabalhando = false;
      }
    },

    async copiar() {
      try {
        await navigator.clipboard.writeText(this.codigos.join('\n'));
        this.copiado = true;
      } catch (e) {
        this.copiado = false;
      }
    },

    abrirApp() {
      window.location.replace('/');
    },

    async recuperar() {
      this.erro = '';
      this.trabalhando = true;
      try {
        await api('/acesso/recuperar', { method: 'POST', body: JSON.stringify({ codigo: this.codigo }) });
      } catch (e) {
        this.erro = mensagemDaPorta(e);
        this.trabalhando = false;
        return;
      }
      // Dentro pelo código: cadastra o passkey deste aparelho (D-110). Se a pessoa cancelar, entra assim mesmo.
      try {
        await cadastrarPasskeyNesteAparelho();
      } catch (e) {
        // Ajustes → Acesso continua oferecendo "Cadastrar passkey neste aparelho".
      }
      window.location.replace('/');
    },
  };
}
