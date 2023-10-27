# Spring Boot Eclipse Store Sample Application

This application is aimed at showcasing the capabilities of the Eclipse Store Spring Boot extension. It demonstrates how to integrate a pre-configured storage into an application as a bean, how to utilize the locking API, and how to design an MVC application using Eclipse Store.

## Implementation Notes
* Synchronization over shared data structures is handled using the annotations `@Read` and `@Write`.
* The Controller performs data conversion into JSON, therefore it must always operate with immutable data or, in the case of mutable data, it must receive a copy of such data.

## Build

The generation of the executable jar file can be performed by issuing the following command

```shell
mvn clean package
```

This will create an executable jar file **spring-boot3-simple.jar** within the _target_ maven folder. This can be started by executing the following command

```shell
java -jar target/spring-boot3-simple.jar
```

To launch the test page, open your browser at the following URL

```shell
http://localhost:8080/  
```

## Using App rest api
Load sample data
```shell
curl --location --request POST 'http://localhost:8080/init' \
--header 'Content-Type: application/json'
```

Get all jokes
```shell
curl --location --request GET 'http://localhost:8080' \
--header 'Content-Type: application/json'
```

Get joke by Id
```shell
curl --location --request GET 'http://localhost:8080/joke?id=50' \
--header 'Content-Type: application/json' 
```

Add new Joke
```shell
curl --location --request POST 'http://localhost:8080/add' \
--header 'Content-Type: application/json' \
--data-raw '{great new joke}'
```
