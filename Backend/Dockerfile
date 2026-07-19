# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Certaines plateformes (Render, Heroku...) exposent la connexion à la base via
# une seule variable DATABASE_URL au format postgres://user:pass@host:port/db.
# Si elle est présente, on la traduit en SPRING_DATASOURCE_URL (jdbc:postgresql://host:port/db)
# avant de démarrer. Sans cette variable (Docker Compose, CI), ce script ne change rien :
# SPRING_DATASOURCE_URL garde la valeur déjà fournie par l'environnement.
ENTRYPOINT ["sh", "-c", "if [ -n \"$DATABASE_URL\" ]; then export SPRING_DATASOURCE_URL=$(echo \"$DATABASE_URL\" | sed -E 's#^postgres(ql)?://.*@#jdbc:postgresql://#'); fi; exec java -jar app.jar"]
