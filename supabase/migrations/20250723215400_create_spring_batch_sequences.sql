-- Create missing Spring Batch sequences for job execution
-- These sequences are required by Spring Batch for generating unique IDs

-- Create the main batch job sequence that Spring Batch expects
CREATE SEQUENCE IF NOT EXISTS answer42.batch_job_seq 
    START WITH 1 
    INCREMENT BY 1;

-- Create additional Spring Batch sequences for completeness
CREATE SEQUENCE IF NOT EXISTS answer42.batch_job_execution_seq 
    START WITH 1 
    INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS answer42.batch_step_execution_seq 
    START WITH 1 
    INCREMENT BY 1;

-- Grant necessary permissions to ensure sequences can be used
GRANT USAGE, SELECT ON SEQUENCE answer42.batch_job_seq TO postgres;
GRANT USAGE, SELECT ON SEQUENCE answer42.batch_job_execution_seq TO postgres;
GRANT USAGE, SELECT ON SEQUENCE answer42.batch_step_execution_seq TO postgres;

-- Add comment for documentation
COMMENT ON SEQUENCE answer42.batch_job_seq IS 'Sequence for Spring Batch job ID generation';
COMMENT ON SEQUENCE answer42.batch_job_execution_seq IS 'Sequence for Spring Batch job execution ID generation';
COMMENT ON SEQUENCE answer42.batch_step_execution_seq IS 'Sequence for Spring Batch step execution ID generation';
