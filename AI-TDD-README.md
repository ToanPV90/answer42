# AI-TDD (AI-Driven Test-Driven Development) Guide

## Overview

AI-TDD is a revolutionary development methodology that combines the rigor of Test-Driven Development with the power of AI agents to create a fully automated, quality-assured development pipeline. This system transforms feature ideas into production-ready code through a structured, agent-driven workflow.

## Methodology Pipeline

The AI-TDD methodology follows a strict 6-stage pipeline:

```
IDEA → PRD → DESIGN → PLAN → IMPLEMENTATION → TESTING
  ↓      ↓      ↓       ↓         ↓            ↓
Agent1  Agent2  Agent3  Agent4   Agent5     Agent6
```

Each stage has a dedicated AI agent that produces structured documentation and executable code, ensuring complete traceability and quality at every step.

## AI Agents Overview

### 🔮 1. Idea Generator Agent
**Purpose**: Transform feature concepts into structured idea documents  
**Input**: Feature description or problem statement  
**Output**: `idea.md` with technical feasibility and scope  
**Location**: `.claude/agents/idea-generator.json`

### 📋 2. PRD Creator Agent  
**Purpose**: Convert ideas into comprehensive Product Requirements Documents  
**Input**: `idea.md`  
**Output**: `PRD.md` with user stories and acceptance criteria  
**Location**: `.claude/agents/prd-creator.json`

### 🏗️ 3. Design Document Agent
**Purpose**: Transform requirements into technical architecture  
**Input**: `PRD.md`  
**Output**: `design.md` with system design and API specifications  
**Location**: `.claude/agents/design-document.json`

### 📝 4. Plan Creator Agent
**Purpose**: Break down design into actionable implementation steps  
**Input**: `design.md`  
**Output**: `plan.md` with numbered tasks and verification criteria  
**Location**: `.claude/agents/plan-creator.json`

### 💻 5. Code Implementer Agent
**Purpose**: Execute implementation steps with quality enforcement  
**Input**: `plan.md`  
**Output**: Production-ready code with automated quality checks  
**Location**: `.claude/agents/code-implementer.json`

### 🧪 6. Test Generator Agent
**Purpose**: Generate comprehensive unit tests using Symflower  
**Input**: Implemented code  
**Output**: Test files with 80%+ coverage  
**Location**: `.claude/agents/test-generator.json`

## Prerequisites

### Required Tools
- **Java 21** - Primary development language
- **Maven 3.8+** - Build automation
- **Spring Boot 3.4.5** - Application framework
- **Vaadin 24.7.3** - UI framework
- **PostgreSQL** - Database
- **Symflower CLI** - Automated test generation
- **Git** - Version control

### Installation Commands
```bash
# Install Symflower for test generation
curl -sSLf https://get.symflower.com/install | sh

# Verify installation
symflower --version

# Maven wrapper (already included)
./mvnw --version
```

### Environment Setup
```bash
# Clone repository
git clone https://github.com/samjd-zz/answer42.git
cd answer42

# Set up environment variables
cp .example.env .env
# Edit .env with your configurations

# Install dependencies
./mvnw clean install
```

## Usage Guide

### Complete Feature Development Workflow

#### Step 1: Generate Feature Idea
```bash
# Create feature folder first
mkdir -p ai-tdd-docs/real-time-collaboration

# Agent Command: idea-generator.json -> generate_idea
# Manual Process: Create idea.md file

echo "Feature: Real-time collaboration system" > feature-request.txt
# Use AI agent to generate comprehensive idea.md
```

**Example idea.md structure:**
```markdown
# Feature Idea: Real-time Collaboration System

## Overview
Enable multiple users to collaborate on papers in real-time with live editing and comments.

## Problem Statement
Current system doesn't support real-time collaboration for research teams.

## Proposed Solution
WebSocket-based real-time editing with conflict resolution.

## Expected Benefits
- Increased team productivity by 40%
- Reduced review cycles
- Better research quality through instant feedback

## Technical Considerations
- WebSocket integration with Vaadin
- Operational transformation algorithms
- Database synchronization challenges

## Initial Scope
Phase 1: Basic real-time text editing
Phase 2: Comment system and change tracking

## Success Criteria
- Sub-100ms latency for edits
- Support 10+ concurrent editors
- Zero data loss during conflicts
```

