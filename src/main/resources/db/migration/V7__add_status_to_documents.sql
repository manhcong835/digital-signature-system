-- Add status column to documents table
ALTER TABLE documents ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED';
CREATE INDEX idx_documents_status ON documents(status);
