# 1. ビルド環境としてMavenとJava 21を使用
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# 2. 実行環境として軽量なJava 21イメージを使用
FROM eclipse-temurin:21-jre-jammy
COPY --from=build /target/*.jar app.jar

# 音声ファイルのアップロード先フォルダーを作成
RUN mkdir -p uploaded-audio

# メモリ制限（512MB）を超えないように最適化して起動
ENTRYPOINT ["java", "-Xmx300m", "-Xss512k", "-Dspring.profiles.active=prod", "-jar", "/app.jar"]
