FROM node:latest

COPY . /usr/src/app
WORKDIR /usr/src/app/server
RUN npm install
ENTRYPOINT ["node", "runner.js"]
EXPOSE 8888
