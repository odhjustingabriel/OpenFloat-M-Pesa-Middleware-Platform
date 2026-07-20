FROM eclipse-temurin:21-jre-alpine
ARG JAR_FILE=target/openfloat-gateway-*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8443
ENTRYPOINT ["java","-jar","/app.jar"]
