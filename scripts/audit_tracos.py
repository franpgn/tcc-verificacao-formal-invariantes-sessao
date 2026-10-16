#!/usr/bin/env python3
"""T-A / A.6: para cada traco de violacao (.out com 'State N:'), gera runs/tlc/audit-b32/tracos/traco-<dir>-<prop>.md
com a sequencia de acoes, os estados em forma compacta e a coluna 'depende do artefato D-4 (relogio nao atualizado em
EntregaMensagem)?'. Criterio mecanico (assinatura de I1P): algum passo local (Login/Logout/Block/Unblock/IncrementFailure/
ResetFailures) que incrementa relogio[r] mas NAO altera nenhum registro local (o gate `novoTS > ts local` rejeitou a escrita
porque relogio[r] ficou defasado em relacao a um ts recebido por EntregaMensagem). A leitura do mecanismo e' textual.
Uso: audit_tracos.py RUNS_TLC_DIR SAIDA_DIR [GLOB=prop-*]"""
import re, sys, glob, os


def abrev(flat):
    f = flat.replace('estado |-> ', '').replace('motivo |-> ', 'm=').replace('identidade |-> ', 'id=')
    f = re.sub(r'\[ *(\w+), m=(\w+), id=\w+, ts \|-> (\d+) *\]', lambda m: '%s/%s@%s' % (
        m.group(1)[0], m.group(2).replace('NONE', '-').replace('LOGOUT', 'LO').replace('ADMIN_BLOCK', 'AB'), m.group(3)), f)
    f = re.sub(r'\[status \|-> (\w+), ts \|-> (\d+), contador \|-> (\d+)\]', lambda m: '%s@%s c%s' % (m.group(1)[0], m.group(2), m.group(3)), f)
    f = re.sub(r'\[ *contador \|-> (\d+), status \|-> (\w+), ts \|-> (\d+) *\]', lambda m: '%s@%s c%s' % (m.group(2)[0], m.group(3), m.group(1)), f)
    f = re.sub(r'\[ tipo \|-> (\w+), chave \|-> (\w+), sessao \|-> (\S+), ident \|-> ([^,]*?), sessoesAfetadas \|-> (\{[^}]*\}) \]',
               lambda m: '{%s %s sess=%s id=%s af=%s}' % (m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)), f)
    return f.replace(' :> ', ':').replace(' @@ ', ' ; ')


def estados(path):
    t = open(path, encoding='utf-8', errors='replace').read()
    blocks = re.split(r'^(State \d+: .*)$', t, flags=re.M)
    out = []
    for i in range(1, len(blocks), 2):
        hdr = blocks[i]
        m = re.match(r'State \d+: <(\w+)', hdr)
        acao = 'Init' if 'Initial' in hdr else (m.group(1) if m else '?')
        flat = re.sub(r'\s+', ' ', blocks[i + 1].split('\n\n')[0]).replace('"', '')
        v = {}
        for var in ['sessoes', 'identidades', 'canal', 'relogio', 'particaoAtiva', 'ladoDe', 'vivo']:
            mm = re.search(r'/\\ ' + var + r' = (.*?)(?= /\\ |$)', flat)
            v[var] = abrev(mm.group(1)) if mm else ''
        out.append((acao, v))
    return out


LOCAIS = {'Login': ('sessoes',), 'Logout': ('sessoes',), 'Block': ('identidades',),
          'Unblock': ('identidades',), 'IncrementFailure': ('identidades',), 'ResetFailures': ('identidades',)}