#### Step 2: Create Product Requirements Document
```bash
# Agent Command: prd-creator.json -> create_prd_from_idea
# Input: idea.md
# Output: PRD.md
```

**Agent execution parameters:**
```json
{
  "command": "create_prd_from_idea",
  "parameters": {
    "idea_file": "./ai-tdd-docs/real-time-collaboration/idea.md",
    "output_path": "./ai-tdd-docs/real-time-collaboration/PRD.md"
  }
}
```

#### Step 3: Generate Technical Design
```bash
# Agent Command: design-document.json -> create_design_from_prd
# Input: PRD.md
# Output: design.md
```

**Agent execution parameters:**
```json
{
  "command": "create_design_from_prd",
  "parameters": {
    "prd_file": "./ai-tdd-docs/real-time-collaboration/PRD.md",
    "architecture_style": "event-driven",
    "output_path": "./ai-tdd-docs/real-time-collaboration/design.md"
  }
}
```

#### Step 4: Create Implementation Plan
```bash
# Agent Command: plan-creator.json -> create_implementation_plan
# Input: design.md
# Output: plan.md
```

**Agent execution parameters:**
```json
{
  "command": "create_implementation_plan",
  "parameters": {
    "design_file": "./ai-tdd-docs/real-time-collaboration/design.md",
    "timeline": "2 weeks",
    "output_path": "./ai-tdd-docs/real-time-collaboration/plan.md"
  }
}
```

#### Step 5: Execute Implementation
```bash
# Agent Command: code-implementer.json -> implement_next_step
# Input: plan.md
# Output: Source code files

# Quality checks automatically run:
./mvnw test                 # Unit tests
./mvnw checkstyle:check    # Code style
./mvnw pmd:check           # Static analysis
./mvnw spotbugs:check      # Bug detection
```

**Agent execution parameters:**
```json
{
  "command": "implement_next_step",
  "parameters": {
    "plan_file": "./ai-tdd-docs/real-time-collaboration/plan.md",
    "auto_commit": true
  }
}
```

**Implementation follows these patterns:**
- **NO placeholder code** - All code is production-ready
- **Files under 300 lines** - Proper separation of concerns
- **Dependency injection** - Spring Boot best practices
- **LoggingUtil usage** - Consistent logging patterns
- **Comprehensive error handling** - Proper exception management

#### Step 6: Generate Comprehensive Tests
```bash
# Agent Command: test-generator.json -> generate_tests_for_latest
# Input: Implemented code
# Output: Test files with 80%+ coverage

# Symflower integration
./mvnw exec:exec -Dexec.executable="symflower" -Dexec.args="test --language=java"
```

**Agent execution parameters:**
```json
{
  "command": "generate_tests_for_latest",
  "parameters": {
    "target_coverage": 85,
    "include_integration_tests": true,
    "output_path": "./ai-tdd-docs/real-time-collaboration/test-reports/"
  }
}
```

## Agent Command Reference

### Idea Generator Commands
```json
{
  "generate_idea": {
    "parameters": {
      "feature_description": "string (required)",
      "output_path": "string (optional, default: ./idea.md)"
    }
  },
  "create_feature_concept": {
    "parameters": {
      "problem_statement": "string (required)",
      "context": "string (optional)"
    }
  },
  "document_new_feature": {
    "parameters": {
      "feature_name": "string (required)",
      "feature_overview": "string (optional)"
    }
  }
}
```

### PRD Creator Commands
```json
{
  "create_prd_from_idea": {
    "parameters": {
      "idea_file": "string (required, default: ./idea.md)",
      "output_path": "string (optional, default: ./PRD.md)"
    }
  },
  "generate_product_requirements": {
    "parameters": {
      "idea_path": "string (required)",
      "stakeholders": "array (optional)"
    }
  },
  "transform_idea_to_prd": {
    "parameters": {
      "feature_name": "string (required)",
      "priority": "string (optional, enum: [High, Medium, Low])"
    }
  }
}
```

