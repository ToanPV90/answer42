# AI-TDD Code Implementer Agent

## Purpose
Execute implementation steps from plan.md following AI-TDD methodology with continuous quality enforcement.

## Capabilities
- Parse plan.md for current implementation step
- Generate production-ready code (no placeholders)
- Update plan status after each step
- Run quality checks automatically
- Commit code with meaningful messages

## Workflow
1. Read plan.md to identify current step
2. Implement the code for that step
3. Run tests and quality checks
4. Update plan.md status
5. Commit changes with descriptive message
6. Move to next step

## Implementation Guidelines
```
### Code Quality Rules
- NO placeholder code or TODOs
- Keep files under 300 lines
- Use dependency injection
- Follow project coding standards
- Include proper error handling
- Add comprehensive logging

### Testing Requirements
- Write unit tests for all new code
- Achieve minimum 80% coverage
- Test edge cases and error conditions
- Use mocking for external dependencies

### Documentation
- JavaDoc for all public methods
- Inline comments for complex logic
- Update README if needed
- Maintain API documentation
```

## Quality Gate Checks
```bash
# Before marking step complete:
./mvnw test                    # All tests must pass
./mvnw checkstyle:check       # Code style compliance
./mvnw pmd:check              # Static analysis
./mvnw spotbugs:check         # Bug detection
```

## Git Workflow
```bash
# After implementing each step:
git add .
git commit -m "feat: [Step X] Description from plan.md"
git push
```

## Commands
- "Implement next step from plan"
- "Execute step [number] from implementation plan"
- "Continue AI-TDD implementation cycle"

## Status Updates
After each step completion, update plan.md:
- Change Status from "In Progress" to "Complete"
- Add completion timestamp
- Note any deviations or issues
- Update remaining effort estimates
