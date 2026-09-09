#!/usr/bin/env bash
# 재배포 스크립트. 전제: 새 jar가 WinSCP로 /etc/todolist/app.jar.new 로
# 이미 업로드되어 있어야 한다(운영 중인 app.jar를 직접 덮어쓰지 않도록 파일명을 분리했다).
#
# 사용법: sudo bash redeploy.sh
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "root 권한이 필요합니다. sudo bash redeploy.sh 로 실행하세요." >&2
  exit 1
fi

APP_DIR=/etc/todolist
SERVICE_NAME=todolist
NEW_JAR="${APP_DIR}/app.jar.new"
CUR_JAR="${APP_DIR}/app.jar"
BAK_JAR="${APP_DIR}/app.jar.bak"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"

if [[ ! -f "${NEW_JAR}" ]]; then
  echo "에러: ${NEW_JAR} 가 없습니다." >&2
  echo "먼저 로컬에서 ./mvnw clean package 로 빌드한 target/app.jar 를" >&2
  echo "WinSCP로 ${NEW_JAR} 이름으로 업로드하세요." >&2
  exit 1
fi

echo "[1/4] 기존 jar 백업"
if [[ -f "${CUR_JAR}" ]]; then
  cp "${CUR_JAR}" "${BAK_JAR}"
fi

echo "[2/4] 새 jar로 교체"
mv "${NEW_JAR}" "${CUR_JAR}"

echo "[3/4] 서비스 재시작"
systemctl restart "${SERVICE_NAME}"

echo "[4/4] 헬스체크 대기 (최대 30초)"
ok=false
for i in $(seq 1 15); do
  if curl -sf "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
    ok=true
    break
  fi
  sleep 2
done

echo
if [[ "${ok}" == "true" ]]; then
  echo "===================================================================="
  echo "배포 완료 — ${HEALTH_URL} 정상 응답 확인"
  echo "===================================================================="
else
  echo "===================================================================="
  echo "배포 실패 — 헬스체크가 30초 내에 통과하지 못했습니다."
  echo "최근 로그:"
  journalctl -u "${SERVICE_NAME}" -n 50 --no-pager
  echo "--------------------------------------------------------------------"
  echo "이전 버전으로 되돌리려면:"
  echo "  sudo cp ${BAK_JAR} ${CUR_JAR} && sudo systemctl restart ${SERVICE_NAME}"
  echo "===================================================================="
  exit 1
fi
