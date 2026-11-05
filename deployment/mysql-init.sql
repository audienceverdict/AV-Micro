CREATE DATABASE IF NOT EXISTS av_identity;
CREATE DATABASE IF NOT EXISTS av_business;
CREATE USER IF NOT EXISTS 'av_identity'@'%' IDENTIFIED BY 'local-identity-password';
CREATE USER IF NOT EXISTS 'av_business'@'%' IDENTIFIED BY 'local-business-password';
GRANT ALL PRIVILEGES ON av_identity.* TO 'av_identity'@'%';
GRANT ALL PRIVILEGES ON av_business.* TO 'av_business'@'%';
