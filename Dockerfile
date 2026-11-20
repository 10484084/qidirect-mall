FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY . .
RUN mvn -q -B -s settings.xml -DskipTests package

FROM eclipse-temurin:17-jre

# 统一时区为东八区，避免 JVM(UTC) 与 MySQL(CST) 混用导致超时关单/支付过期延迟 8 小时
ENV TZ=Asia/Shanghai

WORKDIR /app
COPY --from=build /build/mall-admin/target/mall-admin.jar /app/mall-admin.jar
COPY --from=build /build/mall-api/target/mall-api.jar /app/mall-api.jar
COPY --from=build /build/mall-job/target/mall-job.jar /app/mall-job.jar

EXPOSE 8080 8081

ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar"]
CMD ["/app/mall-admin.jar"]
