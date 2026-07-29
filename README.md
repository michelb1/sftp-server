# Lightweight SFTP Server (based on Apache SSHD)

A lightweight SFTP server built on top of Apache MINA SSHD. This project is specifically designed with modern cloud-native infrastructures and strict container security principles in mind.


## Config (Environment)

SFTP\_USERS='user1:password(bcrypt):home\_dir1:subfolder1,subfolder2;user2:password(bcrypt):home\_dir2'

SFTP\_PORT=2222 (Default)

SFTP\_CREATE\_FOLDER\_PERMISSION=true/false (Default false)

SFTP\_DELETE\_FOLDER\_PERMISSION=true/false (Default false)


## Docker

docker build -t sftpserver:latest .

docker run -e SFTP\_USERS='michelb1:$2a$12$k2v0Ck0rELz2fZvl37LabOHlCW.UxYQrFaL2nRQPOlWv1uhNz8CFK:/app/home/michelb1:upload' 
\-p 2223:2222 sftpserver:latest


**Important**: The home folder must be located under /app. Due to non-root container security policies, creating the directory in any other path will fail with permission errors. If you need persistent storage, ensure you mount your volume directly inside /app and configure the appropriate security context (fsGroup: 1001).



## Licenses & Third-Party Software

This project utilizes the following third-party open-source libraries:

### Apache License 2.0
* [Apache MINA SSHD](https://mina.apache.org/sshd-project)
* [Spring Framework & Spring Security](https://spring.io/projects/spring-framework)
* [Apache Commons Logging](https://apache.org)

### MIT License
* [SLF4J (Simple Logging Facade for Java)](https://slf4j.org)
