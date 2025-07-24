# Answer42 Production Deployment Guide

## Environment Configuration

This project supports environment-based configuration for seamless deployment across different environments.

### Files Overview

- `.env` - Local development environment (Supabase local instance)
- `.env.prod` - Production environment template (Supabase cloud)
- `application.properties` - Spring Boot configuration with environment variable support

## Production Deployment Steps

### 1. Configure Production Environment

Copy `.env.prod` to your production server and update the placeholder values:

```bash
# Replace these placeholders in .env.prod:
YOUR_PROJECT_REF          # Your Supabase project reference ID
YOUR_DB_PASSWORD          # Your production database password
your-production-*-here    # All API keys and secrets
```

### 2. Get Your Supabase Cloud Details

From your Supabase dashboard:

1. **Project Reference**: Found in your project URL: `https://YOUR_PROJECT_REF.supabase.co`
2. **Database Password**: Set in Project Settings → Database
3. **API Keys**: Found in Project Settings → API
   - `SUPABASE_ANON_KEY` - Anonymous key
   - `SUPABASE_SERVICE_ROLE_KEY` - Service role key

### 3. Database Connection

The production configuration uses:
```
postgresql://postgres:YOUR_DB_PASSWORD@YOUR_PROJECT_REF.supabase.co:5432/postgres?sslmode=require
```

Key differences from local:
- **Host**: `YOUR_PROJECT_REF.supabase.co` instead of `localhost`
- **Port**: `5432` instead of `54322`
- **SSL**: `sslmode=require` instead of `sslmode=disable`

### 4. Production Optimizations

The `.env.prod` file includes production-optimized settings:

- **Connection Pool**: Larger pool sizes for better concurrency
- **Rate Limits**: Higher limits for production traffic
- **Caching**: Longer cache durations for better performance
- **Logging**: Reduced verbosity for production
- **Security**: Production-appropriate security settings

### 5. Deployment Options

#### Option A: Environment File
```bash
# Copy and configure the production environment file
cp .env.prod .env
# Edit .env with your actual values
# Deploy your application
```

#### Option B: Environment Variables
Set the environment variables directly in your deployment platform:
```bash
export DATABASE_URL="postgresql://postgres:YOUR_DB_PASSWORD@YOUR_PROJECT_REF.supabase.co:5432/postgres?sslmode=require"
export DATABASE_USERNAME="postgres"
export DATABASE_PASSWORD="YOUR_DB_PASSWORD"
# ... set all other variables
```

#### Option C: Docker Environment
```dockerfile
# In your Dockerfile or docker-compose.yml
ENV DATABASE_URL=postgresql://postgres:YOUR_DB_PASSWORD@YOUR_PROJECT_REF.supabase.co:5432/postgres?sslmode=require
ENV DATABASE_USERNAME=postgres
ENV DATABASE_PASSWORD=YOUR_DB_PASSWORD
# ... other environment variables
```

### 6. Database Migrations

Apply migrations to your production database:

```bash
# Using Supabase CLI
supabase db push --linked

# Or manually apply migrations from supabase/migrations/
```

### 7. Verification

After deployment, verify:

1. **Database Connection**: Check application logs for successful connection
2. **API Keys**: Verify AI services are working
3. **File Uploads**: Test file upload functionality
4. **Performance**: Monitor connection pool usage and response times

## Environment Variable Reference

### Required for Production

| Variable | Description | Example |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL connection string | `postgresql://postgres:pass@project.supabase.co:5432/postgres?sslmode=require` |
| `DATABASE_USERNAME` | Database username | `postgres` |
| `DATABASE_PASSWORD` | Database password | `your-secure-password` |
| `OPENAI_API_KEY` | OpenAI API key | `sk-proj-...` |
| `ANTHROPIC_API_KEY` | Anthropic API key | `sk-ant-api03-...` |
| `JWT_SECRET` | JWT signing secret | `base64-encoded-secret` |

### Optional Production Optimizations

| Variable | Default | Production Recommended |
|----------|---------|----------------------|
| `HIKARI_MAXIMUM_POOL_SIZE` | 10 | 20 |
| `HIKARI_MINIMUM_IDLE` | 5 | 10 |
| `JPA_SHOW_SQL` | true | false |
| `HIBERNATE_DDL_AUTO` | update | validate |
| `MAX_FILE_SIZE` | 50MB | 100MB |

## Troubleshooting

### Common Issues

1. **Connection Refused**: Check if your IP is allowed in Supabase network restrictions
2. **SSL Errors**: Ensure `sslmode=require` is set for cloud connections
3. **Pool Exhaustion**: Increase `HIKARI_MAXIMUM_POOL_SIZE` if needed
4. **Migration Errors**: Ensure all migrations are applied to production database

### Health Checks

The application exposes health endpoints:
- `/actuator/health` - Application health status
- `/actuator/metrics` - Application metrics

## Security Considerations

1. **Never commit** `.env.prod` with real credentials to version control
2. **Use strong passwords** for database and JWT secrets
3. **Rotate API keys** regularly
4. **Enable SSL** at the load balancer/reverse proxy level
5. **Monitor logs** for suspicious activity

## Performance Monitoring

Monitor these metrics in production:
- Database connection pool usage
- Response times
- Memory usage
- File upload performance
- AI API response times
