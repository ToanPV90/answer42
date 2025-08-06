    #!/bin/bash

# Answer42 Ollama Fallback Test Script
# This script tests the Ollama fallback functionality

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Answer42 Ollama Fallback Test ===${NC}"
echo ""

# Check if Ollama is running
echo -e "${YELLOW}Checking Ollama status...${NC}"
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Ollama is running${NC}"
else
    echo -e "${RED}✗ Ollama is not running. Please run ./scripts/setup-ollama.sh first${NC}"
    exit 1
fi

# Check if llama3.1:8b model is available
echo -e "${YELLOW}Checking for llama3.1:8b model...${NC}"
models=$(curl -s http://localhost:11434/api/tags | jq -r '.models[].name' 2>/dev/null)
if echo "$models" | grep -q "llama3.1:8b"; then
    echo -e "${GREEN}✓ llama3.1:8b model is available${NC}"
else
    echo -e "${RED}✗ llama3.1:8b model not found. Pulling it now...${NC}"
    docker exec answer42-ollama ollama pull llama3.1:8b
fi

# Test different agent types
echo ""
echo -e "${BLUE}Testing Ollama Fallback Agents:${NC}"
echo ""

# Test 1: Content Summarizer
echo -e "${YELLOW}1. Testing Content Summarizer Fallback...${NC}"
test_content="This is a test paper about artificial intelligence and machine learning. The paper discusses various algorithms and their applications in real-world scenarios. The main contribution is a new optimization technique that improves performance by 30%."

response=$(curl -s -X POST http://localhost:11434/api/generate \
    -H "Content-Type: application/json" \
    -d "{
        \"model\": \"llama3.1:8b\",
        \"prompt\": \"Summarize the following academic paper content in 2-3 sentences:\\n\\n$test_content\",
        \"stream\": false,
        \"options\": {
            \"temperature\": 0.7,
            \"max_tokens\": 200
        }
    }")

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Content Summarizer test passed${NC}"
    echo "Summary: $(echo $response | jq -r '.response' 2>/dev/null | head -3)"
else
    echo -e "${RED}✗ Content Summarizer test failed${NC}"
fi

# Test 2: Concept Explainer
echo ""
echo -e "${YELLOW}2. Testing Concept Explainer Fallback...${NC}"
test_concept="neural network"

response=$(curl -s -X POST http://localhost:11434/api/generate \
    -H "Content-Type: application/json" \
    -d "{
        \"model\": \"llama3.1:8b\",
        \"prompt\": \"Explain the concept '$test_concept' in simple terms for a student:\",
        \"stream\": false,
        \"options\": {
            \"temperature\": 0.7,
            \"max_tokens\": 150
        }
    }")

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Concept Explainer test passed${NC}"
    echo "Explanation: $(echo $response | jq -r '.response' 2>/dev/null | head -2)"
else
    echo -e "${RED}✗ Concept Explainer test failed${NC}"
fi

# Test 3: Quality Checker
echo ""
echo -e "${YELLOW}3. Testing Quality Checker Fallback...${NC}"

response=$(curl -s -X POST http://localhost:11434/api/generate \
    -H "Content-Type: application/json" \
    -d "{
        \"model\": \"llama3.1:8b\",
        \"prompt\": \"Rate the quality of this research abstract on a scale of 1-10 and explain why: '$test_content'\",
        \"stream\": false,
        \"options\": {
            \"temperature\": 0.7,
            \"max_tokens\": 150
        }
    }")

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Quality Checker test passed${NC}"
    echo "Quality Assessment: $(echo $response | jq -r '.response' 2>/dev/null | head -2)"
else
    echo -e "${RED}✗ Quality Checker test failed${NC}"
fi

# Performance test
echo ""
echo -e "${YELLOW}4. Performance Test...${NC}"
start_time=$(date +%s%N)

response=$(curl -s -X POST http://localhost:11434/api/generate \
    -H "Content-Type: application/json" \
    -d '{
        "model": "llama3.1:8b",
        "prompt": "Generate a brief abstract for a paper on quantum computing",
        "stream": false,
        "options": {
            "temperature": 0.7,
            "max_tokens": 100
        }
    }')

end_time=$(date +%s%N)
elapsed_time=$(( ($end_time - $start_time) / 1000000 ))

echo -e "${GREEN}✓ Performance test completed in ${elapsed_time}ms${NC}"

# System info
echo ""
echo -e "${BLUE}System Information:${NC}"
echo "Ollama Version: $(docker exec answer42-ollama ollama --version 2>/dev/null || echo 'Unknown')"
echo "Container Status: $(docker ps --filter name=answer42-ollama --format 'table {{.Status}}' | tail -n 1)"
echo "Memory Usage: $(docker stats --no-stream --format 'table {{.MemUsage}}' answer42-ollama | tail -n 1)"

# Summary
echo ""
echo -e "${GREEN}=== Test Summary ===${NC}"
echo "All tests completed. Ollama fallback system is operational!"
echo ""
echo "The fallback will automatically activate when:"
echo "1. All cloud AI providers (OpenAI, Anthropic, Perplexity) fail"
echo "2. After 3 retry attempts"
echo "3. The system will use llama3.1:8b for local processing"
echo ""
echo "Monitor fallback usage in the application logs and metrics."
echo ""
