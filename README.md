# Java gRPC ZipKin Sample

Proof of concept to send tracing data from gRPC java services to ZipKin

## Testing it

Starting zipkin server
```
docker run -d -p 9411:9411 openzipkin/zipkin
```


Installing sample application
```
mvn clean install
```

Executing the sample application
```
mvn exec:java
```

Take a look into tracing results: http://localhost:9411/


