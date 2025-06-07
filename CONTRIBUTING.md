# Contributing to Answer42

Welcome to Answer42! We're thrilled that you're interested in contributing to our AI-powered academic research platform. This document provides guidelines and information for contributors.

## 🚀 Quick Start

1. **Fork the repository** on GitHub
2. **Clone your fork** locally: `git clone https://github.com/samjd-zz/answer42.git`
3. **Set up your environment** (see [Development Setup](#development-setup))
4. **Create a feature branch**: `git checkout -b feature/your-feature-name`
5. **Make your changes** following our [coding standards](#coding-standards)
6. **Test your changes** thoroughly
7. **Submit a pull request**

## 📋 Table of Contents

- [Development Setup](#development-setup)
- [Project Architecture](#project-architecture)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)
- [Documentation Standards](#documentation-standards)
- [Community Guidelines](#community-guidelines)

## 🛠 Development Setup

### Prerequisites

- **Java 17+** (OpenJDK recommended)
- **Maven 3.8+**
- **Node.js 18+** (for frontend build tools)
- **PostgreSQL 14+** (for database)
- **Git**

### Environment Configuration

1. **Copy environment template**:
   ```bash
   cp .example.env .env
   ```

2. **Configure API keys** in `.env`:
   - OpenAI API Key (required for GPT-based agents)
   - Anthropic API Key (required for Claude-based agents)
   - Perplexity API Key (required for research agents)
   - Stripe keys (optional, for payment features)

3. **Database setup**:
   ```bash
   # Create PostgreSQL database
   createdb answer42
   
   # Import schema (optional - Spring Boot will auto-create)
   psql answer42 < answer42.schema.sql
   ```

4. **Install dependencies**:
   ```bash
   mvn clean install
   npm install  # For frontend build tools
   ```

5. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

The application will be available at `http://localhost:8080`

### IDE Setup

**IntelliJ IDEA** (Recommended):
- Import as Maven project
- Enable annotation processing
- Install plugins: Spring Boot, Vaadin, Checkstyle

**VS Code**:
- Install extensions: Java Extension Pack, Spring Boot Tools, Vaadin

## 🏗 Project Architecture

Answer42 is built with a sophisticated multi-agent architecture:

### Core Technologies
- **Backend**: Spring Boot 3.x, Spring AI, Spring Batch
- **Frontend**: Vaadin 24 (Java-based UI framework)
- **Database**: PostgreSQL with JPA/Hibernate
- **AI Integration**: Multi-provider support (OpenAI, Anthropic, Perplexity)

### Key Components

#### 1. Multi-Agent Pipeline (`src/main/java/com/samjdtechnologies/answer42/service/agent/`)
Our sophisticated AI agent system includes:

- **AbstractConfigurableAgent**: Base class for all agents
- **ContentSummarizerAgent**: Generates paper summaries
- **ConceptExplainerAgent**: Explains complex concepts
- **CitationFormatterAgent**: Formats academic citations
- **QualityCheckerAgent**: Validates content quality
- **MetadataEnhancementAgent**: Enriches paper metadata
- **PaperProcessorAgent**: Core paper processing
- **PerplexityResearchAgent**: Real-time research integration
- **RelatedPaperDiscoveryAgent**: Finds related research

#### 2. Pipeline Infrastructure (`src/main/java/com/samjdtechnologies/answer42/service/pipeline/`)
- **AgentCircuitBreaker**: Fault tolerance and resilience
- **AgentRetryPolicy**: Intelligent retry mechanisms
- **APIRateLimiter**: Rate limiting across providers
- **ProviderRateLimiter**: Provider-specific rate management

#### 3. UI Components (`src/main/java/com/samjdtechnologies/answer42/ui/`)
- Vaadin-based responsive web interface
- View helpers for complex UI logic
- Chat interface for AI interactions

#### 4. Business Logic (`src/main/java/com/samjdtechnologies/answer42/service/`)
- **PaperService**: Core paper management
- **ChatService**: AI chat functionality
- **UserService**: User management and authentication
- **FileTransferService**: File upload/download handling

### Architecture Principles

1. **Separation of Concerns**: Clear boundaries between UI, business logic, and data layers
2. **Agent-Based Design**: Modular AI agents with specific responsibilities
3. **Fault Tolerance**: Circuit breakers, retries, and graceful degradation
4. **Rate Limiting**: Intelligent API usage management
5. **Testability**: Comprehensive unit and integration tests

## 📝 Coding Standards

### Java Code Style

We follow **Google Java Style Guide** with these additions:

#### File Organization
- **Maximum 300 lines per file** (create utility classes if needed)
- Group related methods together
- Use meaningful package names

#### Naming Conventions
```java
// Classes: PascalCase
public class ContentSummarizerAgent

// Methods and variables: camelCase  
public void processDocument()
private String apiKey

// Constants: UPPER_SNAKE_CASE
private static final int MAX_RETRY_ATTEMPTS = 3

// Packages: lowercase.with.dots
com.samjdtechnologies.answer42.service.agent
```

#### Code Quality Rules

1. **No placeholder code** - All implementations must be complete
2. **No mock objects in production** - Use real implementations
3. **No backward compatibility hacks** - Write clean, modern code
4. **Comprehensive error handling** - Never ignore exceptions
5. **Use constants** - Avoid magic numbers and strings

#### Documentation Requirements
```java
/**
 * Processes academic papers using AI agents for content analysis.
 * 
 * @param paper The paper to process
 * @param config Processing configuration
 * @return Processing results with analysis data
 * @throws ProcessingException if paper processing fails
 */
public ProcessingResult processDocument(Paper paper, ProcessingConfig config) {
    // Implementation
}
```

### Spring Boot Best Practices

#### Service Layer
```java
@Service
@Transactional
public class PaperService {
    
    @Autowired
    private PaperRepository paperRepository;
    
    public Paper savePaper(Paper paper) {
        validatePaper(paper);
        return paperRepository.save(paper);
    }
}
```

#### Repository Layer
```java
@Repository
public interface PaperRepository extends JpaRepository<Paper, Long> {
    
    @Query("SELECT p FROM Paper p WHERE p.status = :status")
    List<Paper> findByStatus(@Param("status") ProcessingStatus status);
}
```

#### Configuration Classes
```java
@Configuration
@ConfigurationProperties(prefix = "answer42.ai")
public class AIConfig {
    private String openaiApiKey;
    private String anthropicApiKey;
    // Getters and setters
}
```

### Vaadin UI Guidelines

#### View Structure
```java
@Route("papers")
@PageTitle("Papers | Answer42")
public class PapersView extends VerticalLayout {
    
    private final PaperService paperService;
    private final Grid<Paper> grid;
    
    public PapersView(PaperService paperService) {
        this.paperService = paperService;
        initializeComponents();
        configureLayout();
    }
}
```

## ✅ Testing Guidelines

### Test Coverage Requirements
- **Minimum 80% line coverage**
- **100% coverage for critical paths** (AI agents, payment processing)
- **Integration tests for API endpoints**
- **UI tests for core user flows**

### Testing Structure

#### Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class ContentSummarizerAgentTest {
    
    @Mock
    private OpenAIClient openAIClient;
    
    @InjectMocks
    private ContentSummarizerAgent agent;
    
    @Test
    void shouldGenerateSummaryForValidPaper() {
        // Given
        Paper paper = createTestPaper();
        
        // When
        SummaryResult result = agent.summarize(paper);
        
        // Then
        assertThat(result.getSummary()).isNotEmpty();
        assertThat(result.getKeyPoints()).hasSize(3);
    }
}
```

#### Integration Tests
```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class PaperServiceIntegrationTest {
    
    @Autowired
    private PaperService paperService;
    
    @Test
    @Transactional
    void shouldProcessPaperEndToEnd() {
        // Integration test implementation
    }
}
```

### Test Data Management
- Use `@TestPropertySource` for test configurations
- Create test data builders for complex objects
- Use `@Transactional` for database tests
- Mock external API calls in unit tests

## 🔄 Pull Request Process

### Before Submitting

1. **Ensure all tests pass**: `mvn test`
2. **Run code quality checks**: `mvn checkstyle:check spotbugs:check`
3. **Update documentation** if needed
4. **Add tests** for new functionality
5. **Follow commit message conventions**

### Commit Message Format
```
type(scope): description

- feat: new feature
- fix: bug fix  
- docs: documentation changes
- style: formatting changes
- refactor: code refactoring
- test: adding tests
- chore: maintenance tasks

Examples:
feat(agent): add quality checker agent for content validation
fix(ui): resolve pagination issue in papers view
docs(api): update agent integration documentation
```

### Pull Request Template

When creating a PR, please include:

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature  
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] No breaking changes
```

### Review Process

1. **Automated checks** must pass (CI/CD pipeline)
2. **Two approvals** required from maintainers
3. **All conversations resolved**
4. **No merge conflicts**

## 🐛 Issue Reporting

### Bug Reports

Use the bug report template:

```markdown
**Bug Description**
Clear description of the bug

**Steps to Reproduce**
1. Step one
2. Step two
3. Step three

**Expected Behavior**
What should happen

**Actual Behavior**  
What actually happens

**Environment**
- OS: [e.g., macOS 12.0]
- Java Version: [e.g., OpenJDK 17]
- Browser: [e.g., Chrome 96]

**Additional Context**
Screenshots, logs, etc.
```

### Feature Requests

Include:
- **Problem statement**: What problem does this solve?
- **Proposed solution**: How should it work?
- **Alternatives considered**: Other approaches evaluated
- **Additional context**: Why is this important?

## 📚 Documentation Standards

### Code Documentation
- **JavaDoc for all public methods and classes**
- **Inline comments for complex logic**
- **README updates for new features**
- **Architecture decision records (ADRs) for major changes**

### API Documentation
- Use Spring Boot's built-in documentation features
- Document all REST endpoints
- Include request/response examples
- Maintain Postman collections

### User Documentation
- Update user guides for UI changes
- Create tutorials for new features
- Maintain FAQ for common issues

## 🤝 Community Guidelines

### Code of Conduct

We follow the [Contributor Covenant](https://www.contributor-covenant.org/):

- **Be respectful** and inclusive
- **Be collaborative** and constructive
- **Be patient** with new contributors
- **Be professional** in all interactions

### Communication Channels

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: General questions and ideas
- **Pull Request Reviews**: Code-specific discussions

### Getting Help

1. **Check existing documentation** first
2. **Search closed issues** for similar problems
3. **Create a new issue** with detailed information
4. **Join community discussions** for broader questions

## 🎯 Contribution Areas

We welcome contributions in these areas:

### High Priority
- **AI Agent improvements**: Enhanced reasoning, better error handling
- **Performance optimization**: Database queries, API efficiency
- **Test coverage**: Expanding automated test suite
- **Documentation**: User guides, API documentation

### Medium Priority
- **UI/UX enhancements**: Better user experience
- **New integrations**: Additional AI providers, academic databases
- **Accessibility**: WCAG compliance improvements
- **Mobile responsiveness**: Better mobile experience

### Good First Issues
Look for issues tagged with `good-first-issue` or `help-wanted`

## 📊 Development Metrics

### Quality Gates
- **Test Coverage**: ≥80%
- **Code Quality**: SonarQube rating A
- **Performance**: <2s page load times
- **Security**: Zero critical vulnerabilities

### Definition of Done
- [ ] Feature implemented completely
- [ ] Tests written and passing
- [ ] Code reviewed and approved
- [ ] Documentation updated
- [ ] No security vulnerabilities
- [ ] Performance benchmarks met

## 🔧 Troubleshooting

### Common Setup Issues

**Java Version Problems**:
```bash
# Check Java version
java -version

# Set JAVA_HOME if needed
export JAVA_HOME=/path/to/java17
```

**Database Connection Issues**:
```bash
# Check PostgreSQL status
pg_ctl status

# Verify connection
psql -h localhost -p 5432 -U username -d answer42
```

**API Key Configuration**:
- Ensure `.env` file is in project root
- Verify API keys are valid and active
- Check rate limits on provider dashboards

### Build Issues

**Maven Dependency Problems**:
```bash
# Clean and rebuild
mvn clean install -U

# Skip tests if needed
mvn clean install -DskipTests
```

**Frontend Build Issues**:
```bash
# Clear node modules
rm -rf node_modules package-lock.json
npm install
```

## 🏆 Recognition

Contributors are recognized through:

- **GitHub contributor statistics**
- **Release notes mentions**
- **Community showcase**
- **Maintainer invitation** for outstanding contributors

## 💝 Support the Project

Your donations help us:
- 🔧 **Development Resources** - Support ongoing development and maintenance

### Bitcoin Donations

We accept Bitcoin donations to support the project:

**Bitcoin Address:** `bc1q8hgvafe6qg6z0g06y65xqn4vzf7crhnvtt505f`

<img src="btc-receive.png" alt="Bitcoin Donation QR Code" width="200" height="200">

*Scan the QR code above or copy the Bitcoin address to make a donation*

### Other Ways to Support

- ⭐ **Star the repository** on GitHub
- 🐛 **Report bugs** and suggest improvements
- 📖 **Improve documentation** and write tutorials
- 💬 **Share Answer42** with your academic network
- 🤝 **Contribute code** and new features

Every contribution, whether code or financial, helps make Answer42 better for the global research community!

## 📞 Contact

- **Project Lead**: Shawn (samjd-zz)
- **Email**: shawn@samjdtechnologies.com
- **GitHub Issues**: For bug reports and feature requests
- **Repository**: https://github.com/samjd-zz/answer42

---

Thank you for contributing to Answer42! Together, we're building the future of AI-powered academic research. 🚀

**Happy coding!** 🎉
