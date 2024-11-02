# 1. 빌드 단계
FROM gradle:7.2-jdk11 AS build

# 작업 디렉토리 설정
WORKDIR /app

# 소스 코드 복사
COPY . .

# Gradle wrapper에 실행 권한 부여
RUN chmod +x gradlew

# Gradle 빌드
RUN ./gradlew clean build -x test

# 2. 실행 단계
FROM openjdk:11-jre-slim

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 jar 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 환경 변수 설정
ENV MYSQL_DATABASE=${MYSQLDATABASE}
ENV MYSQL_HOST=${MYSQLHOST}
ENV MYSQL_PORT=${MYSQLPORT}
ENV MYSQL_USER=${MYSQLUSER}
ENV MYSQL_PASSWORD=${MYSQLPASSWORD}
ENV TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
ENV ADMIN_PASSWORD=${ADMIN_PASSWORD}

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
