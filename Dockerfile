FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Dependencias en una capa propia: solo se rebajan si cambia el pom
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# Los tests necesitan la base: corren en local y en el CI, no acá
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home catalog
USER catalog

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
