I'll add best practices for your technology stack. Here's an expanded version with comprehensive guidelines.

# Technology Stack Best Practices

## Backend

### Java 21

- Leverage Java 21 features:
  - **Virtual Threads**: Use for I/O-bound operations for improved scalability
  - **Record Classes**: For immutable data transfer objects
  - **Pattern Matching**: Use enhanced switch expressions and pattern matching for type checks
  - **Text Blocks**: For cleaner SQL queries and JSON templates
  - **Sealed Classes**: For controlled inheritance hierarchies

### Spring Boot 3.4.5

- **Actuator**: Enable production-ready monitoring endpoints with proper security
- **Profiles**: Use profiles for different environments (dev, test, prod)
- **Configuration**: Externalize configuration using `@ConfigurationProperties`
- **Auto-configuration**: Create custom auto-configuration for reusable components
- **Caching**: Implement caching strategies for frequently accessed data

### Spring Security

- **Method Security**: Use `@PreAuthorize` and `@PostAuthorize` for fine-grained control
- **CSRF Protection**: Enable for browser clients, disable for API-only endpoints
- **Content Security Policy**: Implement strict CSP headers
- **Rate Limiting**: Implement to prevent abuse
- **Password Encoding**: Always use BCrypt or Argon2 for password storage

### Spring Data JPA

- **Specification API**: Use for dynamic queries
- **Projections**: Create interface-based projections for efficient data retrieval
- **Auditing**: Enable `@CreatedBy`, `@LastModifiedBy`, etc.
- **Pagination**: Always paginate large result sets
- **Query Methods**: Use naming conventions for simple queries
- **Named Queries**: Use for complex queries that benefit from caching

### Spring AI

- **Prompt Engineering**: Create well-defined prompts with clear instructions
- **Model Selection**: Choose appropriate models based on task complexity
- **Caching Responses**: Cache AI responses where appropriate
- **Fallback Mechanisms**: Implement graceful degradation when AI services are unavailable
- **Streaming Responses**: Use streaming for long-form content generation

### Spring Transaction

- **Isolation Levels**: Set appropriate isolation levels for transactions
- **Propagation**: Use REQUIRED for most cases, REQUIRES_NEW when needed
- **Timeout**: Set reasonable timeouts to prevent hanging operations
- **Read-Only**: Mark read-only transactions to optimize performance
- **Exception Handling**: Define which exceptions should trigger rollbacks

### JWT

- **Short Lived Tokens**: Use short expiration times with refresh tokens
- **Claims Validation**: Validate all claims including issuer and audience
- **Signature Algorithm**: Use RS256 instead of HS256 for better security
- **Token Storage**: Store in HttpOnly cookies to prevent XSS
- **Token Revocation**: Implement a token blacklist or use short expiration with refresh tokens

### PostgreSQL (Supabase)

- **Connection Pooling**: Configure appropriate pool sizes
- **Indexing**: Create proper indexes for frequently queried columns
- **JSONB**: Use for semi-structured data with proper indexes
- **Partitioning**: Implement for large tables
- **RLS (Row Level Security)**: Use Supabase RLS for data security
- **DB Functions**: Move complex queries to database functions
- **Supabase Functions**: Leverage edge functions for backend logic

### Hibernate

- **2nd Level Cache**: Configure for frequently accessed, rarely changed data
- **Batch Operations**: Use for bulk inserts/updates
- **N+1 Query Prevention**: Use `JOIN FETCH` or entity graphs
- **Optimistic Locking**: Implement for concurrent modifications
- **Schema Generation**: Use for development only, use migration tools for production
- **Custom Types**: Create for specialized data types

## Frontend

### Vaadin 24.7.3

- **Lazy Loading**: Use lazy loading for large component trees
- **Server Push**: Configure for real-time updates
- **Component Composition**: Create reusable composite components
- **Theme Variants**: Use for consistent styling
- **Grid Virtualization**: Enable for large datasets
- **Offline Support**: Configure for PWA offline capabilities
- **Error Handling**: Implement consistent error handling patterns
- **Form Binding**: Use Binder for robust form validation

### HTML/CSS/JavaScript

- **CSS Variables**: Use for theme consistency
- **SCSS**: Structure CSS with nesting and variables
- **Web Components**: Create custom elements when needed
- **Responsive Design**: Use CSS Grid and Flexbox for layouts
- **Accessibility**: Follow WCAG guidelines for all components
- **Performance**: Minimize JS execution time and DOM operations

### PWA Support

- **Service Workers**: Configure for offline capability
- **App Manifest**: Create for installable experience
- **Cache Strategies**: Implement for offline access to static resources
- **Background Sync**: Use for offline data synchronization
- **Push Notifications**: Implement for engagement

## Development Tools

### Maven

