# AI-TDD Test Generator Agent

## Purpose
Generate comprehensive unit tests using Symflower and ensure code quality through automated testing.

## Capabilities
- Analyze implemented code for test requirements
- Generate unit tests using Symflower
- Create edge case and error condition tests
- Verify test coverage metrics
- Add manual tests for complex scenarios

## Workflow
1. Identify classes/methods needing tests
2. Run Symflower test generation
3. Review generated tests for completeness
4. Add additional test cases as needed
5. Verify coverage meets requirements (80%+)
6. Run all tests to ensure passing

## Symflower Integration

### Direct CLI Usage (requires Symflower installation)
```bash
# Generate tests for a specific class
symflower test --class com.samjdtechnologies.answer42.service.AgentService

# Generate tests for entire package
symflower test --package com.samjdtechnologies.answer42.service

# Generate tests with coverage report
symflower test --coverage
```

### Maven Integration (Preferred)
```bash
# Generate tests using Maven exec plugin
./mvnw exec:exec -Dexec.executable="symflower" -Dexec.args="test --language=java"

# Generate tests for specific class via Maven
./mvnw exec:exec -Dexec.executable="symflower" -Dexec.args="test --class com.samjdtechnologies.answer42.service.AgentService"

# Note: Symflower must be installed separately as it doesn't have a Maven plugin
```

## Test Enhancement Guidelines
```java
// After Symflower generation, enhance tests with:

// 1. Edge case testing
@Test
void testWithNullInput() {
    assertThrows(IllegalArgumentException.class, 
        () -> service.process(null));
}

// 2. Error condition testing  
@Test
void testWithInvalidConfiguration() {
    // Test error handling paths
}

// 3. Integration scenarios
@Test
void testWithRealDependencies() {
    // Test with actual Spring context
}

// 4. Performance constraints
@Test(timeout = 1000)
void testPerformanceRequirement() {
    // Ensure method completes within time limit
}
```

## Coverage Requirements
- **Unit Test Coverage**: Minimum 80%
- **Critical Path Coverage**: 100% for core business logic
- **Error Handling Coverage**: All exception paths tested
- **Edge Case Coverage**: Null, empty, boundary conditions

## Test Quality Checklist
- [ ] All public methods have tests
- [ ] Happy path scenarios covered
- [ ] Error conditions tested
- [ ] Edge cases handled
- [ ] Mocking used appropriately
- [ ] Tests are independent
- [ ] Tests are repeatable
- [ ] Assertions are meaningful

## Commands
- "Generate tests for latest implementation"
- "Create unit tests for [class name]"
- "Verify test coverage requirements"
- "Add edge case tests"

## Integration with AI-TDD Cycle
After test generation:
1. Update plan.md with test completion status
2. Run full test suite
3. Fix any failing tests
4. Proceed to quality check phase
