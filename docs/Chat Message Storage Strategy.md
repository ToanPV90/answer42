# Chat Message Storage Strategy

## Overview

database schema analysis



### New Approach: Normalized Message Storage

Our new strategy uses a dedicated `chat_messages` table with proper relational links to `chat_sessions`.

## Database Schema

### Primary Tables

1. **`chat_sessions`**
- Stores metadata about the chat session

- Contains a `context` field for session-specific data (but no longer stores messages)

- Tracks the timestamp of the last message through `last_message_at`
2. **`chat_messages`**
- Stores all individual messages with rich metadata

- Links to the parent session through `session_id`

- Now includes enhanced fields for better message management

### Table Relationships

```
chat_sessions (1) ---→ (many) chat_messages
```

## Enhanced Chat Messages Schema

The enhanced `chat_messages` table has the following structure:

| Column | Type | Description |

|-----------------|------------------------|---------------------------------------------------|

| id | UUID | Primary key (auto-generated) |

| session_id | UUID (NOT NULL) | Foreign key to chat_sessions |

| role | TEXT (NOT NULL) | Message sender role (user/assistant) |

| content | TEXT (NOT NULL) | The actual message content |

| citations | JSONB [] | Citations referenced in the message |

| metadata | JSONB | Additional message metadata |

| created_at | TIMESTAMP WITH TZ | When the message was created |

| sequence_number | INTEGER (NOT NULL) | Order within the conversation (default: 0) |

| message_type | TEXT (NOT NULL) | Type of message (default: 'message') |

| is_edited | BOOLEAN (NOT NULL) | Whether message has been edited (default: false) |

| token_count | INTEGER | Number of tokens in the message |

| last_edited_at | TIMESTAMP WITH TZ | When the message was last edited |

### Indexes

- Primary key on `id`

- Index on `session_id` for filtering by session

- Composite index on `(session_id, sequence_number)` for efficient ordered retrieval

### Triggers

- `update_session_timestamp`: Automatically updates `chat_sessions.last_message_at` when new messages are inserted

## Automated Features

Our implementation includes:

1. **Automatic Sequence Numbering**: Messages are automatically assigned sequence numbers to maintain order

2. **Timestamp Management**: The `chat_sessions.last_message_at` field is automatically updated via triggers

3. **Edit Tracking**: Messages track if they've been edited, when, and by whom

## Integration with Analysis System

A critical component of our system is the integration between paper analyses and chat sessions. This section details how paper analyses are generated, stored, and integrated into the chat system.

### Analysis System Overview

Our analysis system consists of two primary tables:

1. **`analysis_tasks`** (currently has 2 records)
- Tracks requested paper analyses (e.g., summaries, deep analyses)

- Contains fields for status tracking, paper_id, user_id, task type

- Links to completed results via result_id
2. **`analysis_results`** (currently has 2 records)
- Stores the actual content of completed analyses

- Contains the analysis output text, metadata, and completion timestamps

- Referenced when displaying analysis results or adding them to chats

### Paper Analysis Workflow

The overall workflow for paper analysis and chat integration is:

1. **Analysis Request**: A user requests analysis of a paper (e.g., summary, deep analysis)

2. **Task Creation**: An `analysis_tasks` record is created with status="pending"

3. **Analysis Execution**: The system processes the paper and generates the analysis

4. **Result Storage**: The analysis content is stored in `analysis_results`

5. **Task Update**: The `analysis_tasks` record is updated with result_id and status="completed"

6. **Chat Integration**: The analysis can be added to a chat session in two ways:
- Automatically via `AutoAnalysisChatUtil` for newly completed analyses

- Manually by the user requesting to discuss a specific analysis

### Analysis to Chat Integration

With our new database structure, analyses are integrated into chats as follows:

1. **Session Context Update**: When an analysis is added to a chat, its ID is stored in the session context:

```json
{

"paperIds": ["paper-uuid-1"],

"analysisIds": ["analysis-uuid-1", "analysis-uuid-2"]

}
```

2. **Message Creation**: The analysis is added as two separate messages in the `chat_messages` table:
- An introduction message (e.g., "I've completed the Deep Analysis of 'Paper Title'")

- The actual analysis content
3. **Message Metadata**: Each message contains metadata linking it to the analysis:

```json
{

"analysis": {

"id": "analysis-uuid-1",

"type": "deep_summary",

"paper_id": "paper-uuid-1"

},

"message_type": "analysis" or "analysis_content"

}
```

4. **Data Relationships**: This creates a linked data structure:

```
papers (1) ←→ (many) analysis_tasks (1) → (1) analysis_results

↓

chat_sessions (1) → (many) chat_messages ← (references)
```

### Handling Analysis in Conversations

When users interact with analyses in a chat, the system:

1. **Retrieves Context**: Uses the `analysisIds` in the session context to know which analyses are available

2. **Finds Related Messages**: Can query messages with `metadata->>'analysis'->>'id' = 'analysis-uuid'`

3. **Loads Additional Details**: Can fetch full analysis details from `analysis_results` when needed

4. **Tracks References**: Citations and references from the paper are accessible via the `citations` table



## Usage Guidelines

### Creating a New Chat Session

```typescript
async function createChatSession(userId: string, mode: string = 'general', context: any = {}) {

const { data, error } = await supabase

.from('chat_sessions')

.insert({

user_id: userId,

mode: mode,

context: context

})

.select()

.single();



if (error) throw error;

return data;

}
```

