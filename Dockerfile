FROM openjdk:8-jre

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY mod-configuration-server/target/*-fat.jar $VERTICLE_HOME/module.jar

# Create user/group 'folio'
RUN groupadd folio && \
    useradd -r -d $VERTICLE_HOME -g folio -M folio && \
    chown -R folio.folio $VERTICLE_HOME

# Run as this user
USER folio

# Launch the verticle
WORKDIR $VERTICLE_HOME

# Expose this port locally in the container
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "module.jar"]
CMD []
