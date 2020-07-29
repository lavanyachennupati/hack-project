FROM openjdk:8-jre-alpine
MAINTAINER  Lavanya Chennupati <lavanya4chennupati@gmail.com>
#ENTRYPOINT ["/bin/bash", "-c", "exec /usr/bin/java $JVM_DEFAULT_ARGS $JVM_ARGS -jar /usr/share/tugboat/tugboat-api-service.jar \"$@\"", "bash"]
CMD ["/usr/bin/java", "-jar", "/usr/share/api-read-cache/api-read-cache.jar"]
COPY target/lib /usr/share/api-read-cache/lib
COPY target/api-read-cache-1.0-SNAPSHOT.jar /usr/share/api-read-cache/api-read-cache.jar
