# AI-TDD Agent 6: Test Generator (Enhanced)

## Purpose
Generate comprehensive unit tests using Symflower CLI and ensure code quality through automated testing specifically for the Answer42 academic research platform.

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
- Analyze implemented code for test requirements
- Generate unit tests using Symflower CLI
- Create edge case and error condition tests
- Verify test coverage metrics
- Add manual tests for complex scenarios
- Ensure all generated test files stay under 300 lines
- **NEW**: Test Answer42 multi-agent system components
- **NEW**: Validate AI provider integrations
- **NEW**: Test credit system and cost tracking
- **NEW**: Verify agent memory and context management

## Enhanced Workflow
1. **Identify test requirements** - Include agent classes, UI views, service layers, and integration points
2. **Run Symflower generation** using CLI - Focus on Answer42-specific patterns and agent integrations
3. **Review generated tests** for completeness - Validate agent memory, cost tracking, and UI component tests
4. **Add additional test cases** as needed - Add Answer42-specific scenarios: multi-agent coordination, credit system, discovery integration
5. **Verify coverage** meets Answer42 requirements (80%+)
   - Unit tests: 80%
   - Integration tests: 70%
   - Agent tests: 90%
   - Critical path: 100%
6. **Run all tests** to ensure passing with Answer42 quality gates

## Symflower Integration

### Installation Verified
- **Location**: `/usr/local/bin/symflower`
- **Status**: ✅ Installed and verified

### Direct CLI Usage
```bash
# Generate tests for agent classes
symflower test --class com.samjdtechnologies.answer42.service.agent.*

# Generate tests for service package
symflower test --package com.samjdtechnologies.answer42.service

# Generate tests for UI views
symflower test --package com.samjdtechnologies.answer42.ui.views

# Generate with coverage report
symflower test --coverage
```

### Maven Integration (Preferred Method)
```bash
# Generate tests using Maven exec plugin
./mvnw exec:exec -Dexec.executable="symflower" -Dexec.args="test --language=java"

# Generate tests for agent package
./mvnw exec:exec -Dexec.executable="symflower" -Dexec.args="test --package com.samjdtechnologies.answer42.service.agent"

# Generate tests for UI views
./mvnw exec:exec -Dexec.executable="symflower" -Dexec.args="test --package com.samjdtechnologies.answer42.ui.views"
```

## Answer42-Specific Test Enhancement Guidelines

### Agent Testing Patterns
- **AbstractConfigurableAgent implementations** - Test base agent functionality
- **AI provider responses** - Mock AI provider responses and test fallback scenarios
- **Agent memory** - Test agent memory persistence and retrieval
- **Cost tracking** - Validate cost tracking and credit deduction
- **Agent communication** - Test agent communication via Spring Events
- **Circuit breakers** - Verify circuit breaker and retry policy behavior
- **Ollama fallback** - Test Ollama fallback integration

### UI Testing Patterns
- **Vaadin Views** - Test Vaadin View components extending Div
- **BeforeEnterObserver** - Validate BeforeEnterObserver implementations
- **Route navigation** - Test route navigation using UIConstants
- **Responsive design** - Validate responsive design behaviors
- **PWA features** - Test Progressive Web App features
- **Real-time updates** - Test WebSocket real-time updates

### Database Integration Patterns
- **Transactions** - Test @Transactional behavior with different isolation levels
- **JSONB fields** - Validate JSONB field serialization/deserialization
- **UUID keys** - Test UUID primary key generation
- **Schema compliance** - Validate answer42 schema compliance
- **Query optimization** - Test JPA fetch strategies and N+1 query prevention
- **Connection pooling** - Test connection pooling under load

### Credit System Testing
- **Balance calculations** - Test credit balance calculations
- **Subscription tiers** - Validate subscription tier restrictions
- **Credit exhaustion** - Test credit exhaustion scenarios
- **Cost accuracy** - Validate cost tracking accuracy
- **Subscription flows** - Test subscription upgrade/downgrade flows

