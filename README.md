# Answer42 - AI-Powered Academic Research Platform

![Answer42 Logo](src/main/resources/META-INF/resources/frontend/images/answer42-logo.svg)

Answer42 is a sophisticated AI-powered platform for academic researchers, students, and scholars to upload, analyze, and interact with research papers using multiple AI providers. The platform combines advanced paper processing with intelligent chat capabilities and a revolutionary multi-agent processing pipeline to make academic research more efficient and insightful.

## Table of Contents

- [What is Answer42?](#what-is-answer42)
- [Technology Stack](#technology-stack)
- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Core Features](#core-features)
- [Multi-Agent Processing Pipeline](#multi-agent-processing-pipeline)
- [Related Papers Discovery System](#related-papers-discovery-system)
- [AI Integration](#ai-integration)
- [Database Design](#database-design)
- [Getting Started](#getting-started)
- [Development](#development)
- [Configuration](#configuration)
- [License](#license)

## What is Answer42?

Answer42 is a comprehensive research assistant that helps academics work with scientific papers through:

- **Intelligent Paper Processing**: Upload PDFs and extract full text, metadata, and structured information
- **Multi-Agent Processing Pipeline**: Eight specialized AI agents for comprehensive paper analysis 
- **Related Papers Discovery**: Multi-source intelligent discovery of related academic papers 
- **Multi-Modal AI Chat**: Three specialized chat modes using different AI providers for various research needs
- **Comprehensive Analysis**: Generate summaries, extract key findings, identify methodologies, and create glossaries
- **External Metadata Integration**: Automatic enhancement using Crossref and Semantic Scholar APIs
- **Project Organization**: Group papers into research projects for better organization
- **Credit-Based System**: Subscription model with credit management for AI operations

## Technology Stack

### Backend

- **Java 21** - Modern Java with latest features
- **Spring Boot 3.4.5** - Enterprise application framework
- **Spring Batch** - Enterprise-grade job processing and workflow orchestration
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database access with Hibernate
- **Spring AI** - Unified AI provider integration
- **PostgreSQL** - Primary database with JSONB support
- **JWT** - Stateless authentication tokens
- **Lombok** - Reduced boilerplate code

### Frontend

- **Vaadin 24.7.3** - Full-stack Java web framework
- **Custom CSS Themes** - Responsive design with dark mode support
- **Progressive Web App** - Offline capabilities and mobile support

### AI Providers

- **Anthropic Claude** - Paper-specific analysis and chat
- **OpenAI GPT-4** - Cross-reference analysis and general chat
- **Perplexity** - Research exploration and external knowledge

### External APIs

- **Crossref API** - Citation network analysis and bibliographic discovery
- **Semantic Scholar API** - Semantic similarity and influence analysis
- **Perplexity API** - Real-time research trends and open access discovery

### Development Tools

- **Maven** - Build automation and dependency management
- **Checkstyle, PMD, SpotBugs** - Code quality and static analysis
- **Spring Boot DevTools** - Development hot-reload

## Architecture Overview

Answer42 follows a clean layered architecture with a sophisticated multi-agent processing system and intelligent discovery capabilities:

```
┌─────────────────────┐
│   Vaadin Views      │  User Interface Layer
├─────────────────────┤
│   Controllers       │  REST API Layer  
├─────────────────────┤
│   Services          │  Business Logic Layer
├─────────────────────┤
│  Multi-Agent        │  AI Processing Layer (🆕 NEW!)
│  Pipeline System    │  Eight Specialized Agents
├─────────────────────┤
│  Discovery System   │  Multi-Source Paper Discovery (🆕 NEW!)
│  (Crossref, S2, AI) │  Citation Networks & Semantic Analysis
├─────────────────────┤
│   Repositories      │  Data Access Layer
├─────────────────────┤
│   PostgreSQL        │  Database Layer
└─────────────────────┘
```

### Key Design Patterns

- **MVC Architecture** - Clear separation of concerns
- **Multi-Agent Pattern** - Specialized AI agents for different tasks (🆕 **NEW!**)
- **Multi-Source Discovery Pattern** - Intelligent paper discovery from multiple APIs (🆕 **NEW!**)
- **Repository Pattern** - Data access abstraction
- **Service Layer** - Business logic encapsulation
- **Dependency Injection** - Loose coupling via Spring IoC
- **Component-Based UI** - Reusable Vaadin components

## Core Features

### 📄 Paper Management

- **PDF Upload & Processing**: Extract text content and metadata from academic papers
- **Real-time Pipeline Processing**: Live progress tracking during multi-agent analysis (🆕 **NEW!**)
- **Metadata Enhancement**: Automatic enrichment via Crossref and Semantic Scholar APIs
- **Comprehensive Storage**: Store papers with full text, abstracts, authors, citations, and analysis results
- **Organization**: Group papers into research projects for better management
- **Bulk Upload**: Process multiple papers simultaneously with parallel pipeline processing

### 🔍 Related Papers Discovery (🆕 **NEW!**)

**Intelligent multi-source discovery system for comprehensive research exploration:**

1. **Multi-Source Discovery**
   
   - **Crossref API Integration**: Citation network analysis and bibliographic discovery
   - **Semantic Scholar API**: Semantic similarity and influence metrics analysis
   - **Perplexity API**: Real-time research trends and open access paper discovery
   - **AI Synthesis Engine**: Intelligent combination and ranking of results from all sources

2. **Advanced Discovery Features**
   
   - **Citation Networks**: Forward and backward citation analysis with relationship mapping
   - **Semantic Similarity**: AI-powered related paper discovery based on content similarity
   - **Author Networks**: Comprehensive researcher tracking and collaboration analysis
   - **Field Evolution**: Historical and current research trends in specific domains
   - **Real-time Context**: Current academic discussions and developments

3. **Interactive Discovery Interface**
   
   - **Smart Filtering**: Filter by discovery source, relationship type, and relevance score
   - **Citation Network Visualization**: Interactive graph-based citation network exploration
   - **Paper Bookmarking**: Save interesting discoveries for future reference
   - **Feedback Collection**: Structured user feedback to improve discovery algorithms
   - **Detailed Statistics**: Comprehensive discovery analytics and source breakdown

4. **Quality Assessment & Ranking**
   
   - **AI-Powered Relevance Scoring**: Multi-factor ranking algorithm using Claude
   - **Duplicate Detection**: Intelligent paper merging and deduplication across sources
   - **Source Reliability Scoring**: Quality assessment based on API source and completeness
   - **User Feedback Integration**: Continuous improvement through user preference learning

### 🤖 Multi-Modal AI Chat

**Three specialized chat modes optimized for different research needs:**

1. **Paper Chat (Anthropic Claude)**
   
   - Deep analysis of individual papers
   - Contextual Q&A about paper content
   - Generate summaries, key findings, and glossaries
   - One-click analysis buttons for common tasks

2. **Cross-Reference Chat (OpenAI GPT-4)**
   
   - Compare multiple papers simultaneously
   - Identify agreements, contradictions, and research gaps
   - Relationship analysis between different studies
   - Methodology and results comparison

3. **Research Explorer (Perplexity)**
   
   - External research and fact-checking
   - Discover related papers and research
   - Verify claims against current literature
   - General academic research assistance

### 📊 Intelligent Analysis

- **Automated Summaries**: Brief, standard, and detailed summaries
- **Key Findings Extraction**: Identify main contributions and results
- **Methodology Analysis**: Extract and analyze research methods
- **Concept Glossaries**: Generate definitions for technical terms
- **Citation Analysis**: Process and structure reference lists
- **Quality Assessment**: AI-powered quality scoring and feedback

### 👤 User Management

- **Secure Authentication**: JWT-based authentication with Spring Security
- **User Profiles**: Customizable user preferences and settings
- **Subscription Management**: Credit-based system with multiple tiers
- **Progress Tracking**: Monitor paper processing and analysis status

## Multi-Agent Processing Pipeline

🆕 **NEW FEATURE**: Answer42 now features a sophisticated multi-agent processing pipeline powered by **Spring Batch** that automatically analyzes papers using eight specialized AI agents.

### Pipeline Architecture

The multi-agent system uses **Spring Batch** for enterprise-grade job processing and workflow orchestration, processing papers through coordinated AI agents in a sequential 8-step pipeline:

```mermaid
flowchart TD
    A[Paper Upload] --> B[Spring Batch Job: paperProcessingJob]
    B --> C[Step 1: Paper Text Extraction<br/>PaperProcessorTasklet<br/>OpenAI GPT-4]
    C --> D[Step 2: Metadata Enhancement<br/>MetadataEnhancementTasklet<br/>OpenAI GPT-4 + APIs]
    D --> E[Step 3: Content Summarization<br/>ContentSummarizerTasklet<br/>Anthropic Claude]
    E --> F[Step 4: Concept Explanation<br/>ConceptExplainerTasklet<br/>OpenAI GPT-4]
    F --> G[Step 5: Perplexity Research<br/>PerplexityResearchTasklet<br/>Perplexity API]
    G --> H[Step 6: Related Paper Discovery<br/>RelatedPaperDiscoveryTasklet<br/>Anthropic Claude + Multi-APIs]
    H --> I[Step 7: Citation Formatting<br/>CitationFormatterTasklet<br/>OpenAI GPT-4]
    I --> J[Step 8: Quality Assessment<br/>QualityCheckerTasklet<br/>Anthropic Claude]
    J --> K[Pipeline Complete<br/>papers.status = 'PROCESSED']

    style B fill:#e1f5fe
    style C fill:#f3e5f5
    style D fill:#f3e5f5
    style E fill:#e8f5e8
    style F fill:#f3e5f5
    style G fill:#fff3e0
    style H fill:#e1f5fe
    style I fill:#f3e5f5
    style J fill:#e8f5e8
    style K fill:#e8f6f3
```

### Specialized AI Agents

**🔧 Paper Processor Agent** (OpenAI GPT-4)

- PDF text extraction and structure analysis
- Section identification (introduction, methods, results, etc.)
- Table and figure recognition
- Mathematical notation processing

**📚 Metadata Enhancement Agent** (OpenAI GPT-4)

- Crossref API integration for bibliographic data
- Semantic Scholar API integration for citation metrics
- DOI resolution and validation
- Author disambiguation and affiliation resolution

**📝 Content Summarizer Agent** (Anthropic Claude)

- Multi-level summary generation (brief, standard, detailed)
- Executive summaries for non-specialists
- Technical summaries for domain experts
- Key findings extraction with confidence scoring

**🧠 Concept Explainer Agent** (OpenAI GPT-4)

- Technical term identification and explanation
- Concept relationship mapping
- Education level-appropriate explanations
- Real-world analogies for complex concepts

**✅ Quality Checker Agent** (Anthropic Claude)

- Multi-dimensional accuracy verification
- Hallucination detection and fact-checking
- Content consistency analysis
- Bias detection and quality scoring

**📖 Citation Formatter Agent** (OpenAI GPT-4)

- Reference list parsing and structuring
- Multiple citation style formatting (APA, MLA, Chicago, IEEE)
- DOI validation and metadata enrichment
- Bibliography generation with quality assessment

**🔍 Related Paper Discovery Agent** (🆕 **NEW!**) (Anthropic Claude + Perplexity API)

- Multi-source paper discovery coordination (Crossref, Semantic Scholar, Perplexity)
- AI-powered relevance score enhancement using Anthropic Claude
- Perplexity API integration for real-time research trends and discussions
- Result synthesis and deduplication across all sources
- Discovery metadata tracking and storage

**🔬 Perplexity Research Agent** (🆕 **NEW!**) (Perplexity API)

- External research and fact verification
- Real-time web search and research synthesis
- Current literature verification and validation
- Research trend analysis and contextualization

### Pipeline Features

**Real-time Progress Tracking**

- Live agent status updates in the Papers view
- Individual agent progress indicators
- Estimated completion times
- WebSocket-powered real-time updates

**Intelligent Task Management**

- Database-driven task lifecycle management
- Spring Event system integration
- Automatic retry mechanisms and error handling
- Agent memory and context management

**Cost-Optimized Processing**

- Token usage tracking and analytics
- Credit-based pricing integration
- Performance monitoring and optimization
- User-aware API key management

### Core vs Enhancement Agents for AI Chat Integration

The multi-agent pipeline produces data that directly powers Answer42's three AI chat modes. Each chat mode requires specific agent outputs to function optimally:

#### **Paper Chat (Anthropic Claude) - Individual Paper Analysis**

**CORE REQUIRED (Essential for functionality):**

- 🔧 **Paper Processor** → `papers.text_content` (raw paper content)
- 📝 **Content Summarizer** → `papers.summary_brief/standard/detailed`, `papers.key_findings`
- 🧠 **Concept Explainer** → `papers.glossary`, `papers.main_concepts`

**ENHANCEMENT (Beneficial but optional):**

- Metadata Enhancement, Perplexity Research, Related Paper Discovery, Citation Formatting, Quality Assessment

#### **Cross-Reference Chat (OpenAI GPT-4) - Multi-Paper Comparison**

**CORE REQUIRED (Essential for functionality):**

- 🔧 **Paper Processor** → `papers.text_content` (raw paper content)
- 📝 **Content Summarizer** → `papers.summary_brief/standard/detailed`, `papers.key_findings`
- 📖 **Citation Formatter** → `papers.citations` (for reference comparisons)
- ✅ **Quality Checker** → `papers.quality_feedback`, `papers.quality_score` (reliability assessment)

**ENHANCEMENT (Beneficial but optional):**

- Metadata Enhancement, Concept Explainer, Perplexity Research, Related Paper Discovery

#### **Research Explorer Chat (Perplexity) - External Research & Discovery**

**CORE REQUIRED (Essential for functionality):**

- 🔧 **Paper Processor** → `papers.text_content` (raw paper content)
- 🔬 **Perplexity Research** → `papers.research_questions`, `papers.methodology_details`
- 🔍 **Related Paper Discovery** → `discovered_papers`, `papers.metadata.research_context`

**ENHANCEMENT (Beneficial but optional):**

- Metadata Enhancement, Content Summarizer, Concept Explainer, Citation Formatting, Quality Assessment

**Database Integration:** The AI chat clients automatically access the appropriate database tables populated by these core agents, enabling contextual conversations about papers with rich, pre-processed information.

## Related Papers Discovery System

🆕 **NEW FEATURE**: Answer42's Related Papers Discovery System provides comprehensive research landscape exploration through multi-source intelligent discovery.

### Discovery Architecture

The system combines multiple academic APIs with AI synthesis for comprehensive paper discovery:

```mermaid
flowchart TD
    A[Paper Input] --> B[Discovery Coordinator]
    B --> C[Crossref Discovery Service]
    B --> D[Semantic Scholar Discovery Service]  
    B --> E[Perplexity Discovery Service]

    C --> F[Citation Networks]
    C --> G[Author Networks]
    C --> H[Venue Discovery]

    D --> I[Semantic Similarity]
    D --> J[Influence Metrics]
    D --> K[Field Classification]

    E --> L[Current Trends]
    E --> M[Open Access Papers]
    E --> N[Academic Discussions]

    F --> O[AI Synthesis Engine]
    G --> O
    H --> O
    I --> O
    J --> O
    K --> O
    L --> O
    M --> O
    N --> O

    O --> P[Relevance Ranking]
    O --> Q[Duplicate Detection]
    O --> R[Quality Assessment]

    P --> S[Discovery Results]
    Q --> S
    R --> S

    S --> T[Database Storage]
    S --> U[UI Presentation]
```

### Discovery Sources

**🔗 Crossref API Discovery**

- Forward citations (papers that cite this work)
- Backward citations (papers this work cites)
- Author network discovery and publication venue exploration
- Subject classification and temporal analysis
- Rate-limited processing (45 requests/second)

**🧠 Semantic Scholar API Discovery**

- Semantic similarity analysis using paper embeddings
- Influence metrics and impact assessment
- Research field classification and author disambiguation
- Citation context analysis
- Free tier integration (100 requests/minute)

**🌐 Perplexity API Discovery**

- Real-time research trend analysis
- Current academic discussions and debates
- Open access paper discovery
- Press coverage and impact tracking
- Trend-aware discovery (10 requests/minute)

### AI Synthesis Engine

**Intelligent Result Processing**

- **Multi-source Deduplication**: DOI, title, and author-based paper matching
- **AI-Powered Relevance Enhancement**: Claude-based scoring using 5-factor algorithm
- **Quality Filtering**: Configurable thresholds with source reliability assessment
- **Batch Processing**: Optimal AI API usage with error handling and fallback

**Advanced Ranking Algorithm**

1. **Base Relevance Score**: From discovery source APIs
2. **Relationship Importance**: Weighted by relationship type (citations > similarity > trends)
3. **Citation Impact**: Based on citation count and influence metrics
4. **Data Completeness**: Higher scores for papers with rich metadata
5. **Source Reliability**: Crossref > Semantic Scholar > Perplexity weighting

### Discovery Database Schema

**Comprehensive Storage System**

- **discovered_papers**: 38 columns with complete paper metadata from all sources
- **paper_relationships**: 13 columns tracking relationship types and strength
- **discovery_results**: 26 columns for discovery session tracking and metrics
- **discovery_feedback**: 20 columns for user feedback and algorithm improvement

**Performance Optimization**

- 31 database indexes for fast querying and filtering
- JSONB fields for flexible metadata storage
- Efficient relationship mapping and discovery result aggregation

### Discovery UI Components

**Interactive Discovery Interface**

- **RelatedPapersSection**: Main discovery results with filtering and statistics
- **CitationNetworkDialog**: Interactive citation network visualization
- **PaperDetailsDialog**: Comprehensive paper information display
- **DiscoveryFeedbackDialog**: Structured user feedback collection
- **Real-time Progress Tracking**: Live discovery status updates

**User Experience Features**

- **Smart Filtering**: By source, relationship type, and relevance score
- **Paper Bookmarking**: Save discoveries with visual feedback
- **Discovery Statistics**: Real-time analytics and source breakdown
- **Feedback Integration**: User preferences for algorithm improvement

## AI Integration

Answer42 uses **Spring AI** for unified AI provider management with optimized model selection:

### Provider-Specific Optimizations

```java
// Anthropic Claude - Best for deep paper analysis
@Value("${spring.ai.anthropic.chat.options.model}")
private String anthropicModel = "claude-3-7-sonnet-latest";

// OpenAI GPT-4 - Optimal for cross-reference analysis  
@Value("${spring.ai.openai.chat.options.model}")
private String openaiModel = "gpt-4o";

// Perplexity - Specialized for research and external knowledge
@Value("${spring.ai.perplexity.chat.options.model}")
private String perplexityModel = "llama-3.1-sonar-small-128k-online";
```

### Chat Session Management

- **Contextual Memory**: Maintain conversation history within sessions
- **Paper Context Injection**: Automatically include relevant paper content
- **Multi-Paper Support**: Handle conversations spanning multiple papers
- **Real-time Processing**: Stream responses for better user experience

### Discovery API Integration

- **Rate Limiting**: Enterprise-grade throttling with circuit breakers
- **Caching**: Multi-level caching with 24-hour duration
- **Error Resilience**: Graceful degradation and fallback mechanisms
- **Cost Tracking**: Comprehensive API usage monitoring

## Database Design

### Core Entities

**Users**

- Authentication and profile information
- Subscription and credit tracking
- User preferences and settings

**Papers**

- Complete paper metadata and content
- Processing status and analysis results
- External API integration data (Crossref, Semantic Scholar)
- JSONB fields for flexible metadata storage

**Chat Sessions**

- AI conversation history and context
- Associated papers and analysis results
- Provider-specific configurations

**Projects**

- Paper organization and grouping
- Research project metadata
- Collaboration features

**Discovery System (🆕 NEW!)**

- **Discovered Papers**: Multi-source paper metadata and discovery information
- **Paper Relationships**: Citation and similarity relationship tracking
- **Discovery Results**: Session-based discovery tracking and analytics
- **Discovery Feedback**: User feedback for algorithm improvement

### JSONB Usage

The platform leverages PostgreSQL's JSONB for flexible data storage:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "metadata", columnDefinition = "jsonb")
private JsonNode metadata;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "key_findings", columnDefinition = "jsonb")
private JsonNode keyFindings;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "topics", columnDefinition = "jsonb")
private List<String> topics;
```

### Theme System

Answer42 uses a structured theme system based on Vaadin best practices:

1. **Consistent Variables**: CSS custom properties for colors, spacing, shadows, etc.
2. **Component Modularity**: Styles organized by component type
3. **Dark Mode Support**: Built-in support for light and dark themes
4. **Responsive Design**: Mobile-first approach with responsive breakpoints
5. **Design System Integration**: Leverages Vaadin Lumo design system

The theme is activated by loading each CSS file in the AppShell class:

```java
@CssImport("./styles/themes/answer42/main.css")
@CssImport("./styles/themes/answer42/styles.css")
@CssImport("./styles/themes/answer42/components/auth-forms.css")
@CssImport("./styles/themes/answer42/components/main-layout.css")
@CssImport("./styles/themes/answer42/components/dashboard.css")
@CssImport("./styles/themes/answer42/components/papers.css")
@CssImport("./styles/themes/answer42/components/discovery.css")
```

## Database Schema Management

### Available Schema Files

Answer42 provides pre-built PostgreSQL schema files for quick database setup:

#### **`answer42.schema-with-data.sql`** - Complete Database with Sample Data
- **Full PostgreSQL dump** with schema structure and sample data
- **40+ tables** including users, papers, discovery system, and AI agent infrastructure
- **Sample papers** for testing the multi-agent pipeline
- **Pre-configured subscription plans** and credit packages
- **Operation costs** for all AI agent types
- **User roles and permissions** setup
- **Indexes and constraints** optimized for production

**Usage:**
```bash
# Import complete database with sample data
psql -d your_database -f answer42.schema-with-data.sql
```

#### **`answer42.schema.sql`** - Schema Structure Only
- **Database structure only** (no sample data)
- **Clean slate** for production deployment
- **All tables, functions, triggers, and indexes**
- **Optimized for fresh installations**

**Usage:**
```bash
# Import schema structure only
psql -d your_database -f answer42.schema.sql
```

### Schema Highlights

**Core Tables:**
- `users` - User authentication and profiles
- `papers` - Academic papers with full-text and metadata
- `discovered_papers` - Multi-source paper discovery results
- `chat_sessions` & `chat_messages` - AI conversation history
- `projects` - Research project organization
- `credit_balances` & `credit_transactions` - Credit system

**AI Agent Tables:**
- `tasks` - Agent task management and tracking
- `agent_memory_store` - Agent context and memory
- `operation_costs` - AI operation pricing by tier
- `user_operations` - Usage tracking and analytics

**Discovery System Tables:**
- `discovery_results` - Discovery session tracking
- `paper_relationships` - Citation and similarity relationships
- `discovery_feedback` - User feedback for algorithm improvement

**Advanced Features:**
- **JSONB Fields** - Flexible metadata storage for papers and analysis results
- **Full-Text Search** - GIN indexes on titles and abstracts
- **Relationship Mapping** - Complex paper relationship tracking
- **Performance Optimization** - 31 specialized indexes for fast queries

### Custom Database Setup Commands

**Dump the entire schema**

```bash
supabase db dump --schema public > schema.sql
```

**Dump specific schema**

```bash
supabase db dump --schema answer42 > answer42_schema.sql
```

**Dump with data**

```bash
supabase db dump --data-only > data.sql
```

**Dump structure only (no data)**

```bash
supabase db dump --schema-only > structure.sql
```

**Dump specific tables**

```bash
supabase db dump --table papers --table users > specific_tables.sql
```

### Database Migration

For existing installations, Spring Boot will automatically handle schema migrations through Hibernate DDL auto-update. For production deployments, consider using the provided schema files for consistent database structure.

### Entity Design Guidelines

- **Database**: snake_case naming convention
- **Java**: camelCase naming convention
- **Entity properties**: Must map to database column names using `@Column`, `@JoinColumn`, `@JoinTable`

**Use Lombok for entities:**

```java
@Data // Lombok annotation for getters, setters, equals, hashCode, toString
@NoArgsConstructor // Lombok for no-args constructor
@AllArgsConstructor // Lombok for all-args constructor

// JPA Default Loading Strategies
@OneToOne    // EAGER by default
@ManyToOne   // EAGER by default
@OneToMany   // LAZY by default
@ManyToMany  // LAZY by default

@UpdateTimestamp // called on insert and update
```

### JSON Fields

For complex attributes, leverage PostgreSQL's JSONB type with Hibernate:

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "table_name", schema = "answer42")
@Data
@NoArgsConstructor
public class YourEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Use JdbcTypeCode annotation for JSON object fields
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    // For array types stored as JSONB, use List<String>
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> topics;
}
```

## Getting Started

### Prerequisites

- **Java 21** or later
- **Maven 3.8** or later  
- **PostgreSQL 14** or later
- **AI API Keys** (OpenAI, Anthropic, Perplexity)

### Quick Start

1. **Clone the repository**
   
   ```bash
   git clone https://github.com/yourusername/answer42.git
   cd answer42
   ```

2. **Configure environment variables**
   Create a `.env` file in the project root:
   
   ```env
   OPENAI_API_KEY=your_openai_api_key
   ANTHROPIC_API_KEY=your_anthropic_api_key
   PERPLEXITY_API_KEY=your_perplexity_api_key
   ```

3. **Set up PostgreSQL**
   
   - Create a database named `postgres`
   - Create schema: `CREATE SCHEMA answer42;`
   - Update connection details in `application.properties`

4. **Build and run**
   
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

5. **Access the application**
   Open your browser to `http://localhost:8080`

### Rapid Deployment with Replit

🚀 **One-Click Cloud Deployment**: Answer42 includes a `.replit` configuration file for instant cloud deployment and development on [Replit](https://replit.com).

**Features:**
- **Zero Setup**: Pre-configured Java GraalVM 21 environment with Maven
- **Instant Launch**: One-click deployment with automatic dependency resolution
- **Cloud IDE**: Full development environment in your browser
- **Live Preview**: Automatic port forwarding on port 8080
- **Production Ready**: Optimized build and deployment pipeline

**Quick Deploy Steps:**
1. Fork the repository on GitHub
2. Import to Replit or click "Run on Replit" 
3. Environment automatically installs Java 21, Maven, and dependencies
4. Application starts with `mvn clean compile spring-boot:run`
5. Access via the provided Replit URL

**Replit Configuration Details:**
```toml
modules = ["java-graalvm21", "web"]
run = "mvn clean compile spring-boot:run"

[deployment]
run = ["sh", "-c", "mvn clean package -DskipTests && java -jar target/*.jar"]

[env]
MAVEN_OPTS = "-Xmx2g -XX:+UseG1GC"
JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
```

**Perfect For:**
- 🎓 **Academic Demos**: Quick setup for research presentations
- 🔬 **Prototype Testing**: Rapid experimentation with AI agents
- 👥 **Collaborative Development**: Shared development environments
- 📚 **Educational Use**: Teaching AI integration patterns
- 🚀 **Proof of Concepts**: Fast deployment for stakeholder reviews

**Environment Variables on Replit:**
- Set your AI API keys in Replit's "Secrets" tab:
  - `OPENAI_API_KEY`
  - `ANTHROPIC_API_KEY` 
  - `PERPLEXITY_API_KEY`
- Database will auto-configure with Replit's built-in PostgreSQL

This makes Answer42 accessible to researchers and developers worldwide without any local setup requirements.

## Development

### Running in Development Mode

```bash
mvn spring-boot:run
```

This enables:

- Vaadin development mode with hot reload
- Detailed SQL logging  
- Development-specific configurations

### Code Quality Tools

The project includes comprehensive code quality checks:

```bash
# Run code style checks
mvn checkstyle:check

# Run static analysis
mvn pmd:check

# Find potential bugs
mvn spotbugs:check

# Run all quality checks
mvn clean verify
```

### Building for Production

```bash
mvn clean package -Pproduction
```

This creates an optimized build with:

- Minified frontend resources
- Production Vaadin compilation
- Optimized JAR/WAR packaging

### 🎨 UI Development & Contributing

**📋 Remaining UI Implementation:**

Answer42 has comprehensive design mockups for UI components that still need implementation. Before contributing to the frontend, please review the design specifications in the `docs/design/` directory:

**Available Design Mockups:**
- `dashboard.png` - Main dashboard layout and widgets
- `projects.png` - Research project management interface
- `profile.png` - User profile and account settings
- `credits.png` - Credit balance and subscription management
- `subscriptions.png` - Subscription plans and billing interface
- `settings.png` - Application settings and preferences
- `studyGuides.png` - Study guide generation and management
- `uploadPaper.png` & `bulkUploadPaper.png` - Paper upload interfaces
- `viewPaper.png` - Individual paper viewing and analysis
- `papers.png` - Paper library and organization
- `aiChat*.png` - AI chat interface variations and workflows

**Design Documentation:**
- `docs/design/README.md` - Complete design system documentation
- `answer42_pitch_deck.md` - Product vision and feature overview

**Contributing Guidelines:**
1. Check `docs/design/` for existing mockups before starting UI work
2. Follow the established Vaadin component patterns in existing views
3. Ensure responsive design compatibility across devices
4. Implement dark mode support using the existing theme system
5. Add appropriate accessibility features (ARIA labels, keyboard navigation)

**UI Architecture:**
- **Views**: Located in `src/main/java/com/samjdtechnologies/answer42/ui/views/`
- **Helpers**: UI helper classes in `src/main/java/com/samjdtechnologies/answer42/ui/helpers/`
- **Themes**: CSS themes in `src/main/frontend/styles/themes/answer42/`
- **Components**: Reusable components following Vaadin best practices

Contributors are encouraged to implement these designed interfaces to complete the platform's user experience.

### 🔧 Technical Debt & Refactoring Opportunities

**🤖 AI Chat System Modernization:**

The current AI chat implementation needs to be refactored to leverage the sophisticated agent system:

**Current Architecture Issues:**
- Chat helper classes should be refactored to proper chat controllers
- Direct AI provider calls should be replaced with enterprise-grade agent implementations
- Missing integration with the retry policy, rate limiting, and circuit breaker systems

**Recommended Refactoring:**
1. **Migrate to Agent-Based Architecture**: Replace direct AI provider calls with the comprehensive agent implementations in `src/main/java/com/samjdtechnologies/answer42/service/agent/`

2. **Available Agent Classes for Integration**:
   - `AbstractConfigurableAgent.java` - Base class with retry policies, rate limiting, and circuit breakers
   - `AnthropicBasedAgent.java` - Optimized for Anthropic Claude interactions
   - `OpenAIBasedAgent.java` - Optimized for OpenAI GPT-4 interactions  
   - `PerplexityBasedAgent.java` - Optimized for Perplexity research queries
   - `ContentSummarizerAgent.java` - Multi-level summarization capabilities
   - `ConceptExplainerAgent.java` - Technical term explanation with education levels
   - `QualityCheckerAgent.java` - Comprehensive quality assessment and bias detection

3. **Benefits of Migration**:
   - ✅ Enterprise-grade error handling and retry mechanisms
   - ✅ Built-in rate limiting and circuit breaker protection
   - ✅ Comprehensive metrics and monitoring
   - ✅ User-aware API key management through AIConfig
   - ✅ Parallel processing capabilities via ThreadConfig
   - ✅ Consistent logging and debugging support

4. **Implementation Priority**:
   - **High**: Paper Chat (Anthropic) → Use `AnthropicBasedAgent` + `ContentSummarizerAgent`
   - **High**: Cross-Reference Chat (OpenAI) → Use `OpenAIBasedAgent` + `QualityCheckerAgent`  
   - **Medium**: Research Explorer (Perplexity) → Use `PerplexityBasedAgent` + `PerplexityResearchAgent`

**Chat Controller Architecture:**
- Move from helper classes to proper Spring MVC controllers
- Implement RESTful endpoints for chat interactions
- Add WebSocket support for real-time streaming responses
- Integrate with the existing agent task management system

This refactoring will significantly improve system reliability, performance monitoring, and maintenance while providing a better user experience through the advanced agent capabilities.

## Configuration

### Key Configuration Files

**application.properties**

- Database connection settings
- AI provider configurations
- Security and JWT settings
- Vaadin development options

**AI Provider Settings**

```properties
# Anthropic Configuration
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.options.model=claude-3-7-sonnet-latest
spring.ai.anthropic.chat.options.temperature=0.7

# OpenAI Configuration  
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.chat.options.temperature=0.7

# Perplexity Configuration
spring.ai.perplexity.api-key=${PERPLEXITY_API_KEY}
spring.ai.perplexity.chat.options.model=llama-3.1-sonar-small-128k-online
```

**Discovery API Configuration**

```properties
# Discovery System Configuration
discovery.enabled=true
discovery.semantic-scholar.base-url=https://api.semanticscholar.org/graph/v1
discovery.crossref.base-url=https://api.crossref.org/works
discovery.max-papers-per-source=50
discovery.cache.duration-hours=24
discovery.rate-limit.enabled=true
discovery.rate-limit.crossref.requests-per-second=45
discovery.rate-limit.semantic-scholar.requests-per-minute=100
discovery.rate-limit.perplexity.requests-per-minute=10
```

**Database Configuration**

```properties
spring.datasource.url=jdbc:postgresql://localhost:54322/postgres
spring.jpa.properties.hibernate.default_schema=answer42
spring.jpa.hibernate.ddl-auto=update
```

## License

Answer42 is licensed under the **Apache License 2.0** - a permissive open source license that allows both personal and commercial use.

This license allows you to:
- ✅ **Use** the software for any purpose (personal, academic, commercial)
- ✅ **Modify** the source code to fit your needs
- ✅ **Distribute** copies of the original or modified software
- ✅ **Patent Grant** protection from patent litigation
- ✅ **Sublicense** and create derivative works

**Requirements:**
- 📄 Include the original copyright notice and license text
- 📝 State any significant changes made to the code
- 🔗 Provide attribution to the original project

For complete license terms, see the [LICENSE](./LICENSE) file in the project root.

**Why Apache License 2.0?**

We chose Apache License 2.0 because it:
- **Encourages Collaboration**: Permissive terms foster community contributions and innovation
- **Enterprise Friendly**: Companies can integrate and build upon Answer42 without restrictive copyleft requirements
- **Patent Protection**: Explicit patent grant protects users from patent litigation
- **Academic Compatible**: Universities and researchers can freely use and modify the platform
- **Industry Standard**: Widely adopted by major open source projects (Apache Software Foundation, Google, Meta)
- **Clear Terms**: Well-understood legal framework reduces licensing uncertainty

**Data Privacy & AI Models:**
- 🔒 Your research content remains private and secure
- 🚫 We never use your data to train AI models
- 🎯 AI processing is performed through external APIs (OpenAI, Anthropic, Perplexity) per their terms
- 📊 Only anonymous usage statistics are collected to improve the platform

**Commercial Use:**
This software is free for commercial use under Apache License 2.0. If you build a business using Answer42, please consider:
- Contributing improvements back to the community
- Sponsoring the project development
- Providing attribution in your product documentation

For questions about licensing, contributions, or commercial partnerships, please open an issue or contact the maintainers.

---

**Answer42** - Making academic research more intelligent, one paper at a time. 🚀📚
