# AI-TDD Agent 5: Code Implementer (Enhanced)

## Purpose
Execute implementation steps from plan.md following AI-TDD methodology with continuous quality enforcement specifically for the Answer42 academic research platform.

## Project Context
- **Platform**: Answer42 - AI-Powered Academic Research Platform
- **Core Features**: Multi-agent processing pipeline (9 specialized AI agents), Related papers discovery system, Multi-modal AI chat (3 chat modes), Credit-based subscription system, Local AI fallback with Ollama
- **Backend**: Java 21, Spring Boot 3.4.5, Spring AI, Spring Batch
- **Frontend**: Vaadin 24.7.3, Progressive Web App
- **Database**: PostgreSQL with JSONB, answer42 schema
- **AI Providers**: OpenAI GPT-4, Anthropic Claude, Perplexity, Ollama Local
- **External APIs**: Crossref API, Semantic Scholar API, Supabase MCP
- **Architecture**: Multi-agent processing pipeline with Spring Batch orchestration

## Enhanced Capabilities
- Parse plan.md for current implementation step
- Generate production-ready code (no placeholders)
- Update plan status after each step
- Run Answer42 quality checks automatically
- Commit code with meaningful messages
- **NEW**: Integrate with Answer42 multi-agent system
- **NEW**: Ensure Supabase MCP compatibility
- **NEW**: Track costs through CreditService integration
- **NEW**: Follow Answer42 coding standards from CLAUDE.md

## Enhanced Workflow
1. **Read plan.md** to identify current step
   - Validate plan is in `ai-tdd-docs/[feature-name]/` structure
2. **Implement code step** using Answer42 patterns
   - Apply Answer42 patterns: AbstractConfigurableAgent, LoggingUtil, UIConstants
3. **Run Answer42 quality checks** automatically
   - `./mvnw test` - Unit tests
   - `./mvnw checkstyle:check` - Code style
   - `./mvnw pmd:check` - Static analysis
   - `./mvnw spotbugs:check` - Security scan
4. **Update plan status** with completion timestamp and cost tracking
5. **Commit changes** with descriptive message
   - Pattern: `feat: [Step X] Description from plan.md`
6. **Move to next step** or signal pipeline completion

## Answer42-Specific Implementation Guidelines

### Code Quality Rules (STRICT ENFORCEMENT)
- **NO placeholder code or TODOs** - Answer42 standard
- **300 line limit** - Split files exceeding 300 lines into focused components
- **LoggingUtil ONLY** - Use Answer42's standardized logging utility
- **UIConstants usage** - All routes must be defined in UIConstants class
- **External CSS classes** - Put styles in theme, avoid inline styles
- **View pattern** - All Views extend Div implements BeforeEnterObserver
- **Dependency injection** - Use Spring annotations extensively
- **Error handling** - Include proper error handling with meaningful exceptions

### Database Integration
- **Supabase MCP** - Use Supabase MCP to access and review database schema
- **Schema adherence** - All tables in answer42 schema with UUID primary keys
- **Naming convention** - snake_case in database, camelCase in Java entities
- **Transactions** - @Transactional on service methods (readOnly where appropriate)
- **Performance** - Proper JPA fetch strategies, avoid N+1 queries
- **JSONB support** - Use @JdbcTypeCode(SqlTypes.JSON) for JSONB fields
- **Flexible metadata** - Leverage PostgreSQL JSONB for flexible metadata storage

### Multi-Agent System Integration
- **Agent patterns** - Follow AbstractConfigurableAgent pattern for AI agents
- **Resilience** - Implement retry policies, circuit breakers, and fallback mechanisms
- **Local fallback** - Use FallbackAgentFactory for Ollama local fallback agents
- **Memory management** - Maintain agent memory through AgentMemoryStore
- **Cost tracking** - Track costs and usage through CreditService integration
- **Pipeline coordination** - Coordinate with Spring Batch pipeline orchestration
- **Communication** - Use Spring Events for agent communication
- **Lifecycle management** - Implement agent task lifecycle management

### UI Development Patterns
- **View inheritance** - All Views extend Div implements BeforeEnterObserver
- **Component structure** - Add components directly to view, NOT to extra container Divs
- **Route management** - Routes defined in UIConstants class
- **Styling approach** - External CSS classes in theme, avoid inline styles
- **Reusability** - Create reusable components in ui.components package
- **Responsive design** - Ensure mobile compatibility with responsive design
- **Design system** - Use Vaadin Lumo design system variables
- **Lifecycle** - Implement proper component lifecycle methods

### AI Provider Integration
- **Configuration** - Use AIConfig for provider management (OpenAI, Anthropic, Perplexity, Ollama)
- **Optimization** - Implement provider-specific optimizations in agent classes
- **Failure handling** - Handle API failures gracefully with circuit breaker patterns
- **Cost control** - Track token usage and implement cost controls
- **Key management** - Use user-aware API key management
- **Local processing** - Implement fallback to Ollama for local processing

## Credit System Integration

### Cost Tracking
- Track AI API calls through CreditService
- Monitor token usage across all providers
- Implement cost-aware retry policies
- Log cost metrics for analytics
- Respect user credit limits and subscription tiers

### Subscription Awareness
- Validate user subscription level before expensive operations
- Implement tier-based feature access
- Handle credit exhaustion gracefully
- Provide cost estimates for operations

## Agent Memory Integration

### Memory Store Patterns
- Use AgentMemoryStore for context preservation
- Maintain conversation history across sessions
- Store intermediate processing results
- Implement memory cleanup strategies
- Share relevant context between agents