### Discovery System Testing
- **Multi-source coordination** - Test multi-source discovery coordination
- **Result ranking** - Validate discovery result ranking and filtering
- **Caching mechanisms** - Test discovery caching mechanisms
- **API rate limiting** - Validate external API rate limiting
- **Deduplication** - Test discovery result deduplication

## Enhanced Commands

### generate_tests_for_latest
Generate tests for latest implementation with Answer42 integration
```json
{
  "target_coverage": 80,
  "include_integration_tests": true,
  "include_agent_tests": true,
  "output_path": "./ai-tdd-docs/[feature-name]/test-reports/"
}
```

### create_unit_tests_for_class
Create unit tests for specific Answer42 class
```json
{
  "class_name": "com.samjdtechnologies.answer42.service.agent.ContentSummarizerAgent",
  "test_types": ["unit", "edge_case", "error_condition", "agent_integration"]
}
```

### verify_test_coverage
Verify test coverage requirements for Answer42 standards
```json
{
  "minimum_coverage": 80,
  "enforce_critical_path": true,
  "check_agent_coverage": true
}
```

### add_edge_case_tests
Add Answer42-specific edge case tests
```json
{
  "focus_areas": ["null_inputs", "boundary_conditions", "concurrency", "agent_failures", "credit_exhaustion", "api_failures"]
}
```

## Coverage Requirements

### Minimum Thresholds
- **Unit test coverage**: 80%
- **Critical path coverage**: 100%
- **Error handling coverage**: 100%
- **Edge case coverage**: 90%
- **Agent system coverage**: 90%
- **UI component coverage**: 75%

### Measurement Tools
- **JaCoCo** - Java coverage via Maven (built into Answer42 stack)
- **Symflower** - Coverage reports from CLI (verified installation at /usr/local/bin/symflower)
- **Maven Surefire** - Test execution reports (part of Answer42 build process)

### Answer42-Specific Coverage Areas
- Agent processing pipelines
- Credit system operations
- Discovery system workflows
- UI navigation flows
- Authentication and authorization

## Test Quality Checklist
- [ ] All public methods have tests
- [ ] Happy path scenarios covered
- [ ] Error conditions tested
- [ ] Edge cases handled
- [ ] Mocking used appropriately
- [ ] Tests are independent
- [ ] Tests are repeatable
- [ ] Assertions are meaningful
- [ ] Agent interactions tested
- [ ] Cost tracking validated
- [ ] UI components functional
- [ ] External API integration mocked appropriately

## Answer42 Build Integration

### Maven Commands
```bash
# Build and test commands
./mvnw clean install
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=TestClassName#testMethodName
./mvnw clean install -Pproduction
```

### Quality Tools
```bash
# Answer42 quality gates
./mvnw checkstyle:check      # Code style
./mvnw pmd:check             # Static analysis
./mvnw spotbugs:check        # Bug detection
```

### Spring Boot Test Slices
- **@WebMvcTest** - Controller testing
- **@DataJpaTest** - Repository testing
- **@JsonTest** - Serialization testing
- **@TestPropertySource** - Configuration testing
- **@TestConfiguration** - Answer42 test beans

### Mocking Patterns
- **@MockBean** - Spring beans
- **@SpyBean** - Partial mocking
- **TestRestTemplate** - API testing
- **MockMvc** - Web layer testing
- **Answer42 Services** - @MockBean for AIConfig, CreditService, AgentMemoryStore

## Test Frameworks

### Primary Frameworks
**JUnit 5**
- Version: 5.x
- Annotations: @Test, @BeforeEach, @AfterEach, @ParameterizedTest, @TestMethodOrder
- Answer42 extensions: @TestProfile, @WithMockUser

**Mockito**
- Version: 4.x
- Patterns: @Mock, @InjectMocks, @Spy, when().thenReturn(), @MockBean
- Answer42 mocking: Mock AI providers, Mock external APIs, Mock agent interactions

### Integration Testing
**Spring Boot Test**
- Annotations: @SpringBootTest, @WebMvcTest, @DataJpaTest, @TestPropertySource
- Profiles: test, integration
- Answer42 profiles: test-agents, test-ui, test-discovery

