# Database Tables Usage Analysis: agent_memory_store and tasks

This document provides a comprehensive analysis of how the Answer42 application uses the `agent_memory_store` and `tasks` database tables, including their schemas, usage patterns, and architectural implications.

## Table of Contents

1. [Overview](#overview)
2. [Agent Memory Store Table](#agent-memory-store-table)
3. [Tasks Table](#tasks-table)
4. [Usage Patterns](#usage-patterns)
5. [Performance Considerations](#performance-considerations)
6. [Security Model](#security-model)
7. [Future Considerations](#future-considerations)

## Overview

Both tables are central to Answer42's agent-based processing architecture:

- **`agent_memory_store`**: Provides persistent memory for agents to avoid redundant processing
- **`tasks`**: Manages asynchronous task execution across different agent types

These tables enable the application to maintain state across restarts, track processing history, and coordinate complex workflows.

## Agent Memory Store Table

### Schema

```sql
CREATE TABLE public.agent_memory_store (
  key TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  data JSONB NOT NULL
);
```

### Purpose

The agent memory store acts as a persistent cache for agents to store state information that needs to survive application restarts. Its primary use case is preventing redundant processing operations.

### Current Usage

#### Paper Processing Memory

**Location**: `api/src/services/agent/specialized/paper-processor-agent.ts`

**Key**: `processed_papers`
**Data Structure**:

```json
{
  "processed": ["paper-id-1", "paper-id-2", "..."]
}
```

#### Implementation Details

1. **Read Operation** (Lines 170-174):
   
   ```typescript
   const { data: memoryData } = await this.supabase
   .from('agent_memory_store')
   .select('data')
   .eq('key', 'processed_papers')
   .single();
   ```

2. **Skip Processing Check** (Lines 176-192):
   
   - Checks if paper ID exists in processed array
   - Returns early if paper was previously processed
   - Prevents reprocessing papers that may have been deleted

3. **Update Operation** (Lines 478-496):
   
   ```typescript
   const currentMemory = memoryData?.data || { processed: [] };
   if (!currentMemory.processed.includes(paperId)) {
   currentMemory.processed.push(paperId);
   
   await this.supabase
    .from('agent_memory_store')
    .update({
      data: currentMemory,
      updated_at: new Date(),
    })
    .eq('key', 'processed_papers');
   }
   ```

#### Business Logic

- **Deduplication**: Prevents expensive PDF processing operations on papers that were already processed
- **Persistence**: Maintains processing history across application restarts
- **Recovery**: Handles cases where papers are deleted but should not be reprocessed

### Row Level Security

- **Policy**: "Admins have full access to agent_memory_store"
- **Access**: Requires authenticated role
- **Scope**: Global access for authenticated agents

## Tasks Table

### Schema

```sql
CREATE TABLE public.tasks (
  id TEXT PRIMARY KEY,
  agent_id TEXT NOT NULL,
  user_id UUID NOT NULL,
  input JSONB NOT NULL,
  status TEXT NOT NULL,
  error TEXT,
  result JSONB,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ
);
```

### Purpose

The tasks table provides a comprehensive tracking system for asynchronous agent operations, including status management, result storage, and error handling.

### Usage Patterns

#### 1. General Agent Tasks

**Service**: `api/src/services/agent/agent.service.ts`

**Task Lifecycle**:

1. **Creation** (Lines 183-189):
   
   - Generates unique task ID (`task-${uuidv4()}`)
   - Stores in memory Map for fast access
   - Inserts into database with retry mechanism
   - Handles special 'system' user ID conversion

2. **Status Updates** (Lines 72-84):
   
   - Updates status via message queue responses
   - Stores completion time and execution duration
   - Handles both success and failure scenarios

3. **Result Storage**:
   
   - Stores task results in memory Map
   - Updates database with final results
   - Supports both text and JSON formats

#### 2. Analysis Tasks

**Service**: `api/src/services/analysis/paper-analysis.service.ts`

**Specialized Operations**:

- **Table**: Uses `analysis_tasks` (appears to be a separate table or view)
- **User Association**: Links tasks to specific users and papers
- **Status Tracking**: Manages IN_PROGRESS → COMPLETED/FAILED transitions
- **Result Management**: Stores analysis results and error messages

#### 3. Processing Pipeline Tasks

**Service**: `api/src/services/processing/paper-pipeline.service.ts`

**Pipeline Management**:

- **Task Creation**: Creates processing tasks for PDF operations
- **Progress Tracking**: Updates task progress percentage
- **Status Management**: Handles PENDING → PROCESSING → COMPLETED workflow
- **Error Handling**: Stores detailed error information for failed tasks

### Task Status Values

- **pending**: Initial state when task is created
- **processing/in_progress**: Task is actively being executed
- **completed**: Task finished successfully
- **failed**: Task encountered an error

### Performance Optimizations

#### Indexes

```sql
CREATE INDEX idx_tasks_agent_id ON public.tasks (agent_id);
CREATE INDEX idx_tasks_user_id ON public.tasks (user_id);
CREATE INDEX idx_tasks_status ON public.tasks (status);
```

#### Memory Management

- **In-Memory Cache**: Uses Map objects for fast task lookup
- **Timeout Handling**: 90-second timeout with graceful fallback
- **Garbage Collection**: Explicit GC calls for memory-intensive operations
- **Active Task Tracking**: Global Set for monitoring active tasks

### Error Handling Strategies

#### Database Resilience

1. **Retry Mechanism**: Up to 3 attempts for database operations
2. **Graceful Degradation**: Continues execution despite database errors
3. **Duplicate Handling**: Manages duplicate key errors gracefully
4. **Missing Table Handling**: Continues in development mode if tables don't exist

#### Timeout Management

```typescript
// Creates default result instead of failing on timeout
const defaultResult: AgentResult = {
  taskId,
  agentId: '',
  content: JSON.stringify({ message: 'Task execution timed out, using default result' }),
  format: 'json',
  success: true,
  completedAt: new Date(),
  executionTimeMs: Date.now() - startTime,
};
```

## Usage Patterns

### Agent Coordination

1. **Message Queue Integration**: Tasks communicate via message queue system
2. **Correlation IDs**: Task IDs serve as correlation IDs for async communication
3. **Result Aggregation**: Results collected from multiple agent types
4. **State Synchronization**: Database and memory state kept in sync

### User Context Management

1. **User Association**: All tasks linked to specific users
2. **Permission Checking**: RLS policies enforce user-specific access
3. **System Tasks**: Special handling for system-initiated tasks
4. **Session Tracking**: Integration with user session management

## Security Model

### Row Level Security Policies

#### Agent Memory Store

- **Admin Access**: Full CRUD for authenticated roles
- **Scope**: Global access (no user-specific restrictions)
- **Rationale**: Agent memory is shared infrastructure

#### Tasks Table

- **User Access**: Users can only access their own tasks
- **Admin Access**: Service role can view all tasks
- **System Tasks**: Special UUID for system-initiated operations

### Data Protection

- **Input Sanitization**: JSONB validation for task inputs
- **Error Isolation**: Error messages don't leak sensitive information
- **Audit Trail**: Complete timestamp tracking for all operations

## Performance Considerations

### Scaling Factors

1. **Task Volume**: High-frequency task creation during bulk operations
2. **Memory Usage**: Large PDF processing can consume significant memory
3. **Database Load**: Frequent status updates during active processing
4. **Result Size**: JSONB results can be substantial for large analyses

### Optimization Strategies

1. **Connection Pooling**: Supabase client reuse across operations
2. **Batch Operations**: Group multiple updates where possible
3. **Index Usage**: Strategic indexes on high-query columns
4. **Memory Cleanup**: Explicit garbage collection for large operations

## Future Considerations

### Potential Enhancements

1. **Partitioning**: Consider time-based partitioning for tasks table
2. **Archival**: Automated cleanup of old completed tasks
3. **Metrics**: Add performance monitoring and alerting
4. **Caching**: Redis integration for high-frequency memory operations

### Monitoring Needs

1. **Task Duration Tracking**: Identify slow-performing agents
2. **Error Rate Monitoring**: Track failure patterns by agent type
3. **Memory Usage Trends**: Monitor agent memory store growth
4. **Database Performance**: Query performance and connection usage

## Conclusion

The `agent_memory_store` and `tasks` tables form the backbone of Answer42's asynchronous processing architecture. They provide:

- **Reliability**: Robust error handling and recovery mechanisms
- **Persistence**: State preservation across application restarts
- **Scalability**: Efficient indexing and memory management
- **Security**: Comprehensive RLS policies and access controls
- **Observability**: Complete audit trails and status tracking

This architecture enables complex multi-agent workflows while maintaining data integrity and user isolation, making it suitable for the research-focused use cases that Answer42 serves.