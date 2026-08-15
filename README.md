# Lightweight SFTP Server (based on Apache SSHD)

A lightweight SFTP server built on top of Apache MINA SSHD. This project is specifically designed with modern cloud-native infrastructures and strict container security principles in mind.


## Config (Environment)

#Required:

SFTP_USERS='user1:password(bcrypt):home\_dir1:subfolder1,subfolder2;user2:password(bcrypt):home\_dir2'

#Optional:

SFTP_PORT (Default 2222)

SFTP_HOST_KEY_PATH (Default -> use a generated key - currently only works with RSA keys)

SFTP_HOST_KEY_PW (Default -> no passwordcheck)

SFTP_CREATE_FOLDER_PERMISSION=true/false (Default false)

SFTP_DELETE_FOLDER_PERMISSION=true/false (Default false)


## Docker

docker build -t sftpserver:latest .

docker run -e SFTP\_USERS='michelb1:$2a$12$AIO5VkkYfTGBoByYg4rSHeQy0rUOWDQ1zUnIBhjEMkcWlhMi069.u:/app/home/michelb1:upload' 
\-p 2222:2222 sftpserver:latest


**Important**: Place the home folder and hostkey under the /app/ directory. Because the container runs as a non-root user, creating files or directories outside this path may result in permission errors.



## Licenses & Third-Party Software

This project utilizes the following third-party open-source libraries:

### Apache License 2.0
* [Apache MINA SSHD](https://mina.apache.org/sshd-project)
* [Spring Framework & Spring Security](https://spring.io/projects/spring-framework)
* [Apache Commons Logging](https://apache.org)

### MIT License
* [SLF4J (Simple Logging Facade for Java)](https://slf4j.org)
