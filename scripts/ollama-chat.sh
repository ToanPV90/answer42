#!/bin/bash

# Answer42 Ollama Chat Interface
# A clean TUI for interacting with the local Ollama service

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
BOLD='\033[1m'
DIM='\033[2m'

# Configuration
OLLAMA_URL="http://localhost:11434"
MODEL="llama3.1:8b"
TEMP=0.7
MAX_TOKENS=500

# Function to print header
print_header() {
    clear
    echo -e "${BOLD}${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${BLUE}║              Answer42 Ollama Chat Interface              ║${NC}"
    echo -e "${BOLD}${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
    echo -e "${DIM}Connected to: ${OLLAMA_URL}${NC}"
    echo -e "${DIM}Model: ${MODEL}${NC}"
    echo -e "${DIM}Temperature: ${TEMP} | Max Tokens: ${MAX_TOKENS}${NC}"
    echo ""
}

# Function to check if Ollama is running
check_ollama() {
    echo -e "${YELLOW}Checking Ollama connection...${NC}"
    if ! curl -s "${OLLAMA_URL}/api/tags" > /dev/null 2>&1; then
        echo -e "${RED}❌ Error: Cannot connect to Ollama at ${OLLAMA_URL}${NC}"
        echo -e "${YELLOW}Please make sure Ollama is running:${NC}"
        echo -e "${CYAN}  docker-compose -f docker-compose.ollama.yml up -d${NC}"
        exit 1
    fi
    
    # Check if model is available
    if ! curl -s "${OLLAMA_URL}/api/tags" | jq -r '.models[].name' | grep -q "${MODEL}"; then
        echo -e "${RED}❌ Error: Model ${MODEL} not found${NC}"
        echo -e "${YELLOW}Pulling model...${NC}"
        docker exec answer42-ollama ollama pull "${MODEL}"
    fi
    
    echo -e "${GREEN}✅ Connected to Ollama successfully${NC}"
    echo ""
}

# Function to format and display response
display_response() {
    local response="$1"
    local model=$(echo "$response" | jq -r '.model // "unknown"')
    local text=$(echo "$response" | jq -r '.response // .message.content // "No response"')
    local created=$(echo "$response" | jq -r '.created_at // ""')
    local total_duration=$(echo "$response" | jq -r '.total_duration // 0')
    local eval_count=$(echo "$response" | jq -r '.eval_count // 0')
    local eval_duration=$(echo "$response" | jq -r '.eval_duration // 0')
    
    # Calculate metrics
    local duration_seconds=0
    local tokens_per_second=0
    if [ "$total_duration" != "0" ] && [ "$total_duration" != "null" ]; then
        duration_seconds=$(echo "scale=2; $total_duration / 1000000000" | bc -l 2>/dev/null || echo "0")
    fi
    if [ "$eval_count" != "0" ] && [ "$eval_count" != "null" ] && [ "$eval_duration" != "0" ] && [ "$eval_duration" != "null" ]; then
        tokens_per_second=$(echo "scale=1; $eval_count * 1000000000 / $eval_duration" | bc -l 2>/dev/null || echo "0")
    fi
    
    echo -e "${BOLD}${GREEN}🤖 Ollama Response:${NC}"
    echo -e "${CYAN}┌─────────────────────────────────────────────────────────────┐${NC}"
    
    # Format and display the response text with proper word wrapping
    echo "$text" | fold -s -w 59 | while IFS= read -r line; do
        echo -e "${CYAN}│${NC} $(printf "%-57s" "$line") ${CYAN}│${NC}"
    done
    
    echo -e "${CYAN}└─────────────────────────────────────────────────────────────┘${NC}"
    
    # Display metrics if available
    if [ "$duration_seconds" != "0" ] && [ "$eval_count" != "0" ]; then
        echo -e "${DIM}📊 Metrics: ${duration_seconds}s total | ${eval_count} tokens | ${tokens_per_second} tokens/sec${NC}"
    fi
    echo ""
}

# Function to send question to Ollama
ask_ollama() {
    local question="$1"
    
    echo -e "${YELLOW}🤔 Thinking...${NC}"
    
    # Prepare JSON payload
    local json_payload=$(jq -n \
        --arg model "$MODEL" \
        --arg prompt "$question" \
        --argjson stream false \
        --argjson temp "$TEMP" \
        --argjson max_tokens "$MAX_TOKENS" \
        '{
            model: $model,
            prompt: $prompt,
            stream: $stream,
            options: {
                temperature: $temp,
                num_predict: $max_tokens
            }
        }')
    
    # Make request to Ollama
    local response=$(curl -s -X POST "${OLLAMA_URL}/api/generate" \
        -H "Content-Type: application/json" \
        -d "$json_payload")
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Error: Failed to connect to Ollama${NC}"
        return 1
    fi
    
    # Check if response contains error
    if echo "$response" | jq -e '.error' > /dev/null 2>&1; then
        local error_msg=$(echo "$response" | jq -r '.error')
        echo -e "${RED}❌ Error: $error_msg${NC}"
        return 1
    fi
    
    display_response "$response"
}

