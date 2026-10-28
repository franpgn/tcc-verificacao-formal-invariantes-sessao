cd ~/tcc-gate
echo "### vm_inicio=$(date -Is) (so leitura, V-S3)"
echo "### git"; git log --oneline -1; git status --porcelain | wc -l
echo "### hashes"; sha256sum runs/gate-netdegrade/l0c.sh runs/gate-netdegrade/l-lib.sh scripts/inject-netdegrade.sh runs/gate-b29/g5b.sh runs/vm-s2-2026-10-27/portao51.sh
echo "### stat (mtime ns)"; stat -c '%y %n' runs/vm-s3-2026-10-28/* runs/gate-netdegrade/l0c/* runs/gate-netdegrade/go.log
echo "### go.log"; cat -n runs/gate-netdegrade/go.log
echo "### journal unit precise"; sudo -n journalctl -u tcc-heal-356755-18620.service -u tcc-heal-356755-18620.timer --no-pager -o short-iso-precise | grep -v 'pam_unix\|COMMAND='
echo "### journal systemd sobre a unit (rc/CPU)"; sudo -n journalctl _PID=1 --since '2026-10-28 04:59:00' --until '2026-10-28 05:01:30' --no-pager -o short-iso-precise | grep tcc-heal
echo "### sudo COMMAND 04:55..05:02 (sem leituras do coordenador)"; sudo -n journalctl _COMM=sudo --since '2026-10-28 04:55:00' --until '2026-10-28 05:02:00' --no-pager -o short-iso-precise | grep 'COMMAND=' | grep -Ev 'podman inspect|readlink|qdisc show|filter show|journalctl|systemctl (cat|show)|/usr/bin/true$|curl' | sed 's/.*sudo\[[0-9]*\]: *//' | sort | uniq -c
echo "### sudo tc de escrita / systemd-run / heal 04:50..agora"; sudo -n journalctl _COMM=sudo --since '2026-10-28 04:50:00' --no-pager -o short-iso-precise | grep 'COMMAND=' | grep -E 'qdisc (add|del|change|replace)|filter (add|del)|systemd-run|inject-netdegrade|systemctl (stop|start|disable)' 
echo "### timers/units"; systemctl list-timers --all | grep -c tcc-heal; systemctl list-units --all | grep -c tcc-heal
echo "### tc nos 3 netns"; for n in isn1 isn2 isn3; do p=$(sudo -n podman inspect -f '{{.State.Pid}}' $n); echo "$n $p $(sudo -n nsenter -t $p -n tc qdisc show | tr '\n' ' ') filtros=$(sudo -n nsenter -t $p -n tc filter show dev eth0 | wc -l)"; done
echo "### vistas desde 04:55"; for n in isn1 isn2 isn3; do echo "$n $(sudo -n podman logs --since 2026-10-28T04:55:00Z $n 2>&1 | grep -cE 'ISPN000094|ISPN000093|ISPN000436|SUSPECT|MergeView|ISPN100000|ISPN100001')"; done
echo "### ultima vista"; for n in isn1 isn2 isn3; do sudo -n podman logs $n 2>&1 | grep -E 'ISPN000094|ISPN000093' | tail -1; done
echo "### health agora"; for p in 11222 11223 11224; do curl -s -m 5 http://127.0.0.1:$p/rest/v2/cache-managers/default/health/status; echo " $p"; done
echo "### lock"; flock -n /var/lock/tcc-vm.lock true && echo LIVRE || echo OCUPADO; ls /tmp | grep -i 'inject\|netdeg' || echo "(sem locks do injetor em /tmp)"
echo "### tmux"; tmux ls 2>&1
echo "### uname/boot"; uname -r; cat /proc/sys/kernel/random/boot_id
echo "### vm_fim=$(date -Is)"
