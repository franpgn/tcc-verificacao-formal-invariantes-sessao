# Runbook — VM Hyper-V para o gate F3 (tc-netem)

Runbook **copiável** executado pelo **Gestor/usuário**. O motivo da VM: o kernel do WSL2 **não** tem o módulo `sch_netem`, então o `tc qdisc ... netem` falha lá; uma VM Linux com `linux-modules-extra` traz o módulo e libera o F3.

- Os passos 0–2 rodam em **PowerShell ELEVADO** no Windows (Hyper-V). O Passo 0/1 podem exigir **reboot**, que encerra a sessão do Claude.
- Os passos 3–6 rodam **dentro da VM** (console `vmconnect` ou SSH).
- Config aprovada: **Ubuntu Server 24.04 LTS**, Gen2, **4 vCPU / 8 GB RAM / 40 GB** disco dinâmico, rede **Default Switch (NAT)**, engine **Podman** (fallback Docker), repo via `git clone` dentro da VM.
- Rastreabilidade: F3 = `Metodologia.tex` §3.3.2 (T18, +50 ms no p99); backlog **B-12**. Decisões P-1..P-5 no `docs/plano-f3-vm-netem.md` (mesma branch).

> Política: nada vai para `origin/*` sem aprovação explícita do Gestor. Este runbook não faz push.

---

## Passo 0 — Hyper-V já está ativo? (decide se há reboot)

Abrir **PowerShell como Administrador** e rodar:

```powershell
Get-Service vmms | Select-Object Name, Status     # vmms = Hyper-V Virtual Machine Management
Get-VM                                            # se responder (mesmo vazio) => gestão Hyper-V OK
```

Interpretação:

- **`vmms` `Running` e `Get-VM` responde** (elevado) -> **Hyper-V já ativo; PULAR o reboot.** Ir direto ao Passo 1. (O `Get-VM` que falhou antes foi por shell **não-elevado**.)
- **Erro de feature ausente / `vmms` não existe** -> habilitar a feature e **reiniciar**:

```powershell
# (ELEVADO) habilita Hyper-V completo; -All inclui plataforma + ferramentas de gestão
Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V -All
# Reiniciar a máquina quando solicitado (ISSO ENCERRA A SESSÃO DO CLAUDE).
Restart-Computer
```

Confirmar o estado da feature, se quiser:

```powershell
Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All | Select-Object State
# State = Enabled => OK
```

---

## Passo 1 — Criar a VM Gen2 (PowerShell ELEVADO)

Ajustar `$iso` para o caminho real da ISO baixada do Ubuntu Server 24.04.

```powershell
# (ELEVADO)
$nome = "tcc-f3"
$iso  = "C:\caminho\ubuntu-24.04-live-server-amd64.iso"   # AJUSTAR
$vhd  = "C:\Hyper-V\$nome\$nome.vhdx"

# 1) Cria a VM Gen2 com 8 GB de RAM e VHDX dinâmico de 40 GB na Default Switch (NAT)
New-VM -Name $nome -Generation 2 -MemoryStartupBytes 8GB `
       -NewVHDPath $vhd -NewVHDSizeBytes 40GB `
       -SwitchName "Default Switch"

# 2) 4 vCPU
Set-VMProcessor -VMName $nome -Count 4

# 3) Memória fixa em 8 GB (sem Dynamic Memory, para medição estável)
Set-VMMemory -VMName $nome -DynamicMemoryEnabled $false -StartupBytes 8GB

# 4) Anexa a ISO como DVD
Add-VMDvdDrive -VMName $nome -Path $iso

# 5) Gen2 => Secure Boot ON; Ubuntu boota com o template da MS UEFI CA
Set-VMFirmware -VMName $nome -EnableSecureBoot On `
               -SecureBootTemplate "MicrosoftUEFICertificateAuthority"

# 6) Boot pelo DVD no 1º arranque (instalação)
Set-VMFirmware -VMName $nome -FirstBootDevice (Get-VMDvdDrive -VMName $nome)

# 7) Liga e abre o console
Start-VM -Name $nome
vmconnect.exe localhost $nome
```

> Se a Default Switch não existir (raro), criar uma NAT switch manualmente ou usar `Get-VMSwitch` para listar as disponíveis e trocar `-SwitchName`.

---

## Passo 2 — Instalar Ubuntu Server 24.04 (console vmconnect)

Instalação padrão do Ubuntu Server:

- **Usuário**: criar usuário/senha (anotar; será o `sudo`).
- **Rede**: aceitar DHCP da **Default Switch (NAT)** — sai para a internet, o host alcança a VM.
- **OpenSSH**: marcar "Install OpenSSH server" (opcional, mas recomendado — permite SSH em vez de só console).
- Sem snaps extras necessários.

Após concluir e antes do primeiro boot do sistema instalado, remover o DVD do boot e reiniciar (PowerShell ELEVADO no host):

```powershell
# (ELEVADO) remove a ISO para a VM bootar do disco
Set-VMDvdDrive -VMName "tcc-f3" -Path $null
Restart-VM -Name "tcc-f3" -Force
```

Descobrir o IP da VM (para SSH), dentro da VM:

```bash
ip -br addr           # anota o IPv4 da interface NAT
```

---

## Passo 3 — Toolchain dentro da VM

```bash
sudo apt update && sudo apt -y upgrade
sudo apt install -y git podman openjdk-17-jdk maven python3-venv \
                    iproute2 linux-modules-extra-$(uname -r)
