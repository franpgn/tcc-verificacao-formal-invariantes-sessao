import json,sys
B='C:/Users/Francesco/Development/tcc/dados-vm/cs3-2026-11/fat-nucleo-previa-20261107T150934Z/campanha-s3-fatorial'
for ex in sys.argv[1:]:
    d=B+'/'+ex
    mk={}
    for l in open(d+'/marcadores.txt'):
        t=dict(x.split('=',1) for x in l.split()); mk[t['event']]=int(t['t_epoch_ns'])
    s=mk['start']; print('==',ex)
    last=None
    for l in open(d+'/observaveis-rep-001.jsonl',encoding='utf-8'):
        o=json.loads(l)
        if o['tipo']=='topologia':
            t=(o['t_ns']-s)/1e9
            if 30<t<160:
                sig=tuple((p['no'][-5:],p['respondeu'],p['numero_de_nos'],p['motivo']) for p in o['por_no'])
                if sig!=last: print('  top %.1f'%t, sig); last=sig
        if o['tipo']=='janela':
            t=(o['t_fim_ns']-s)/1e9
            if 36<t<150 and int(t)%12<4: print('  jan %.1f tot=%d err=%d unav=%d lag=%s'%(t,o['total'],o['erros'],o['unavailable'],o['lag']['nivel']))
