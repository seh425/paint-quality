FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY server/pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY server/src ./src
RUN mvn -B -q clean package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