# linux-modules-extra-$(uname -r) é o pacote que traz o sch_netem
# (o motivo de existir a VM; o kernel do WSL2 não tem esse módulo).
```

Fallback Docker (só se o Podman travar com a imagem pinada):

```bash
sudo apt install -y docker.io docker-compose-v2
```

> Se `linux-modules-extra-$(uname -r)` reclamar de versão não encontrada, rodar `sudo apt update` de novo e/ou `uname -r` para conferir que o kernel rodando bate com o pacote. Se houve `apt upgrade` de kernel, **reiniciar a VM** antes de seguir, para que `uname -r` aponte o kernel novo.

---

## Passo 4 — GATE netem (DECISIVO)

Este é o passo que **diferencia a VM do WSL2**. Se ele falhar, o F3 continua **bloqueado**.

```bash
sudo modprobe sch_netem
lsmod | grep sch_netem        # ESPERADO: linha 'sch_netem ...' aparece

# Descobrir a interface NAT real (NÃO assumir eth0 cegamente):
ip -br link                   # tipicamente 'eth0' com Default Switch, mas confirmar

# Sonda no host da VM (troque eth0 pela iface mostrada acima, se diferente):
sudo tc qdisc add dev eth0 root netem delay 10ms && \
sudo tc qdisc show dev eth0 && \
sudo tc qdisc del dev eth0 root
```

**Esperado: PASS** — o `add` aceita, o `show` lista o netem, o `del` limpa. (No WSL2 isso falhava: `sch_netem` ausente.)

> **Se ESTE passo FALHAR** (módulo não carrega, ou `tc ... netem` é rejeitado): o F3 está **bloqueado** — NÃO adianta seguir aos passos 5/6. Provável causa: `linux-modules-extra` não corresponde ao `uname -r` (reinstalar o pacote certo + reboot da VM). Se mesmo assim falhar, **PARAR e reportar ao Gestor** — é o momento de reavaliar o escopo do F3 (possível empurrar para TCC-II).

---

## Passo 5 — Repo + cluster + validação

```bash
# 1) Clonar o repo (HTTPS público; ajustar URL se for outro remoto/branch)
git clone https://github.com/franpgn/tcc-desenvolvimento.git
cd tcc-desenvolvimento

# 2) Subir o cluster (imagem Infinispan pinada no compose)
podman-compose -f cluster/podman-compose.yml up -d

# 3) Confirmar a interface DENTRO do container alvo (pode diferir do host):
podman exec isn2 ip -br link    # confirma a iface egress do container (ver nota abaixo)

# 4) Validar saúde: cluster size=3 e HEALTHY (mesmo endpoint do run-baseline.sh)
curl -s --digest -u admin:infinispan \
  http://localhost:11222/rest/v2/cache-managers/default/health
# ESPERADO: health_status HEALTHY e number_of_nodes = 3

# 5) Empacotar o workload
mvn -f workload/pom.xml package

# 6) Smoke do jitter (T-jit-6): aplica/remove netem por 5s no container isn2.
#    Ajustar --iface para a iface CONFIRMADA no passo 5.3.
scripts/inject-jitter.sh --node isn2 --iface eth0 --duration 5
# ESPERADO: exit 0; durante a janela 'tc qdisc show' lista netem; ao fim, limpo.
```

> **Nota sobre a interface (P-2).** Na VM Hyper-V com Default Switch a iface **do host** costuma ser `eth0`, mas **confirme** com `ip -br link`. A iface que importa para o netem é a **de dentro do container** `isn2` (egress), obtida com `podman exec isn2 ip -br link` — geralmente `eth0`, mas se for outra (ex.: `enp0s3` em alguns layouts), passar `--iface <nome>` em `inject-jitter.sh` e `--jitter-iface <nome>` em `run-baseline.sh`.

---

## Passo 6 — Bateria (re-smoke F1, depois F3)

**6a) Re-smoke F1** — o ramo F1 do `run-baseline.sh` foi **refatorado** ao integrar o F3 (ressalva do Senior no B-12); revalidar 1 rep antes da bateria pesada:

```bash
scripts/run-baseline.sh --faults "none F1" --reps 1
# ESPERADO: gera runs/<cen>-none e runs/<cen>-F1 sem erro; F1 derruba/reativa isn2,
# cluster volta a size=3 HEALTHY entre reps.
```

**6b) Bateria F3** — cenários S1/S2, 3 reps, janela de 90 s:

```bash
scripts/run-baseline.sh --faults "none F3" --scenarios "S1 S2" --reps 3 --duration 90
# Se a iface do container não for eth0, acrescentar: --jitter-iface <nome>
# ESPERADO: runs/S1-F3/ e runs/S2-F3/ com rep-001..003.csv + comparacao.json (none x F3).
```

Conferir os artefatos:

```bash
ls runs/S1-F3 runs/S2-F3
cat runs/S1-F3/comparacao.json   # bloco none x F3
```

> Antes da bateria longa, vale um dry-run de sanidade (não exige netem):
> `scripts/run-baseline.sh --faults "none F3" --scenarios S1 --reps 1 --duration 60 --dry-run`

---

## Resumo dos gates

| Gate | Comando-chave | PASS = |
|---|---|---|
| Hyper-V | `Get-Service vmms; Get-VM` (elevado) | responde sem reboot |
| netem (host VM) | `sudo tc qdisc add dev <if> root netem delay 10ms` | aceito (≠ WSL2) |
| netem (container) | `inject-jitter.sh --node isn2 --duration 5` | exit 0, sem resíduo |
| cluster | `curl .../health` | `HEALTHY`, `number_of_nodes=3` |
| bateria F3 | `run-baseline.sh --faults "none F3" ...` | `runs/*-F3/comparacao.json` |

Se o gate netem (host ou container) falhar, **parar e reportar** — F3 fica bloqueado e entra reavaliação de escopo (TCC-II).
