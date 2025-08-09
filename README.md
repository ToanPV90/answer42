Answer42 - AI-Powered Academic Research Platform

[](https://github.com/samjd-zz/answer42#answer42---ai-powered-academic-research-platform)

[![Answer42 Logo](https://github.com/samjd-zz/answer42/raw/main/src/main/resources/META-INF/resources/frontend/images/answer42-logo.svg)](https://github.com/samjd-zz/answer42/blob/main/src/main/resources/META-INF/resources/frontend/images/answer42-logo.svg)

Answer42 is a sophisticated AI-powered platform for academic researchers, students, and scholars to upload, analyze, and interact with research papers using multiple AI providers. The platform combines advanced paper processing with intelligent chat capabilities, a revolutionary multi-agent processing pipeline, and a comprehensive local AI fallback system using Ollama to ensure continuous operation even when cloud providers fail.

## Table of Contents

* [What is Answer42?](#what-is-answer42)
* [Technology Stack](#technology-stack)
* [Architecture Overview](#architecture-overview)
* [Core Features](#core-features)
* [Multi-Agent Processing Pipeline](#multi-agent-processing-pipeline)
* [Related Papers Discovery System](#related-papers-discovery-system)
* [AI Integration](#ai-integration)
* [Database Design](#database-design)
* [Getting Started](#getting-started)
* [Development](#development)
* [Configuration](#configuration)
* [Support the Project](#-support-the-project)
* [License](#license)
* [Contact](#-contact)

What is Answer42?

[](https://github.com/samjd-zz/answer42#what-is-answer42)

Answer42 is a comprehensive research assistant that helps academics work with scientific papers through:

* **Intelligent Paper Processing**: Upload PDFs and extract full text, metadata, and structured information
* **Multi-Agent Processing Pipeline**: Nine specialized AI agents for comprehensive paper analysis
* **Related Papers Discovery**: Multi-source intelligent discovery of related academic papers
* **Multi-Modal AI Chat**: Three specialized chat modes using different AI providers for various research needs
* **Comprehensive Analysis**: Generate summaries, extract key findings, identify methodologies, and create glossaries
* **External Metadata Integration**: Automatic enhancement using Crossref and Semantic Scholar APIs
* **Project Organization**: Group papers into research projects for better organization
* **Credit-Based System**: Subscription model with credit management for AI operations

🚀 Project Links

[](https://github.com/samjd-zz/answer42#-project-links)

* **Deep Funding RFP**: [Port Answer 42 to SingularityNET](https://deepfunding.ai/rfp_ideation/port-answer-42-to-snet/) - Proposal for integrating Answer42 with the SingularityNET ecosystem

Technology Stack

[](https://github.com/samjd-zz/answer42#technology-stack)

### Backend

[](https://github.com/samjd-zz/answer42#backend)

* **Java 21** - Modern Java with latest features
* **Spring Boot 3.4.5** - Enterprise application framework
* **Spring Batch** - Enterprise-grade job processing and workflow orchestration
* **Spring Security** - Authentication and authorization
* **Spring Data JPA** - Database access with Hibernate
* **Spring AI** - Unified AI provider integration
* **PostgreSQL** - Primary database with JSONB support
* **JWT** - Stateless authentication tokens
* **Lombok** - Reduced boilerplate code

### Frontend

[](https://github.com/samjd-zz/answer42#frontend)

* **Vaadin 24.7.3** - Full-stack Java web framework
* **Custom CSS Themes** - Responsive design with dark mode support
* **Progressive Web App** - Offline capabilities and mobile support

### AI Providers

[](https://github.com/samjd-zz/answer42#ai-providers)

* **Anthropic Claude** - Paper-specific analysis and chat
* **OpenAI GPT-4** - Cross-reference analysis and general chat
* **Perplexity** - Research exploration and external knowledge
* **Ollama (Local)** - ✅ **FULLY INTEGRATED!** Local AI processing fallback with comprehensive resilience system

#### 🛡️ **Enterprise-Grade Resilience System** (✅ **COMPLETED**)

Answer42 now features a fully operational **local AI fallback system** powered by **Ollama** that ensures uninterrupted research workflows:

**✅ **Automatic Fallback**: When cloud providers fail, the system automatically switches to local Ollama models**
**✅ **Complete Agent Coverage**: All 9 AI agents have fully implemented local fallback agents**
**✅ **Intelligent Retry Logic**: Advanced retry policies with circuit breaker protection managed by Spring**
**✅ **Performance Monitoring**: Comprehensive fallback metrics via FallbackMetricsService**
**✅ **Quality Assurance**: Local models optimized for academic content processing (8K char limit)**
**✅ **Docker Integration**: Production-ready Ollama service with health checks and improved monitoring**

**Fallback Agents Implemented (Complete 9/9 Coverage):**
- 📝 **ContentSummarizerFallbackAgent** - Multi-level summarization with truncation support
- 🧠 **ConceptExplainerFallbackAgent** - Simplified explanations for local models
- 📚 **MetadataEnhancementFallbackAgent** - Keywords, categories, and summary tags
- 🔧 **PaperProcessorFallbackAgent** - Comprehensive paper analysis with fallback methods
- ✅ **QualityCheckerFallbackAgent** - Quality scoring with letter grades (A-F)
- 📖 **CitationFormatterFallbackAgent** - Multi-style formatting with regex fallback
- 🔍 **CitationVerifierFallbackAgent** - Citation validation and accuracy verification
- 🔗 **RelatedPaperDiscoveryFallbackAgent** - Multi-source paper discovery with AI synthesis and rule-based fallback
- 🔬 **PerplexityResearchFallbackAgent** - Local research analysis with fact verification and synthesis

**Complete Docker Deployment:**

**1. Supabase Local Development Stack:**
```bash
# Start Supabase local development environment
supabase start

# This starts the complete Supabase stack:
# - PostgreSQL Database (port 54322)
# - PostgREST API (port 54321) 
# - Supabase Studio (port 54323)
# - Edge Functions (port 54324)
# - Inbucket Email Testing (port 54324)

# Verify Supabase services
supabase status

# Apply database migrations
supabase db push

# Reset database with fresh schema and seed data
supabase db reset
```

**2. Ollama AI Fallback Service:**
```bash
# Start Ollama service with Docker Compose
docker-compose -f docker-compose.ollama.yml up -d

# Verify service is running and healthy
docker-compose -f docker-compose.ollama.yml ps

# Pull the required LLaMA 3.1 8B model
docker exec answer42-ollama ollama pull llama3.1:8b

# Test the service
curl -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{"model": "llama3.1:8b", "prompt": "Hello, test", "stream": false}'
```

**3. Complete Development Environment:**
```bash
# Start all services in the correct order
supabase start                                    # Database & API stack
docker-compose -f docker-compose.ollama.yml up -d # AI fallback service
./mvnw spring-boot:run                           # Spring Boot application

# Access points:
# - Application: http://localhost:8080
# - Supabase Studio: http://localhost:54323
# - Database: postgresql://postgres:postgres@localhost:54322/postgres
# - Ollama API: http://localhost:11434
```

**Service Ports Summary:**
- **Answer42 App**: `8080`
- **Supabase API**: `54321` 
- **PostgreSQL DB**: `54322`
- **Supabase Studio**: `54323`
- **Email Testing**: `54324`
- **Ollama API**: `11434`

**Alternative Setup Scripts:**
```bash
# Install Ollama with Docker (automated script)
./scripts/setup-ollama.sh

# Test the fallback system
./scripts/test-ollama-fallback.sh
```

**System Benefits:**
- **99.5%+ Uptime**: Maintains service availability even during cloud provider outages
- **Cost Optimization**: Reduces dependency on expensive cloud API calls
- **Privacy Enhancement**: Sensitive content can be processed locally
- **Performance Consistency**: Predictable local processing times
- **Research Continuity**: Never lose progress due to external service failures

### External APIs

[](https://github.com/samjd-zz/answer42#external-apis)

* **Crossref API** - Citation network analysis and bibliographic discovery
* **Semantic Scholar API** - Semantic similarity and influence analysis
* **Perplexity API** - Real-time research trends and open access discovery

### Development Tools

[](https://github.com/samjd-zz/answer42#development-tools)

* **Maven** - Build automation and dependency management
* **Checkstyle, PMD, SpotBugs** - Code quality and static analysis
* **Spring Boot DevTools** - Development hot-reload

Architecture Overview

[](https://github.com/samjd-zz/answer42#architecture-overview)

Answer42 follows a clean layered architecture with a sophisticated multi-agent processing system and intelligent discovery capabilities:

```mermaid
graph TB
    subgraph "User Interface Layer"
        UI[Vaadin Views]
        PWA[Progressive Web App]
    end
    
    subgraph "REST API Layer"
        CTRL[Controllers]
        WS[WebSocket Endpoints]
    end
    
    subgraph "Business Logic Layer"
        PS[Paper Service]
        CS[Chat Service]
        US[User Service]
        SS[Subscription Service]
    end
    
    subgraph "AI Processing Layer 🆕"
        direction TB
        SB[Spring Batch Job Launcher]
        
        subgraph "Multi-Agent Pipeline"
            A1[🔧 Paper Processor]
            A2[📚 Metadata Enhancement]
            A3[📝 Content Summarizer]
            A4[🧠 Concept Explainer]
            A5[✅ Quality Checker]
            A6[📖 Citation Formatter]
            A7[🔍 Related Paper Discovery]
            A8[🔬 Perplexity Research]
        end
    end
    
    subgraph "Discovery System Layer 🆕"
        DS[Discovery Orchestrator]
        CR[Crossref API]
        S2[Semantic Scholar API]
        PX[Perplexity API]
        AI[AI Synthesis Engine]
    end
    
    subgraph "Data Access Layer"
        REPO[Repositories]
        CACHE[Redis Cache]
    end
    
    subgraph "Database Layer"
        PG[(PostgreSQL)]
        BATCH[(Spring Batch Tables)]
        DISCO[(Discovery Tables)]
    end
    
    subgraph "External AI Providers"
        CLAUDE[Anthropic Claude]
        GPT[OpenAI GPT-4]
        PERP[Perplexity AI]
    end
    
    subgraph "Local AI Fallback 🆕"
        OLLAMA[Ollama Local Models]
        FB_FACTORY[Fallback Agent Factory]
        FB_AGENTS[6 Fallback Agents]
    end
    
    UI --> CTRL
    PWA --> WS
    CTRL --> PS
    CTRL --> CS
    WS --> PS
    
    PS --> SB
    SB --> A1
    A1 --> A2
    A2 --> A3
    A3 --> A4
    A4 --> A5
    A5 --> A6
    A6 --> A7
    A7 --> A8
    
    PS --> DS
    DS --> CR
    DS --> S2
    DS --> PX
    DS --> AI
    
    A1 --> GPT
    A2 --> GPT
    A3 --> CLAUDE
    A4 --> GPT
    A5 --> CLAUDE
    A6 --> GPT
    A7 --> CLAUDE
    A7 --> PERP
    A8 --> PERP
    
    AI --> CLAUDE
    
    %% Fallback connections (when cloud providers fail)
    A1 -.->|Fallback| OLLAMA
    A2 -.->|Fallback| OLLAMA
    A3 -.->|Fallback| OLLAMA
    A4 -.->|Fallback| OLLAMA
    A5 -.->|Fallback| OLLAMA
    A6 -.->|Fallback| OLLAMA
    
    FB_FACTORY --> FB_AGENTS
    FB_AGENTS --> OLLAMA
    
    PS --> REPO
    CS --> REPO
    US --> REPO
    SS --> REPO
    
    REPO --> PG
    SB --> BATCH
    DS --> DISCO
    BATCH --> PG
    DISCO --> PG
    
    REPO --> CACHE
```

### Key Design Patterns

[](https://github.com/samjd-zz/answer42#key-design-patterns)

* **MVC Architecture** - Clear separation of concerns
* **Multi-Agent Pattern** - Specialized AI agents for different tasks (🆕 **NEW!**)
* **Multi-Source Discovery Pattern** - Intelligent paper discovery from multiple APIs (🆕 **NEW!**)
* **Repository Pattern** - Data access abstraction
* **Service Layer** - Business logic encapsulation
* **Dependency Injection** - Loose coupling via Spring IoC
* **Component-Based UI** - Reusable Vaadin components

Core Features

[](https://github.com/samjd-zz/answer42#core-features)

### 📄 Paper Management

[](https://github.com/samjd-zz/answer42#-paper-management)

* **PDF Upload & Processing**: Extract text content and metadata from academic papers
* **Real-time Pipeline Processing**: Live progress tracking during multi-agent analysis (🆕 **NEW!**)
* **Metadata Enhancement**: Automatic enrichment via Crossref and Semantic Scholar APIs
* **Comprehensive Storage**: Store papers with full text, abstracts, authors, citations, and analysis results
* **Organization**: Group papers into research projects for better management
* **Bulk Upload**: Process multiple papers simultaneously with parallel pipeline processing

### 🔍 Related Papers Discovery (🆕 **NEW!**)

[](https://github.com/samjd-zz/answer42#-related-papers-discovery--new)

**Intelligent multi-source discovery system for comprehensive research exploration:**

1. **Multi-Source Discovery**
  
  * **Crossref API Integration**: Citation network analysis and bibliographic discovery
  * **Semantic Scholar API**: Semantic similarity and influence metrics analysis
  * **Perplexity API**: Real-time research trends and open access paper discovery
  * **AI Synthesis Engine**: Intelligent combination and ranking of results from all sources
2. **Advanced Discovery Features**
  
  * **Citation Networks**: Forward and backward citation analysis with relationship mapping
  * **Semantic Similarity**: AI-powered related paper discovery based on content similarity
  * **Author Networks**: Comprehensive researcher tracking and collaboration analysis
  * **Field Evolution**: Historical and current research trends in specific domains
  * **Real-time Context**: Current academic discussions and developments
3. **Interactive Discovery Interface**
  
  * **Smart Filtering**: Filter by discovery source, relationship type, and relevance score
  * **Citation Network Visualization**: Interactive graph-based citation network exploration
  * **Paper Bookmarking**: Save interesting discoveries for future reference
  * **Feedback Collection**: Structured user feedback to improve discovery algorithms
  * **Detailed Statistics**: Comprehensive discovery analytics and source breakdown
4. **Quality Assessment & Ranking**
  
  * **AI-Powered Relevance Scoring**: Multi-factor ranking algorithm using Claude
  * **Duplicate Detection**: Intelligent paper merging and deduplication across sources
  * **Source Reliability Scoring**: Quality assessment based on API source and completeness
  * **User Feedback Integration**: Continuous improvement through user preference learning

### 🤖 Multi-Modal AI Chat

[](https://github.com/samjd-zz/answer42#-multi-modal-ai-chat)

**Three specialized chat modes optimized for different research needs:**

1. **Paper Chat (Anthropic Claude)**
  
  * Deep analysis of individual papers
  * Contextual Q&A about paper content
  * Generate summaries, key findings, and glossaries
  * One-click analysis buttons for common tasks
2. **Cross-Reference Chat (OpenAI GPT-4)**
  
  * Compare multiple papers simultaneously
  * Identify agreements, contradictions, and research gaps
  * Relationship analysis between different studies
  * Methodology and results comparison
3. **Research Explorer (Perplexity)**
  
  * External research and fact-checking
  * Discover related papers and research
  * Verify claims against current literature
  * General academic research assistance

### 📊 Intelligent Analysis

[](https://github.com/samjd-zz/answer42#-intelligent-analysis)

* **Automated Summaries**: Brief, standard, and detailed summaries
* **Key Findings Extraction**: Identify main contributions and results
* **Methodology Analysis**: Extract and analyze research methods
* **Concept Glossaries**: Generate definitions for technical terms
* **Citation Analysis**: Process and structure reference lists
* **Quality Assessment**: AI-powered quality scoring and feedback

### 👤 User Management

[](https://github.com/samjd-zz/answer42#-user-management)

* **Secure Authentication**: JWT-based authentication with Spring Security
* **User Profiles**: Customizable user preferences and settings
* **Subscription Management**: Credit-based system with multiple tiers
* **Progress Tracking**: Monitor paper processing and analysis status

Multi-Agent Processing Pipeline

[](https://github.com/samjd-zz/answer42#multi-agent-processing-pipeline)

🆕 **NEW FEATURE**: Answer42 now features a sophisticated multi-agent processing pipeline powered by **Spring Batch** that automatically analyzes papers using nine specialized AI agents.

### Pipeline Architecture

[](https://github.com/samjd-zz/answer42#pipeline-architecture)

The multi-agent system uses **Spring Batch** for enterprise-grade job processing and workflow orchestration, processing papers through coordinated AI agents in a sequential 8-step pipeline:

```mermaid
flowchart TD
    START([Paper Upload]) --> VALIDATE{Paper Valid?}
    VALIDATE -->|No| ERROR([Processing Failed])
    VALIDATE -->|Yes| BATCH[Spring Batch Job Launch]
    
    BATCH --> A1[🔧 Paper Processor Agent<br/>OpenAI GPT-4]
    A1 --> A1_RESULT{Success?}
    A1_RESULT -->|No| RETRY1[Retry with Fallback]
    A1_RESULT -->|Yes| A2[📚 Metadata Enhancement Agent<br/>OpenAI GPT-4]
    RETRY1 --> A2
    
    A2 --> A2_RESULT{Success?}
    A2_RESULT -->|No| RETRY2[Retry with Fallback]
    A2_RESULT -->|Yes| A3[📝 Content Summarizer Agent<br/>Anthropic Claude]
    RETRY2 --> A3
    
    A3 --> A3_RESULT{Success?}
    A3_RESULT -->|No| RETRY3[Retry with Fallback]
    A3_RESULT -->|Yes| A4[🧠 Concept Explainer Agent<br/>OpenAI GPT-4]
    RETRY3 --> A4
    
    A4 --> A4_RESULT{Success?}
    A4_RESULT -->|No| RETRY4[Retry with Fallback]
    A4_RESULT -->|Yes| A5[✅ Quality Checker Agent<br/>Anthropic Claude]
    RETRY4 --> A5
    
    A5 --> A5_RESULT{Success?}
    A5_RESULT -->|No| RETRY5[Retry with Fallback]
    A5_RESULT -->|Yes| A6[📖 Citation Formatter Agent<br/>OpenAI GPT-4]
    RETRY5 --> A6
    
    A6 --> A6_RESULT{Success?}
    A6_RESULT -->|No| RETRY6[Retry with Fallback]
    A6_RESULT -->|Yes| A7[🔍 Related Paper Discovery Agent<br/>Claude + Perplexity]
    RETRY6 --> A7
    
    A7 --> A7_RESULT{Success?}
    A7_RESULT -->|No| RETRY7[Retry with Fallback]
    A7_RESULT -->|Yes| A8[🔬 Perplexity Research Agent<br/>Perplexity API]
    RETRY7 --> A8
    
    A8 --> A8_RESULT{Success?}
    A8_RESULT -->|No| RETRY8[Retry with Fallback]
    A8_RESULT -->|Yes| COMPLETE([Processing Complete])
    RETRY8 --> COMPLETE
    
    subgraph "External APIs"
        CROSSREF[Crossref API]
        SCHOLAR[Semantic Scholar API]
        PERPLEXITY[Perplexity API]
    end
    
    subgraph "AI Providers"
        OPENAI[OpenAI GPT-4]
        CLAUDE[Anthropic Claude]
        PERP[Perplexity AI]
    end
    
    subgraph "Database Storage"
        PAPERS[(Papers Table)]
        TASKS[(Tasks Table)]
        MEMORY[(Agent Memory)]
        BATCH_DB[(Spring Batch Tables)]
    end
    
    A1 -.-> OPENAI
    A2 -.-> OPENAI
    A2 -.-> CROSSREF
    A2 -.-> SCHOLAR
    A3 -.-> CLAUDE
    A4 -.-> OPENAI
    A5 -.-> CLAUDE
    A6 -.-> OPENAI
    A7 -.-> CLAUDE
    A7 -.-> PERPLEXITY
    A8 -.-> PERP
    
    BATCH -.-> BATCH_DB
    A1 -.-> PAPERS
    A2 -.-> PAPERS
    A3 -.-> PAPERS
    A4 -.-> PAPERS
    A5 -.-> PAPERS
    A6 -.-> PAPERS
    A7 -.-> PAPERS
    A8 -.-> PAPERS
    
    A1 -.-> TASKS
    A2 -.-> TASKS
    A3 -.-> TASKS
    A4 -.-> TASKS
    A5 -.-> TASKS
    A6 -.-> TASKS
    A7 -.-> TASKS
    A8 -.-> TASKS
    
    A1 -.-> MEMORY
    A2 -.-> MEMORY
    A3 -.-> MEMORY
    A4 -.-> MEMORY
    A5 -.-> MEMORY
    A6 -.-> MEMORY
    A7 -.-> MEMORY
    A8 -.-> MEMORY
    
    style START fill:#e1f5fe
    style COMPLETE fill:#e8f5e8
    style ERROR fill:#ffebee
    style BATCH fill:#fff3e0
    style A1 fill:#e3f2fd
    style A2 fill:#e3f2fd
    style A3 fill:#f3e5f5
    style A4 fill:#e3f2fd
    style A5 fill:#f3e5f5
    style A6 fill:#e3f2fd
    style A7 fill:#f3e5f5
    style A8 fill:#fff8e1
```

### Specialized AI Agents

[](https://github.com/samjd-zz/answer42#specialized-ai-agents)

**🔧 Paper Processor Agent** (OpenAI GPT-4)

* PDF text extraction and structure analysis
* Section identification (introduction, methods, results, etc.)
* Table and figure recognition
* Mathematical notation processing

**📚 Metadata Enhancement Agent** (OpenAI GPT-4)

* Crossref API integration for bibliographic data
* Semantic Scholar API integration for citation metrics
* DOI resolution and validation
* Author disambiguation and affiliation resolution

**📝 Content Summarizer Agent** (Anthropic Claude)

* Multi-level summary generation (brief, standard, detailed)
* Executive summaries for non-specialists
* Technical summaries for domain experts
* Key findings extraction with confidence scoring

**🧠 Concept Explainer Agent** (OpenAI GPT-4)

* Technical term identification and explanation
* Concept relationship mapping
* Education level-appropriate explanations
* Real-world analogies for complex concepts

**✅ Quality Checker Agent** (Anthropic Claude)

* Multi-dimensional accuracy verification
* Hallucination detection and fact-checking
* Content consistency analysis
* Bias detection and quality scoring

**📖 Citation Formatter Agent** (OpenAI GPT-4)

* Reference list parsing and structuring
* Multiple citation style formatting (APA, MLA, Chicago, IEEE)
* DOI validation and metadata enrichment
* Bibliography generation with quality assessment

**🔍 Related Paper Discovery Agent** (🆕 **NEW!**) (Anthropic Claude + Perplexity API)

* Multi-source paper discovery coordination (Crossref, Semantic Scholar, Perplexity)
* AI-powered relevance score enhancement using Anthropic Claude
* Perplexity API integration for real-time research trends and discussions
* Result synthesis and deduplication across all sources
* Discovery metadata tracking and storage

**🔍 Citation Verifier Agent** (🆕 **NEW!**) (OpenAI GPT-4)

* Citation accuracy verification and validation
* DOI resolution and bibliographic cross-checking
* Reference completeness assessment
* Academic integrity scoring and verification

**🔬 Perplexity Research Agent** (🆕 **NEW!**) (Perplexity API)

* External research and fact verification
* Real-time web search and research synthesis
* Current literature verification and validation
* Research trend analysis and contextualization

### Pipeline Features

[](https://github.com/samjd-zz/answer42#pipeline-features)

**Real-time Progress Tracking**

* Live agent status updates in the Papers view
* Individual agent progress indicators
* Estimated completion times
* WebSocket-powered real-time updates

**Intelligent Task Management**

* Database-driven task lifecycle management
* Spring Event system integration
* Automatic retry mechanisms and error handling
* Agent memory and context management

**Cost-Optimized Processing**

* Token usage tracking and analytics
* Credit-based pricing integration
* Performance monitoring and optimization
* User-aware API key management

### Core vs Enhancement Agents for AI Chat Integration

[](https://github.com/samjd-zz/answer42#core-vs-enhancement-agents-for-ai-chat-integration)

The multi-agent pipeline produces data that directly powers Answer42's three AI chat modes. Each chat mode requires specific agent outputs to function optimally:

#### **Paper Chat (Anthropic Claude) - Individual Paper Analysis**

[](https://github.com/samjd-zz/answer42#paper-chat-anthropic-claude---individual-paper-analysis)

**CORE REQUIRED (Essential for functionality):**

* 🔧 **Paper Processor** → `papers.text_content` (raw paper content)
* 📝 **Content Summarizer** → `papers.summary_brief/standard/detailed`, `papers.key_findings`
* 🧠 **Concept Explainer** → `papers.glossary`, `papers.main_concepts`

**ENHANCEMENT (Beneficial but optional):**

* Metadata Enhancement, Perplexity Research, Related Paper Discovery, Citation Formatting, Quality Assessment

#### **Cross-Reference Chat (OpenAI GPT-4) - Multi-Paper Comparison**

[](https://github.com/samjd-zz/answer42#cross-reference-chat-openai-gpt-4---multi-paper-comparison)

**CORE REQUIRED (Essential for functionality):**

* 🔧 **Paper Processor** → `papers.text_content` (raw paper content)
* 📝 **Content Summarizer** → `papers.summary_brief/standard/detailed`, `papers.key_findings`
* 📖 **Citation Formatter** → `papers.citations` (for reference comparisons)
* ✅ **Quality Checker** → `papers.quality_feedback`, `papers.quality_score` (reliability assessment)

**ENHANCEMENT (Beneficial but optional):**

* Metadata Enhancement, Concept Explainer, Perplexity Research, Related Paper Discovery

#### **Research Explorer Chat (Perplexity) - External Research & Discovery**

[](https://github.com/samjd-zz/answer42#research-explorer-chat-perplexity---external-research--discovery)

**CORE REQUIRED (Essential for functionality):**

* 🔧 **Paper Processor** → `papers.text_content` (raw paper content)
* 🔬 **Perplexity Research** → `papers.research_questions`, `papers.methodology_details`
* 🔍 **Related Paper Discovery** → `discovered_papers`, `papers.metadata.research_context`

**ENHANCEMENT (Beneficial but optional):**

* Metadata Enhancement, Content Summarizer, Concept Explainer, Citation Formatting, Quality Assessment

**Database Integration:** The AI chat clients automatically access the appropriate database tables populated by these core agents, enabling contextual conversations about papers with rich, pre-processed information.

Related Papers Discovery System

[](https://github.com/samjd-zz/answer42#related-papers-discovery-system)

🆕 **NEW FEATURE**: Answer42's Related Papers Discovery System provides comprehensive research landscape exploration through multi-source intelligent discovery.

### Discovery Architecture

[](https://github.com/samjd-zz/answer42#discovery-architecture)

The system combines multiple academic APIs with AI synthesis for comprehensive paper discovery:

```mermaid
flowchart TD
    INPUT[Source Paper] --> ORCHESTRATOR[Discovery Orchestrator]
    
    ORCHESTRATOR --> CROSSREF_TASK[Crossref Discovery Task]
    ORCHESTRATOR --> SCHOLAR_TASK[Semantic Scholar Discovery Task]
    ORCHESTRATOR --> PERPLEXITY_TASK[Perplexity Discovery Task]
    
    subgraph "Crossref Discovery"
        CROSSREF_TASK --> CROSSREF_API[Crossref API<br/>45 req/sec]
        CROSSREF_API --> CROSSREF_CITATIONS[Citation Network Analysis]
        CROSSREF_API --> CROSSREF_AUTHORS[Author Network Discovery]
        CROSSREF_API --> CROSSREF_VENUES[Publication Venue Analysis]
        CROSSREF_CITATIONS --> CROSSREF_RESULTS[Crossref Results]
        CROSSREF_AUTHORS --> CROSSREF_RESULTS
        CROSSREF_VENUES --> CROSSREF_RESULTS
    end
    
    subgraph "Semantic Scholar Discovery"
        SCHOLAR_TASK --> SCHOLAR_API[Semantic Scholar API<br/>100 req/min]
        SCHOLAR_API --> SCHOLAR_SIMILARITY[Semantic Similarity Analysis]
        SCHOLAR_API --> SCHOLAR_INFLUENCE[Influence Metrics Assessment]
        SCHOLAR_API --> SCHOLAR_FIELDS[Research Field Classification]
        SCHOLAR_SIMILARITY --> SCHOLAR_RESULTS[Semantic Scholar Results]
        SCHOLAR_INFLUENCE --> SCHOLAR_RESULTS
        SCHOLAR_FIELDS --> SCHOLAR_RESULTS
    end
    
    subgraph "Perplexity Discovery"
        PERPLEXITY_TASK --> PERPLEXITY_API[Perplexity API<br/>10 req/min]
        PERPLEXITY_API --> PERPLEXITY_TRENDS[Research Trend Analysis]
        PERPLEXITY_API --> PERPLEXITY_DISCUSSIONS[Academic Discussions]
        PERPLEXITY_API --> PERPLEXITY_OPENACCESS[Open Access Discovery]
        PERPLEXITY_TRENDS --> PERPLEXITY_RESULTS[Perplexity Results]
        PERPLEXITY_DISCUSSIONS --> PERPLEXITY_RESULTS
        PERPLEXITY_OPENACCESS --> PERPLEXITY_RESULTS
    end
    
    CROSSREF_RESULTS --> SYNTHESIS[AI Synthesis Engine<br/>Anthropic Claude]
    SCHOLAR_RESULTS --> SYNTHESIS
    PERPLEXITY_RESULTS --> SYNTHESIS
    
    SYNTHESIS --> DEDUPLICATION[Multi-source Deduplication<br/>DOI, Title, Author Matching]
    DEDUPLICATION --> RELEVANCE[AI-Powered Relevance Scoring<br/>5-Factor Algorithm]
    RELEVANCE --> QUALITY[Quality Filtering<br/>Configurable Thresholds]
    QUALITY --> RANKING[Advanced Ranking Algorithm]
    
    RANKING --> STORAGE[Database Storage]
    
    subgraph "Database Tables"
        STORAGE --> DISCOVERED_PAPERS[(discovered_papers<br/>38 columns)]
        STORAGE --> PAPER_RELATIONSHIPS[(paper_relationships<br/>13 columns)]
        STORAGE --> DISCOVERY_RESULTS[(discovery_results<br/>26 columns)]
        STORAGE --> DISCOVERY_FEEDBACK[(discovery_feedback<br/>20 columns)]
    end
    
    STORAGE --> UI[Discovery UI Components]
    
    subgraph "User Interface"
        UI --> RELATED_PAPERS[RelatedPapersSection<br/>Main Results & Filtering]
        UI --> CITATION_NETWORK[CitationNetworkDialog<br/>Interactive Visualization]
        UI --> PAPER_DETAILS[PaperDetailsDialog<br/>Comprehensive Information]
        UI --> FEEDBACK_DIALOG[DiscoveryFeedbackDialog<br/>User Feedback Collection]
    end
    
    FEEDBACK_DIALOG --> FEEDBACK_LOOP[Algorithm Improvement]
    FEEDBACK_LOOP --> SYNTHESIS
    
    style INPUT fill:#e1f5fe
    style ORCHESTRATOR fill:#fff3e0
    style SYNTHESIS fill:#f3e5f5
    style STORAGE fill:#e8f5e8
    style UI fill:#e3f2fd
    style CROSSREF_TASK fill:#ffebee
    style SCHOLAR_TASK fill:#e8eaf6
    style PERPLEXITY_TASK fill:#fff8e1
    style DEDUPLICATION fill:#f1f8e9
    style RELEVANCE fill:#fce4ec
    style QUALITY fill:#e0f2f1
    style RANKING fill:#e8f5e8
```

### Discovery Sources

[](https://github.com/samjd-zz/answer42#discovery-sources)

**🔗 Crossref API Discovery**

* Forward citations (papers that cite this work)
* Backward citations (papers this work cites)
* Author network discovery and publication venue exploration
* Subject classification and temporal analysis
* Rate-limited processing (45 requests/second)

**🧠 Semantic Scholar API Discovery**

* Semantic similarity analysis using paper embeddings
* Influence metrics and impact assessment
* Research field classification and author disambiguation
* Citation context analysis
* Free tier integration (100 requests/minute)

**🌐 Perplexity API Discovery**

* Real-time research trend analysis
* Current academic discussions and debates
* Open access paper discovery
* Press coverage and impact tracking
* Trend-aware discovery (10 requests/minute)

### AI Synthesis Engine

[](https://github.com/samjd-zz/answer42#ai-synthesis-engine)

**Intelligent Result Processing**

* **Multi-source Deduplication**: DOI, title, and author-based paper matching
* **AI-Powered Relevance Enhancement**: Claude-based scoring using 5-factor algorithm
* **Quality Filtering**: Configurable thresholds with source reliability assessment
* **Batch Processing**: Optimal AI API usage with error handling and fallback

**Advanced Ranking Algorithm**

1. **Base Relevance Score**: From discovery source APIs
2. **Relationship Importance**: Weighted by relationship type (citations > similarity > trends)
3. **Citation Impact**: Based on citation count and influence metrics
4. **Data Completeness**: Higher scores for papers with rich metadata
5. **Source Reliability**: Crossref > Semantic Scholar > Perplexity weighting

### Discovery Database Schema

[](https://github.com/samjd-zz/answer42#discovery-database-schema)

**Comprehensive Storage System**

* **discovered_papers**: 38 columns with complete paper metadata from all sources
* **paper_relationships**: 13 columns tracking relationship types and strength
* **discovery_results**: 26 columns for discovery session tracking and metrics
* **discovery_feedback**: 20 columns for user feedback and algorithm improvement

**Performance Optimization**

* 31 database indexes for fast querying and filtering
* JSONB fields for flexible metadata storage
* Efficient relationship mapping and discovery result aggregation

### Discovery UI Components

[](https://github.com/samjd-zz/answer42#discovery-ui-components)

**Interactive Discovery Interface**

* **RelatedPapersSection**: Main discovery results with filtering and statistics
* **CitationNetworkDialog**: Interactive citation network visualization
* **PaperDetailsDialog**: Comprehensive paper information display
* **DiscoveryFeedbackDialog**: Structured user feedback collection
* **Real-time Progress Tracking**: Live discovery status updates

**User Experience Features**

* **Smart Filtering**: By source, relationship type, and relevance score
* **Paper Bookmarking**: Save discoveries with visual feedback
* **Discovery Statistics**: Real-time analytics and source breakdown
* **Feedback Integration**: User preferences for algorithm improvement

AI Integration

[](https://github.com/samjd-zz/answer42#ai-integration)

Answer42 uses **Spring AI** for unified AI provider management with optimized model selection:

### Provider-Specific Optimizations

[](https://github.com/samjd-zz/answer42#provider-specific-optimizations)

    // Anthropic Claude - Best for deep paper analysis
    @Value("${spring.ai.anthropic.chat.options.model}")
    private String anthropicModel = "claude-3-7-sonnet-latest";
    
    // OpenAI GPT-4 - Optimal for cross-reference analysis  
    @Value("${spring.ai.openai.chat.options.model}")
    private String openaiModel = "gpt-4o";
    
    // Perplexity - Specialized for research and external knowledge
    @Value("${spring.ai.perplexity.chat.options.model}")
    private String perplexityModel = "llama-3.1-sonar-small-128k-online";

### Chat Session Management

[](https://github.com/samjd-zz/answer42#chat-session-management)

* **Contextual Memory**: Maintain conversation history within sessions
* **Paper Context Injection**: Automatically include relevant paper content
* **Multi-Paper Support**: Handle conversations spanning multiple papers
* **Real-time Processing**: Stream responses for better user experience

### Discovery API Integration

[](https://github.com/samjd-zz/answer42#discovery-api-integration)

* **Rate Limiting**: Enterprise-grade throttling with circuit breakers
* **Caching**: Multi-level caching with 24-hour duration
* **Error Resilience**: Graceful degradation and fallback mechanisms
* **Cost Tracking**: Comprehensive API usage monitoring

Database Design

[](https://github.com/samjd-zz/answer42#database-design)

### Core Entities

[](https://github.com/samjd-zz/answer42#core-entities)

**Users**

* Authentication and profile information
* Subscription and credit tracking
* User preferences and settings

**Papers**

* Complete paper metadata and content
* Processing status and analysis results
* External API integration data (Crossref, Semantic Scholar)
* JSONB fields for flexible metadata storage

**Chat Sessions**

* AI conversation history and context
* Associated papers and analysis results
* Provider-specific configurations

**Projects**

* Paper organization and grouping
* Research project metadata
* Collaboration features

**Discovery System (🆕 NEW!)**

* **Discovered Papers**: Multi-source paper metadata and discovery information
* **Paper Relationships**: Citation and similarity relationship tracking
* **Discovery Results**: Session-based discovery tracking and analytics
* **Discovery Feedback**: User feedback for algorithm improvement

### JSONB Usage

[](https://github.com/samjd-zz/answer42#jsonb-usage)

The platform leverages PostgreSQL's JSONB for flexible data storage:

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_findings", columnDefinition = "jsonb")
    private JsonNode keyFindings;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics", columnDefinition = "jsonb")
    private List<String> topics;

### Theme System

[](https://github.com/samjd-zz/answer42#theme-system)

Answer42 uses a structured theme system based on Vaadin best practices:

1. **Consistent Variables**: CSS custom properties for colors, spacing, shadows, etc.
2. **Component Modularity**: Styles organized by component type
3. **Dark Mode Support**: Built-in support for light and dark themes
4. **Responsive Design**: Mobile-first approach with responsive breakpoints
5. **Design System Integration**: Leverages Vaadin Lumo design system

The theme is activated by loading each CSS file in the AppShell class:

    @CssImport("./styles/themes/answer42/main.css")
    @CssImport("./styles/themes/answer42/styles.css")
    @CssImport("./styles/themes/answer42/components/auth-forms.css")
    @CssImport("./styles/themes/answer42/components/main-layout.css")
    @CssImport("./styles/themes/answer42/components/dashboard.css")
    @CssImport("./styles/themes/answer42/components/papers.css")
    @CssImport("./styles/themes/answer42/components/discovery.css")

Database Schema Management

[](https://github.com/samjd-zz/answer42#database-schema-management)

### Available Schema Files

[](https://github.com/samjd-zz/answer42#available-schema-files)

Answer42 provides pre-built PostgreSQL schema files for quick database setup:

#### **`answer42.schema-with-data.sql`** - Complete Database with Sample Data

[](https://github.com/samjd-zz/answer42#answer42schema-with-datasql---complete-database-with-sample-data)

* **Full PostgreSQL dump** with schema structure and sample data
* **40+ tables** including users, papers, discovery system, and AI agent infrastructure
* **Sample papers** for testing the multi-agent pipeline
* **Pre-configured subscription plans** and credit packages
* **Operation costs** for all AI agent types
* **User roles and permissions** setup
* **Indexes and constraints** optimized for production

**Usage:**

    # Import complete database with sample data
    psql -d your_database -f answer42.schema-with-data.sql

#### **`answer42.schema.sql`** - Schema Structure Only

[](https://github.com/samjd-zz/answer42#answer42schemasql---schema-structure-only)

* **Database structure only** (no sample data)
* **Clean slate** for production deployment
* **All tables, functions, triggers, and indexes**
* **Optimized for fresh installations**

**Usage:**

    # Import schema structure only
    psql -d your_database -f answer42.schema.sql

### Schema Highlights

[](https://github.com/samjd-zz/answer42#schema-highlights)

**Core Tables:**

* `users` - User authentication and profiles
* `papers` - Academic papers with full-text and metadata
* `discovered_papers` - Multi-source paper discovery results
* `chat_sessions` & `chat_messages` - AI conversation history
* `projects` - Research project organization
* `credit_balances` & `credit_transactions` - Credit system

**AI Agent Tables:**

* `tasks` - Agent task management and tracking
* `agent_memory_store` - Agent context and memory
* `operation_costs` - AI operation pricing by tier
* `user_operations` - Usage tracking and analytics

**Discovery System Tables:**

* `discovery_results` - Discovery session tracking
* `paper_relationships` - Citation and similarity relationships
* `discovery_feedback` - User feedback for algorithm improvement

**Advanced Features:**

* **JSONB Fields** - Flexible metadata storage for papers and analysis results
* **Full-Text Search** - GIN indexes on titles and abstracts
* **Relationship Mapping** - Complex paper relationship tracking
* **Performance Optimization** - 31 specialized indexes for fast queries

### Custom Database Setup Commands

[](https://github.com/samjd-zz/answer42#custom-database-setup-commands)

**Dump the entire schema**

    supabase db dump --schema public > schema.sql

**Dump specific schema**

    supabase db dump --schema answer42 > answer42_schema.sql

**Dump with data**

    supabase db dump --data-only > data.sql

**Dump structure only (no data)**

    supabase db dump --schema-only > structure.sql

**Dump specific tables**

    supabase db dump --table papers --table users > specific_tables.sql

### Database Migration

[](https://github.com/samjd-zz/answer42#database-migration)

For existing installations, Spring Boot will automatically handle schema migrations through Hibernate DDL auto-update. For production deployments, consider using the provided schema files for consistent database structure.

### Entity Design Guidelines

[](https://github.com/samjd-zz/answer42#entity-design-guidelines)

* **Database**: snake_case naming convention
* **Java**: camelCase naming convention
* **Entity properties**: Must map to database column names using `@Column`, `@JoinColumn`, `@JoinTable`

**Use Lombok for entities:**

    @Data // Lombok annotation for getters, setters, equals, hashCode, toString
    @NoArgsConstructor // Lombok for no-args constructor
    @AllArgsConstructor // Lombok for all-args constructor
    
    // JPA Default Loading Strategies
    @OneToOne    // EAGER by default
    @ManyToOne   // EAGER by default
    @OneToMany   // LAZY by default
    @ManyToMany  // LAZY by default
    
    @UpdateTimestamp // called on insert and update

### JSON Fields

[](https://github.com/samjd-zz/answer42#json-fields)

For complex attributes, leverage PostgreSQL's JSONB type with Hibernate:

    import org.hibernate.annotations.JdbcTypeCode;
    import org.hibernate.type.SqlTypes;
    
    @Entity
    @Table(name = "table_name", schema = "answer42")
    @Data
    @NoArgsConstructor
    public class YourEntity {    @Id
        @GeneratedValue(strategy = GenerationType.UUID)    private UUID id;    // Use JdbcTypeCode annotation for JSON object fields
        @JdbcTypeCode(SqlTypes.JSON)    @Column(columnDefinition = "jsonb")    private Map<String, Object> attributes;    // For array types stored as JSONB, use List<String>
        @JdbcTypeCode(SqlTypes.JSON)    @Column(columnDefinition = "jsonb")    private List<String> topics;}

Getting Started

[](https://github.com/samjd-zz/answer42#getting-started)

### Prerequisites

[](https://github.com/samjd-zz/answer42#prerequisites)

* **Java 21** or later
* **Maven 3.8** or later
* **PostgreSQL 14** or later
* **AI API Keys** (OpenAI, Anthropic, Perplexity)

### Quick Start

[](https://github.com/samjd-zz/answer42#quick-start)

1. **Clone the repository**
  
      git clone https://github.com/samjd-zz/answer42.git
      cd answer42
  
2. **Configure environment variables** Create a `.env` file in the project root:
  
      OPENAI_API_KEY=your_openai_api_key
      ANTHROPIC_API_KEY=your_anthropic_api_key
      PERPLEXITY_API_KEY=your_perplexity_api_key
  
3. **Set up PostgreSQL**
  
  * Create a database named `postgres`
  * Create schema: `CREATE SCHEMA answer42;`
  * Update connection details in `application.properties`
4. **Build and run**
  
      mvn clean installmvn spring-boot:run
  
5. **Access the application** Open your browser to `http://localhost:8080`
  

### Rapid Deployment with Replit

[](https://github.com/samjd-zz/answer42#rapid-deployment-with-replit)

🚀 **One-Click Cloud Deployment**: Answer42 includes a `.replit` configuration file for instant cloud deployment and development on [Replit](https://replit.com/).

**Features:**

* **Zero Setup**: Pre-configured Java GraalVM 21 environment with Maven
* **Instant Launch**: One-click deployment with automatic dependency resolution
* **Cloud IDE**: Full development environment in your browser
* **Live Preview**: Automatic port forwarding on port 8080
* **Production Ready**: Optimized build and deployment pipeline

**Quick Deploy Steps:**

1. Fork the repository on GitHub
2. Import to Replit or click "Run on Replit"
3. Environment automatically installs Java 21, Maven, and dependencies
4. Application starts with `mvn clean compile spring-boot:run`
5. Access via the provided Replit URL

**Replit Configuration Details:**

    modules = ["java-graalvm21", "web"]
    run = "mvn clean compile spring-boot:run"
    
    [deployment]
    run = ["sh", "-c", "mvn clean package -DskipTests && java -jar target/*.jar"][env]
    MAVEN_OPTS = "-Xmx2g -XX:+UseG1GC"
    JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

**Perfect For:**

* 🎓 **Academic Demos**: Quick setup for research presentations
* 🔬 **Prototype Testing**: Rapid experimentation with AI agents
* 👥 **Collaborative Development**: Shared development environments
* 📚 **Educational Use**: Teaching AI integration patterns
* 🚀 **Proof of Concepts**: Fast deployment for stakeholder reviews

**Environment Variables on Replit:**

* Set your AI API keys in Replit's "Secrets" tab:
  * `OPENAI_API_KEY`
  * `ANTHROPIC_API_KEY`
  * `PERPLEXITY_API_KEY`
* Database will auto-configure with Replit's built-in PostgreSQL

This makes Answer42 accessible to researchers and developers worldwide without any local setup requirements.Development

[](https://github.com/samjd-zz/answer42#development)

### Running in Development Mode

[](https://github.com/samjd-zz/answer42#running-in-development-mode)

    mvn spring-boot:run

This enables:

* Vaadin development mode with hot reload
* Detailed SQL logging
* Development-specific configurations

### Code Quality Tools

[](https://github.com/samjd-zz/answer42#code-quality-tools)

The project includes comprehensive code quality checks:

    # Run code style checks
    mvn checkstyle:check
    
    # Run static analysis
    mvn pmd:check
    
    # Find potential bugs
    mvn spotbugs:check
    
    # Run all quality checks
    mvn clean verify

### Building for Production

[](https://github.com/samjd-zz/answer42#building-for-production)

    mvn clean package -Pproduction

This creates an optimized build with:

* Minified frontend resources
* Production Vaadin compilation
* Optimized JAR/WAR packaging

### 🎨 UI Development & Contributing

[](https://github.com/samjd-zz/answer42#-ui-development--contributing)

**📋 Remaining UI Implementation:**

Answer42 has comprehensive design mockups for UI components that still need implementation. Before contributing to the frontend, please review the design specifications in the `docs/design/` directory:

**Available Design Mockups:**

* `dashboard.png` - Main dashboard layout and widgets
* `projects.png` - Research project management interface
* `profile.png` - User profile and account settings
* `credits.png` - Credit balance and subscription management
* `subscriptions.png` - Subscription plans and billing interface
* `settings.png` - Application settings and preferences
* `studyGuides.png` - Study guide generation and management
* `uploadPaper.png` & `bulkUploadPaper.png` - Paper upload interfaces
* `viewPaper.png` - Individual paper viewing and analysis
* `papers.png` - Paper library and organization
* `aiChat*.png` - AI chat interface variations and workflows

**Design Documentation:**

* `docs/design/README.md` - Complete design system documentation
* `answer42_pitch_deck.md` - Product vision and feature overview

**Contributing Guidelines:**

1. Check `docs/design/` for existing mockups before starting UI work
2. Follow the established Vaadin component patterns in existing views
3. Ensure responsive design compatibility across devices
4. Implement dark mode support using the existing theme system
5. Add appropriate accessibility features (ARIA labels, keyboard navigation)

**UI Architecture:**

* **Views**: Located in `src/main/java/com/samjdtechnologies/answer42/ui/views/`
* **Helpers**: UI helper classes in `src/main/java/com/samjdtechnologies/answer42/ui/helpers/`
* **Themes**: CSS themes in `src/main/frontend/styles/themes/answer42/`
* **Components**: Reusable components following Vaadin best practices

Contributors are encouraged to implement these designed interfaces to complete the platform's user experience.

### 🔧 Technical Debt & Refactoring Opportunities

[](https://github.com/samjd-zz/answer42#-technical-debt--refactoring-opportunities)

**🤖 AI Chat System Modernization:**

The current AI chat implementation needs to be refactored to leverage the sophisticated agent system:

**Current Architecture Issues:**

* Chat helper classes should be refactored to proper chat controllers
* Direct AI provider calls should be replaced with enterprise-grade agent implementations
* Missing integration with the retry policy, rate limiting, and circuit breaker systems

**Recommended Refactoring:**

1. **Migrate to Agent-Based Architecture**: Replace direct AI provider calls with the comprehensive agent implementations in `src/main/java/com/samjdtechnologies/answer42/service/agent/`
  
2. **Available Agent Classes for Integration**:
  
  * `AbstractConfigurableAgent.java` - Base class with retry policies, rate limiting, and circuit breakers
  * `AnthropicBasedAgent.java` - Optimized for Anthropic Claude interactions
  * `OpenAIBasedAgent.java` - Optimized for OpenAI GPT-4 interactions
  * `PerplexityBasedAgent.java` - Optimized for Perplexity research queries
  * `ContentSummarizerAgent.java` - Multi-level summarization capabilities
  * `ConceptExplainerAgent.java` - Technical term explanation with education levels
  * `QualityCheckerAgent.java` - Comprehensive quality assessment and bias detection
3. **Benefits of Migration**:
  
  * ✅ Enterprise-grade error handling and retry mechanisms
  * ✅ Built-in rate limiting and circuit breaker protection
  * ✅ Comprehensive metrics and monitoring
  * ✅ User-aware API key management through AIConfig
  * ✅ Parallel processing capabilities via ThreadConfig
  * ✅ Consistent logging and debugging support
4. **Implementation Priority**:
  
  * **High**: Paper Chat (Anthropic) → Use `AnthropicBasedAgent` + `ContentSummarizerAgent`
  * **High**: Cross-Reference Chat (OpenAI) → Use `OpenAIBasedAgent` + `QualityCheckerAgent`
  * **Medium**: Research Explorer (Perplexity) → Use `PerplexityBasedAgent` + `PerplexityResearchAgent`

**Chat Controller Architecture:**

* Move from helper classes to proper Spring MVC controllers
* Implement RESTful endpoints for chat interactions
* Add WebSocket support for real-time streaming responses
* Integrate with the existing agent task management system

This refactoring will significantly improve system reliability, performance monitoring, and maintenance while providing a better user experience through the advanced agent capabilities.Configuration

[](https://github.com/samjd-zz/answer42#configuration)

### Key Configuration Files

[](https://github.com/samjd-zz/answer42#key-configuration-files)

**application.properties**

* Database connection settings
* AI provider configurations
* Security and JWT settings
* Vaadin development options

**AI Provider Settings**

    # Anthropic Configuration (Production-optimized with Haiku for speed and reliability)
    spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:dummy-key}
    spring.ai.anthropic.chat.options.model=claude-3-haiku-20240307
    spring.ai.anthropic.chat.options.temperature=0.7
    spring.ai.anthropic.chat.options.max-tokens=4000
    spring.ai.anthropic.http.connect-timeout=120s
    spring.ai.anthropic.http.read-timeout=600s
    spring.ai.anthropic.http.write-timeout=120s
    
    # OpenAI Configuration (Production-optimized with GPT-3.5-turbo for high-volume use)
    spring.ai.openai.api-key=${OPENAI_API_KEY:dummy-key}
    spring.ai.openai.chat.options.model=gpt-3.5-turbo
    spring.ai.openai.chat.options.temperature=0.7
    spring.ai.openai.chat.options.max-completion-tokens=4000
    spring.ai.openai.http.connect-timeout=300s
    spring.ai.openai.http.read-timeout=1200s
    spring.ai.openai.http.write-timeout=300s
    
    # Perplexity Configuration (Extended timeouts for research operations)
    spring.ai.perplexity.api-key=${PERPLEXITY_API_KEY:dummy-key}
    spring.ai.perplexity.chat.options.model=sonar-pro
    spring.ai.perplexity.chat.options.temperature=0.7
    spring.ai.perplexity.chat.options.max-tokens=4000
    spring.ai.perplexity.http.connect-timeout=300s
    spring.ai.perplexity.http.read-timeout=1800s
    spring.ai.perplexity.http.write-timeout=300s

**Discovery API Configuration**

    # Related Paper Discovery Configuration (Environment-aware)
    discovery.enabled=${DISCOVERY_ENABLED:true}
    discovery.semantic-scholar.api-key=${SEMANTIC_SCHOLAR_API_KEY:}
    discovery.semantic-scholar.base-url=https://api.semanticscholar.org/graph/v1
    discovery.crossref.base-url=https://api.crossref.org/works
    discovery.max-papers-per-source=${DISCOVERY_MAX_PAPERS_PER_SOURCE:50}
    discovery.cache.duration-hours=${DISCOVERY_CACHE_DURATION_HOURS:24}
    discovery.rate-limit.enabled=${DISCOVERY_RATE_LIMIT_ENABLED:true}
    discovery.rate-limit.crossref.requests-per-second=45
    discovery.rate-limit.semantic-scholar.requests-per-minute=100
    discovery.rate-limit.perplexity.requests-per-minute=10

**Ollama Local Fallback Configuration** (🆕 **NEW!**)

    # Ollama Local Processing Configuration (Extended timeouts for laptop processing)
    spring.ai.ollama.enabled=${OLLAMA_ENABLED:true}
    spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
    spring.ai.ollama.chat.options.model=${OLLAMA_MODEL:llama3.1:8b}
    spring.ai.ollama.chat.options.temperature=0.7
    spring.ai.ollama.chat.options.max-tokens=4000
    spring.ai.ollama.timeout=1800000
    spring.ai.ollama.http.connect-timeout=300s
    spring.ai.ollama.http.read-timeout=1800s
    spring.ai.ollama.http.write-timeout=300s
    
    # Fallback Configuration (Environment-aware)
    spring.ai.fallback.enabled=${FALLBACK_ENABLED:true}
    spring.ai.fallback.retry-after-failures=${FALLBACK_RETRY_AFTER_FAILURES:3}
    spring.ai.fallback.timeout-seconds=${FALLBACK_TIMEOUT_SECONDS:60}
    spring.ai.fallback.connection-check-timeout=${FALLBACK_CONNECTION_CHECK_TIMEOUT:5000}
    spring.ai.fallback.health-check-interval=${FALLBACK_HEALTH_CHECK_INTERVAL:30000}

**Database Configuration** (Environment-aware with connection pooling)

    # PostgreSQL Connection
    spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:54322/postgres?ssl=false&sslmode=disable}
    spring.datasource.username=${DATABASE_USERNAME:postgres}
    spring.datasource.password=${DATABASE_PASSWORD:postgres}
    
    # JPA / Hibernate Configuration
    spring.jpa.properties.hibernate.default_schema=answer42
    spring.jpa.hibernate.ddl-auto=${HIBERNATE_DDL_AUTO:update}
    spring.jpa.show-sql=false
    spring.jpa.open-in-view=false
    
    # Connection Pool Settings (HikariCP)
    spring.datasource.hikari.maximum-pool-size=${HIKARI_MAXIMUM_POOL_SIZE:10}
    spring.datasource.hikari.minimum-idle=${HIKARI_MINIMUM_IDLE:5}
    spring.datasource.hikari.connection-timeout=${HIKARI_CONNECTION_TIMEOUT:20000}
    spring.datasource.hikari.idle-timeout=${HIKARI_IDLE_TIMEOUT:300000}
    spring.datasource.hikari.max-lifetime=${HIKARI_MAX_LIFETIME:1200000}

**Additional Configuration**

    # Vaadin Configuration
    vaadin.productionMode=false
    vaadin.closeIdleSessions=true
    vaadin.heartbeatInterval=60
    vaadin.push.transport=websocket
    
    # File Upload Configuration
    spring.servlet.multipart.max-file-size=${MAX_FILE_SIZE:50MB}
    spring.servlet.multipart.max-request-size=${MAX_REQUEST_SIZE:60MB}
    upload.large-file-threshold=${LARGE_FILE_THRESHOLD:52428800}
    
    # JWT Authentication
    app.auth.jwt.secret=${JWT_SECRET:}
    app.auth.jwt.expiration=86400000
    app.auth.jwt.header=Authorization
    app.auth.jwt.prefix=Bearer
    
    # Spring Batch Configuration
    spring.batch.job.enabled=false
    spring.batch.jdbc.initialize-schema=never
    spring.batch.jdbc.table-prefix=answer42.BATCH_
    
    # Thread Management
    spring.task.execution.thread-name-prefix=answer42-exec-
    spring.task.scheduling.thread-name-prefix=answer42-sched-
    
    # Metrics Export (5-minute intervals)
    management.metrics.export.logging.enabled=true
    management.metrics.export.logging.step=PT5M

💝 Support the Project

[](https://github.com/samjd-zz/answer42#-support-the-project)

Your support helps us maintain and expand this platform for the global research community.

### Why Support Answer42?

[](https://github.com/samjd-zz/answer42#why-support-answer42)

Your donations directly support:

* 🤖 **AI API Credits** - Keeping all 8 specialized agents running smoothly
* 🚀 **Infrastructure Scaling** - Supporting more researchers worldwide
* 📚 **Feature Development** - Adding new AI providers and academic integrations
* 🔧 **Maintenance & Updates** - Ongoing development and bug fixes
* 🎓 **Educational Access** - Keeping the platform free for students and researchers

### Bitcoin Donations

[](https://github.com/samjd-zz/answer42#bitcoin-donations)

We accept Bitcoin donations to support the project:

**Bitcoin Address:** `bc1q8hgvafe6qg6z0g06y65xqn4vzf7crhnvtt505f`

[![Bitcoin Donation QR Code](https://github.com/samjd-zz/answer42/raw/main/btc-receive.png)](https://github.com/samjd-zz/answer42/blob/main/btc-receive.png)

_Scan the QR code above or copy the Bitcoin address to make a donation_

### Other Ways to Support

[](https://github.com/samjd-zz/answer42#other-ways-to-support)

* ⭐ **Star the repository** on GitHub
* 🐛 **Report bugs** and suggest improvements
* 📖 **Contribute documentation** and tutorials
* 💬 **Share Answer42** with your academic network
* 🤝 **Contribute code** and new features
* 🎓 **Use Answer42** for your research and provide feedback

Every contribution, whether financial or community-based, helps make Answer42 better for researchers worldwide!License

[](https://github.com/samjd-zz/answer42#license)

Answer42 is licensed under the **Apache License 2.0** - a permissive open source license that allows both personal and commercial use.

This license allows you to:

* ✅ **Use** the software for any purpose (personal, academic, commercial)
* ✅ **Modify** the source code to fit your needs
* ✅ **Distribute** copies of the original or modified software
* ✅ **Patent Grant** protection from patent litigation
* ✅ **Sublicense** and create derivative works

**Requirements:**

* 📄 Include the original copyright notice and license text
* 📝 State any significant changes made to the code
* 🔗 Provide attribution to the original project

For complete license terms, see the [LICENSE](https://github.com/samjd-zz/answer42/blob/main/LICENSE) file in the project root.

**Why Apache License 2.0?**

We chose Apache License 2.0 because it:

* **Encourages Collaboration**: Permissive terms foster community contributions and innovation
* **Enterprise Friendly**: Companies can integrate and build upon Answer42 without restrictive copyleft requirements
* **Patent Protection**: Explicit patent grant protects users from patent litigation
* **Academic Compatible**: Universities and researchers can freely use and modify the platform
* **Industry Standard**: Widely adopted by major open source projects (Apache Software Foundation, Google, Meta)
* **Clear Terms**: Well-understood legal framework reduces licensing uncertainty

**Data Privacy & AI Models:**

* 🔒 Your research content remains private and secure
* 🚫 We never use your data to train AI models
* 🎯 AI processing is performed through external APIs (OpenAI, Anthropic, Perplexity) per their terms
* 📊 Only anonymous usage statistics are collected to improve the platform

**Commercial Use:** This software is free for commercial use under Apache License 2.0. If you build a business using Answer42, please consider:

* Contributing improvements back to the community
* Sponsoring the project development
* Providing attribution in your product documentation

For questions about licensing, contributions, or commercial partnerships, please open an issue or contact the maintainers.📞 Contact

[](https://github.com/samjd-zz/answer42#-contact)

* **Project Lead**: Shawn (samjd-zz)
* **Email**: [shawn@samjdtechnologies.com](mailto:shawn@samjdtechnologies.com)
* **LinkedIn Profile**: https://www.linkedin.com/in/shawn-jackson-dyck-52aa74358
* **LinkedIn Group**: https://www.linkedin.com/groups/13285008/ (AI-TDD Community)
* **GitHub Issues**: For bug reports and feature requests
* **Repository**: [GitHub - samjd-zz/answer42: A modern research management platform built with Spring Boot and Vaadin](https://github.com/samjd-zz/answer42)

* * *

**Answer42** - Making academic research more intelligent, one paper at a time. 🚀📚Answer42 - AI-Powered Academic Research Platform
