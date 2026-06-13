CREATE DATABASE IF NOT EXISTS agent_test
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE agent_test;

CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  title VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  message_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_updated_at (user_id, updated_at),
  KEY idx_status_updated_at (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS posts (
  id BIGINT NOT NULL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  round_num INT NOT NULL,
  send_from VARCHAR(128) NOT NULL,
  send_to VARCHAR(128) NOT NULL,
  message MEDIUMTEXT,
  attachments MEDIUMTEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_conversation_round_created_at (conversation_id, round_num, created_at),
  KEY idx_conversation_created_at (conversation_id, created_at),
  CONSTRAINT fk_posts_conversation_id
    FOREIGN KEY (conversation_id) REFERENCES conversations (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_summaries (
  id BIGINT NOT NULL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  summary TEXT NOT NULL,
  covered_round_num INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conversation_id (conversation_id),
  KEY idx_updated_at (updated_at),
  CONSTRAINT fk_conversation_summaries_conversation_id
    FOREIGN KEY (conversation_id) REFERENCES conversations (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment_documents (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  document_code VARCHAR(64) NOT NULL,
  document_type VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
  payer VARCHAR(255),
  payee VARCHAR(255),
  pay_time DATETIME,
  has_receipt TINYINT NOT NULL DEFAULT 0,
  receipt_file_name VARCHAR(255),
  receipt_file_url VARCHAR(1024),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_document_code (document_code),
  KEY idx_status_pay_time (status, pay_time),
  KEY idx_document_type (document_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO payment_documents (
  document_code, document_type, status, amount, payer, payee,
  pay_time, has_receipt, receipt_file_name, receipt_file_url
)
SELECT 'EC2025', 'PAYMENT', 'PAID', 10000.00, '安克创新科技股份有限公司', '供应商A',
       '2026-05-20 15:30:00', 1, 'EC2025_银行回单.pdf', 'https://example.com/receipts/EC2025.pdf'
WHERE NOT EXISTS (SELECT 1 FROM payment_documents WHERE document_code = 'EC2025');

INSERT INTO payment_documents (
  document_code, document_type, status, amount, payer, payee,
  pay_time, has_receipt, receipt_file_name, receipt_file_url
)
SELECT 'LA2025', 'PAYMENT', 'UNPAID', 5200.00, '安克创新科技股份有限公司', '供应商B',
       NULL, 0, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM payment_documents WHERE document_code = 'LA2025');

INSERT INTO payment_documents (
  document_code, document_type, status, amount, payer, payee,
  pay_time, has_receipt, receipt_file_name, receipt_file_url
)
SELECT 'TR2025', 'TRANSFER', 'PAID', 8800.00, '安克创新科技股份有限公司', '供应商C',
       '2026-05-22 10:15:00', 1, 'TR2025_银行回单.pdf', 'https://example.com/receipts/TR2025.pdf'
WHERE NOT EXISTS (SELECT 1 FROM payment_documents WHERE document_code = 'TR2025');
