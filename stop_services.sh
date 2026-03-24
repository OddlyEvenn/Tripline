#!/bin/bash
echo "Stopping Tripline Services..."

# Port 8080 for Spring Boot
echo "Killing Backend on port 8080..."
fuser -k 8080/tcp 2>/dev/null || kill $(lsof -t -i:8080) 2>/dev/null

# Port 5173 for Vite
echo "Killing Frontend on port 5173..."
fuser -k 5173/tcp 2>/dev/null || kill $(lsof -t -i:5173) 2>/dev/null

# Batch version kill (for Windows users running this in bash)
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    echo "Windows detected, using taskkill to be sure..."
    taskkill //F //IM java.exe //T 2>/dev/null
    taskkill //F //IM node.exe //T 2>/dev/null
fi

echo "Services stopped."
