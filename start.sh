#!/bin/bash
set -e

echo "🚀 启动 AdSim..."

# 后端
echo ""
echo "📦 启动后端 (port 8180)..."
cd "$(dirname "$0")/server"
./gradlew bootRun --args='--spring.profiles.active=local' &
BACKEND_PID=$!

# 前端
echo "📦 启动前端 (port 5173)..."
cd "$(dirname "$0")/web"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "✅ 已启动:"
echo "  后端 PID=$BACKEND_PID -> http://localhost:8180"
echo "  前端 PID=$FRONTEND_PID -> http://localhost:5173"
echo ""
echo "按 Ctrl+C 停止所有服务"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" SIGINT SIGTERM
wait
