# Answer42 Database Cleanup Scripts

This directory contains database cleanup scripts for different scenarios during development and maintenance of the Answer42 platform.

## Scripts Overview

### 1. `cleanup-database.sql` - Complete Data Cleanup
**Purpose**: Full cleanup of all papers, projects, and agent-populated data while preserving user accounts and system settings.

**What it removes**:
- All papers and paper content
- All projects and project-paper relationships
- All discovered papers and relationships
- All agent tasks and memory
- All AI analysis results and summaries
- All chat sessions and messages
- All study materials (flashcards, concept maps, etc.)
- Spring Batch job history
- Token usage metrics

**What it preserves**:
- User accounts and authentication
- Subscription plans and user subscriptions
- Credit balances and transaction history
- User settings and preferences
- System configuration (operation costs, etc.)

**Usage**:
```bash
psql -d postgres -f scripts/cleanup-database.sql
```

### 2. `cleanup-database-quick.sql` - Fast Development Reset
**Purpose**: Rapid cleanup for development environments using TRUNCATE for speed.

**Use when**: You need to quickly reset everything during development.

**Features**:
- Uses TRUNCATE for fast deletion
- Temporarily disables foreign key constraints
- Provides summary statistics
- Resets sequences

**Usage**:
```bash
psql -d postgres -f scripts/cleanup-database-quick.sql
```

### 3. `cleanup-agent-data.sql` - Agent Data Only Cleanup
**Purpose**: Clean only agent-populated data while preserving user-uploaded papers and projects.

**What it removes**:
- Discovered papers and relationships
- Analysis results and tasks
- Citation and metadata verifications
- AI summaries and processing results
- Agent memory and task history
- Token metrics
- Spring Batch job history
- Agent-populated fields in papers table (resets to raw uploaded state)

**What it preserves**:
- User-uploaded papers (returns to unprocessed state)
- Projects and project organization
- Chat history and user content
- All user data and settings

**Usage**:
```bash
psql -d postgres -f scripts/cleanup-agent-data.sql
```

**Use case**: Testing agent processing on existing papers without re-uploading them.

## Database Information

**Answer42 Database Details**:
- **Default Database Name**: `postgres` (Supabase default)
- **Schema**: `answer42` 
- **Total Tables**: 47 tables in answer42 schema
- **Connection**: Uses Supabase PostgreSQL instance

**Alternative Database Names**:
If using a different database name, replace `postgres` in the usage commands with your database name:
```bash
psql -d your_database_name -f scripts/cleanup-database.sql
```

## Safety Features

All scripts include:
- **Transaction safety**: Wrapped in BEGIN/COMMIT for atomic operations
- **Detailed logging**: Shows before/after counts and deletion statistics
- **Verification queries**: Confirms cleanup was successful
- **Preserved data summary**: Shows what data remains after cleanup

## Usage Recommendations

### For Development
- Use `cleanup-database-quick.sql` for rapid resets during development
- Use `cleanup-agent-data.sql` when testing agent functionality

### For Testing
- Use `cleanup-database.sql` for comprehensive testing scenarios
- Use `cleanup-agent-data.sql` to test agent processing without re-uploading papers

### For Production
- **⚠️ WARNING**: These scripts are designed for development use
- Never run on production without proper backups
- Consider creating custom scripts with additional safety checks for production use

## Database Schema Understanding

The scripts handle the following table relationships:

```
papers (core)
├── paper_content (1:1)
├── paper_sections (1:many)
├── paper_tags (many:many)
├── paper_bookmarks (1:many)
├── citations (1:many)
├── metadata_verifications (1:many)
└── summaries (1:many)

projects (core)
└── project_papers (many:many with papers)

discovered_papers (agent-populated)
├── paper_relationships (many:many)
└── discovery_feedback (1:many)

discovery_results (agent-populated)
└── discovery_feedback (1:many)

tasks (agent-populated)
├── analysis_tasks (1:1 or 1:many)
└── analysis_results (1:many)

chat_sessions
└── chat_messages (1:many)

users (preserved)
├── user_settings (1:1)
├── credit_balances (1:1)
├── subscriptions (1:many)
└── various other user-related tables
```

## Monitoring Cleanup Results

Each script provides detailed output showing:
- Tables processed
- Row counts before and after
- Total records deleted
- Verification of key tables
- Summary of preserved data

Example output:
```
===============================================================================
ANSWER42 DATABASE CLEANUP COMPLETED
===============================================================================

Table                    | Before | After | Deleted
========================|========|=======|=========
papers                  |    150 |     0 |     150
discovered_papers       |    892 |     0 |     892
token_metrics          |   1204 |     0 |    1204
...

TOTAL RECORDS DELETED: 3,456
```

## Connection Examples

### Supabase Local Development
```bash
# Using Supabase CLI
supabase db reset --linked
psql -d postgres -f scripts/cleanup-database.sql

# Direct connection to local Supabase
psql -h localhost -p 54322 -U postgres -d postgres -f scripts/cleanup-database.sql
```

### Remote Supabase Instance
```bash
# Using connection string from Supabase dashboard
psql "postgresql://postgres:[password]@db.[project].supabase.co:5432/postgres" -f scripts/cleanup-database.sql
```

### Docker PostgreSQL
```bash
# If running PostgreSQL in Docker
docker exec -i your-postgres-container psql -U postgres -d postgres < scripts/cleanup-database.sql
```

## Troubleshooting

### Foreign Key Constraint Errors
If you encounter foreign key constraint errors:
1. Check that you're running the complete script (not partial)
2. Ensure no concurrent transactions are modifying the data
3. Use the quick cleanup script which temporarily disables constraints

### Permission Errors
Ensure your database user has:
- DELETE permissions on all answer42 schema tables
- TRUNCATE permissions (for quick cleanup)
- SEQUENCE permissions (for resetting sequences)
- CREATE permissions for temporary functions

### Incomplete Cleanup
If cleanup appears incomplete:
1. Check the verification queries at the end of the script output
2. Look for any error messages during script execution
3. Verify foreign key relationships weren't missed

### Connection Issues
Common connection problems and solutions:
- **SSL errors**: Use `sslmode=disable` for local development
- **Authentication**: Ensure correct username/password
- **Network**: Check if database is accessible from your location
- **Schema**: Verify the `answer42` schema exists

## Extending the Scripts

To add new tables to cleanup:
1. Identify foreign key dependencies
2. Add to appropriate cleanup phase
3. Include in statistics capture
4. Add to verification queries
5. Update this documentation

## Related Files

- `.clinerules` - Contains development guidelines
- `schema-dump/aug-9-2025-schema.sql` - Current database schema
- Supabase migration files in `supabase/migrations/`
- `README.md` - Main project documentation
- `CLAUDE.md` - Claude-specific development guidelines

## Best Practices

1. **Always backup before cleanup** in production environments
2. **Test scripts** in development environment first
3. **Monitor output** for any unexpected results
4. **Verify cleanup** using the provided verification queries
5. **Document custom modifications** if you extend the scripts
6. **Use appropriate script** for your specific use case

## Script Selection Guide

| Scenario | Recommended Script | Reason |
|----------|-------------------|--------|
| Complete development reset | `cleanup-database-quick.sql` | Fastest execution |
| Testing new features | `cleanup-database.sql` | Complete but safe cleanup |
| Agent development/testing | `cleanup-agent-data.sql` | Preserves test papers |
| Production maintenance | Custom script | Additional safety required |
| Emergency cleanup | `cleanup-database-quick.sql` | Speed is critical |

**Note**: Always use version control and proper backup procedures when working with database cleanup scripts.
