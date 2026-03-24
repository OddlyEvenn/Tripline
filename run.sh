#!/bin/bash
echo "Starting Tripline Services..."

# Start Backend in background
echo "Starting Backend (Spring Boot)..."
(cd Tripline_Backend && ./mvnw spring-boot:run) &

# Start Frontend in background
echo "Starting Frontend (Vite)..."
(cd Tripline_Frontend && npm run dev) &

echo "Services are starting in the background."
echo "Use 'stop_services.sh' to stop them."
