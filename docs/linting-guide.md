# Answer42 Code Quality and Linting Guide

This document provides an overview of the code quality tools and linting configuration used in the Answer42 project to enforce our architecture and coding standards.

## Overview

Answer42 implements a comprehensive set of static analysis tools to ensure code quality, maintainability, and adherence to our architectural patterns. These tools run as part of the Maven build process and will cause the build to fail if violations are detected.

## Tools Configured

The project uses the following static analysis tools:

1. **Checkstyle**: Enforces code style guidelines
2. **PMD**: Performs static code analysis to find common programming flaws
3. **SpotBugs**: Detects potential bugs in Java programs
4. **Maven Enforcer**: Ensures build environment and dependency requirements are met

## Key Rules Enforced

### Architecture Rules

- **Layered Architecture Separation**:
  - Views must not directly access Repositories
  - Repositories must not access Views
  - Services must not import UI components
  - Controllers should only access data through Services, not directly through Repositories

### Code Style

- **File Organization**:
  
  - Import order: java/javax first, then third-party, then project imports
  - No unused imports
  - Classes limited to 300 lines maximum

- **Naming Conventions**:
  
  - camelCase for methods/variables
  - PascalCase for classes
  - ALL_CAPS for constants

- **Logging**:
  
  - Must use LoggingUtil class for all logging operations
  - Direct logger calls are prohibited

- **View Classes**:
  
  - Must extend Div and implement BeforeEnterObserver

- **Documentation**:
  
  - JavaDoc required for public methods
  - @param and @return tags required

### Method Structure

- Methods limited to 60 lines
- Cyclomatic complexity maximum of 15 per method

## Running the Linting Tools

The linting tools are configured to run only when explicitly called, not automatically during the build process. This gives you more control over when to apply code quality checks.

```bash
# Run Checkstyle only
mvn checkstyle:check

# Run PMD only
mvn pmd:check

# Run SpotBugs only
mvn spotbugs:check

# Run Maven Enforcer only
mvn enforcer:enforce

# Run all validations
mvn checkstyle:check pmd:check spotbugs:check enforcer:enforce
```

## Fixing Common Issues

### Checkstyle

- Use the LoggingUtil class for logging instead of directly using loggers:
  
  ```java
  // Incorrect
  logger.info("Some message");
  
  // Correct
  LoggingUtil.info(logger, "methodName", "Some message");
  ```

- Keep files under 300 lines by extracting functionality into helper classes

- Ensure View classes follow the required pattern:
  
  ```java
  public class MyView extends Div implements BeforeEnterObserver {
      // Implementation
  }
  ```

### PMD

- Follow layered architecture patterns:
  - Views → Services → Repositories
  - Never skip a layer
- Keep methods under 60 lines to improve readability
- Break complex methods into smaller, more focused methods

### SpotBugs

- Initialize all fields in constructors
- Avoid exposing internal arrays or collections directly
- Handle resources properly (try-with-resources)

## Suppressing Warnings

In rare cases, it may be necessary to suppress a specific warning. Use suppression annotations sparingly and always include justification:

```java
// Checkstyle
@SuppressWarnings("checkstyle:MagicNumber")

// PMD
@SuppressWarnings("PMD.AvoidDuplicateLiterals")

// SpotBugs
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("EI_EXPOSE_REP2")
```

## IDE Integration

### VS Code

For VS Code users, install:

- Checkstyle for Java
- PMD for Java
- SpotBugs

Configure these extensions to use the same configuration files as Maven.

### IntelliJ IDEA

For IntelliJ IDEA users, install:

- CheckStyle-IDEA
- PMDPlugin
- SpotBugs

## Further Resources

- [Checkstyle Documentation](https://checkstyle.sourceforge.io/)
- [PMD Documentation](https://pmd.github.io/)
- [SpotBugs Documentation](https://spotbugs.github.io/)
