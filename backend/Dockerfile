# 后端镜像：Maven 构建 + JRE 运行
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/student-growth-platform.jar app.jar
# 生产镜像需挂载中文字体（TTF）并指向该路径；不把字体打进仓库
ENV EXPORT_FONT_PATH=/fonts/simhei.ttf
ENV FILE_DIR=/data/files
ENV EXPORT_DIR=/data/export
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
