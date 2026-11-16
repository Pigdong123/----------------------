FROM eclipse-temurin:17-jre-jammy

# 转换引擎（开源算法）：构建镜像时按需安装，取消注释即可启用
# 音频引擎：ffmpeg
RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg \
        imagemagick \
        libreoffice \
        fonts-noto-cjk \
        fonts-dejavu \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/campus-format-server.jar app.jar

ENV CAMPUS_STORAGE_LOCAL_PATH=/data/campus-format/files
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
