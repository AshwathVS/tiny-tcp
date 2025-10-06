# Running it locally
Check the `demo_server` folder, and run `demo_server.Main` class to start the server. To configure the server properties, use the `application.json` file in the resources folder. 

The server currently has two endpoints `/hello` and `/delay`. For testing the server client interaction, run the `client.Main` class (configure the port the client connects to)
The format for client input is: PATH | header1=value1;header2=value2 (optional) | body (optional)

Example:
```
/hello|Auth=12345678|my_payload
11:37:56.182 [main] INFO client.NonBlockingClient -- Response from server: Hello from server, your body was: [my_payload], your headers was: [{Keep-Alive=true, Auth=12345678}]

/delay|Delay=500
11:38:07.501 [main] INFO client.NonBlockingClient -- Response from server: Waited for 500ms

/delay|Delay=1000
11:38:17.374 [main] INFO client.NonBlockingClient -- Response from server: Waited for 1000ms
```




# Server Request Format
Decided to try out a minimal format for request format.
```
 [HeaderLength: 4 bytes][BodyLength: 4 bytes][Header bytes][Body bytes]

Inside header bytes:
 [PathLength: 2 bytes][Path bytes][HeaderCount: 2 bytes][Header1][Header2]...

Each header entry:
 [KeyLength: 2 bytes][Key bytes][ValueLength: 2 bytes][Value bytes]
```