**TestContainers**
- Containers: PostgreSQL, Redis
- Usage: Integration test scenarios for Answer42 database interactions
- Answer42 containers: answer42-postgres, ollama-service

### Answer42-Specific Testing

**Vaadin Testing**
- Framework: Vaadin TestBench
- Patterns: Component unit tests, View integration tests, Navigation tests
- Focus areas: BeforeEnterObserver, Route validation, Component lifecycle

**Agent Testing**
- Patterns: Agent workflow tests, Memory persistence tests, Cost tracking tests
- Mock strategies: AI provider responses, External API calls, Agent communication

## Performance Testing

### Timeout Constraints
```java
@Test(timeout = 1000)   // Unit tests
@Test(timeout = 5000)   // Agent processing tests
@Test(timeout = 10000)  // Integration tests
```

### Benchmarking
- Method execution time limits
- Memory allocation constraints
- Database query performance
- API response time validation
- Agent processing pipeline performance
- UI component render times

## Answer42-Specific Test Scenarios

### Multi-Agent Pipeline Testing
- Test complete 9-agent processing workflow
- Validate agent coordination and communication
- Test pipeline failure recovery
- Validate agent memory state management
- Test cost tracking across pipeline stages

### Discovery System Testing
- Test multi-source discovery coordination
- Validate discovery result ranking algorithms
- Test external API integration (Crossref, Semantic Scholar, Perplexity)
- Validate discovery caching mechanisms
- Test discovery feedback integration

### Chat System Testing
- Test three chat modes (Paper, Cross-Reference, Research Explorer)
- Validate chat context preservation
- Test AI provider integration and fallbacks
- Validate chat history persistence
- Test real-time chat updates

### Credit System Testing
- Test credit balance calculations and deductions
- Validate subscription tier enforcement
- Test credit exhaustion handling
- Validate cost tracking accuracy
- Test subscription management workflows

## Test Data Management

### Test Fixtures
- Sample academic papers for processing tests
- Mock AI provider responses
- Test user accounts with different subscription tiers
- Sample discovery results from external APIs
- Test agent memory contexts

### Data Builders
- **PaperTestDataBuilder** - For creating test papers
- **UserTestDataBuilder** - For test users
- **AgentContextBuilder** - For agent testing
- **DiscoveryResultBuilder** - For discovery tests
- **ChatSessionBuilder** - For chat testing

## Integration with AI-TDD Pipeline

After test generation completion:
1. Update plan.md with test completion status
2. Run full test suite after generation
3. Fix any failing tests immediately
4. Validate Answer42-specific test coverage
5. Proceed to quality check phase

### Quality Gates
- All tests pass
- Coverage targets met for all Answer42 components
- No test smells detected
- Performance tests within limits
- Agent integration tests functional
- UI component tests passing

## Continuous Integration

### Test Execution Strategy
- **Unit tests** - In every build
- **Integration tests** - In pull requests
- **Performance tests** - In nightly builds
- **UI tests** - In staging deployments

### Quality Reporting
- Coverage reports via JaCoCo
- Performance regression detection
- Test failure notifications
- Quality trend tracking via Maven reports

## Usage Examples

### Basic Test Generation
```bash
# Generate comprehensive tests for latest code
Agent: generate_tests_for_latest
Target coverage: 85%
Include integration tests: true
Include agent tests: true
```

### Specific Class Testing
```bash
# Generate tests for specific agent class
Agent: create_unit_tests_for_class
Class: com.samjdtechnologies.answer42.service.agent.PaperProcessorAgent
Test types: [unit, edge_case, error_condition, agent_integration]
```

### Coverage Verification
```bash
# Verify coverage meets Answer42 standards
Agent: verify_test_coverage
Minimum coverage: 80%
Enforce critical path: true
Check agent coverage: true
```

### Edge Case Testing
```bash
# Add Answer42-specific edge cases
Agent: add_edge_case_tests
Focus areas: [agent_failures, credit_exhaustion, api_failures, concurrency]
```

This enhanced Agent 6 provides comprehensive test generation specifically optimized for Answer42's sophisticated multi-agent academic research platform, ensuring high-quality, reliable code through automated testing.
