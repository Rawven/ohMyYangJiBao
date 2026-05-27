CREATE TABLE IF NOT EXISTS fund (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    nav DECIMAL(10, 4) NOT NULL,
    nav_date DATE NOT NULL,
    day_increase DECIMAL(6, 4) DEFAULT 0,
    establish_date DATE,
    company VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS nav_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    nav DECIMAL(10, 4) NOT NULL,
    date DATE NOT NULL,
    UNIQUE (fund_code, date)
);

CREATE TABLE IF NOT EXISTS holding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    fund_name VARCHAR(100) NOT NULL,
    shares DECIMAL(14, 2) NOT NULL,
    cost_nav DECIMAL(10, 4) NOT NULL,
    buy_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS fund_holding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    stock_code VARCHAR(20),
    hold_ratio DECIMAL(6, 2),
    change_ratio DECIMAL(6, 2),
    report_date DATE,
    UNIQUE (fund_code, stock_name)
);

CREATE TABLE IF NOT EXISTS transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    type VARCHAR(4) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    nav DECIMAL(10, 4) NOT NULL,
    shares DECIMAL(14, 2) NOT NULL,
    transaction_date DATETIME NOT NULL,
    note VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls JSON,
    render_blocks JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);
