# 1·3단계: 빌드 이미지에서 JAR를 만들고 실행 이미지에는 JRE와 산출물만 복사하는 다단계 빌드.
FROM gradle:9.7.1-jdk25 AS build
WORKDIR /app
# 루트 wrapper/catalog가 서비스와 공유 모듈을 함께 빌드한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gateway/build.gradle ./gateway/build.gradle
COPY backend/build.gradle ./backend/build.gradle
COPY libs/platform-core/build.gradle ./libs/platform-core/build.gradle
RUN bash ./gradlew :gateway:dependencies --no-daemon
COPY libs/platform-core/src ./libs/platform-core/src
COPY gateway/src ./gateway/src
RUN bash ./gradlew :gateway:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin app
COPY --chown=10001:10001 --from=build /app/gateway/build/libs/app.jar app.jar
# 앱은 root가 아닌 고정 UID로 실행한다. wget은 Compose healthcheck에 사용한다.
USER 10001:10001
# EXPOSE는 포트 문서화이며 호스트 공개는 Compose ports 또는 Kubernetes Service가 담당한다.
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
