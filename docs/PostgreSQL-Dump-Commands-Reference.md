# PostgreSQL Dump Commands Reference

> Replace `supabase_db_answer42` with your actual Docker container name

## Basic Dumps

### Default dump (includes both schema and data)

```bash
docker exec -it supabase_db_answer42 pg_dump -U postgres -n answer42 postgres > /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

### With password environment variable

```bash
docker exec -it supabase_db_answer42 sh -c "PGPASSWORD='postgres' pg_dump -U postgres -n answer42 postgres" > /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

## Selective Dumps

### Data only (no schema/structure)

```bash
docker exec -it supabase_db_answer42 pg_dump -U postgres -n answer42 --data-only postgres > /home/shawn/projs/answer42/aug-9-2025-data.sql
```

### Schema only (no data)

```bash
docker exec -it supabase_db_answer42 pg_dump -U postgres -n answer42 --schema-only postgres > /home/shawn/projs/answer42/aug-9-2025-schema.sql
```

## Format Options

### With INSERT statements (more portable but slower)

```bash
docker exec -it supabase_db_answer42 pg_dump -U postgres -n answer42 --inserts postgres > /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

### With column names in INSERT statements (most portable)

```bash
docker exec -it supabase_db_answer42 pg_dump -U postgres -n answer42 --column-inserts postgres > /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

### Compressed custom format (smaller, faster, but needs pg_restore)

```bash
docker exec -it supabase_db_answer42 pg_dump -U postgres -n answer42 -Fc postgres > /home/shawn/projs/answer42/aug-9-2025-dump.dump
```

## Verbose and Debugging

### Verbose output (shows progress)

```bash
docker exec -it supabase_db_answer42 pg_dump -U postgres -n answer42 -v postgres > /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

## Alternative Approaches

### Using Docker run with PostgreSQL 17 image (no container needed)

```bash
docker run --rm --network host -e PGPASSWORD=postgres postgres:17 pg_dump -h 127.0.0.1 -p 54322 -U postgres -n answer42 postgres > /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

### Direct command if pg_dump version 17 is installed locally

```bash
export PGPASSWORD='postgres' && pg_dump -h 127.0.0.1 -p 54322 -U postgres -n answer42 postgres > /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

## Restore Commands

### Restore from SQL dump

```bash
docker exec -i supabase_db_answer42 psql -U postgres postgres < /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

### Restore from compressed dump

```bash
docker exec -i supabase_db_answer42 pg_restore -U postgres -d postgres /home/shawn/projs/answer42/aug-9-2025-dump.dump
```

## Verification Commands

### Check if dump contains data (look for COPY or INSERT statements)

```bash
grep -E "^COPY|^INSERT" /home/shawn/projs/answer42/aug-9-2025-dump.sql | head -20
```

### Check file size

```bash
ls -lh /home/shawn/projs/answer42/aug-9-2025-dump.sql
```

### Count tables in dump

```bash
grep "^CREATE TABLE" /home/shawn/projs/answer42/aug-9-2025-dump.sql | wc -l
```

## Useful Flags Reference

| Flag                   | Description                       |
| ---------------------- | --------------------------------- |
| `-n answer42`          | Dump only the 'answer42' schema   |
| `--data-only` (`-a`)   | Dump only data, no schema         |
| `--schema-only` (`-s`) | Dump only schema, no data         |
| `--inserts`            | Use INSERT instead of COPY        |
| `--column-inserts`     | Use INSERT with column names      |
| `-Fc`                  | Custom compressed format          |
| `-Ft`                  | Tar format                        |
| `-Fp`                  | Plain SQL (default)               |
| `-v`                   | Verbose mode                      |
| `--no-owner`           | Don't dump ownership              |
| `--no-privileges`      | Don't dump privileges             |
| `--if-exists`          | Add IF EXISTS to DROP statements  |
| `--clean`              | Add DROP statements before CREATE |

## Tips

- **Default behavior**: `pg_dump` includes both schema and data by default
- **COPY vs INSERT**: COPY statements are faster but less portable; INSERT statements are slower but work across different database systems
- **Compressed dumps**: Use `-Fc` for large databases to save space and time
- **Schema-specific**: The `-n answer42` flag dumps only the specified schema
- **Version matching**: Ensure pg_dump version matches or exceeds the PostgreSQL server version
