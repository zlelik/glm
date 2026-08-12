# syntax=docker/dockerfile:1

# ================================================================================================
# Stage 1 (builder): Chainguard's "-dev" Tomcat image is the SAME build as ":latest" but ships a
# shell and basic tools. We use it to read Chainguard's CURRENT server.xml - so every rebuild picks
# up their latest hardened config - and inject our HTTPS connector into it. This edit must happen
# here because the final hardened image (stage 2) has no shell.
#
# Both stages use the moving ":latest" tag, so `docker compose build --pull` refreshes both to the
# newest Chainguard release together.
# ================================================================================================
FROM cgr.dev/chainguard/tomcat:latest-dev AS conf
USER root

# Our HTTPS connector snippet (a readable, version-controlled file).
COPY docker/tomcat/https-connector.xml /tmp/https-connector.xml

# Insert the connector immediately before the closing </Service> tag of Chainguard's server.xml.
# awk (POSIX, present in the -dev image) avoids fragile multi-line sed substitutions.
# NOTE: assumes CATALINA_HOME=/usr/local/tomcat (the documented path); adjust if that ever changes.
RUN awk '/<\/Service>/ { while ((getline line < "/tmp/https-connector.xml") > 0) print line } { print }' \
        /usr/local/tomcat/conf/server.xml > /tmp/server.xml \
    && mv /tmp/server.xml /usr/local/tomcat/conf/server.xml

# ================================================================================================
# Stage 2 (final): the hardened, shell-less Chainguard image that actually gets deployed. We only
# copy in the patched server.xml. The WAR, the TLS cert/key and the logs are all supplied at runtime
# via volumes (see docker-compose.yml), so none of them are baked into the image.
# ================================================================================================
FROM cgr.dev/chainguard/tomcat:latest
COPY --from=conf /usr/local/tomcat/conf/server.xml /usr/local/tomcat/conf/server.xml

# HTTPS connector port (compose maps host 443 -> container 8443).
EXPOSE 8443