LEITURA = {
    'Init>Logout>Particionar>Mesclar': 'Logout em r1 (ts 2) ainda nao entregue a r2/r3; Particionar {r1}|{r2,r3}; Mesclar copia o registro de rf (lado vencedor) que ainda ve VALID ts 1: o logout confirmado em r1 e revertido. Violacao do passo Mesclar (I1F, I1M; I4F/I4M porque o motivo vira NONE).',
    'Init>Block>Particionar>Mesclar': 'Block em r1 (identidade BLOCKED, sessoes ADMIN_BLOCK) ainda nao entregue; Particionar; Mesclar com rf sem o ID_BLOQUEIA: r1 volta a OPEN e as sessoes a VALID. Violacao de I2Fa (e de I1F/I1M: sessao INVALID volta a VALID).',
    'Init>IncrementFailure>IncrementFailure': 'Auto-bloqueio por limite (LIMITE=2): a segunda IncrementFailure torna a identidade BLOCKED em r1 sem invalidar sessoes VALID da mesma replica. I2Fb (invariante de estado) violada sem falha. Mecanismo (a) do P-27; o codigo real faz o mesmo (IdentityState.incrementar nao invalida sessoes).',
    'Init>Logout>Logout>Block>EntregaMensagem': 'Logout(s1,s2) em r1 (ts 2,3) e Block em r2 (ts 2) sao concorrentes; r2 aplica o proprio Block (ADMIN_BLOCK ts 2) e depois recebe Logout(s2) ts 3 > 2: motivo ADMIN_BLOCK -> LOGOUT com a sessao permanecendo INVALID. I4F/I4Fp violadas por corrida de canal, sem falha.',
    'Init>Logout>Block>EntregaMensagem': 'LIMITE=6 (auto-bloqueio inalcancavel). Logout(s1) e Block em r1; sessoesAfetadas do Block = {s2} (s1 ja INVALID em r1). ID_BLOQUEIA entregue a r2 antes do Logout(s1): r2 fica BLOCKED com s1 VALID. Mecanismo (b) do P-27: cascata calculada na origem aplicada a replica que ainda ve a sessao VALID (artefato do modelo de copias locais).',
    'Init>Logout>Block>Particionar>Mesclar': 'Logout(s1) em r1 e Block em r2 concorrentes; Particionar; Mesclar com rf=r2 troca em r1 o motivo LOGOUT por ADMIN_BLOCK mantendo INVALID. I4Mp violada no passo Mesclar; a pre-condicao (dois motivos concorrentes) existe sem falha (ver I4Fp).',
    'Init>Login>EntregaMensagem>Logout': 'ARTEFATO D-4: Login em r1 (ts 1), entrega a r2 (relogio[r2] continua 0), Logout em r2 calcula novoTS = 1, nao supera ts local (1): escrita local rejeitada mas mensagem transmitida e descartada pelas demais. I1P violada sem falha.',
    'Init>Login>Login>EntregaMensagem>Logout>EntregaMensagem>EntregaMensagem': 'ARTEFATO D-4 (reordenacao): Login(s2) em r1 ts 2 entregue a r2 sem atualizar relogio[r2]; Logout(s2) em r2 sai com ts 1 (rejeitado local), r3 recebe o Logout (ts 1) antes do Login (ts 2) e o Login ressuscita a sessao. I1F/I4F violadas sem falha no modelo livre.',
    'Init>Login>Logout>Particionar>Mesclar': 'Modelo livre: mesma reversao por Mesclar do traco semeado (Login e Logout em r1 antes da particao). Independente de D-4.',
    'Init>Login>IncrementFailure>IncrementFailure': 'Modelo livre: auto-bloqueio por limite (mecanismo (a) do P-27). Independente de D-4.',
    'Init>Login>Particionar>Mesclar': 'MesclarPerdeChave (modelo): Login em r1 ainda nao propagado, Particionar, Mesclar com rf sem a chave: r1 passa de VALID a NONE. Evidencia sobre o modelo, nada sobre o Infinispan real.',
}


def main():
    base, saida = sys.argv[1], sys.argv[2]
    pat = sys.argv[3] if len(sys.argv) > 3 else 'prop-*'
    os.makedirs(saida, exist_ok=True)
    linhas = ['dir\tarquivo\testados\tsequencia\tpasso_local_rejeitado_por_ts\tdepende_D4']
    for o in sorted(glob.glob(os.path.join(base, pat, '*.out'))):
        e = estados(o)
        if not e:
            continue
        d, nome = os.path.basename(os.path.dirname(o)), os.path.basename(o)[:-4]
        seq = '>'.join(a for a, _ in e)
        rej = []
        for k in range(1, len(e)):
            a, v = e[k]
            if a in LOCAIS and all(v[x] == e[k - 1][1][x] for x in LOCAIS[a]):
                rej.append('estado %d (%s)' % (k + 1, a))
        dep = 'SIM' if rej else 'NAO'
        linhas.append('\t'.join([d, nome + '.out', str(len(e)), seq, '; '.join(rej) or '-', dep]))
        md = ['# Traco %s / %s' % (d, nome), '',
              'Fonte bruta: `runs/tlc/%s/%s.out` (%d estados, incluindo o inicial). Acoes: `%s`.' % (d, nome, len(e), seq), '',
              '**Depende do artefato D-4 (P-26)?** %s. Passo local rejeitado pelo gate de ts: %s.' % (dep, '; '.join(rej) or 'nenhum'), '',
              '**Leitura:** ' + LEITURA.get(seq, '(sem texto pre-definido para esta sequencia)'), '', '```']
        for k, (a, v) in enumerate(e):
            md.append('State %d <%s>' % (k + 1, a))
            for var in ['sessoes', 'identidades', 'canal', 'relogio', 'ladoDe']:
                md.append('   %-12s %s' % (var, v[var]))
            if v['particaoAtiva'] == 'TRUE':
                md.append('   particaoAtiva TRUE')
        md.append('```')
        open(os.path.join(saida, 'traco-%s-%s.md' % (d, nome)), 'w', encoding='utf-8').write('\n'.join(md) + '\n')
    open(os.path.join(saida, 'classificacao-d4.tsv'), 'w').write('\n'.join(linhas) + '\n')
    print('%d tracos' % (len(linhas) - 1))


main()
