#!/bin/bash

# Answer42 Ollama Local Fallback Setup Script
# This script sets up and tests the Ollama integration for Answer42

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Answer42 Ollama Local Fallback Setup ===${NC}"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}Error: Docker Compose is not installed. Please install Docker Compose first.${NC}"
    exit 1
fi

# Start Ollama container
echo -e "${YELLOW}Starting Ollama container...${NC}"
docker-compose -f docker-compose.ollama.yml up -d

# Wait for Ollama to be ready
echo -e "${YELLOW}Waiting for Ollama to be ready...${NC}"
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo -e "${GREEN}Ollama is ready!${NC}"
        break
    fi
    echo -n "."
    sleep 2
    attempt=$((attempt + 1))
done

if [ $attempt -eq $max_attempts ]; then
    echo -e "${RED}Error: Ollama failed to start within 60 seconds${NC}"
    exit 1
fi

# Pull required models
echo ""
echo -e "${YELLOW}Pulling required Ollama models...${NC}"

# Primary model
echo -e "${YELLOW}Pulling llama3.1:8b (primary fallback model)...${NC}"
docker exec answer42-ollama ollama pull llama3.1:8b

# Optional: Pull alternative models
read -p "Do you want to pull additional models? (y/n) " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Pulling mistral:7b...${NC}"
    docker exec answer42-ollama ollama pull mistral:7b
    
    echo -e "${YELLOW}Pulling codellama:7b...${NC}"
    docker exec answer42-ollama ollama pull codellama:7b
fi

# Test Ollama API
echo ""
echo -e "${YELLOW}Testing Ollama API...${NC}"

# Test generation endpoint
test_response=$(curl -s -X POST http://localhost:11434/api/generate \
    -H "Content-Type: application/json" \
    -d '{
        "model": "llama3.1:8b",
        "prompt": "Hello, this is a test. Please respond with OK.",
        "stream": false,
        "options": {
            "temperature": 0.7,
            "max_tokens": 50
        }
    }')

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Ollama API test successful${NC}"
    echo "Response: $(echo $test_response | jq -r '.response' 2>/dev/null || echo $test_response)"
else
    echo -e "${RED}✗ Ollama API test failed${NC}"
fi

# Check available models
echo ""
echo -e "${YELLOW}Available Ollama models:${NC}"
curl -s http://localhost:11434/api/tags | jq -r '.models[].name' 2>/dev/null || echo "Unable to fetch models"

# Display configuration summary
echo ""
echo -e "${GREEN}=== Configuration Summary ===${NC}"
echo "Ollama URL: http://localhost:11434"
echo "Primary Model: llama3.1:8b"
echo "Container Name: answer42-ollama"
echo "Data Volume: ollama-data"
echo ""

# Check if .env file exists
if [ -f ".env" ]; then
    echo -e "${GREEN}✓ .env file found${NC}"
    
    # Check Ollama configuration in .env
    if grep -q "OLLAMA_ENABLED=true" .env; then
        echo -e "${GREEN}✓ Ollama is enabled in .env${NC}"
    else
        echo -e "${YELLOW}! Ollama is not enabled in .env. Add OLLAMA_ENABLED=true to enable fallback${NC}"
    fi
else
    echo -e "${YELLOW}! No .env file found. Make sure to configure Ollama in your .env file${NC}"
fi

echo ""
echo -e "${GREEN}=== Setup Complete ===${NC}"
echo "Ollama is now running and ready for use as a fallback provider!"
echo ""
echo "To stop Ollama: docker-compose -f docker-compose.ollama.yml down"
echo "To view logs: docker logs answer42-ollama"
echo "To pull more models: docker exec answer42-ollama ollama pull <model-name>"
echo ""
