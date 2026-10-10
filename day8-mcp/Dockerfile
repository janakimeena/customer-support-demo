# One image for the whole Day 8 demo. stdio MCP means the client launches the server as a
# child process, so the server jar has to sit in the same container as whoever talks to it.
#
#   docker compose run --rm explorer                  # wire explorer (raw JSON-RPC)
#   docker compose --profile inspector up inspector   # MCP Inspector at http://localhost:6274
#   docker compose up host                            # Spring AI + Gemini host at http://localhost:8080

# --- build: compile both modules and run the tests ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY booknest-mcp-server/pom.xml booknest-mcp-server/
COPY booknest-chat-host/pom.xml booknest-chat-host/
# Cache dependencies in their own layer, so a source change doesn't download them again.
RUN ./mvnw -B -q dependency:go-offline || true
COPY booknest-mcp-server/src booknest-mcp-server/src
COPY booknest-chat-host/src booknest-chat-host/src
RUN ./mvnw -B package \
 && mkdir /out \
 && cp booknest-mcp-server/target/booknest-mcp.jar /out/ \
 && cp booknest-chat-host/target/booknest-chat-host-*.jar /out/booknest-chat-host.jar

# --- app: server jar, host jar and the wire explorer ---
# A JDK, not a JRE: `java WireExplorer.java` compiles the source file at launch.
FROM eclipse-temurin:21-jdk AS app
WORKDIR /app
COPY --from=build /out/ ./
COPY mcp-wire-explorer/WireExplorer.java explorer/
ENV BOOKNEST_SERVER_JAR=/app/booknest-mcp.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/booknest-chat-host.jar"]

# --- inspector: MCP Inspector (Node) plus a Java runtime to launch the server ---
FROM node:22-slim AS inspector
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH
RUN npm install -g @modelcontextprotocol/inspector@2.10.1
COPY --from=build /out/booknest-mcp.jar /app/booknest-mcp.jar
COPY docker/inspector-mcp.json /app/mcp.json
# Inside the container the Inspector must listen on all interfaces for the published ports to
# reach the browser. docker-compose.yml publishes them on 127.0.0.1 only, and the auth token stays on.
ENV HOST=0.0.0.0 \
    DANGEROUSLY_BIND_ALL_INTERFACES=true \
    MCP_AUTO_OPEN_ENABLED=false \
    MCP_INSPECTOR_SECRET_STORE=memory
# 6274 is the UI, 6275 the MCP Apps sandbox.
EXPOSE 6274 6275
CMD ["mcp-inspector", "--web", "--config", "/app/mcp.json", "--server", "booknest"]
