FROM enmasseproject/nodejs-base:6

RUN mkdir -p /opt/app-root/src/
WORKDIR /opt/app-root/src/

ARG version=latest
ADD build/console.tgz /opt/app-root/src/

RUN ["/bin/bash", "-c", "npm install"]

EXPOSE 56720 8080

CMD ["node", "/opt/app-root/src/bin/console_server.js"]