# Function to show available models
show_models() {
    echo -e "${YELLOW}Available models:${NC}"
    local models=$(curl -s "${OLLAMA_URL}/api/tags" | jq -r '.models[]? | "\(.name) (\(.size/1000000000 | floor)GB)"' 2>/dev/null)
    if [ -n "$models" ]; then
        echo "$models" | while read -r model; do
            echo -e "${CYAN}  • ${model}${NC}"
        done
    else
        echo -e "${RED}  No models found${NC}"
    fi
    echo ""
}

# Function to show help
show_help() {
    echo -e "${BOLD}${BLUE}Available Commands:${NC}"
    echo -e "${CYAN}  /help${NC}     - Show this help message"
    echo -e "${CYAN}  /models${NC}   - Show available models"
    echo -e "${CYAN}  /clear${NC}    - Clear the screen"
    echo -e "${CYAN}  /config${NC}   - Show current configuration"
    echo -e "${CYAN}  /quit${NC}     - Exit the chat"
    echo -e "${CYAN}  /temp <n>${NC} - Set temperature (0.0-2.0)"
    echo -e "${CYAN}  /tokens <n>${NC} - Set max tokens (1-2000)"
    echo ""
    echo -e "${YELLOW}Tips:${NC}"
    echo -e "${DIM}  • Press Enter on empty line to repeat last question${NC}"
    echo -e "${DIM}  • Use Ctrl+C to interrupt long responses${NC}"
    echo -e "${DIM}  • Temperature controls creativity (higher = more creative)${NC}"
    echo ""
}

# Function to show config
show_config() {
    echo -e "${BOLD}${BLUE}Current Configuration:${NC}"
    echo -e "${CYAN}  URL:       ${NC}${OLLAMA_URL}"
    echo -e "${CYAN}  Model:     ${NC}${MODEL}"
    echo -e "${CYAN}  Temperature: ${NC}${TEMP}"
    echo -e "${CYAN}  Max Tokens:  ${NC}${MAX_TOKENS}"
    echo ""
}

# Interactive chat function
interactive_chat() {
    local last_question=""
    
    while true; do
        echo -e "${BOLD}${PURPLE}💬 Ask me anything (or type /help for commands):${NC}"
        echo -ne "${GREEN}❯ ${NC}"
        read -r input
        
        # Handle empty input (repeat last question)
        if [ -z "$input" ] && [ -n "$last_question" ]; then
            input="$last_question"
            echo -e "${DIM}Repeating: $input${NC}"
        fi
        
        # Handle commands
        case "$input" in
            "/help")
                show_help
                continue
                ;;
            "/models")
                show_models
                continue
                ;;
            "/clear")
                print_header
                continue
                ;;
            "/config")
                show_config
                continue
                ;;
            "/quit"|"/exit")
                echo -e "${YELLOW}👋 Goodbye!${NC}"
                exit 0
                ;;
            "/temp "*)
                new_temp="${input#/temp }"
                if [[ "$new_temp" =~ ^[0-9]*\.?[0-9]+$ ]] && (( $(echo "$new_temp <= 2.0" | bc -l) )) && (( $(echo "$new_temp >= 0.0" | bc -l) )); then
                    TEMP="$new_temp"
                    echo -e "${GREEN}✅ Temperature set to ${TEMP}${NC}"
                else
                    echo -e "${RED}❌ Invalid temperature. Use a number between 0.0 and 2.0${NC}"
                fi
                continue
                ;;
            "/tokens "*)
                new_tokens="${input#/tokens }"
                if [[ "$new_tokens" =~ ^[0-9]+$ ]] && [ "$new_tokens" -ge 1 ] && [ "$new_tokens" -le 2000 ]; then
                    MAX_TOKENS="$new_tokens"
                    echo -e "${GREEN}✅ Max tokens set to ${MAX_TOKENS}${NC}"
                else
                    echo -e "${RED}❌ Invalid token count. Use a number between 1 and 2000${NC}"
                fi
                continue
                ;;
            "")
                if [ -z "$last_question" ]; then
                    echo -e "${YELLOW}💡 Enter a question or type /help for commands${NC}"
                    continue
                fi
                ;;
        esac
        
        # Ask Ollama
        if [ -n "$input" ]; then
            last_question="$input"
            ask_ollama "$input"
        fi
    done
}

# Single question mode
single_question_mode() {
    local question="$1"
    ask_ollama "$question"
}

# Main function
main() {
    # Check if jq and bc are available
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}Error: 'jq' is required but not installed.${NC}"
        echo -e "${YELLOW}Install with: sudo apt install jq${NC}"
        exit 1
    fi
    
    if ! command -v bc &> /dev/null; then
        echo -e "${RED}Error: 'bc' is required but not installed.${NC}"
        echo -e "${YELLOW}Install with: sudo apt install bc${NC}"
        exit 1
    fi
    
    print_header
    check_ollama
    
    # Check if question provided as argument
    if [ $# -gt 0 ]; then
        local question="$*"
        echo -e "${BOLD}${PURPLE}❓ Question: ${NC}${question}"
        echo ""
        single_question_mode "$question"
    else
        show_help
        interactive_chat
    fi
}

# Handle Ctrl+C gracefully
trap 'echo -e "\n${YELLOW}👋 Chat interrupted. Goodbye!${NC}"; exit 0' INT

# Run main function
main "$@"
