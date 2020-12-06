# Introduction

Implemented the HTTP client and the HTTP remote file manager using UDP protocol. Because UDP protocol does not guarantee the transfer, to ensure reliability, the Automatic-Repeat-Request (ARQ) protocol, three-way handshaking (SYN, SYN-ACK, ACK), and closing a connection (FIN, FIN-ACK, ACK) are implemented.

# Technologies

Maven
Java
IDE: Eclipse

# Examples of use
1. Run router.go and set drop rate and max delay time
```
go run router.go --drop-rate=0.2
or
go run router.go --max-delay=500ms
or
go run router.go --drop-rate=0.2 --max-delay=500ms
```

2. Run the server and enter httpfs command
```
httpfs -v
```

3. Run the client and enter httpc command (support both GET and POST command)
```
httpc get -v 'http://localhost:8080/'
or
httpc post -v -f "any content" 'http://localhost:8080/dir3/dir33/text33.txt'
```

