# Answer42 - AI-Powered Academic Research Platform

![Answer42 Logo](src/main/resources/META-INF/resources/frontend/images/answer42-logo.svg)

Answer42 is a sophisticated AI-powered platform for academic researchers, students, and scholars to upload, analyze, and interact with research papers using multiple AI providers. The platform combines advanced paper processing with intelligent chat capabilities to make academic research more efficient and insightful.

## Table of Contents

- [What is Answer42?](#what-is-answer42)
- [Technology Stack](#technology-stack)
- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Core Features](#core-features)
- [AI Integration](#ai-integration)
- [Database Design](#database-design)
- [Getting Started](#getting-started)
- [Development](#development)
- [Configuration](#configuration)

## What is Answer42?

Answer42 is a comprehensive research assistant that helps academics work with scientific papers through:

- **Intelligent Paper Processing**: Upload PDFs and extract full text, metadata, and structured information
- **Multi-Modal AI Chat**: Three specialized chat modes using different AI providers for various research needs
- **Comprehensive Analysis**: Generate summaries, extract key findings, identify methodologies, and create glossaries
- **External Metadata Integration**: Automatic enhancement using Crossref and Semantic Scholar APIs
- **Project Organization**: Group papers into research projects for better organization
- **Credit-Based System**: Subscription model with credit management for AI operations

## Technology Stack

### Backend

- **Java 21** - Modern Java with latest features
- **Spring Boot 3.4.5** - Enterprise application framework
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

### Development Tools

- **Maven** - Build automation and dependency management
- **Checkstyle, PMD, SpotBugs** - Code quality and static analysis
- **Spring Boot DevTools** - Development hot-reload

## Architecture Overview

Answer42 follows a clean layered architecture:

```
┌─────────────────────┐
│   Vaadin Views      │  User Interface Layer
├─────────────────────┤
│   Controllers       │  REST API Layer  
├─────────────────────┤
│   Services          │  Business Logic Layer
├─────────────────────┤
│   Repositories      │  Data Access Layer
├─────────────────────┤
│   PostgreSQL        │  Database Layer
└─────────────────────┘
```

### Key Design Patterns

- **MVC Architecture** - Clear separation of concerns
- **Repository Pattern** - Data access abstraction
- **Service Layer** - Business logic encapsulation
- **Dependency Injection** - Loose coupling via Spring IoC
- **Component-Based UI** - Reusable Vaadin components

## Project Structure

```
src/
├── main/
│   ├── bundles/                              # Development bundles
│   │   ├── dev.bundle                        # Development configuration bundle
│   │   └── README.md                         # Bundle documentation
│   ├── frontend/                             # Frontend resources
│   │   ├── generated/                        # Generated Vaadin files
│   │   │   ├── flow/                         # Vaadin Flow components
│   │   │   │   ├── Flow.tsx                  # React Flow adapter
│   │   │   │   ├── ReactAdapter.tsx          # React integration
│   │   │   │   └── web-components/           # Web component definitions
│   │   │   ├── jar-resources/                # JAR-packaged resources
│   │   │   │   ├── images/                   # Application images
│   │   │   │   │   ├── answer42-logo.svg     # Main logo
│   │   │   │   │   └── icons/                # UI icons for all providers
│   │   │   │   │       ├── ai_chatbot_avatar_*.{png,svg}  # AI avatar icons
│   │   │   │   │       ├── openai_icon_*.{png,svg}        # OpenAI icons
│   │   │   │   │       ├── perplexity_icon_*.{png,svg}    # Perplexity icons
│   │   │   │   │       ├── paper_chat_icon_*.{png,svg}    # Paper chat icons
│   │   │   │   │       └── user_avatar_icon_*.{png,svg}   # User avatar icons
│   │   │   │   ├── copilot/                  # Vaadin Copilot tools
│   │   │   │   ├── vaadin-dev-tools/         # Development tools
│   │   │   │   └── *Connector.js             # Frontend connectors
│   │   │   ├── index.tsx                     # Main entry point
│   │   │   ├── routes.tsx                    # Application routing
│   │   │   └── vaadin.ts                     # Vaadin configuration
│   │   ├── styles/themes/answer42/           # Custom theme
│   │   │   ├── main.css                      # Core styles
│   │   │   ├── styles.css                    # Global variables
│   │   │   ├── answer42-dark.css             # Dark mode theme
│   │   │   ├── theme.json                    # Theme configuration
│   │   │   └── components/                   # Component-specific styles
│   │   │       ├── ai-chat.css               # Chat interface styles
│   │   │       ├── auth-forms.css            # Authentication forms
│   │   │       ├── bulk-upload.css           # Bulk upload interface
│   │   │       ├── credits.css               # Credit management
│   │   │       ├── dashboard.css             # Dashboard styles
│   │   │       ├── main-layout.css           # Main layout
│   │   │       ├── papers.css                # Paper management
│   │   │       ├── profile.css               # User profile
│   │   │       ├── projects.css              # Project management
│   │   │       ├── settings.css              # Settings interface
│   │   │       ├── subscription.css          # Subscription management
│   │   │       └── upload-paper.css          # Paper upload
│   │   └── index.html                        # HTML entry point
│   ├── java/com/samjdtechnologies/answer42/
│   │   ├── Answer42Application.java          # Spring Boot main class
│   │   ├── ServletInitializer.java           # WAR deployment initializer
│   │   ├── config/                           # Configuration classes
│   │   │   ├── AIConfig.java                 # AI provider configurations
│   │   │   ├── AppConfig.java                # General application config
│   │   │   ├── DatabaseConfig.java           # Database settings
│   │   │   ├── EnvironmentConfig.java        # Environment variables
│   │   │   ├── ErrorConfig.java              # Error handling
│   │   │   ├── JwtConfig.java                # JWT configuration
│   │   │   ├── LoggingConfig.java            # Logging setup
│   │   │   ├── SecurityConfig.java           # Security configuration
│   │   │   ├── ThreadConfig.java             # Thread pool configuration
│   │   │   ├── TransactionConfig.java        # Transaction management
│   │   │   ├── VaadinDevModeConfig.java      # Vaadin development
│   │   │   ├── VaadinSessionConfig.java      # Vaadin session config
│   │   │   └── VaadinThreadManagerConfig.java # Vaadin threading
│   │   ├── controller/                       # REST endpoints
│   │   │   ├── AuthController.java           # Authentication API
│   │   │   ├── HeartbeatController.java      # Health checks
│   │   │   ├── SystemController.java         # System management
│   │   │   └── TestController.java           # Testing endpoints
│   │   ├── model/                            # Data models
│   │   │   ├── FileEntry.java                # File system entries
│   │   │   ├── daos/                         # JPA entities
│   │   │   │   ├── AnalysisResult.java       # AI analysis results
│   │   │   │   ├── AnalysisTask.java         # Background analysis tasks
│   │   │   │   ├── ChatMessage.java          # Chat conversation messages
│   │   │   │   ├── ChatSession.java          # AI chat sessions
│   │   │   │   ├── CreditBalance.java        # User credit balances
│   │   │   │   ├── CreditTransaction.java    # Credit transactions
│   │   │   │   ├── Paper.java                # Research papers
│   │   │   │   ├── Project.java              # Research projects
│   │   │   │   ├── Subscription.java         # User subscriptions
│   │   │   │   ├── SubscriptionPlan.java     # Subscription plans
│   │   │   │   ├── User.java                 # User accounts
│   │   │   │   └── UserPreferences.java      # User preferences
│   │   │   └── enums/                        # Enumeration types
│   │   │       ├── AIProvider.java           # AI service providers
│   │   │       ├── AnalysisType.java         # Types of analysis
│   │   │       ├── ChatMode.java             # Chat interaction modes
│   │   │       └── FileStatus.java           # File processing status
│   │   ├── processors/                       # Background processing
│   │   │   ├── AIChatMessageProcessor.java   # Chat message handling
│   │   │   ├── AnthropicPaperAnalysisProcessor.java # Anthropic analysis
│   │   │   └── PaperBulkUploadProcessor.java # Bulk paper processing
│   │   ├── repository/                       # Data access interfaces
│   │   │   ├── AnalysisResultRepository.java # Analysis results data
│   │   │   ├── AnalysisTaskRepository.java   # Analysis tasks data
│   │   │   ├── ChatMessageRepository.java    # Chat messages data
│   │   │   ├── ChatSessionRepository.java    # Chat sessions data
│   │   │   ├── CreditBalanceRepository.java  # Credit balances data
│   │   │   ├── CreditTransactionRepository.java # Credit transactions data
│   │   │   ├── PaperRepository.java          # Papers data access
│   │   │   ├── ProjectRepository.java        # Projects data access
│   │   │   ├── SubscriptionPlanRepository.java # Subscription plans data
│   │   │   ├── SubscriptionRepository.java   # Subscriptions data
│   │   │   ├── UserPreferencesRepository.java # User preferences data
│   │   │   └── UserRepository.java           # Users data access
│   │   ├── security/                         # Authentication & authorization
│   │   │   ├── CustomUserDetailsService.java # User authentication
│   │   │   ├── JwtAuthenticationFilter.java  # JWT filter
│   │   │   └── JwtTokenUtil.java             # JWT token management
│   │   ├── service/                          # Business logic
│   │   │   ├── ChatService.java              # AI chat orchestration
│   │   │   ├── CreditService.java            # Credit management
│   │   │   ├── PaperAnalysisService.java     # AI-powered analysis
│   │   │   ├── PaperService.java             # Paper management
│   │   │   ├── ProjectService.java           # Project management
│   │   │   ├── SubscriptionService.java      # Subscription management
│   │   │   ├── UserPreferencesService.java   # User preferences
│   │   │   ├── UserService.java              # User management
│   │   │   └── helper/                       # Service helpers
│   │   │       ├── AIInteractionHelper.java  # AI interaction utilities
│   │   │       ├── ChatMessageHelper.java    # Chat message utilities
│   │   │       └── ChatSessionHelper.java    # Chat session utilities
│   │   ├── transaction/                      # Transaction management
│   │   │   └── TransactionMonitor.java       # Transaction monitoring
│   │   ├── ui/                               # Vaadin user interface
│   │   │   ├── AppShell.java                 # Application shell
│   │   │   ├── constants/                    # UI constants
│   │   │   │   └── UIConstants.java          # UI constant definitions
│   │   │   ├── layout/                       # Layout components
│   │   │   │   └── MainLayout.java           # Main application layout
│   │   │   ├── service/                      # UI services
│   │   │   │   └── AuthenticationService.java # UI authentication
│   │   │   ├── theme/                        # Theme components
│   │   │   │   └── Answer42Theme.java        # Custom theme
│   │   │   └── views/                        # Application views
│   │   │       ├── AIChatView.java           # AI chat interface
│   │   │       ├── BulkUploadView.java       # Bulk paper upload
│   │   │       ├── CreditsView.java          # Credit management
│   │   │       ├── DashboardView.java        # Main dashboard
│   │   │       ├── LoginView.java            # User login
│   │   │       ├── PapersView.java           # Paper management
│   │   │       ├── ProfileView.java          # User profile
│   │   │       ├── ProjectsView.java         # Project management
│   │   │       ├── RegisterView.java         # User registration
│   │   │       ├── SettingsView.java         # Application settings
│   │   │       ├── SubscriptionView.java     # Subscription management
│   │   │       ├── UploadPaperView.java      # Single paper upload
│   │   │       ├── components/               # Reusable UI components
│   │   │       │   ├── AIChatContainer.java  # Chat interface container
│   │   │       │   ├── AIChatGeneralMesssageBubble.java # General message bubble
│   │   │       │   ├── AIChatModeTabs.java   # Chat mode selection
│   │   │       │   ├── AIChatProgressMessageBubble.java # Progress messages
│   │   │       │   ├── AIChatThinkingMessageBubble.java # Thinking indicator
│   │   │       │   ├── AIChatWelcomeSection.java # Chat welcome screen
│   │   │       │   ├── AnthropicPoweredAnalysisSection.java # Anthropic section
│   │   │       │   ├── AuthorContact.java    # Author contact component
│   │   │       │   ├── PaperPill.java        # Paper display chip
│   │   │       │   └── PaperSelectionDialog.java # Paper picker dialog
│   │   │       └── helpers/                  # View helpers
│   │   │           ├── AIChatViewHelper.java # AI chat view utilities
│   │   │           ├── PapersViewHelper.java # Papers view utilities
│   │   │           ├── ProjectsViewHelper.java # Projects view utilities
│   │   │           └── SubscriptionViewHelper.java # Subscription utilities
│   │   └── util/                             # Utility classes
│   │       ├── HibernateUtil.java            # Hibernate utilities
│   │       └── LoggingUtil.java              # Logging utilities
│   └── resources/
│       ├── application.properties            # Main configuration
│       ├── META-INF/resources/               # Web assets
│       │   ├── favicon.ico                   # Application favicon
│       │   ├── favicon.svg                   # SVG favicon
│       │   ├── manifest.webmanifest          # PWA manifest
│       │   ├── frontend/                     # Frontend resources
│       │   │   ├── images/                   # Static images
│       │   │   │   ├── answer42-logo.svg     # Application logo
│       │   │   │   ├── bitcoin-qr-mock.svg   # Bitcoin QR code mock
│       │   │   │   └── icons/                # Application icons
│       │   │   ├── jwt-injector.js           # JWT injection script
│       │   │   ├── sw.js                     # Service worker
│       │   │   ├── sw-loader.js              # Service worker loader
│       │   │   ├── sw-register.js            # Service worker registration
│       │   │   └── styles/                   # Additional styles
│       │   └── images/                       # Resource images
│       │       └── answer42-logo.svg         # Logo resource
│       ├── static/                           # Static web resources
│       │   ├── favicon.ico                   # Static favicon
│       │   ├── favicon.svg                   # Static SVG favicon
│       │   ├── manifest.webmanifest          # Static PWA manifest
│       │   ├── offline.html                  # Offline page
│       │   ├── offline-stub.html             # Offline stub
│       │   └── images/                       # Static images
│       │       ├── answer42-logo.svg         # Static logo
│       │       └── logo.svg                  # Generic logo
│       └── templates/                        # Template files
└── test/                                     # Unit and integration tests
    └── java/com/samjdtechnologies/answer42/
        └── Answer42ApplicationTests.java     # Main application tests
```

## Core Features

### 📄 Paper Management

- **PDF Upload & Processing**: Extract text content and metadata from academic papers
- **Metadata Enhancement**: Automatic enrichment via Crossref and Semantic Scholar APIs
- **Comprehensive Storage**: Store papers with full text, abstracts, authors, citations, and analysis results
- **Organization**: Group papers into research projects for better management
- **Bulk Upload**: Process multiple papers simultaneously

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
```

## Database Schema Management

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

**Database Configuration**

```properties
spring.datasource.url=jdbc:postgresql://localhost:54322/postgres
spring.jpa.properties.hibernate.default_schema=answer42
spring.jpa.hibernate.ddl-auto=update
```

---

**Answer42** - Making academic research more intelligent, one paper at a time. 🚀📚