### Design Document Commands
```json
{
  "create_design_from_prd": {
    "parameters": {
      "prd_file": "string (required, default: ./PRD.md)",
      "output_path": "string (optional, default: ./design.md)"
    }
  },
  "generate_technical_design": {
    "parameters": {
      "prd_path": "string (required)",
      "architecture_style": "string (optional, enum: [microservices, monolithic, event-driven, layered])"
    }
  }
}
```

### Plan Creator Commands
```json
{
  "create_implementation_plan": {
    "parameters": {
      "design_file": "string (required, default: ./design.md)",
      "output_path": "string (optional, default: ./plan.md)"
    }
  },
  "generate_step_by_step_plan": {
    "parameters": {
      "design_path": "string (required)",
      "team_size": "integer (optional, default: 1)",
      "timeline": "string (optional)"
    }
  }
}
```

### Code Implementer Commands
```json
{
  "implement_next_step": {
    "parameters": {
      "plan_file": "string (optional, default: ./plan.md)",
      "auto_commit": "boolean (optional, default: true)"
    }
  },
  "execute_step_by_number": {
    "parameters": {
      "step_number": "integer (required)",
      "plan_path": "string (optional, default: ./plan.md)"
    }
  },
  "continue_ai_tdd_cycle": {
    "parameters": {
      "max_steps": "integer (optional, default: 5)",
      "stop_on_failure": "boolean (optional, default: true)"
    }
  }
}
```

### Test Generator Commands
```json
{
  "generate_tests_for_latest": {
    "parameters": {
      "target_coverage": "integer (optional, default: 80)",
      "include_integration_tests": "boolean (optional, default: false)"
    }
  },
  "create_unit_tests_for_class": {
    "parameters": {
      "class_name": "string (required)",
      "test_types": "array (optional, default: [unit, edge_case, error_condition])"
    }
  },
  "verify_test_coverage": {
    "parameters": {
      "minimum_coverage": "integer (optional, default: 80)",
      "enforce_critical_path": "boolean (optional, default: true)"
    }
  }
}
```

## Quality Gates

Every step in the AI-TDD pipeline enforces strict quality gates:

### Code Quality Requirements
- **No placeholder code** - All implementations are production-ready
- **File size limit** - Maximum 300 lines per file
- **Dependency injection** - Proper Spring Boot patterns
- **Error handling** - Comprehensive exception management
- **Logging** - LoggingUtil usage throughout

### Testing Requirements
- **80% minimum coverage** - Unit test coverage
- **100% critical path** - Core business logic coverage
- **Edge case testing** - Null, boundary, and error conditions
- **Integration tests** - End-to-end workflow validation

### Maven Quality Commands
```bash
# Run all quality checks
./mvnw clean verify

# Individual checks
./mvnw test                    # Unit tests
./mvnw checkstyle:check       # Code style compliance
./mvnw pmd:check              # Static analysis
./mvnw spotbugs:check         # Security and bug detection
```

## Project Integration

### Technology Stack Integration
- **Spring Boot 3.4.5** - Constructor injection, @ConfigurationProperties
- **Vaadin 24.7.3** - Lumo design system, type-safe navigation
- **PostgreSQL** - @JdbcTypeCode for JSONB, proper indexing
- **Spring AI** - Multi-provider support (OpenAI, Anthropic, Perplexity, Ollama)

### File Structure
```
answer42/
├── AI-TDD-README.md           # This guide
├── .claude/agents/            # AI agent configurations
│   ├── idea-generator.json
│   ├── prd-creator.json
│   ├── design-document.json
│   ├── plan-creator.json
│   ├── code-implementer.json
│   └── test-generator.json
├── ai-tdd-docs/               # AI-TDD documentation workspace
│   ├── README.md              # Documentation workspace guide
│   ├── [feature-name-1]/      # Feature project folder
│   │   ├── idea.md            # Agent 1: Idea Generator output
│   │   ├── PRD.md             # Agent 2: PRD Creator output
│   │   ├── design.md          # Agent 3: Design Document output
│   │   ├── plan.md            # Agent 4: Plan Creator output
│   │   └── test-reports/      # Agent 6: Test Generator outputs
│   └── [feature-name-2]/      # Another feature project
└── src/                       # Generated source code (Agent 5)
```

