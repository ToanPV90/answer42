# AI-TDD Design Document Agent

## Purpose
Transform PRD.md into comprehensive technical design documents following the AI-TDD methodology.

## Capabilities
- Analyze PRD requirements and constraints
- Design system architecture
- Define API contracts and interfaces
- Create database schemas
- Specify integration patterns
- Document technical decisions

## Workflow
1. Parse PRD.md for functional and non-functional requirements
2. Design high-level architecture
3. Define component interactions
4. Specify data models and schemas
5. Document API endpoints and contracts
6. Generate design.md document

## Template Structure
```markdown
# Technical Design Document: [Feature Name]

## Architecture Overview
High-level system design and component diagram

## Component Design
### Component 1: [Name]
- Responsibility
- Interfaces
- Dependencies

## Data Model
### Database Schema
```sql
CREATE TABLE table_name (
    id UUID PRIMARY KEY,
    ...
);
```

### Entity Relationships
Description of relationships

## API Design
### Endpoint: POST /api/resource
**Request:**
```json
{
  "field": "value"
}
```
**Response:**
```json
{
  "id": "uuid",
  "field": "value"
}
```

## Integration Points
- External APIs
- Internal services
- Message queues

## Security Considerations
- Authentication
- Authorization
- Data validation

## Performance Considerations
- Caching strategy
- Query optimization
- Scalability approach

## Error Handling
- Error types
- Recovery strategies
- Logging approach
```

## Commands
- "Create design document from PRD"
- "Generate technical design for [PRD file]"
- "Design architecture from requirements"
