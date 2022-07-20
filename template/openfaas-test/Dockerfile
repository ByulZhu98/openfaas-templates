# Get a build environment
FROM maven:3.8.5-openjdk-11 as build-aot

# Set working dir, copy whole project to /home/app
WORKDIR /home/app
COPY . /home/app/

# copy .jar file
# RUN cd function && mvn clean package -U -Dquarkus.package.type=uber-jar  -Dmaven.test.skip=true
RUN cp /home/app/function/target/demo-1.0-SNAPSHOT-runner.jar ./application.jar

# RUN java -Djarmode=layertools -jar application.jar extract

# use the openfaas image to get the watchdog
FROM openfaas/of-watchdog:0.8.1 as watchdog

# Create new image
FROM registry.access.redhat.com/ubi8/ubi-minimal as base

RUN microdnf install shadow-utils
RUN groupadd app && useradd app -g app -d /home/app && mkdir -p /home/app/config
USER app

WORKDIR /home/app

FROM base

COPY --from=build-aot /home/app/function/target/classes/*.properties /home/app/config/
COPY --from=build-aot /home/app/function/target/*-runner /home/app/function
COPY --from=watchdog /fwatchdog /usr/bin/fwatchdog

USER root
RUN ln -s /home/app/function /usr/bin/function
USER app

# fwatchdog收到web请求后的转发地址，java进程监听的端口
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'
ENV cgi_headers="true"
ENV fprocess="java -jar /home/app/function/target/demo-1.0-SNAPSHOT-runner.jar"
ENV mode="http"
ENV upstream_url="http://127.0.0.1:8080"

ENV exec_timeout="20s"
ENV write_timeout="25s"
ENV read_timeout="25s"

EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=5s CMD [ -e /tmp/.lock ] || exit 1

# 容器启动命令，这里是执行二进制文件fwatchdog
CMD ["fwatchdog"]

