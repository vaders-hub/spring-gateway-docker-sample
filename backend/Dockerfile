# 1·3단계: 빌드 이미지에서 JAR를 만들고 실행 이미지에는 JRE와 산출물만 복사하는 다단계 빌드.
FROM gradle:9.7.1-jdk25 AS build
WORKDIR /app
# 소스보다 의존성 정의를 먼저 복사해 소스만 바뀔 때 의존성 다운로드 layer를 재사용한다.
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon
COPY src ./src
# bootJar는 실행 JAR를 만들지만 test 작업 통과를 의미하지는 않는다.
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin app

COPY --chown=10001:10001 --from=build /app/build/libs/app.jar app.jar
# 앱은 root가 아닌 고정 UID로 실행한다. wget은 Compose healthcheck에 사용한다.
USER 10001:10001
# EXPOSE는 포트 문서화이며 호스트 공개는 Compose ports 또는 Kubernetes Service가 담당한다.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
