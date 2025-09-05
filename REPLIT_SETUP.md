# Answer42 Replit Configuration Guide

## Overview

The `.replit` file has been configured with environment variables optimized for Replit's resource constraints and deployment environment.

## Key Configuration Changes

### Database Settings
- **Connection**: Standard PostgreSQL JDBC connection
- **Pool Size**: Reduced to 5 max connections (vs 10-20 for other environments)
- **Timeouts**: Adjusted for Replit's network characteristics

### Performance Optimizations
- **Thread Pool**: Smaller core size (2) and max size (10) for Replit's CPU limits
- **File Uploads**: Reduced to 25MB max (vs 50MB+ for other environments)
- **Memory**: Optimized connection pool settings for Replit's memory constraints

### Rate Limiting
- **Conservative Limits**: Reduced API call rates to prevent hitting Replit's network limits
- **Crossref**: 20 requests/second (vs 45+ elsewhere)
- **Semantic Scholar**: 50 requests/minute (vs 100+ elsewhere)
- **Perplexity**: 5 requests/minute (vs 10+ elsewhere)

### Logging & Monitoring
- **Reduced Verbosity**: INFO level logging to minimize console output
- **SQL Logging**: Disabled to reduce noise
- **Metrics**: Extended interval (10 minutes) to reduce overhead

## Environment Variables in .replit

The following variables are set directly in the `.replit` file for basic functionality:

```toml
DATABASE_URL = "jdbc:postgresql://localhost:5432/answer42?sslmode=disable"
DATABASE_USERNAME = "postgres"
DATABASE_PASSWORD = "postgres"
HIKARI_MAXIMUM_POOL_SIZE = "5"
HIKARI_MINIMUM_IDLE = "2"
# ... and many more
```

## Sensitive Variables (Use Replit Secrets)

For production deployment on Replit, set these in **Replit Secrets** instead of the `.replit` file:

### Required API Keys
- `OPENAI_API_KEY` - Your OpenAI API key
- `ANTHROPIC_API_KEY` - Your Anthropic API key  
- `PERPLEXITY_API_KEY` - Your Perplexity API key
- `JWT_SECRET` - Secure JWT signing secret

### Optional API Keys
- `SEMANTIC_SCHOLAR_API_KEY` - For higher rate limits
- `STRIPE_PUBLISHABLE_KEY` - For payment features
- `STRIPE_SECRET_KEY` - For payment features
- `STRIPE_WEBHOOK_SECRET` - For payment webhooks

## Setting Up Replit Secrets

1. Open your Replit project
2. Go to the **Secrets** tab (lock icon in sidebar)
3. Add each sensitive environment variable as a secret
4. The application will automatically use secrets over `.replit` env vars

## Database Setup on Replit

### Option 1: PostgreSQL Repl (Recommended)
1. Create a new PostgreSQL Repl
2. Update `DATABASE_URL` in secrets to point to your PostgreSQL Repl
3. Run Supabase migrations manually or use the PostgreSQL console

### Option 2: External Database
1. Use Supabase cloud, Railway, or another PostgreSQL provider
2. Update `DATABASE_URL` in secrets with the external connection string
3. Ensure SSL settings match your provider's requirements

## Running on Replit

1. **Fork/Import** the repository to Replit
2. **Set Secrets** for all required API keys
3. **Configure Database** (see options above)
4. **Run** - The application will start automatically with `mvn spring-boot:run`

## Troubleshooting

### Common Issues
- **Out of Memory**: Reduce `HIKARI_MAXIMUM_POOL_SIZE` further
- **Connection Timeouts**: Increase `HIKARI_CONNECTION_TIMEOUT`
- **Rate Limiting**: Reduce discovery rate limits further
- **File Upload Errors**: Reduce `MAX_FILE_SIZE` if needed

### Resource Monitoring
- Monitor Replit's resource usage in the console
- Adjust thread pool and connection pool sizes as needed
- Use the `/actuator/health` endpoint to check application status

## Performance Notes

Replit has resource constraints compared to dedicated servers:
- **CPU**: Limited cores, adjust thread pools accordingly
- **Memory**: Limited RAM, optimize connection pools
- **Network**: Shared bandwidth, use conservative rate limits
- **Storage**: Limited disk space for file uploads

The configuration in `.replit` is optimized for these constraints while maintaining functionality.