## Git Workflow Integration

### Commit Patterns
```bash
# Automated commits follow this pattern:
feat: [Step X] Description from plan.md

# Example commits:
feat: [Step 1] Create WebSocket configuration service
feat: [Step 2] Implement real-time message broadcasting
test: [Step 3] Add comprehensive unit tests for WebSocket service
```

### Branch Strategy
- **feature/ai-tdd-[feature-name]** - Feature development branches
- **main** - Production-ready code
- **develop** - Integration branch

## Troubleshooting

### Common Issues

#### Agent Execution Failures
```bash
# Check Java version
java --version  # Should be 21+

# Verify Maven setup
./mvnw --version

# Check Symflower installation
symflower --version
```

#### Quality Gate Failures
```bash
# Fix code style issues
./mvnw checkstyle:check
# Review checkstyle.xml for standards

# Fix static analysis issues  
./mvnw pmd:check
# Review pmd-ruleset.xml for rules

# Fix security vulnerabilities
./mvnw spotbugs:check
# Review spotbugs-exclude.xml for suppressions
```

#### Test Generation Issues
```bash
# Ensure Symflower is properly installed
which symflower

# Run Symflower directly
symflower test --language=java --package com.samjdtechnologies.answer42.service

# Check test coverage
./mvnw jacoco:report
```

### Performance Optimization
- **Parallel execution** - Use `./mvnw -T 4` for multi-threaded builds
- **Incremental compilation** - Maven incremental compilation enabled
- **Test optimization** - Symflower focuses on critical paths first

## Best Practices

### Development Guidelines
1. **Always follow the pipeline** - Never skip stages
2. **Review each document** - Ensure quality at every step
3. **Run quality gates** - Before proceeding to next stage
4. **Commit frequently** - After each successful step
5. **Document deviations** - Update plan.md with any changes

### Code Standards
- **Spring Boot patterns** - Use constructor injection, proper configuration
- **Vaadin best practices** - Component lifecycle, type-safe routing
- **Database optimization** - Proper indexing, query optimization
- **Security first** - Input validation, proper authentication
- **Performance monitoring** - Logging, metrics, health checks

### Testing Strategy
- **Unit tests first** - Test individual components
- **Integration tests** - Test component interactions
- **End-to-end tests** - Test complete user workflows
- **Performance tests** - Validate response times
- **Security tests** - Validate security constraints

## Advanced Usage

### Custom Agent Configurations
Agents can be customized by modifying their JSON configuration files:

```bash
# Edit agent configuration
vi .claude/agents/code-implementer.json

# Modify parameters, commands, or workflows
# Restart agent execution with new configuration
```

### Batch Processing
```bash
# Execute multiple agents in sequence
for agent in idea-generator prd-creator design-document plan-creator; do
    echo "Executing $agent..."
    # Execute agent logic here
done
```

### Integration with CI/CD
```yaml
# GitHub Actions example
name: AI-TDD Pipeline
on:
  push:
    paths: ['idea.md', 'PRD.md', 'design.md', 'plan.md']

jobs:
  ai-tdd-validation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Validate AI-TDD Documents
        run: |
          ./mvnw clean verify
          ./scripts/validate-ai-tdd-pipeline.sh
```

## Contributing

When contributing to the AI-TDD system:

1. **Follow the methodology** - All features must go through the 6-stage pipeline
2. **Update documentation** - Keep agent configurations current
3. **Test thoroughly** - Ensure 80%+ coverage on all new code
4. **Quality first** - All quality gates must pass
5. **Document decisions** - Update relevant .md files

## Support

For questions or issues with AI-TDD:

1. **Check this README** - Most common issues are covered
2. **Review agent logs** - Check execution output for errors
3. **Validate setup** - Ensure all prerequisites are installed
4. **Run diagnostics** - Execute quality checks individually
5. **Create GitHub issue** - With full error logs and context

---

**AI-TDD** represents the future of software development - structured, automated, and quality-assured from conception to deployment. By following this methodology, you ensure enterprise-grade code quality while maintaining rapid development velocity.
