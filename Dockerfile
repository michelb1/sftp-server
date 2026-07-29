FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN mkdir -p /app/home/

COPY --from=build /app/target/*jar-with-dependencies.jar app.jar

RUN addgroup -g 1001 -S sftpgroup && adduser -u 1001 -S sftpuser -G sftpgroup
RUN chown -R sftpuser:sftpgroup /app/home/

USER 1001

ENTRYPOINT ["java", "-jar", "app.jar"]
