#!/usr/bin/env bash
# EC2(Amazon Linux 2023)에 todo-backend 배포 환경을 처음 한 번 갖추는 스크립트.
# 이미 실행 중인 인스턴스를 전제로 하며, 여러 번 실행해도 안전하게(idempotent) 만든다.
#
# 사용법: sudo bash install.sh
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "root 권한이 필요합니다. sudo bash install.sh 로 실행하세요." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR=/etc/todolist
SERVICE_NAME=todolist

echo "[1/6] Java 21(headless) 설치 확인"
if ! rpm -q java-21-amazon-corretto-headless >/dev/null 2>&1; then
  dnf install -y java-21-amazon-corretto-headless
else
  echo "  이미 설치되어 있음 — 건너뜀"
fi
java -version

echo "[2/6] 서비스 전용 계정(${SERVICE_NAME}) 생성 확인"
if ! id "${SERVICE_NAME}" >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /sbin/nologin "${SERVICE_NAME}"
else
  echo "  이미 존재함 — 건너뜀"
fi

echo "[3/6] 설정 디렉토리(${APP_DIR}) 준비"
mkdir -p "${APP_DIR}"
chown root:"${SERVICE_NAME}" "${APP_DIR}"
chmod 750 "${APP_DIR}"
echo "  ⚠ WinSCP로 이 경로에 직접 파일을 올리려면, 사이트 고급 설정 > 환경 > SFTP 에서"
echo "    'sudo로 권한 상승'을 켜야 합니다(이 경로는 root 소유입니다)."

echo "[4/6] 스왑 1GB 확인/생성 (RAM 1GB인 t2.micro에서 OOM 방지)"
if swapon --show=NAME --noheadings | grep -q '^/swapfile$'; then
  echo "  이미 활성화되어 있음 — 건너뜀"
elif [[ -f /swapfile ]]; then
  echo "  /swapfile은 있으나 비활성 상태 — swapon만 실행"
  swapon /swapfile
else
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  if ! grep -q '^/swapfile ' /etc/fstab; then
    echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
  fi
fi
free -h

echo "[5/6] systemd 유닛·JVM 옵션 파일 설치"
cp "${SCRIPT_DIR}/todolist.service" /etc/systemd/system/todolist.service
cp "${SCRIPT_DIR}/todolist.conf" "${APP_DIR}/todolist.conf"
# todolist.env(비밀값)는 이 스크립트가 건드리지 않는다 — WinSCP로 직접 올린다.

echo "[6/6] systemd 등록"
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"

echo
echo "===================================================================="
if [[ -f "${APP_DIR}/app.jar" && -f "${APP_DIR}/todolist.env" ]]; then
  echo "app.jar / todolist.env가 이미 존재합니다. 바로 시작할 수 있습니다:"
  echo "  sudo systemctl start ${SERVICE_NAME}"
else
  echo "환경 준비가 끝났습니다. 아직 서비스는 시작하지 않았습니다."
  echo "다음을 WinSCP로 ${APP_DIR}/ 에 업로드한 뒤 시작하세요:"
  [[ -f "${APP_DIR}/app.jar" ]]      || echo "  - app.jar (로컬에서 ./mvnw clean package 로 빌드한 target/app.jar)"
  [[ -f "${APP_DIR}/todolist.env" ]] || echo "  - todolist.env (todolist.env.example을 채운 파일)"
  echo "업로드 후: sudo systemctl start ${SERVICE_NAME}"
fi
echo "상태 확인: sudo systemctl status ${SERVICE_NAME}"
echo "로그 확인: sudo journalctl -u ${SERVICE_NAME} -f"
echo "===================================================================="
