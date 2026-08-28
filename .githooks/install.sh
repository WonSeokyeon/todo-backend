#!/bin/sh
# git 훅 활성화 (macOS / Linux / Git Bash)
# 저장소를 클론한 뒤 한 번만 실행하면 된다.
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit .githooks/commit-msg .githooks/pre-push 2>/dev/null
echo "✔ git 훅이 활성화되었습니다. (core.hooksPath = .githooks)"
