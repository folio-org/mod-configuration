FROM alpine:latest
RUN apk --no-cache add openjdk8-jre

ENV VERTICLE_FILE configuration-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME

EXPOSE 8081

ENTRYPOINT ["sh", "-c"]
CMD ["java -jar $VERTICLE_FILE"]
