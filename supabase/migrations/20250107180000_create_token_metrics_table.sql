-- Create token_metrics table for comprehensive AI usage tracking
-- Migration: 20250107180000_create_token_metrics_table.sql

BEGIN;

-- Create token_metrics table
CREATE TABLE IF NOT EXISTS answer42.token_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES answer42.users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    agent_type VARCHAR(100) NOT NULL,
    task_id VARCHAR(100),
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(10,6) DEFAULT 0.000000,
    processing_time_ms BIGINT,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    session_id VARCHAR(100),
    paper_id UUID REFERENCES answer42.papers(id) ON DELETE SET NULL,
    model_name VARCHAR(100),
    temperature DOUBLE PRECISION,
    max_tokens INTEGER,
    success BOOLEAN NOT NULL DEFAULT true,
    error_message TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for optimal query performance
CREATE INDEX IF NOT EXISTS idx_token_metrics_user_id ON answer42.token_metrics(user_id);
CREATE INDEX IF NOT EXISTS idx_token_metrics_provider ON answer42.token_metrics(provider);
CREATE INDEX IF NOT EXISTS idx_token_metrics_agent_type ON answer42.token_metrics(agent_type);
CREATE INDEX IF NOT EXISTS idx_token_metrics_timestamp ON answer42.token_metrics(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_token_metrics_task_id ON answer42.token_metrics(task_id);
CREATE INDEX IF NOT EXISTS idx_token_metrics_paper_id ON answer42.token_metrics(paper_id);
CREATE INDEX IF NOT EXISTS idx_token_metrics_session_id ON answer42.token_metrics(session_id);
CREATE INDEX IF NOT EXISTS idx_token_metrics_success ON answer42.token_metrics(success);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_token_metrics_user_timestamp ON answer42.token_metrics(user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_token_metrics_provider_agent ON answer42.token_metrics(provider, agent_type);
CREATE INDEX IF NOT EXISTS idx_token_metrics_user_provider ON answer42.token_metrics(user_id, provider);
CREATE INDEX IF NOT EXISTS idx_token_metrics_timestamp_success ON answer42.token_metrics(timestamp DESC, success);

-- JSONB index for metadata queries
CREATE INDEX IF NOT EXISTS idx_token_metrics_metadata_gin ON answer42.token_metrics USING gin (metadata);

-- Trigger for updating updated_at timestamp
CREATE OR REPLACE FUNCTION answer42.update_token_metrics_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER token_metrics_updated_at
    BEFORE UPDATE ON answer42.token_metrics
    FOR EACH ROW
    EXECUTE FUNCTION answer42.update_token_metrics_updated_at();

-- Trigger to automatically calculate total_tokens on insert/update
CREATE OR REPLACE FUNCTION answer42.calculate_total_tokens()
RETURNS TRIGGER AS $$
BEGIN
    -- Calculate total tokens if not provided
    IF NEW.total_tokens = 0 OR NEW.total_tokens IS NULL THEN
        NEW.total_tokens = COALESCE(NEW.input_tokens, 0) + COALESCE(NEW.output_tokens, 0);
    END IF;
    
    -- Ensure cost is never null
    IF NEW.estimated_cost IS NULL THEN
        NEW.estimated_cost = 0.000000;
    END IF;
    
    -- Ensure success is never null
    IF NEW.success IS NULL THEN
        NEW.success = true;
    END IF;
    
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER token_metrics_calculate_totals
    BEFORE INSERT OR UPDATE ON answer42.token_metrics
    FOR EACH ROW
    EXECUTE FUNCTION answer42.calculate_total_tokens();

-- Create view for daily usage statistics
CREATE OR REPLACE VIEW answer42.daily_token_usage AS
SELECT 
    DATE(timestamp) as usage_date,
    provider,
    agent_type,
    COUNT(*) as request_count,
    SUM(input_tokens) as total_input_tokens,
    SUM(output_tokens) as total_output_tokens,
    SUM(total_tokens) as total_tokens,
    SUM(estimated_cost) as total_cost,
    AVG(processing_time_ms) as avg_processing_time,
    COUNT(CASE WHEN success = false THEN 1 END) as failure_count,
    ROUND((COUNT(CASE WHEN success = true THEN 1 END) * 100.0 / COUNT(*)), 2) as success_rate
FROM answer42.token_metrics
GROUP BY DATE(timestamp), provider, agent_type
ORDER BY usage_date DESC, total_tokens DESC;

-- Create view for user statistics
CREATE OR REPLACE VIEW answer42.user_token_statistics AS
SELECT 
    tm.user_id,
    u.email as user_email,
    COUNT(*) as total_requests,
    SUM(tm.input_tokens) as total_input_tokens,
    SUM(tm.output_tokens) as total_output_tokens,
    SUM(tm.total_tokens) as total_tokens,
    SUM(tm.estimated_cost) as total_cost,
    AVG(tm.processing_time_ms) as avg_processing_time,
    MAX(tm.timestamp) as last_activity,
    COUNT(DISTINCT tm.provider) as providers_used,
    COUNT(DISTINCT tm.agent_type) as agents_used,
    COUNT(CASE WHEN tm.success = false THEN 1 END) as failure_count,
    ROUND((COUNT(CASE WHEN tm.success = true THEN 1 END) * 100.0 / COUNT(*)), 2) as success_rate
FROM answer42.token_metrics tm
JOIN answer42.users u ON tm.user_id = u.id
GROUP BY tm.user_id, u.email
ORDER BY total_tokens DESC;

-- Create function for cleaning up old token metrics (data retention)
CREATE OR REPLACE FUNCTION answer42.cleanup_old_token_metrics(retention_days INTEGER DEFAULT 365)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_date TIMESTAMP;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL;
    
    DELETE FROM answer42.token_metrics 
    WHERE timestamp < cutoff_date;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ language 'plpgsql';

-- Add comments for documentation
COMMENT ON TABLE answer42.token_metrics IS 'Comprehensive tracking of AI token usage, costs, and performance metrics';
COMMENT ON COLUMN answer42.token_metrics.provider IS 'AI provider used (OPENAI, ANTHROPIC, PERPLEXITY, OLLAMA)';
COMMENT ON COLUMN answer42.token_metrics.agent_type IS 'Type of agent that processed the request';
COMMENT ON COLUMN answer42.token_metrics.task_id IS 'Unique identifier for the task being processed';
COMMENT ON COLUMN answer42.token_metrics.input_tokens IS 'Number of tokens in the input/prompt';
COMMENT ON COLUMN answer42.token_metrics.output_tokens IS 'Number of tokens in the response';
COMMENT ON COLUMN answer42.token_metrics.total_tokens IS 'Total tokens used (input + output)';
COMMENT ON COLUMN answer42.token_metrics.estimated_cost IS 'Estimated cost in USD for the operation';
COMMENT ON COLUMN answer42.token_metrics.processing_time_ms IS 'Time taken to process the request in milliseconds';
COMMENT ON COLUMN answer42.token_metrics.metadata IS 'Additional metadata as JSONB (model parameters, etc.)';
COMMENT ON COLUMN answer42.token_metrics.session_id IS 'Session identifier for grouping related requests';
COMMENT ON COLUMN answer42.token_metrics.success IS 'Whether the operation completed successfully';

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON answer42.token_metrics TO service_role;
GRANT SELECT ON answer42.daily_token_usage TO service_role;
GRANT SELECT ON answer42.user_token_statistics TO service_role;
GRANT EXECUTE ON FUNCTION answer42.cleanup_old_token_metrics TO service_role;

-- Insert some sample data for testing (optional)
-- INSERT INTO answer42.token_metrics (user_id, provider, agent_type, task_id, input_tokens, output_tokens, estimated_cost)
-- SELECT 
--     u.id,
--     'OPENAI',
--     'PAPER_PROCESSOR',
--     'sample_task_' || generate_random_uuid(),
--     floor(random() * 1000 + 100)::integer,
--     floor(random() * 500 + 50)::integer,
--     (random() * 0.05)::decimal(10,6)
-- FROM answer42.users u
-- LIMIT 5;

COMMIT;
