-- Synthetic schema + seed for the simplecache module.
-- Replaces the datacharmer/test_db image baked by simplecache/docker/dockerfile.
-- Small enough that Testcontainers + MySQL 8 cold start completes in seconds,
-- large enough that the cache vs. JDBC timing assertions still hold.

CREATE TABLE departments (
    dept_no   VARCHAR(4)   PRIMARY KEY,
    dept_name VARCHAR(40)  NOT NULL
);

CREATE TABLE employees (
    emp_no     BIGINT       PRIMARY KEY,
    birth_date DATE         NOT NULL,
    first_name VARCHAR(14)  NOT NULL,
    last_name  VARCHAR(16)  NOT NULL,
    gender     CHAR(1)      NOT NULL,
    hire_date  DATE         NOT NULL
);

CREATE TABLE dept_emp (
    emp_no     BIGINT      NOT NULL,
    dept_no    VARCHAR(4)  NOT NULL,
    PRIMARY KEY (emp_no, dept_no),
    FOREIGN KEY (emp_no)  REFERENCES employees (emp_no),
    FOREIGN KEY (dept_no) REFERENCES departments (dept_no)
);

INSERT INTO departments (dept_no, dept_name) VALUES
    ('d001', 'Marketing'),
    ('d002', 'Finance'),
    ('d003', 'Human Resources'),
    ('d004', 'Production'),
    ('d005', 'Development'),
    ('d006', 'Quality Management'),
    ('d007', 'Sales'),
    ('d008', 'Research'),
    ('d009', 'Customer Service');

-- Generate 5 000 employees + dept_emp rows via a recursive CTE so the seed
-- file remains small while populating enough data for the cache benchmark.
SET @@cte_max_recursion_depth = 10000;

INSERT INTO employees (emp_no, birth_date, first_name, last_name, gender, hire_date)
WITH RECURSIVE seq (n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 5000
)
SELECT
    10000 + n,
    DATE_ADD('1955-01-01', INTERVAL (n * 7) DAY),
    CONCAT('First', n),
    CONCAT('Last',  n),
    IF(MOD(n, 2) = 0, 'M', 'F'),
    DATE_ADD('1985-01-01', INTERVAL (n * 3) DAY)
FROM seq;

INSERT INTO dept_emp (emp_no, dept_no)
SELECT
    emp_no,
    CONCAT('d00', 1 + MOD(emp_no, 9))
FROM employees;
