FROM youjunl/kkfileview-base:latest
ADD server/target/kkFileView-*.tar.gz /opt/
ENV KKFILEVIEW_BIN_FOLDER=/opt/kkFileView-4.4.0-beta/bin
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Dspring.config.location=/opt/kkFileView-4.4.0-beta/config/application.properties","-jar","/opt/kkFileView-4.4.0-beta/bin/kkFileView-4.4.0-beta.jar"]
EXPOSE 8012
