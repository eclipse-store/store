# Spring Boot Eclipse Store Sample Application (Advanced example)

This application serves as a comprehensive display of the Eclipse Store Spring Boot extension's functionalities. 
It exemplifies the seamless integration of a pre-configured storage into an application as a bean, illustrates 
effective utilization of the locking API, and provides insights into designing an MVC application using Eclipse Store.

## Implementation Notes
* The Controller performs data conversion into JSON, therefore it must always operate with immutable data or, in the case of mutable data, it must receive a copy of such data.
* Minimum Java version is 17.
* Build tool is Apache Maven.

## Build

The generation of the executable jar file can be performed by issuing the following command:

```shell
mvn clean package
```

This will create an executable jar file **spring-boot3-advanced.jar** within the _target_ maven folder. 
This can be started by executing the following command

```shell
java -jar target/spring-boot3-advanced.jar
```

To launch the test page, open your browser at the following URL
```shell
http://localhost:8080/  
```

## Using Swagger UI

The example application provides a handy UI for sending REST request to provided endpoints. To use the UI, please run  the application
and open http://localhost:8080/swagger-ui/index.html in your browser. Then navigate to desired operation, fill required data and 
send the requests.

## Using Console

You may be interested in the state of your data. The example includes the Console for debugging purposes. 
Please open http://localhost:8080/store-console/
in your browser, enter http://localhost:8080/store-data/jokesStore/, click connect and inspect the data jokes in your store.
Enter http://localhost:8080/store-data/muppetsStore/, click connect and inspect the data muppets in your store.


## Using App rest api
This examples use Curl for Linux/macOS. You can use any other tool for sending HTTP requests.
Load sample data. Without this step, the application will not contain any data. It is not mandatory to execute this step, but without it you will have to add all your data manually.
```shell
curl --location --request POST 'http://localhost:8080/jokes/init' \
--header 'Content-Type: application/json'
```

Get all jokes
```shell
curl --location --request GET 'http://localhost:8080/jokes' \
--header 'Content-Type: application/json'
```

Get joke by Id
```shell
curl --location --request GET 'http://localhost:8080/jokes/joke?id=50' \
--header 'Content-Type: application/json' 
```

Add new Joke
```shell
curl --location --request POST 'http://localhost:8080/jokes/add' \
--header 'Content-Type: application/json' \
--data-raw '{great new joke}'
```

## Using App rest api for Muppets

Load sample data for Muppets. Without this step, the application will not contain any Muppet data. It is not mandatory to execute this step, but without it there will be no data in the storage.
```shell
curl --location --request POST 'http://localhost:8080/muppets/init' \
--header 'Content-Type: application/json'
```

Get all Muppets
```shell
curl --location --request GET 'http://localhost:8080/muppets' \
--header 'Content-Type: application/json'
```

Get Muppet by Id
```shell
curl --location --request GET 'http://localhost:8080/muppets/muppet?id=5' \
--header 'Content-Type: application/json' 
```
