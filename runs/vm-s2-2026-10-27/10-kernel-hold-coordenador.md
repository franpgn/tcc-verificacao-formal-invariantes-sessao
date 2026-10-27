# Hold do kernel na VM (coordenador, 2026-10-27 21:08 UTC), autorizado pelo Gestor
- `systemctl stop apt-daily-upgrade.service` rc 0; processo u-u 2714 (desde 21/10) seguia com o lock do dpkg.
- `systemctl kill --signal=TERM apt-daily-upgrade.service` (cgroup inteiro): u-u, needrestart e systemctl encerrados; lock do dpkg livre; `dpkg --audit` rc 0 (nada pela metade); nenhum pacote instalado.
- `apt-mark hold` rc 0: linux-image/modules/modules-extra/headers-6.8.0-147-generic, linux-generic, linux-image-generic, linux-headers-generic.
- `systemctl disable --now fwupd-refresh.timer` rc 0; `systemctl stop fwupd-refresh.service` (job 956 pendurado desde 21/10) rc 0; `systemctl stop unattended-upgrades.service` rc 0.
- is-enabled: unattended-upgrades.service, apt-daily.timer, apt-daily-upgrade.timer, fwupd-refresh.timer = disabled. `reset-failed`; sistema `running`.
- uname -r 6.8.0-147-generic; isn1..3 Up (healthy); lock tcc-vm livre; sem tmux.