### Adding a Message

```typescript
async function addMessage(sessionId: string, role: string, content: string, metadata: any = {}) {

// Get the next sequence number

const { data: maxSeq } = await supabase

.from('chat_messages')

.select('sequence_number')

.eq('session_id', sessionId)

.order('sequence_number', { ascending: false })

.limit(1)

.single();



const nextSeq = maxSeq ? maxSeq.sequence_number + 1 : 0;



// Insert the new message

const { data, error } = await supabase

.from('chat_messages')

.insert({

session_id: sessionId,

role: role,

content: content,

metadata: metadata,

sequence_number: nextSeq

})

.select()

.single();



if (error) throw error;

return data;

}
```

### Retrieving a Conversation

```typescript
async function getConversation(sessionId: string) {

// Get the session

const { data: session, error: sessionError } = await supabase

.from('chat_sessions')

.select('*')

.eq('id', sessionId)

.single();



if (sessionError) throw sessionError;



// Get all messages in order

const { data: messages, error: messagesError } = await supabase

.from('chat_messages')

.select('*')

.eq('session_id', sessionId)

.order('sequence_number', { ascending: true });



if (messagesError) throw messagesError;



return {

session,

messages

};

}
```

### Adding an Analysis to a Chat Session

```typescript
async function addAnalysisToChat(sessionId: string, analysisResultId: string) {

// Get the analysis result

const { data: analysis, error: analysisError } = await supabase

.from('analysis_results')

.select('content, analysis_type, paper_id, papers:paper_id(title)')

.eq('id', analysisResultId)

.single();



if (analysisError || !analysis) throw analysisError;



// Format the analysis type for display

const analysisType = analysis.analysis_type

.replace(/_/g, ' ')

.split(' ')

.map((word: string) => word.charAt(0).toUpperCase() + word.slice(1))

.join(' ');



// Create introduction message

await addMessage(

sessionId,

'assistant',

`I've completed the ${analysisType} of "${analysis.papers.title}". Ask me specific questions about it.`,

{

analysis: {

id: analysisResultId,

type: analysis.analysis_type,

paper_id: analysis.paper_id

},

message_type: 'analysis'

}

);



// Create content message with the actual analysis

await addMessage(

sessionId,

'assistant',

analysis.content,

{

analysis: {

id: analysisResultId,

type: analysis.analysis_type,

paper_id: analysis.paper_id

},

message_type: 'analysis_content'

}

);

}
```

## Session Context Usage

The `context` field in `chat_sessions` should now be used only for session-specific metadata such as:

- **Paper references**: `{ paperIds: ['uuid1', 'uuid2'] }`

- **Session settings**: `{ temperature: 0.7, model: 'claude-3-opus-20240229' }`

- **UI state**: `{ visualizationState: { ... } }`

- **Feature flags**: `{ enableCitations: true, enableFormatting: true }`

It should NOT store messages, which now go into the `chat_messages` table.

## Message Types

The `message_type` field enables different types of content within a conversation:

- `'message'` - Standard chat message

- `'analysis'` - Analysis results

- `'system'` - System notifications

- `'error'` - Error messages

- `'annotation'` - User annotations/notes

## Integrations with Other Systems

### Integration with Citation System

For messages that reference citations:

1. Citations from papers are stored in the `citations` table (currently has 15 records)

2. When creating messages that reference citations, add citation IDs to the `citations` JSONB array

3. When displaying messages, you can fetch related citation details to provide context

### Integration with Credit System

Our credit system implementation works correctly:

1. The `mode_operation_mapping` table (3 records) maps chat modes to operation types

2. The `operation_costs` table (20 records) defines credit costs for each operation type

3. Before creating a chat or sending a message, the system verifies credit availability

Credit costs for chat operations:

- Paper chat: 4 credits (standard) / 7 credits (premium)

- Cross-reference chat: 4 credits (standard) / 7 credits (premium), plus additional for more papers

- Research explorer chat: 4 credits (standard) / 6 credits (premium)

### Integration with Visualization System

For cross-reference chat visualization:

1. Previously, visualization state was stored in `chat_sessions.context.visualizationState`

2. With our new approach, you can either:
- Continue using the context field for visualization state (simpler for small states)

- Implement usage of the `visualization_states` table for more complex visualizations



## Features Enabled

This structured approach enables:

1. **Message Editing**: Track edits and provide edit history

2. **Message Reactions**: Allow users to react to specific messages

3. **Message Threading**: Implement threaded replies to specific messages

4. **Message Search**: Implement searching within and across conversations

5. **Message Analytics**: Analyze message patterns, lengths, and response times

6. **Message References**: Allow messages to reference other messages

## Potential Future Enhancements

Building on this foundation, potential future enhancements include:

1. **Message Archiving**: Move older messages to an archive table for performance

2. **Message Attachments**: Add support for file attachments to messages

3. **Message Templates**: Create reusable message templates

4. **Message Versioning**: Track all versions of edited messages

5. **Advanced Message Types**: Implement specialized rendering for different message types

## Conclusion

By implementing this new message storage strategy, Answer42 gains a more structured, performant, and feature-rich chat system. The normalized approach provides better separation of concerns, makes message operations more explicit, and enables a range of new features while fixing the existing issues with analysis integration.