### Context Management
- Preserve user preferences and settings
- Maintain paper processing context
- Track multi-step workflow progress
- Enable context-aware error recovery

## Enhanced Commands

### implement_next_step
Implement next step from plan with Answer42 integration
```json
{
  "plan_file": "./ai-tdd-docs/[feature-name]/plan.md",
  "auto_commit": true,
  "track_costs": true
}
```

### execute_step_by_number
Execute specific step number from implementation plan
```json
{
  "step_number": 3,
  "plan_path": "./ai-tdd-docs/[feature-name]/plan.md",
  "agent_context": true
}
```

### continue_ai_tdd_cycle
Continue AI-TDD implementation cycle with Answer42 pipeline integration
```json
{
  "max_steps": 5,
  "stop_on_failure": true,
  "pipeline_coordination": true
}
```

## Quality Gate Checks

### Standard Checks
```bash
./mvnw test                    # Unit tests must pass
./mvnw checkstyle:check       # Code style compliance
./mvnw pmd:check              # Static analysis clean
./mvnw spotbugs:check         # Security vulnerabilities
```

### Answer42-Specific Checks
- No TODO or placeholder code
- LoggingUtil usage verification
- UIConstants route compliance
- Agent pattern compliance
- Database schema adherence

## Technology Integration

### Spring Boot 3.4.5 Patterns
- Constructor injection over field injection
- Use @ConfigurationProperties for configuration binding
- Implement proper exception handling with @ControllerAdvice
- Leverage Spring Events for loose coupling
- Use Spring Security for authentication/authorization
- Apply @Transactional with proper isolation levels
- Use Spring Batch for pipeline orchestration

### Answer42-Specific Spring Beans
- **AIConfig** - Bean for AI provider management
- **ThreadConfig** - For parallel processing
- **CreditService** - For cost tracking
- **AgentMemoryStore** - For context management
- **LoggingUtil** - For standardized logging

### Vaadin 24.7.3 Patterns
- Use Lumo design system variables
- Implement proper data binding with Binder
- Create type-safe navigation with RouteParameters
- Use proper component lifecycle methods
- Ensure responsive design with CSS Grid/Flexbox

### Answer42 UI Standards
- All routes defined in UIConstants
- Views extend Div implements BeforeEnterObserver
- Use external CSS classes from theme
- Progressive Web App capabilities
- Real-time updates with WebSocket integration

## Enhanced Status Updates

Track additional Answer42-specific metrics:
- **Status**: [ ] Not Started / [x] In Progress / [x] Complete
- **Completion timestamp**
- **Cost tracking updates** - API usage and credit consumption
- **Agent memory context updates** - Context state changes
- **Deviations or issues notes**
- **Updated effort estimates**

## Error Handling & Logging

### Enhanced Error Patterns
- Custom exceptions with meaningful messages
- Circuit breaker patterns for external services
- Fallback mechanisms for AI service failures
- Structured error responses to UI
- **NEW**: Agent failure recovery strategies

### Enhanced Logging
- Use LoggingUtil for all operations
- Log errors with proper context
- Never log sensitive data (API keys, user content)
- Include correlation IDs for tracing
- **NEW**: Log agent coordination activities
- **NEW**: Track cost-related events

## Performance Considerations

### Answer42-Specific Optimizations
- Implement caching for discovery results (24-hour duration)
- Use rate limiting for external API calls
- Optimize database queries with proper indexing
- Use async processing for long-running operations
- Implement connection pooling for database access
- **NEW**: Use parallel processing for multi-agent coordination
- **NEW**: Monitor and optimize agent memory usage

## Answer42 System Integrations

### Discovery System Integration
- Integrate with multi-source discovery (Crossref, Semantic Scholar, Perplexity)
- Use discovery caching patterns
- Implement discovery result ranking and filtering

### Chat System Integration
- Support three chat modes (Paper, Cross-Reference, Research Explorer)
- Integrate with agent-generated content
- Maintain chat context and history

### Pipeline Orchestration
- Coordinate with 9-agent processing pipeline
- Use Spring Batch for workflow management
- Implement pipeline progress tracking
- Handle agent failure scenarios

## Git Workflow Integration

### Enhanced Pre-Commit Checks
- Run Answer42 quality gates
- Verify plan status update
- Check for TODO/placeholder code
- **NEW**: Validate agent integration patterns
- **NEW**: Confirm cost tracking updates

### Commit Pattern
```bash
feat: [Step X] Description from plan.md
```

### Branch Strategy
```bash
feature/ai-tdd-[feature-name]
```

## Integration with AI-TDD Pipeline

After implementation completion:
1. Update plan.md with completion status and cost metrics
2. Update agent memory context
3. Coordinate with next pipeline stage (Test Generator)
4. Signal completion to pipeline orchestrator
5. Commit changes with descriptive message following Answer42 conventions

## Usage Examples

### Basic Implementation
```bash
# Implement next step in plan
Agent: implement_next_step
Plan: ./ai-tdd-docs/semantic-search/plan.md
Auto-commit: true
Cost tracking: true
```

### Pipeline Coordination
```bash
# Continue with multi-agent coordination
Agent: continue_ai_tdd_cycle  
Max steps: 3
Pipeline coordination: true
Stop on failure: true
```

### Step-by-Step Execution
```bash
# Execute specific step with agent context
Agent: execute_step_by_number
Step: 5
Plan: ./ai-tdd-docs/discovery-enhancement/plan.md
Agent context: true
```

This enhanced Agent 5 represents the state-of-the-art in AI-TDD implementation, specifically tailored for Answer42's sophisticated multi-agent academic research platform.
