# Websockets Overview

Websockets provide a continuous connection between the client and the server
that can be used to send individual messages back and forth at will without the
need for constant polling by the client.

At present, websocket messages are intended to be unidirectional from server to
client for the purpose of messaging clients when specific events occur on the
server (e.g. a new notification is delivered to a user, a task is approved by a
reviewer, etc) so that clients need not poll for these events using the REST
API.  The only supported client-originated messages are "housekeeping"
messages, such as subscribe and unsubscribe to control which types of messages
a client receives, and a ping message that clients can can use to test that the
websocket connection remains live. All of these client-generated messages are
consumed and processed privately within this package and are not propagated on
to other server code.  Communication of data to the server still must occur via
the REST API at this time.


## Class Overview

`WebSocketActor`s are responsible for bi-directional communication with client
websockets. A new instance is instantiated by the `WebSocketController` when a
new websocket connection is established, and that WebSocketActor instance
manages communication with that specific websocket connection.

When a WebSocketActor instance receives a message from the client websocket, it
consumes and processes the message as needed, including interacting with an
Akka mediator to subscribe or unsubscribe itself to message subscription types
on behalf of the client. When it receives a message from the server, it simply
transmits a JSON representation of that message to the client websocket.

`WebSocketMessages` defines case classes representing the various types of
messages and payload data that can be transmitted via websocket, along with
helper methods for easily and properly constructing those messages.

An [Akka Mediator](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html)
is used to manage publish/subscribe of the various message subscription types.
When a client sends a subscribe or unsubscribe message, its WebSocketActor will
process the message and subscribe or unsubscribe itself with the mediator for
the requested message subscription type. When server code wishes to transmit a
message to clients, the WebSocketPublisher sends the message to the mediator,
which then sends it to all subscribed WebSocketActors for transmission to their
clients.

`WebSocketPublisher` is an Akka actor responsible for communicating
server-generated messages to the mediator for publication to subscribed
clients.

`WebSocketProvider` provides a convenient `sendMessage` function that server
code can use to easily send a message via the WebSocketPublisher without having
to interact directly or be aware of the Akka actor system. Most server code
will wish to use this method to send messages rather than trying to deal with
the Akka actor system.


## Adding a New Message Type

1. Edit WebSocketMessages and add:
  - case class for payload data
  - case class for message
  - helper method for generating message
  - writes for data class
  - writes for message class
  - new subscription type if needed

2. Edit WebSocketActor and add:
  - match case for the new message type
