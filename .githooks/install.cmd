@echo off
REM git 훅 활성화 (Windows 명령 프롬프트)
REM 저장소를 클론한 뒤 한 번만 실행하면 된다.
git config core.hooksPath .githooks
echo 완료: git 훅이 활성화되었습니다. (core.hooksPath = .githooks)