- **BOM (Bill of Materials)**: Use Spring Boot BOM for dependency management
- **Profiles**: Create for different environments
- **Plugins**: Configure Spotless, JaCoCo, etc.
- **Multi-Module Projects**: Structure for large applications
- **Dependency Analysis**: Regularly check for unused or duplicated dependencies
- **Version Properties**: Centralize version numbers in properties

### Spring Boot DevTools

- **Live Reload**: Enable for faster development
- **Property Defaults**: Override for development environment
- **Remote Debugging**: Configure for remote development
- **Application Restart**: Tune trigger file paths

### JUnit

- **Parameterized Tests**: Use for comprehensive test coverage
- **Mocking**: Use Mockito for dependencies
- **Spring Test**: Leverage context caching for faster tests
- **Test Slices**: Use @WebMvcTest, @DataJpaTest, etc.
- **Test Containers**: Use for integration tests with real databases
- **Mutation Testing**: Consider PIT for test quality

### Supabase

- **Edge Functions**: Use for serverless capabilities
- **Realtime**: Leverage for live updates
- **Storage**: Use for file storage with security rules
- **Auth**: Integrate with Spring Security when needed
- **Replication**: Configure for read replicas if needed
- **Metrics**: Monitor query performance

## AI Models

### Claude (Anthropic)

- **Context Management**: Optimize prompt construction to fit context window
- **System Prompts**: Create well-defined system prompts for consistent behavior
- **Fallback Strategies**: Implement for handling rate limits or failures
- **Response Validation**: Validate structured outputs against schemas
- **Progressive Generation**: Stream responses for better user experience
- **Prompt Engineering**: Create reusable prompt templates for common tasks

### Perplexity API

- **Query Optimization**: Structure queries for most relevant results
- **Citation Tracking**: Maintain source information for accountability
- **Result Caching**: Cache results for similar queries
- **Error Handling**: Gracefully degrade when API is unavailable
- **Answer Validation**: Implement validation logic for critical information

## Additional Best Practices

### Architecture

- **Hexagonal Architecture**: Implement ports and adapters pattern
- **Microservices**: Consider for complex domains with clear boundaries
- **API Versioning**: Plan for evolution with proper versioning
- **Event Sourcing**: Consider for complex domains with audit requirements
- **CQRS**: Separate read and write models for complex domains

### Security

- **Security Headers**: Implement secure headers (CSP, HSTS, etc.)
- **Input Validation**: Validate all inputs at controller level
- **Output Encoding**: Encode outputs to prevent XSS
- **Secrets Management**: Use environment variables or vault solutions
- **Dependency Scanning**: Regularly scan for vulnerabilities
- **Penetration Testing**: Schedule regular security audits

### Performance

- **Caching Strategy**: Implement multi-level caching
- **Connection Pooling**: Configure appropriate pool sizes
- **Async Processing**: Use for time-consuming operations
- **Database Optimization**: Regular execution plan analysis
- **Load Testing**: Implement as part of CI/CD pipeline
- **Resource Monitoring**: Set up alerts for resource exhaustion

### DevOps

- **CI/CD Pipeline**: Automate build, test, and deployment
- **Infrastructure as Code**: Use Terraform or similar
- **Containerization**: Use Docker for consistent environments
- **Environment Parity**: Ensure dev/test/prod similarity
- **Blue/Green Deployment**: Implement for zero-downtime updates
- **Monitoring**: Set up comprehensive monitoring and alerting

### Documentation

- **API Documentation**: Generate with SpringDoc OpenAPI
- **Architecture Decision Records**: Document significant decisions
- **Living Documentation**: Keep documentation close to code
- **User Guides**: Create for end users
- **Developer Onboarding**: Document setup process
- **Runbooks**: Create for operational procedures

### Quality Assurance

- **Code Reviews**: Enforce thorough review process
- **Automated Testing**: Achieve high test coverage
- **Static Analysis**: Configure SonarQube or similar
- **Performance Testing**: Include in CI/CD pipeline
- **Accessibility Testing**: Regular audits for compliance
- **Security Scanning**: Regular dependency and code scanning

### Vaadin-Specific

- **UI Components**: Create reusable components for consistency
- **Theming**: Implement consistent theming via UIConstants
- **Router Configuration**: Use lazy loading for views
- **Event Bus**: Implement for component communication
- **State Management**: Use view models for complex state
- **Error Boundaries**: Implement for graceful error handling
- **Loading Indicators**: Add for long operations
- **Data Providers**: Implement efficient data providers for collections

### Coding Standards

- Follow the guidelines in CLAUDE.md religiously
- Use LoggingUtil for all logging operations
- Keep classes under 300 lines of code
- Use proper error handling with contextual information
- Implement proper schema naming with answer42 schema prefix
- Use UUID for entity IDs instead of auto-incrementing integers
- Never use deprecated methods
- Prioritize composition over inheritance
- Follow SOLID principles consistently

This comprehensive list of best practices should help maintain high code quality and performance throughout your project.
