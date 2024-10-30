# 1. 기본 이미지로 OpenJDK 사용
FROM openjdk:17-jdk-slim

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. Maven 또는 Gradle로 빌드된 JAR 파일 복사
COPY build/libs/*.jar app.jar

# 4. 환경 변수 설정 (Railway에서 제공하는 환경 변수)
ENV SPRING_PROFILES_ACTIVE=prod
ENV DATABASE_URL=${DATABASE_URL}
ENV DATABASE_USERNAME=${DATABASE_USERNAME}
ENV DATABASE_PASSWORD=${DATABASE_PASSWORD}
ENV TELEGRAM_BOT_USERNAME=${TELEGRAM_BOT_USERNAME}
ENV TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
ENV ADMIN_PASSWORD=${ADMIN_PASSWORD}
ENV PORT=${PORT}

# 5. 컨테이너가 시작될 때 실행할 명령어 설정
ENTRYPOINT ["java", "-jar", "app.jar"]
